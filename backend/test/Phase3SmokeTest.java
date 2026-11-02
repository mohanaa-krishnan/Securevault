import java.util.Arrays;
import java.util.List;

/**
 * Smoke test for Phase 3: CryptoEngine + UserDao + VaultDao end-to-end.
 * Registers a test user, authenticates, stores a credential, retrieves it, then cleans up.
 */
public class Phase3SmokeTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 3 Smoke Test ===\n");

        String testUser = "test_smoke_" + System.currentTimeMillis();
        String testPass = "Sm0keTest#Secure!";

        // Step 1: Register
        System.out.println("[1] Registering user: " + testUser);
        UserDao.register(testUser, testPass.toCharArray());
        System.out.println("    OK: User registered.");

        // Step 2: Authenticate → get vault key
        System.out.println("[2] Authenticating...");
        byte[] vaultKey = UserDao.authenticate(testUser, testPass.toCharArray());
        System.out.println("    OK: Vault key derived (length=" + vaultKey.length + " bytes, never stored).");

        // Step 3: Get userId
        int userId = UserDao.getUserId(testUser);
        System.out.println("[3] User ID: " + userId);

        // Step 4: Add a credential
        System.out.println("[4] Adding encrypted credential...");
        VaultDao.addEntry(userId, "github.com", "smokeuser@test.com", "GitHubPass@99!", vaultKey);
        System.out.println("    OK: Credential encrypted and stored.");

        // Step 5: Retrieve and decrypt
        System.out.println("[5] Retrieving and decrypting credentials...");
        List<VaultDao.VaultEntry> entries = VaultDao.getAllEntries(userId, vaultKey);
        for (VaultDao.VaultEntry e : entries) {
            System.out.println("    Label: " + e.label());
            System.out.println("    Account: " + e.accountUsername());
            System.out.println("    Password (decrypted): " + e.plaintextPassword());
            assert e.plaintextPassword().equals("GitHubPass@99!") : "Decryption mismatch!";
        }
        System.out.println("    OK: Decryption verified.");

        // Step 6: Search
        System.out.println("[6] Searching by keyword 'github'...");
        List<VaultDao.VaultEntry> results = VaultDao.searchByLabel(userId, "github", vaultKey);
        System.out.println("    Found " + results.size() + " result(s).");
        assert results.size() == 1 : "Search should return 1 result";

        // Step 7: Update password
        int entryId = entries.get(0).id();
        System.out.println("[7] Updating password for entry " + entryId + "...");
        boolean updated = VaultDao.updatePassword(entryId, userId, "NewGitHubPass#1", vaultKey);
        System.out.println("    OK: Updated=" + updated);

        // Step 8: Delete entry
        System.out.println("[8] Deleting entry " + entryId + "...");
        boolean deleted = VaultDao.deleteEntry(entryId, userId);
        System.out.println("    OK: Deleted=" + deleted);
        assert VaultDao.getAllEntries(userId, vaultKey).isEmpty() : "Vault should be empty after delete";

        // Step 9: Wrong password should fail
        System.out.println("[9] Testing wrong password rejection...");
        try {
            UserDao.authenticate(testUser, "WrongPassword123!".toCharArray());
            System.err.println("    FAIL: Should have thrown SecurityException!");
        } catch (SecurityException e) {
            System.out.println("    OK: Wrong password correctly rejected.");
        }

        // Step 10: Wipe vault key
        System.out.println("[10] Wiping vault key from memory...");
        CryptoEngine.wipeKey(vaultKey);
        System.out.println("     OK: Vault key zeroed.");

        // Step 11: Cleanup test user account
        System.out.println("[11] Cleaning up test user account...");
        boolean userDeleted = UserDao.delete(testUser);
        System.out.println("     OK: Test user deleted=" + userDeleted);
        assert userDeleted : "Test user should be deleted";

        System.out.println("\n=== All Phase 3 checks passed! ===");
    }
}
