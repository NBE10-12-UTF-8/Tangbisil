ALTER TABLE member ADD COLUMN email_verified BIT NOT NULL DEFAULT FALSE;

CREATE TABLE email_verification_token (
    id BINARY(16) NOT NULL,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    verified BIT NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id)
);

CREATE INDEX idx_email_verification_token_email ON email_verification_token (email);