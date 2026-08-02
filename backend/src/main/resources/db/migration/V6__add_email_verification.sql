-- Email verification + reusable one-time tokens.
--
-- 1) email_verified on user_account.
-- Every pre-existing account is backfilled to TRUE: the admin, the professionals and the
-- previously self-registered patients were all onboarded before an email-verification step
-- existed, so blocking them on deploy would lock the clinic out of its own system. Only NEW
-- public self-registrations start with email_verified = FALSE (AuthService forces it), and
-- only the patient auto-registration flow requires verification.
ALTER TABLE user_account
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_account
   SET email_verified = TRUE;

-- 2) Reusable one-time tokens for email verification and password reset.
-- Only the SHA-256 hash of the token is stored; the plain token only ever travels inside the
-- emailed link. A token is single-use (used_at) and expires (expires_at).
CREATE TABLE user_token (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES user_account (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_token_user_id ON user_token (user_id);
CREATE INDEX idx_user_token_expires_at ON user_token (expires_at);
