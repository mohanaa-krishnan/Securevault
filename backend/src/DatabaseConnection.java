import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Centralized MariaDB JDBC connection provider for SecureVault.
 * Loads connection parameters from environment variables with safe local defaults.
 * Passwords are never hardcoded.
 */
public class DatabaseConnection {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 3306;
    private static final String DEFAULT_DATABASE = "password_manager";
    private static final String DEFAULT_USER = "pm_user";
    private static final int CONNECT_TIMEOUT_MS = 5000;

    static {
        try {
            // Explicitly load MariaDB JDBC Driver
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Driver will still attempt automatic registration via ServiceLoader on JDBC 4.0+
        }
    }

    /**
     * Obtains a connection using environment variables (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD).
     *
     * @return an active java.sql.Connection
     * @throws IllegalStateException if DB_PASSWORD is not set
     * @throws SQLException if connection fails
     */
    public static Connection getConnection() throws SQLException {
        String host = getEnvOrDotEnv("DB_HOST", DEFAULT_HOST);
        int port = getPortOrDefault("DB_PORT", DEFAULT_PORT);
        String database = getEnvOrDotEnv("DB_NAME", DEFAULT_DATABASE);
        String user = getEnvOrDotEnv("DB_USER", DEFAULT_USER);
        String password = getEnvOrDotEnv("DB_PASSWORD", null);

        if (password == null || password.trim().isEmpty()) {
            throw new IllegalStateException(
                "DB_PASSWORD environment variable is not set. " +
                "Please set DB_PASSWORD in your environment or in a .env file before connecting."
            );
        }

        return getConnection(host, port, database, user, password);
    }

    /**
     * Obtains a connection using explicitly specified parameters.
     *
     * @param host Database host (e.g. 127.0.0.1)
     * @param port Database port (e.g. 3306)
     * @param database Database name (e.g. password_manager)
     * @param user Database username (e.g. pm_user)
     * @param password Database password
     * @return an active java.sql.Connection
     * @throws SQLException if connection fails
     */
    public static Connection getConnection(String host, int port, String database, String user, String password) throws SQLException {
        if (password == null) {
            throw new IllegalArgumentException("Database password cannot be null");
        }
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s?connectTimeout=%d", host, port, database, CONNECT_TIMEOUT_MS);
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    /**
     * Executes a fast probe query (SELECT 1) to verify connection readiness.
     *
     * @return true if probe succeeds, false otherwise
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Queries and returns the connected database server version (SELECT VERSION()).
     *
     * @return version string reported by MariaDB
     * @throws SQLException if query fails
     */
    public static String getDatabaseVersion() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
            throw new SQLException("Empty result set returned when querying database version.");
        }
    }

    private static String getEnvOrDotEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        val = readFromDotEnv(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
    }

    private static int getPortOrDefault(String key, int defaultValue) {
        String val = getEnvOrDotEnv(key, null);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    private static String readFromDotEnv(String key) {
        String[] candidatePaths = {".env", "../.env", "backend/.env"};
        for (String p : candidatePaths) {
            java.io.File file = new java.io.File(p);
            if (file.exists() && file.isFile()) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("#") || line.isEmpty()) continue;
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            String k = line.substring(0, eq).trim();
                            if (k.equals(key)) {
                                String v = line.substring(eq + 1).trim();
                                if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                                    v = v.substring(1, v.length() - 1);
                                }
                                return v;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
