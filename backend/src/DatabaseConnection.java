import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized MariaDB JDBC connection provider for SecureVault.
 * Loads connection parameters from environment variables with safe local defaults.
 * Passwords are never hardcoded in source code.
 */
public class DatabaseConnection {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 3306;
    private static final String DEFAULT_DATABASE = "password_manager";
    private static final String DEFAULT_USER = "pm_user";
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private static final boolean DRIVER_LOADED;
    private static final Throwable DRIVER_LOAD_ERROR;
    private static final Map<String, String> DOT_ENV_CACHE;

    static {
        boolean loaded = false;
        Throwable error = null;
        try {
            Class.forName("org.mariadb.jdbc.Driver");
            loaded = true;
        } catch (ClassNotFoundException | LinkageError e) {
            error = e;
        }
        DRIVER_LOADED = loaded;
        DRIVER_LOAD_ERROR = error;

        // Preload .env cache once to avoid disk I/O on every connection
        DOT_ENV_CACHE = Collections.unmodifiableMap(loadDotEnvFiles());
    }

    /**
     * Obtains a connection using environment variables or local .env fallback.
     *
     * @return an active java.sql.Connection
     * @throws IllegalStateException if DB_PASSWORD is not set or driver is missing
     * @throws SQLException if connection fails
     */
    public static Connection getConnection() throws SQLException {
        ensureDriverAvailable();

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
        ensureDriverAvailable();
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
     * Queries and returns the connected database server version using an existing connection.
     *
     * @param conn active database connection
     * @return version string reported by MariaDB
     * @throws SQLException if query fails
     */
    public static String getDatabaseVersion(Connection conn) throws SQLException {
        if (conn == null || conn.isClosed()) {
            throw new IllegalArgumentException("Connection must be open and non-null");
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
            throw new SQLException("Empty result set returned when querying database version.");
        }
    }

    /**
     * Queries and returns the connected database server version.
     *
     * @return version string reported by MariaDB
     * @throws SQLException if query fails
     */
    public static String getDatabaseVersion() throws SQLException {
        try (Connection conn = getConnection()) {
            return getDatabaseVersion(conn);
        }
    }

    private static void ensureDriverAvailable() {
        if (!DRIVER_LOADED) {
            throw new IllegalStateException(
                "MariaDB JDBC Driver (org.mariadb.jdbc.Driver) is not available on classpath. " +
                "Ensure mariadb-java-client dependency is resolved.",
                DRIVER_LOAD_ERROR
            );
        }
    }

    private static String getEnvOrDotEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        val = DOT_ENV_CACHE.get(key);
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

    private static Map<String, String> loadDotEnvFiles() {
        Map<String, String> envMap = new HashMap<>();
        // Look only within project boundaries to prevent directory traversal
        String[] candidatePaths = {".env", "backend/.env"};
        for (String p : candidatePaths) {
            File file = new File(p);
            if (file.exists() && file.isFile()) {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("#") || line.isEmpty()) continue;
                        if (line.startsWith("export ")) {
                            line = line.substring(7).trim();
                        }
                        int eq = line.indexOf('=');
                        if (eq > 0) {
                            String k = line.substring(0, eq).trim();
                            String v = line.substring(eq + 1).trim();
                            if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                                v = v.substring(1, v.length() - 1);
                            }
                            envMap.putIfAbsent(k, v);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return envMap;
    }
}
