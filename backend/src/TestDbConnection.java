import java.sql.Connection;
import java.sql.SQLException;

/**
 * Standalone verification utility to test MariaDB connection.
 * Can be run directly from terminal or IDE to verify connectivity.
 */
public class TestDbConnection {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" SecureVault: MariaDB Connection Probe");
        System.out.println("==================================================");

        String host = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("DB_PORT", "3306");
        String db = System.getenv().getOrDefault("DB_NAME", "password_manager");
        String user = System.getenv().getOrDefault("DB_USER", "pm_user");

        System.out.println("Target: " + user + "@" + host + ":" + port + "/" + db);

        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println(" [SUCCESS] Connected successfully!");
            System.out.println(" JDBC URL: " + conn.getMetaData().getURL());
            System.out.println(" Driver Name: " + conn.getMetaData().getDriverName());
            System.out.println(" Driver Version: " + conn.getMetaData().getDriverVersion());
            System.out.println(" Database Product: " + conn.getMetaData().getDatabaseProductName());
            System.out.println(" Database Version: " + DatabaseConnection.getDatabaseVersion());
            System.out.println("==================================================");
            System.out.println(" MariaDB connectivity verified successfully.");
            System.out.println("==================================================");
        } catch (IllegalStateException e) {
            System.err.println("\n [CONFIG ERROR] " + e.getMessage());
            System.err.println("Please run: export DB_PASSWORD='your_password'");
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("\n [CONNECTION FAILED] " + e.getMessage());
            System.err.println("SQLState: " + e.getSQLState());
            System.err.println("ErrorCode: " + e.getErrorCode());
            System.exit(2);
        }
    }
}
