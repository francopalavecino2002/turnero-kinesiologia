-- Staff manual booking: PROFESSIONAL/ADMIN can book an appointment on behalf of a patient who
-- has no account (a walk-in or phone booking). Such a "guest" patient has no user_account row,
-- only a name and phone (email optional), so user_id must become nullable and we add columns to
-- hold the guest's own data.
ALTER TABLE patient
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE patient
    ADD COLUMN guest_name VARCHAR(255),
    ADD COLUMN guest_phone VARCHAR(50),
    ADD COLUMN guest_email VARCHAR(255);

-- A patient row must be either a registered account (user_id) or a guest (guest_name) - never
-- neither. Existing rows already satisfy this since they all have user_id set.
ALTER TABLE patient
    ADD CONSTRAINT chk_patient_user_or_guest
    CHECK (user_id IS NOT NULL OR guest_name IS NOT NULL);
