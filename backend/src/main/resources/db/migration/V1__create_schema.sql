CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('PACIENTE', 'PROFESIONAL', 'ADMINISTRADOR')),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE paciente (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    apellido VARCHAR(255) NOT NULL,
    telefono VARCHAR(50) NOT NULL,
    usuario_id BIGINT NOT NULL UNIQUE REFERENCES usuario (id)
);

CREATE TABLE profesional (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    apellido VARCHAR(255) NOT NULL,
    usuario_id BIGINT NOT NULL UNIQUE REFERENCES usuario (id)
);

CREATE TABLE servicio (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    duracion_min INTEGER NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE profesional_servicio (
    profesional_id BIGINT NOT NULL REFERENCES profesional (id),
    servicio_id BIGINT NOT NULL REFERENCES servicio (id),
    PRIMARY KEY (profesional_id, servicio_id)
);

CREATE TABLE disponibilidad (
    id BIGSERIAL PRIMARY KEY,
    profesional_id BIGINT NOT NULL REFERENCES profesional (id),
    dia_semana VARCHAR(20) NOT NULL CHECK (
        dia_semana IN ('LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO')
    ),
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL
);

CREATE TABLE turno (
    id BIGSERIAL PRIMARY KEY,
    paciente_id BIGINT NOT NULL REFERENCES paciente (id),
    profesional_id BIGINT NOT NULL REFERENCES profesional (id),
    servicio_id BIGINT NOT NULL REFERENCES servicio (id),
    fecha_hora TIMESTAMP NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (
        estado IN ('RESERVADO', 'CONFIRMADO', 'CANCELADO', 'COMPLETADO', 'AUSENTE')
    )
);

CREATE INDEX idx_disponibilidad_profesional ON disponibilidad (profesional_id);
CREATE INDEX idx_turno_profesional ON turno (profesional_id);
CREATE INDEX idx_turno_paciente ON turno (paciente_id);
CREATE INDEX idx_turno_fecha_hora ON turno (fecha_hora);
