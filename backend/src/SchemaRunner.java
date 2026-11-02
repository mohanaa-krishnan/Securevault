import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/**
 * Runs the SecureVault schema migration against the connected MariaDB database.
 * Safe to run multiple times — uses DROP IF EXISTS before each CREATE.
 */
public class SchemaRunner {

    private static final List<String> SCHEMA_STATEMENTS = Arrays.asList(

        // Drop in dependency order
        "DROP TABLE IF EXISTS vault_entries",
        "DROP TABLE IF EXISTS users",

        // users table
        "CREATE TABLE users (" +
        "  id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY," +
        "  username   VARCHAR(100)  NOT NULL," +
        "  kdf_salt   VARCHAR(64)   NOT NULL," +
        "  auth_hash  VARCHAR(128)  NOT NULL," +
        "  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP," +
        "  CONSTRAINT uq_username UNIQUE (username)" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        // vault_entries table
        "CREATE TABLE vault_entries (" +
        "  id                 INT UNSIGNED AUTO_INCREMENT PRIMARY KEY," +
        "  user_id            INT UNSIGNED  NOT NULL," +
        "  label              VARCHAR(255)  NOT NULL," +
        "  account_username   VARCHAR(255)  NOT NULL," +
        "  encrypted_password TEXT          NOT NULL," +
        "  iv                 VARCHAR(24)   NOT NULL," +
        "  created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP," +
        "  updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
        "  CONSTRAINT fk_vault_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        // Indexes
        "CREATE INDEX idx_vault_user ON vault_entries (user_id)",
        "CREATE INDEX idx_vault_user_label ON vault_entries (user_id, label)"
    );

    public static void run() throws SQLException {
        System.out.println("[Schema] Applying schema to password_manager...");
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : SCHEMA_STATEMENTS) {
                stmt.execute(sql);
                System.out.println("[Schema]  OK: " + sql.substring(0, Math.min(55, sql.length())).replace('\n', ' ') + "...");
            }
        }
        System.out.println("[Schema] Schema applied successfully.");
    }

    public static void main(String[] args) throws Exception {
        run();
    }
}
