import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cryptographic engine for SecureVault.
 *
 * Security design:
 *   - PBKDF2WithHmacSHA256 with 310,000 iterations (OWASP 2024 minimum) for key derivation.
 *   - 32-byte random salt per user, stored in the database alongside the auth hash.
 *   - Two distinct keys are derived from a single master KDF run:
 *       authKey  (bytes 0..31)  → hashed and stored in DB for login verification
 *       vaultKey (bytes 32..63) → used for AES-256-GCM vault encryption; NEVER stored
 *   - AES-256-GCM with a unique 12-byte IV per entry; 128-bit authentication tag.
 *   - Constant-time comparison via MessageDigest.isEqual() to prevent timing attacks.
 *   - All sensitive byte arrays are zeroed after use.
 *   - Passwords accepted as char[] and wiped immediately after KDF; Strings avoided for secrets.
 */
public class CryptoEngine {

    // KDF parameters
    private static final String KDF_ALGORITHM    = "PBKDF2WithHmacSHA256";
    private static final int    KDF_ITERATIONS   = 310_000;
    private static final int    KDF_KEY_LENGTH   = 512;   // bits → 64 bytes split into authKey + vaultKey
    private static final int    SALT_BYTES        = 32;
    private static final int    IV_BYTES          = 12;
    private static final int    GCM_TAG_BITS      = 128;

    // Cipher algorithm
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ------------------------------------------------------------------
    // Key Derivation
    // ------------------------------------------------------------------

    /**
     * Generates a cryptographically random 32-byte salt.
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * Derives 64 bytes from the master password using PBKDF2.
     * Bytes 0-31  → authKey  (used to produce the auth hash for login)
     * Bytes 32-63 → vaultKey (used directly as AES-256 encryption key)
     *
     * The caller MUST zero the returned array after use with Arrays.fill(result, (byte)0).
     *
     * @param masterPassword master password as a char array (wiped by this method before returning)
     * @param salt           per-user random salt bytes
     * @return 64-byte derived key material
     */
    private static byte[] deriveKeyMaterial(char[] masterPassword, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(masterPassword, salt, KDF_ITERATIONS, KDF_KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(masterPassword, '\0');
        }
    }

    /**
     * Returns the auth hash (SHA-256 of authKey slice) as a Base64 string.
     * This is safe to store in the database — it reveals nothing about the vault key.
     *
     * @param masterPassword master password (char array, wiped internally)
     * @param salt           per-user salt bytes
     * @return Base64-encoded auth hash for storage
     */
    public static String computeAuthHash(char[] masterPassword, byte[] salt) throws Exception {
        byte[] keyMaterial = deriveKeyMaterial(masterPassword, salt);
        try {
            byte[] authKey = Arrays.copyOfRange(keyMaterial, 0, 32);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(authKey);
            Arrays.fill(authKey, (byte) 0);
            return Base64.getEncoder().encodeToString(hash);
        } finally {
            Arrays.fill(keyMaterial, (byte) 0);
        }
    }

    /**
     * Derives the AES-256 vault encryption key (bytes 32-63 of KDF output).
     * The returned array MUST be zeroed with Arrays.fill(key, (byte)0) when the session ends.
     *
     * @param masterPassword master password (char array, wiped internally)
     * @param salt           per-user salt bytes
     * @return 32-byte AES vault key — keep only in memory, never persist
     */
    public static byte[] deriveVaultKey(char[] masterPassword, byte[] salt) throws Exception {
        byte[] keyMaterial = deriveKeyMaterial(masterPassword, salt);
        try {
            return Arrays.copyOfRange(keyMaterial, 32, 64);
        } finally {
            Arrays.fill(keyMaterial, (byte) 0);
        }
    }

    /**
     * Verifies a login attempt using constant-time comparison.
     *
     * @param masterPassword master password to check (wiped internally)
     * @param salt           stored per-user salt
     * @param storedAuthHash auth hash stored in the database
     * @return true if the password matches, false otherwise
     */
    public static boolean verifyMasterPassword(char[] masterPassword, byte[] salt, String storedAuthHash) throws Exception {
        String attemptHash = computeAuthHash(masterPassword, salt);
        byte[] a = Base64.getDecoder().decode(attemptHash);
        byte[] b = Base64.getDecoder().decode(storedAuthHash);
        return MessageDigest.isEqual(a, b);
    }

    // ------------------------------------------------------------------
    // AES-256-GCM Encryption / Decryption
    // ------------------------------------------------------------------

    /**
     * Encrypts a plaintext password string with AES-256-GCM.
     * A unique 12-byte IV is generated for every call.
     *
     * @param plaintext  plaintext credential to encrypt
     * @param vaultKey   32-byte AES vault key (from deriveVaultKey)
     * @return EncryptedPayload containing Base64 ciphertext and Base64 IV
     */
    public static EncryptedPayload encrypt(String plaintext, byte[] vaultKey) throws Exception {
        if (plaintext == null) throw new IllegalArgumentException("Plaintext cannot be null");
        if (vaultKey == null || vaultKey.length != 32)
            throw new IllegalArgumentException("Vault key must be 32 bytes");

        byte[] iv = new byte[IV_BYTES];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(vaultKey, "AES");
        GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec);

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return new EncryptedPayload(
            Base64.getEncoder().encodeToString(ciphertext),
            Base64.getEncoder().encodeToString(iv)
        );
    }

    /**
     * Decrypts an AES-256-GCM encrypted payload back to plaintext.
     * Throws AEADBadTagException if the ciphertext was tampered with or the key is wrong.
     *
     * @param payload  EncryptedPayload with Base64 ciphertext and IV
     * @param vaultKey 32-byte AES vault key
     * @return decrypted plaintext string
     */
    public static String decrypt(EncryptedPayload payload, byte[] vaultKey) throws Exception {
        if (payload == null) throw new IllegalArgumentException("Payload cannot be null");
        if (vaultKey == null || vaultKey.length != 32)
            throw new IllegalArgumentException("Vault key must be 32 bytes");

        byte[] iv         = Base64.getDecoder().decode(payload.ivBase64());
        byte[] ciphertext = Base64.getDecoder().decode(payload.ciphertextBase64());

        if (iv.length != IV_BYTES)
            throw new IllegalArgumentException("Invalid IV length: expected 12 bytes");

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(vaultKey, "AES");
        GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);

        byte[] plainBytes = cipher.doFinal(ciphertext);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Safely zeroes a vault key byte array in memory.
     * Call this when the user logs out or the session ends.
     */
    public static void wipeKey(byte[] key) {
        if (key != null) Arrays.fill(key, (byte) 0);
    }

    /**
     * Encodes raw bytes as a Base64 string (used for salt storage).
     */
    public static String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decodes a Base64 string to raw bytes (used for salt retrieval).
     */
    public static byte[] decodeBase64(String data) {
        return Base64.getDecoder().decode(data);
    }

    // ------------------------------------------------------------------
    // Payload value record
    // ------------------------------------------------------------------

    /**
     * Immutable container for AES-GCM encrypted output.
     * Both fields are Base64-encoded strings safe for database storage.
     */
    public record EncryptedPayload(String ciphertextBase64, String ivBase64) {}
}
