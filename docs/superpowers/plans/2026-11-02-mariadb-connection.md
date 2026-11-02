# Java + Maven + MariaDB Connection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish and verify a reliable, secure Java-to-MariaDB JDBC database connection using Maven and the MariaDB Java Client driver, without hardcoding database credentials in source code.

**Architecture:** Introduce standard Maven build configuration (`pom.xml`) with `mariadb-java-client` and JUnit 5. Implement a centralized, reusable `DatabaseConnection` class that loads connection parameters (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`) from environment variables with sensible local defaults. Provide an automated JUnit test and standalone verification runner to validate connection handshakes and query execution.

**Tech Stack:** Java 17+, Maven 3.8+, MariaDB Server 13.0.2, MariaDB Java Client 3.5.6, JUnit Jupiter 5.10.2.

**Spec:** Password Manager Project — Database Setup & Development Handoff (provided in prompt context).

## Global Constraints

- Database server: MariaDB 13.0.2 running on `127.0.0.1:3306`.
- Target database: `password_manager`.
- Database application user: `pm_user` (`pm_user`@`localhost`, `pm_user`@`127.0.0.1`).
- Driver: `org.mariadb.jdbc:mariadb-java-client:3.5.6`.
- Connection URL format: `jdbc:mariadb://127.0.0.1:3306/password_manager`.
- Zero credential exposure: Never hardcode database passwords in Java files, configuration files committed to Git, or project documentation.
- Git hygiene: Never commit `.dat`, `.vault`, `.env`, or compiled `target/` binaries.

## Review Focus

1. **Missing or Null Database Password:** When `DB_PASSWORD` is not set in the environment, `DatabaseConnection.getConnection()` should fail fast with a descriptive `IllegalStateException` rather than throwing an ambiguous SQL access-denied error.
2. **Server Unreachable / Connection Timeout:** When MariaDB is stopped or port 3306 is inaccessible, the connection attempt should respect a fail-fast connect timeout (e.g. 5 seconds) rather than hanging indefinitely.
3. **Connection Leak / Unclosed Resources:** `getConnection()` returns an active `java.sql.Connection` intended for use with try-with-resources. Documentation and helper methods must enforce proper closing to avoid exhausting MariaDB max connections.
4. **Special Characters in Database Password:** Passwords containing URL-reserved characters or spaces must be supported cleanly through JDBC properties or `DriverManager.getConnection(url, user, password)` without string formatting corruptions.
5. **Class Loading Failure:** If the driver dependency is misconfigured or missing from classpath, clear guidance on `ClassNotFoundException` / `SQLException: No suitable driver found` must be provided.

---

### Task 1: Git Hygiene & Repository Cleanup

**Files:**
- Modify: `.gitignore`
- Untrack: `backend/src/users.dat`, `backend/src/storage.dat`

**Interfaces:**
- Consumes: Existing git repository.
- Produces: Clean git tracking configuration excluding binaries, secrets, and build targets.

- [ ] **Step 1: Update `.gitignore`**
Update `.gitignore` to include:
```gitignore
# Build targets
target/
*.class

# Storage and secret files
*.dat
*.vault
.env
*.env

# IDE and OS files
.idea/
*.iml
.DS_Store
```

- [ ] **Step 2: Untrack committed binary `.dat` files**
Run:
```bash
git rm --cached backend/src/users.dat backend/src/storage.dat
```
Expected: Files staged for deletion from Git index while remaining on disk if needed.

- [ ] **Step 3: Commit Git hygiene changes**
```bash
git add .gitignore
git commit -m "chore: add .dat, .vault, target/ to .gitignore and untrack binary files"
```

---

### Task 2: Maven Project Configuration (`pom.xml`)

**Files:**
- Create: `pom.xml`

**Interfaces:**
- Consumes: Maven build system, Java compiler.
- Produces: Maven dependency resolution for `org.mariadb.jdbc:mariadb-java-client:3.5.6` and `org.junit.jupiter:junit-jupiter:5.10.2`.

- [ ] **Step 1: Write `pom.xml`**
Define standard Maven coordinates:
- `groupId`: `com.securevault`
- `artifactId`: `securevault`
- `version`: `1.0.0-SNAPSHOT`
- Properties: `maven.compiler.source` = 17 (or 21), `maven.compiler.target` = 17 (or 21), `project.build.sourceEncoding` = UTF-8.
- Dependencies:
  - `org.mariadb.jdbc:mariadb-java-client:3.5.6`
  - `org.junit.jupiter:junit-jupiter:5.10.2` (scope `test`)
- Build plugins: `maven-compiler-plugin:3.11.0`, `maven-surefire-plugin:3.2.5`.

- [ ] **Step 2: Verify Maven configuration**
Run: `mvn clean compile` (or `mvn dependency:resolve`)
Expected: `BUILD SUCCESS` with dependencies downloaded.

- [ ] **Step 3: Commit `pom.xml`**
```bash
git add pom.xml
git commit -m "build: configure Maven with MariaDB JDBC driver and JUnit 5"
```

---

### Task 3: Centralized Database Connection Manager (`DatabaseConnection.java`)

**Files:**
- Create: `backend/src/DatabaseConnection.java` (or `src/main/java/DatabaseConnection.java`)

**Interfaces:**
- Produces:
  - `public static Connection getConnection() throws SQLException`
  - `public static Connection getConnection(String host, int port, String database, String user, String password) throws SQLException`
  - `public static boolean testConnection() throws SQLException`
  - `public static String getDatabaseVersion() throws SQLException`

- [ ] **Step 1: Write `DatabaseConnection.java`**
Implement connection helper:
- Read connection settings with fallback:
  - `DB_HOST`: default `127.0.0.1`
  - `DB_PORT`: default `3306`
  - `DB_NAME`: default `password_manager`
  - `DB_USER`: default `pm_user`
  - `DB_PASSWORD`: read from environment variable `DB_PASSWORD`
- If `DB_PASSWORD` is null or empty, throw `IllegalStateException("DB_PASSWORD environment variable is not set. Please set DB_PASSWORD before connecting.")`.
- Construct JDBC URL: `jdbc:mariadb://` + host + `:` + port + `/` + database + `?connectTimeout=5000`.
- Load driver explicitly with `Class.forName("org.mariadb.jdbc.Driver")` for robust runtime loading across all environments.
- Provide `testConnection()` executing `SELECT 1`.
- Provide `getDatabaseVersion()` executing `SELECT VERSION()`.

- [ ] **Step 2: Compile and verify class interface**
Run: `javac -cp ... DatabaseConnection.java` or `mvn compile`
Expected: Clean compilation with no errors.

- [ ] **Step 3: Commit `DatabaseConnection.java`**
```bash
git add backend/src/DatabaseConnection.java
git commit -m "feat: implement centralized DatabaseConnection manager with env-based credentials"
```

---

### Task 4: Automated Verification Test & Connection Probe

**Files:**
- Create: `backend/src/TestDbConnection.java` (standalone CLI probe)
- Create: `src/test/java/DatabaseConnectionTest.java` (JUnit 5 test)

**Interfaces:**
- Consumes: `DatabaseConnection.getConnection()`, `DatabaseConnection.getDatabaseVersion()`.
- Produces: Executable verification proving MariaDB connectivity and version retrieval.

- [ ] **Step 1: Implement `TestDbConnection.java`**
Write a simple executable probe:
```java
public class TestDbConnection {
    public static void main(String[] args) {
        System.out.println("Checking MariaDB connection...");
        try (Connection conn = DatabaseConnection.getConnection()) {
            System.out.println(" Connected successfully to: " + conn.getMetaData().getURL());
            System.out.println(" Server version: " + DatabaseConnection.getDatabaseVersion());
        } catch (IllegalStateException e) {
            System.err.println(" Configuration error: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println(" Connection failed: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Implement `DatabaseConnectionTest.java`**
JUnit test class verifying:
- Throws `IllegalStateException` when password is unset.
- Connects successfully and verifies non-empty version string when credentials are supplied.

- [ ] **Step 3: Run verification probe**
Run:
```bash
export DB_PASSWORD="your_rotated_password"
mvn test  # or java -cp ... TestDbConnection
```
Expected: `Connected successfully`, prints `Server version: 13.0.2-MariaDB`.

- [ ] **Step 4: Commit verification tools**
```bash
git add backend/src/TestDbConnection.java
git commit -m "test: add MariaDB connection verification probe and test cases"
```

---

### Task 5: User Verification Handoff (Password Rotation & Workbench Validation)

**Files:**
- Documentation / instructions in terminal.

**Steps:**
- [ ] **Step 1: Rotate `pm_user` password in MariaDB**
Run as root:
```bash
sudo mariadb -e "ALTER USER 'pm_user'@'localhost' IDENTIFIED BY 'NEW_PASSWORD'; ALTER USER 'pm_user'@'127.0.0.1' IDENTIFIED BY 'NEW_PASSWORD'; FLUSH PRIVILEGES;"
```
- [ ] **Step 2: Verify TCP connection using MariaDB CLI**
```bash
mariadb -h 127.0.0.1 -P 3306 -u pm_user -p password_manager
```
Enter `NEW_PASSWORD` and verify prompt connects to `password_manager`.

- [ ] **Step 3: Test MySQL Workbench Connection**
Set Hostname: `127.0.0.1`, Port: `3306`, Username: `pm_user`, Password: `NEW_PASSWORD`. Click "Test Connection".

- [ ] **Step 4: Execute Java connection probe**
```bash
export DB_PASSWORD='NEW_PASSWORD'
# Run probe
```
Confirm Java receives connection handshake and prints server version.
