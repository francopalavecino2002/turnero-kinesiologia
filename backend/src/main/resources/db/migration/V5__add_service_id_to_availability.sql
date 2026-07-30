ALTER TABLE availability ADD COLUMN service_id BIGINT REFERENCES service (id);
