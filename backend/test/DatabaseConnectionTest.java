import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

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
    @DisplayName("getConnection with empty password environment variable throws IllegalStateException when unset")
    void testMissingEnvPasswordThrows() {
        String dbPass = System.getenv("DB_PASSWORD");
        if (dbPass == null || dbPass.trim().isEmpty()) {
            assertThrows(IllegalStateException.class, () -> {
                DatabaseConnection.getConnection();
            });
        }
    }

    @Test
    @DisplayName("getConnection succeeds when valid credentials provided in environment")
    void testLiveConnectionWhenConfigured() {
        String dbPass = System.getenv("DB_PASSWORD");
        if (dbPass != null && !dbPass.trim().isEmpty()) {
            assertDoesNotThrow(() -> {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    assertNotNull(conn);
                    assertFalse(conn.isClosed());
                    String version = DatabaseConnection.getDatabaseVersion();
                    assertNotNull(version);
                    assertTrue(version.toLowerCase().contains("mariadb") || version.toLowerCase().contains("mysql"));
                }
            });
        }
    }
}
