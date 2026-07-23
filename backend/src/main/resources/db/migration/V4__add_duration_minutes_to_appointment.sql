ALTER TABLE appointment ADD COLUMN duration_minutes INTEGER;

UPDATE appointment a
   SET duration_minutes = (SELECT s.duration_min
                             FROM service s
                            WHERE s.id = a.service_id);

ALTER TABLE appointment ALTER COLUMN duration_minutes SET NOT NULL;

ALTER TABLE appointment ADD CONSTRAINT chk_appointment_duration_minutes_positive
    CHECK (duration_minutes > 0);
