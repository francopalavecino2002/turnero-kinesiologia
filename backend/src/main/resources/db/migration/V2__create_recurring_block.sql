CREATE TABLE recurring_block (
    id BIGSERIAL PRIMARY KEY,
    day_of_week VARCHAR(20) NOT NULL CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    service_id BIGINT REFERENCES service (id),
    professional_id BIGINT REFERENCES professional (id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description VARCHAR(255) NOT NULL
);

CREATE INDEX idx_recurring_block_day_of_week ON recurring_block (day_of_week);
