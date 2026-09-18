-- Columnas opcionales: las citas existentes conservan sus datos y tokens.
ALTER TABLE citas ADD COLUMN clave_idempotencia VARCHAR(36) NULL;
ALTER TABLE citas ADD COLUMN huella_reserva VARCHAR(64) NULL;
ALTER TABLE citas ADD CONSTRAINT uk_cita_idempotencia UNIQUE (clave_idempotencia);
