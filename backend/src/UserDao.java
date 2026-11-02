import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Data Access Object for SecureVault user accounts.
 *
 * Security guarantees:
 *   - Master password is accepted as char[] and wiped immediately after use.
 *   - Only the auth hash is written to the database; the vault key is never persisted.
 *   - A successful authenticate() call returns the ephemeral vault key for the session.
 *   - The caller must wipe the vault key with CryptoEngine.wipeKey() on logout.
 */
public class UserDao {

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Registers a new user account.
     * Generates a random salt, derives the auth hash, and stores both in the database.
     * The vault key is derived but immediately wiped — it will be re-derived on login.
     *
     * @param username       chosen username (must be unique)
     * @param masterPassword master password as char[] (wiped by this method)
     * @throws IllegalArgumentException if username is blank or already taken
     * @throws SQLException on database errors
     */
    public static void register(String username, char[] masterPassword) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            Arrays.fill(masterPassword, '\0');
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        if (masterPassword == null || masterPassword.length == 0) {
            throw new IllegalArgumentException("Master password cannot be empty.");
        }
        if (usernameExists(username.trim())) {
            Arrays.fill(masterPassword, '\0');
            throw new IllegalArgumentException("Username '" + username.trim() + "' is already taken.");
        }

        byte[] salt = CryptoEngine.generateSalt();
        String saltBase64 = CryptoEngine.encodeBase64(salt);

        // computeAuthHash wipes masterPassword internally
        String authHash = CryptoEngine.computeAuthHash(masterPassword, salt);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO users (username, kdf_salt, auth_hash) VALUES (?, ?, ?)")) {
            ps.setString(1, username.trim());
            ps.setString(2, saltBase64);
            ps.setString(3, authHash);
            ps.executeUpdate();
        }
    }

    /**
     * Authenticates a user and returns the ephemeral vault key.
     *
     * @param username       username to authenticate
     * @param masterPassword master password as char[] (wiped by this method)
     * @return 32-byte vault key if credentials are correct — MUST be wiped with CryptoEngine.wipeKey() on logout
     * @throws SecurityException    if credentials are invalid (username not found or wrong password)
     * @throws SQLException         on database errors
     */
    public static byte[] authenticate(String username, char[] masterPassword) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            Arrays.fill(masterPassword, '\0');
            throw new SecurityException("Invalid credentials.");
        }

        UserRecord record = findUserByUsername(username.trim());
        if (record == null) {
            // Timing guard: still derive a key even for unknown users to prevent enumeration
            char[] dummy = Arrays.copyOf(masterPassword, masterPassword.length);
            Arrays.fill(masterPassword, '\0');
            byte[] dummySalt = CryptoEngine.generateSalt();
            byte[] dummyKey = CryptoEngine.deriveVaultKey(dummy, dummySalt);
            CryptoEngine.wipeKey(dummyKey);
            throw new SecurityException("Invalid credentials.");
        }

        byte[] salt = CryptoEngine.decodeBase64(record.kdfSalt());

        // Verify password (masterPassword is wiped inside verifyMasterPassword)
        char[] passwordCopy = Arrays.copyOf(masterPassword, masterPassword.length);
        Arrays.fill(masterPassword, '\0');

        boolean valid = CryptoEngine.verifyMasterPassword(passwordCopy, salt, record.authHash());

        if (!valid) {
            throw new SecurityException("Invalid credentials.");
        }

        // Derive the vault key for this session
        char[] passwordForKey = Arrays.copyOf(passwordCopy, passwordCopy.length);
        Arrays.fill(passwordCopy, '\0');
        return CryptoEngine.deriveVaultKey(passwordForKey, salt);
    }

    /**
     * Returns the numeric user ID for a given username.
     *
     * @param username the username to look up
     * @return user ID or -1 if not found
     */
    public static int getUserId(String username) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("id") : -1;
            }
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static boolean usernameExists(String username) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM users WHERE username = ? LIMIT 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static UserRecord findUserByUsername(String username) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, username, kdf_salt, auth_hash FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserRecord(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("kdf_salt"),
                        rs.getString("auth_hash")
                    );
                }
                return null;
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal value record
    // ------------------------------------------------------------------

    private record UserRecord(int id, String username, String kdfSalt, String authHash) {}
}
