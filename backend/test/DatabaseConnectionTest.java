import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseConnectionTest {

    @Test
    @DisplayName("getConnection with null password should throw IllegalArgumentException")
    void testNullPasswordThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConnection.getConnection("127.0.0.1", 3306, "password_manager", "pm_user", null);
        });
    }

    @Test
    @DisplayName("getConnection with closed or null connection in getDatabaseVersion throws IllegalArgumentException")
    void testNullConnectionThrowsInGetVersion() {
        assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConnection.getDatabaseVersion(null);
        });
    }

    @Test
    @DisplayName("getConnection succeeds when valid credentials provided in environment or .env")
    void testLiveConnectionWhenConfigured() {
        boolean canConnect = DatabaseConnection.testConnection();
        Assumptions.assumeTrue(canConnect, "MariaDB credentials not configured or server unreachable; skipping live test");

        assertDoesNotThrow(() -> {
            try (Connection conn = DatabaseConnection.getConnection()) {
                assertNotNull(conn);
                assertFalse(conn.isClosed());
                String version = DatabaseConnection.getDatabaseVersion(conn);
                assertNotNull(version);
                assertTrue(
                    version.toLowerCase().contains("mariadb") || version.toLowerCase().contains("mysql"),
                    "Expected MariaDB or MySQL version string, got: " + version
                );
            }
        });
    }
}
