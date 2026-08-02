-- Used ONLY by EmailVerificationMigrationIntegrationTest: simulates rows that already existed
-- in user_account right before V6 (email verification) was applied. This location is merged with
-- the real migrations via spring.flyway.locations, so it slots in between V5 and V6.
INSERT INTO user_account (email, password, role, active, must_change_password) VALUES
    ('pre-patient@example.com', 'x', 'PATIENT', TRUE, FALSE),
    ('pre-professional@example.com', 'x', 'PROFESSIONAL', TRUE, TRUE),
    ('pre-admin@example.com', 'x', 'ADMIN', TRUE, TRUE);
