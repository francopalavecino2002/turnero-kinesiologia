CREATE TABLE user_account (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('PATIENT', 'PROFESSIONAL', 'ADMIN')),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE patient (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL UNIQUE REFERENCES user_account (id)
);

CREATE TABLE professional (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL UNIQUE REFERENCES user_account (id)
);

CREATE TABLE service (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    duration_min INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE professional_service (
    professional_id BIGINT NOT NULL REFERENCES professional (id),
    service_id BIGINT NOT NULL REFERENCES service (id),
    PRIMARY KEY (professional_id, service_id)
);

CREATE TABLE availability (
    id BIGSERIAL PRIMARY KEY,
    professional_id BIGINT NOT NULL REFERENCES professional (id),
    day_of_week VARCHAR(20) NOT NULL CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

CREATE TABLE appointment (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patient (id),
    professional_id BIGINT NOT NULL REFERENCES professional (id),
    service_id BIGINT NOT NULL REFERENCES service (id),
    date_time TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (
        status IN ('BOOKED', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')
    )
);

CREATE INDEX idx_availability_professional ON availability (professional_id);
CREATE INDEX idx_appointment_professional ON appointment (professional_id);
CREATE INDEX idx_appointment_patient ON appointment (patient_id);
CREATE INDEX idx_appointment_date_time ON appointment (date_time);
