-- =============================================================
-- SecureVault Password Manager — Database Schema
-- Database: password_manager
-- MariaDB 13.0.2+
-- =============================================================
-- Security design:
--   • The master password is NEVER stored. Only a salted PBKDF2
--     auth_hash is kept for login verification.
--   • The AES-256-GCM vault encryption key is derived ephemerally
--     in memory from the master password and is never persisted.
--   • Each vault entry stores its own 12-byte GCM IV alongside the
--     ciphertext so re-encryption is possible without a single
--     shared IV.
-- =============================================================

-- Drop in safe dependency order
DROP TABLE IF EXISTS vault_entries;
DROP TABLE IF EXISTS users;

-- ----------------------------------------------------------
-- users: one row per registered SecureVault account
-- ----------------------------------------------------------
CREATE TABLE users (
    id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(100)  NOT NULL,
    -- 32-byte PBKDF2 / Argon2id salt (Base64-encoded, 48 chars)
    kdf_salt      VARCHAR(64)   NOT NULL,
    -- PBKDF2WithHmacSHA256 / Argon2id derived auth hash (Base64, 88 chars)
    auth_hash     VARCHAR(128)  NOT NULL,
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_username UNIQUE (username)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------
-- vault_entries: encrypted credentials, partitioned per user
-- ----------------------------------------------------------
CREATE TABLE vault_entries (
    id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id          INT UNSIGNED  NOT NULL,
    -- Human-readable label (website or app name)
    label            VARCHAR(255)  NOT NULL,
    -- Account identifier stored in cleartext (email / username)
    account_username VARCHAR(255)  NOT NULL,
    -- AES-256-GCM ciphertext of the stored password (Base64)
    encrypted_password TEXT        NOT NULL,
    -- 12-byte GCM IV, unique per entry (Base64, 16 chars)
    iv               VARCHAR(24)   NOT NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_vault_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- Index for fast per-user vault lookups
CREATE INDEX idx_vault_user ON vault_entries (user_id);

-- Index for label search within a user's vault
CREATE INDEX idx_vault_user_label ON vault_entries (user_id, label);
