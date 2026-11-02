import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoEngineTest {

    @Test
    @DisplayName("PBKDF2 derives valid 32-byte vault key and deterministic auth hash")
    void testKeyDerivation() throws Exception {
        byte[] salt = CryptoEngine.generateSalt();
        assertEquals(32, salt.length);

        char[] pass1 = "MasterKey#2026".toCharArray();
        char[] pass2 = "MasterKey#2026".toCharArray();

        String authHash1 = CryptoEngine.computeAuthHash(pass1, salt);
        String authHash2 = CryptoEngine.computeAuthHash(pass2, salt);

        assertNotNull(authHash1);
        assertEquals(authHash1, authHash2, "Identical password and salt must produce identical auth hash");

        char[] pass3 = "MasterKey#2026".toCharArray();
        byte[] vaultKey = CryptoEngine.deriveVaultKey(pass3, salt);
        assertEquals(32, vaultKey.length, "Vault encryption key must be exactly 32 bytes (AES-256)");
        CryptoEngine.wipeKey(vaultKey);
    }

    @Test
    @DisplayName("AES-256-GCM encryption and decryption roundtrip produces exact plaintext")
    void testEncryptDecryptRoundtrip() throws Exception {
        byte[] salt = CryptoEngine.generateSalt();
        char[] pass = "SecretPassphrase!".toCharArray();
        byte[] key = CryptoEngine.deriveVaultKey(pass, salt);

        String original = "MySuperSecretBankPassword#99&";
        CryptoEngine.EncryptedPayload payload = CryptoEngine.encrypt(original, key);

        assertNotNull(payload.ciphertextBase64());
        assertNotNull(payload.ivBase64());

        String decrypted = CryptoEngine.decrypt(payload, key);
        assertEquals(original, decrypted);

        CryptoEngine.wipeKey(key);
    }

    @Test
    @DisplayName("Each encryption generates a unique random IV and distinct ciphertext")
    void testUniqueIVPerEncryption() throws Exception {
        byte[] salt = CryptoEngine.generateSalt();
        byte[] key = CryptoEngine.deriveVaultKey("Passphrase".toCharArray(), salt);

        String text = "SamePassword123";
        CryptoEngine.EncryptedPayload p1 = CryptoEngine.encrypt(text, key);
        CryptoEngine.EncryptedPayload p2 = CryptoEngine.encrypt(text, key);

        assertNotEquals(p1.ivBase64(), p2.ivBase64(), "IV must never be reused across encryptions");
        assertNotEquals(p1.ciphertextBase64(), p2.ciphertextBase64());

        CryptoEngine.wipeKey(key);
    }

    @Test
    @DisplayName("Decryption fails when ciphertext is tampered with (GCM authentication check)")
    void testTamperedCiphertextFails() throws Exception {
        byte[] salt = CryptoEngine.generateSalt();
        byte[] key = CryptoEngine.deriveVaultKey("Passphrase".toCharArray(), salt);

        CryptoEngine.EncryptedPayload payload = CryptoEngine.encrypt("SecretData", key);

        // Tamper with ciphertext by altering a base64 character
        String tamperedCipher = payload.ciphertextBase64();
        char altered = tamperedCipher.charAt(0) == 'A' ? 'B' : 'A';
        tamperedCipher = altered + tamperedCipher.substring(1);

        CryptoEngine.EncryptedPayload tamperedPayload = new CryptoEngine.EncryptedPayload(tamperedCipher, payload.ivBase64());

        assertThrows(Exception.class, () -> {
            CryptoEngine.decrypt(tamperedPayload, key);
        });

        CryptoEngine.wipeKey(key);
    }

    @Test
    @DisplayName("Decryption fails when an incorrect key is supplied")
    void testWrongKeyDecryptionFails() throws Exception {
        byte[] salt = CryptoEngine.generateSalt();
        byte[] correctKey = CryptoEngine.deriveVaultKey("CorrectKey".toCharArray(), salt);
        byte[] wrongKey = CryptoEngine.deriveVaultKey("WrongKey".toCharArray(), salt);

        CryptoEngine.EncryptedPayload payload = CryptoEngine.encrypt("SecretMessage", correctKey);

        assertThrows(Exception.class, () -> {
            CryptoEngine.decrypt(payload, wrongKey);
        });

        CryptoEngine.wipeKey(correctKey);
        CryptoEngine.wipeKey(wrongKey);
    }

    @Test
    @DisplayName("wipeKey successfully zeros all bytes in memory")
    void testWipeKey() {
        byte[] key = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        CryptoEngine.wipeKey(key);
        for (byte b : key) {
            assertEquals(0, b, "Byte in wiped key must be zero");
        }
    }
}
