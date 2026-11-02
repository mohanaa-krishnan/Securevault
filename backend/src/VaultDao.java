import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for SecureVault vault entries.
 *
 * Every method requires a userId to ensure strict per-user isolation.
 * Passwords are stored encrypted (AES-256-GCM); the vaultKey is never
 * written to the database and must be supplied by the caller for each operation.
 */
public class VaultDao {

    // ------------------------------------------------------------------
    // Public record representing a decrypted vault entry
    // ------------------------------------------------------------------

    public record VaultEntry(
        int id,
        int userId,
        String label,
        String accountUsername,
        String plaintextPassword   // populated only after decryption
    ) {}

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    /**
     * Adds a new encrypted credential to the vault.
     *
     * @param userId           authenticated user's ID
     * @param label            website or app name
     * @param accountUsername  email or username for that site
     * @param plainPassword    plaintext password to encrypt and store
     * @param vaultKey         32-byte session vault key (from UserDao.authenticate)
     * @throws Exception on crypto or database errors
     */
    public static void addEntry(int userId, String label, String accountUsername,
                                String plainPassword, byte[] vaultKey) throws Exception {
        validateUserId(userId);
        validateString(label, "Label");
        validateString(accountUsername, "Account username");
        validateString(plainPassword, "Password");

        CryptoEngine.EncryptedPayload payload = CryptoEngine.encrypt(plainPassword, vaultKey);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO vault_entries (user_id, label, account_username, encrypted_password, iv) " +
                 "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setString(2, label.trim());
            ps.setString(3, accountUsername.trim());
            ps.setString(4, payload.ciphertextBase64());
            ps.setString(5, payload.ivBase64());
            ps.executeUpdate();
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("An entry for '" + label.trim() + "' already exists in your vault.");
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    /**
     * Returns all vault entries for the user, with decrypted passwords.
     *
     * @param userId   authenticated user's ID
     * @param vaultKey 32-byte session vault key
     * @return list of VaultEntry records (decrypted)
     */
    public static List<VaultEntry> getAllEntries(int userId, byte[] vaultKey) throws Exception {
        validateUserId(userId);
        List<VaultEntry> entries = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, user_id, label, account_username, encrypted_password, iv " +
                 "FROM vault_entries WHERE user_id = ? ORDER BY label ASC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String plainPassword = decryptEntry(rs, vaultKey);
                    entries.add(new VaultEntry(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("label"),
                        rs.getString("account_username"),
                        plainPassword
                    ));
                }
            }
        }
        return entries;
    }

    /**
     * Searches for vault entries whose label contains the given keyword (case-insensitive).
     *
     * @param userId   authenticated user's ID
     * @param keyword  search term (partial match on label)
     * @param vaultKey 32-byte session vault key
     * @return list of matching VaultEntry records (decrypted)
     */
    public static List<VaultEntry> searchByLabel(int userId, String keyword, byte[] vaultKey) throws Exception {
        validateUserId(userId);
        List<VaultEntry> entries = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, user_id, label, account_username, encrypted_password, iv " +
                 "FROM vault_entries WHERE user_id = ? AND LOWER(label) LIKE LOWER(?) ORDER BY label ASC")) {
            ps.setInt(1, userId);
            ps.setString(2, "%" + (keyword == null ? "" : keyword.trim()) + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String plainPassword = decryptEntry(rs, vaultKey);
                    entries.add(new VaultEntry(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("label"),
                        rs.getString("account_username"),
                        plainPassword
                    ));
                }
            }
        }
        return entries;
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    /**
     * Updates the password for an existing vault entry.
     * The entry must belong to the given userId (enforced in SQL WHERE clause).
     *
     * @param entryId      ID of the vault entry to update
     * @param userId       authenticated user's ID (ownership guard)
     * @param newPassword  new plaintext password to encrypt and store
     * @param vaultKey     32-byte session vault key
     * @return true if the entry was found and updated, false if not found
     */
    public static boolean updatePassword(int entryId, int userId,
                                         String newPassword, byte[] vaultKey) throws Exception {
        validateUserId(userId);
        validateString(newPassword, "New password");

        CryptoEngine.EncryptedPayload payload = CryptoEngine.encrypt(newPassword, vaultKey);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE vault_entries SET encrypted_password = ?, iv = ?, updated_at = CURRENT_TIMESTAMP " +
                 "WHERE id = ? AND user_id = ?")) {
            ps.setString(1, payload.ciphertextBase64());
            ps.setString(2, payload.ivBase64());
            ps.setInt(3, entryId);
            ps.setInt(4, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Updates the account username for an existing vault entry.
     * The entry must belong to the given userId.
     *
     * @param entryId         ID of the vault entry to update
     * @param userId          authenticated user's ID (ownership guard)
     * @param newAccountUser  new account username/email to store
     * @return true if the entry was found and updated, false if not found
     */
    public static boolean updateAccountUsername(int entryId, int userId, String newAccountUser) throws SQLException {
        validateUserId(userId);
        validateString(newAccountUser, "Account username");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE vault_entries SET account_username = ?, updated_at = CURRENT_TIMESTAMP " +
                 "WHERE id = ? AND user_id = ?")) {
            ps.setString(1, newAccountUser.trim());
            ps.setInt(2, entryId);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    /**
     * Deletes a single vault entry by ID.
     * The entry must belong to the given userId (enforced in SQL WHERE clause).
     *
     * @param entryId  ID of the vault entry to delete
     * @param userId   authenticated user's ID (ownership guard)
     * @return true if the entry was found and deleted, false if not found
     */
    public static boolean deleteEntry(int entryId, int userId) throws SQLException {
        validateUserId(userId);

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM vault_entries WHERE id = ? AND user_id = ?")) {
            ps.setInt(1, entryId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static String decryptEntry(ResultSet rs, byte[] vaultKey) throws Exception {
        String ciphertext = rs.getString("encrypted_password");
        String iv = rs.getString("iv");
        return CryptoEngine.decrypt(new CryptoEngine.EncryptedPayload(ciphertext, iv), vaultKey);
    }

    private static void validateUserId(int userId) {
        if (userId <= 0) throw new IllegalArgumentException("Invalid user ID: " + userId);
    }

    private static void validateString(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }
    }
}
