-- Administración operativa: servicios desactivables y horario semanal por barbero.
ALTER TABLE servicios
    ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;

CREATE INDEX idx_servicios_activo ON servicios(activo);

CREATE TABLE horarios_barbero (
    id BIGINT NOT NULL AUTO_INCREMENT,
    barbero_id BINARY(16) NOT NULL,
    dia_semana VARCHAR(12) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    hora_inicio TIME NULL,
    hora_fin TIME NULL,
    descanso_inicio TIME NULL,
    descanso_fin TIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_horario_barbero_dia UNIQUE (barbero_id, dia_semana),
    CONSTRAINT fk_horario_barbero_barbero FOREIGN KEY (barbero_id)
        REFERENCES barberos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
