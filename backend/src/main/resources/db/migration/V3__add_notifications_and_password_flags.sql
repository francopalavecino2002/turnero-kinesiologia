ALTER TABLE patient
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user_account
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
