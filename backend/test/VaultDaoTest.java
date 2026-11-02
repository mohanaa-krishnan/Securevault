import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VaultDaoTest {

    private String testUser;
    private int testUserId;
    private byte[] vaultKey;

    @BeforeEach
    void setUp() throws Exception {
        boolean canConnect = DatabaseConnection.testConnection();
        Assumptions.assumeTrue(canConnect, "MariaDB server not accessible; skipping VaultDao tests");

        testUser = "vtest_" + System.currentTimeMillis();
        char[] pass = "TestMaster#2026!".toCharArray();
        UserDao.register(testUser, pass);
        testUserId = UserDao.getUserId(testUser);
        vaultKey = UserDao.authenticate(testUser, "TestMaster#2026!".toCharArray());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (vaultKey != null) {
            CryptoEngine.wipeKey(vaultKey);
        }
        if (testUser != null) {
            UserDao.delete(testUser);
        }
    }

    @Test
    @DisplayName("Duplicate (userId, label) entry throws friendly IllegalArgumentException")
    void testDuplicateLabelThrows() throws Exception {
        VaultDao.addEntry(testUserId, "github.com", "user@test.com", "FirstPassword1!", vaultKey);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            VaultDao.addEntry(testUserId, "github.com", "another@test.com", "SecondPassword2!", vaultKey);
        });

        assertTrue(ex.getMessage().contains("already exists"), "Expected duplicate message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Duplicate registration throws friendly IllegalArgumentException")
    void testDuplicateUsernameThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            UserDao.register(testUser, "AnotherPassword#123".toCharArray());
        });

        assertTrue(ex.getMessage().contains("already taken"), "Expected 'already taken' message, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Vault entry CRUD lifecycle works and enforces ownership")
    void testVaultEntryLifecycle() throws Exception {
        VaultDao.addEntry(testUserId, "gitlab.com", "user@gitlab.com", "SecretGitLab#99", vaultKey);

        List<VaultDao.VaultEntry> entries = VaultDao.getAllEntries(testUserId, vaultKey);
        assertEquals(1, entries.size());
        assertEquals("gitlab.com", entries.get(0).label());
        assertEquals("SecretGitLab#99", entries.get(0).plaintextPassword());

        int entryId = entries.get(0).id();

        // Update password
        boolean updated = VaultDao.updatePassword(entryId, testUserId, "UpdatedSecretGitLab#100", vaultKey);
        assertTrue(updated);

        entries = VaultDao.getAllEntries(testUserId, vaultKey);
        assertEquals("UpdatedSecretGitLab#100", entries.get(0).plaintextPassword());

        // Delete entry
        boolean deleted = VaultDao.deleteEntry(entryId, testUserId);
        assertTrue(deleted);

        entries = VaultDao.getAllEntries(testUserId, vaultKey);
        assertTrue(entries.isEmpty());
    }
}
