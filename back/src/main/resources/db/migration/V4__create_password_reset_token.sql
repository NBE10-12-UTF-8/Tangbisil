CREATE TABLE password_reset_token (
    id BINARY(16) NOT NULL,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(6) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_email UNIQUE (email),
    INDEX idx_password_reset_token_expires_at (expires_at)
);