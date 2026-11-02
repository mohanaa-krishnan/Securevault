# SecureVault — Encrypted Password Manager

SecureVault is an open-source, encrypted desktop password manager built with **Java Swing**, **MariaDB**, and **AES-256-GCM** authenticated encryption.

---

## Key Features

- **Authenticated Cryptography:**
  - **AES-256-GCM** encryption with unique 12-byte IVs per entry and 128-bit authentication tags.
  - **PBKDF2WithHmacSHA256** key derivation (310,000 iterations, OWASP recommended).
  - **Zero Key Persistence:** The vault encryption key is held only in volatile memory during an active session and zeroed on logout or exit.
  - **Constant-Time Verification:** Login authentication hashes are compared using `MessageDigest.isEqual` to prevent timing attacks.
- **Relational MariaDB Storage:**
  - Multi-user schema (`users` and `vault_entries`) with foreign keys and cascading deletes.
  - No plaintext credentials or master keys in the database.
- **Java Swing Desktop Interface:**
  - Modern desktop UI with CardLayout navigation (Unlock Vault & Create Account).
  - Real-time password strength checklist.
  - Live credential search by website or username.
  - Built-in cryptographically secure password generator.
  - Password masking with show/hide toggle and one-click clipboard copy.

---

## Tech Stack

- **UI:** Java Swing (Desktop GUI)
- **Language:** Java 17+
- **Database:** MariaDB 13+ via MariaDB Java Client JDBC (3.5.6)
- **Testing:** JUnit Jupiter 5.10.2
- **Build Tool:** Maven

---

## Quick Start

### 1. Configure Database Credentials

Ensure MariaDB is running and create the `password_manager` database. Set your database password in a `.env` file in the project root:

```bash
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=password_manager
DB_USER=pm_user
DB_PASSWORD=your_secure_password
```

*(Note: `.env` is listed in `.gitignore` and is never committed to Git).*

### 2. Apply Database Schema

If you haven't run the migration yet, initialize the database tables:

```bash
java -cp "bin:lib/*:backend/src" SchemaRunner
```

### 3. Launch the Application

Launch the desktop UI:

```bash
./run-app.sh
```

*(Or open `backend/src/SecureVaultApp.java` in VS Code and click **Run**).*

### 4. Run Automated Tests

Execute the complete JUnit 5 test suite:

```bash
./run-tests.sh
```

---

## Project Structure

```
Securevault/
├── backend/
│   ├── src/
│   │   ├── CryptoEngine.java         # AES-256-GCM & PBKDF2 cryptographic engine
│   │   ├── DatabaseConnection.java   # Centralized JDBC connection manager with .env fallback
│   │   ├── UserDao.java              # User registration and authentication DAO
│   │   ├── VaultDao.java             # Encrypted credential storage & CRUD DAO
│   │   ├── SecureVaultApp.java       # Java Swing desktop GUI application
│   │   ├── SchemaRunner.java         # Database schema migration runner
│   │   └── TestDbConnection.java     # CLI database connection diagnostic probe
│   └── test/
│       ├── CryptoEngineTest.java     # Cryptography unit test suite
│       └── DatabaseConnectionTest.java # Database connectivity test suite
├── database/
│   └── schema.sql                    # MariaDB relational DDL schema
├── lib/                              # Local JAR dependencies (JDBC driver, JUnit runner)
├── pom.xml                           # Maven project configuration
├── run-app.sh                        # Desktop launcher script
└── run-tests.sh                      # Automated test suite launcher script
```
