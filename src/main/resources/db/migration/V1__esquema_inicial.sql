-- Esquema inicial de RAND Barbería.
-- En instalaciones nuevas Flyway crea todo desde cero.
-- En bases antiguas ya existentes, baseline-on-migrate marca V1 como aplicada
-- y comienza en V2, evitando reconstruir o borrar datos.

CREATE TABLE usuarios (
    id BINARY(16) NOT NULL,
    nombre VARCHAR(120) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    email VARCHAR(150) NULL,
    password VARCHAR(255) NULL,
    rol VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_telefono UNIQUE (telefono)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE servicios (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(300) NULL,
    duracion_minutos INT NOT NULL,
    precio DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE barberos (
    id BINARY(16) NOT NULL,
    nombre VARCHAR(255) NULL,
    especialidad VARCHAR(255) NULL,
    foto_url VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE citas (
    id BINARY(16) NOT NULL,
    fecha_hora_inicio DATETIME(6) NOT NULL,
    fecha_hora_fin DATETIME(6) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    usuario_id BINARY(16) NOT NULL,
    servicio_id BIGINT NOT NULL,
    barbero_id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_cita_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_cita_servicio FOREIGN KEY (servicio_id) REFERENCES servicios(id),
    CONSTRAINT fk_cita_barbero FOREIGN KEY (barbero_id) REFERENCES barberos(id),
    INDEX idx_cita_rango_horario (fecha_hora_inicio, fecha_hora_fin),
    INDEX idx_cita_barbero_horario (barbero_id, fecha_hora_inicio, fecha_hora_fin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
