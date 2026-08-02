-- Google OAuth login.
--
-- 1) auth_provider on user_account: distinguishes accounts that authenticate with a system
-- password (LOCAL) from those created via Google OAuth (GOOGLE), which have no password.
-- Every pre-existing account is backfilled to LOCAL via the DEFAULT: they were all created
-- through the email/password flow (public registration, admin bootstrap, admin-created
-- professionals, dev seeds) before OAuth existed.
ALTER TABLE user_account
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

ALTER TABLE user_account
    ADD CONSTRAINT chk_user_account_auth_provider
    CHECK (auth_provider IN ('LOCAL', 'GOOGLE'));

-- 2) password becomes nullable: Google-created accounts have no system password, so a NOT NULL
-- constraint on password would make it impossible to persist them.
ALTER TABLE user_account
    ALTER COLUMN password DROP NOT NULL;
