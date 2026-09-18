-- Gestión administrativa del equipo de RAND Barbería.
-- Los barberos se desactivan sin perder el historial de citas.

ALTER TABLE barberos
    ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;

UPDATE barberos
SET nombre = 'Barbero RAND'
WHERE nombre IS NULL OR TRIM(nombre) = '';

UPDATE barberos
SET especialidad = 'Barbería profesional'
WHERE especialidad IS NULL OR TRIM(especialidad) = '';

ALTER TABLE barberos
    MODIFY COLUMN nombre VARCHAR(120) NOT NULL,
    MODIFY COLUMN especialidad VARCHAR(160) NOT NULL,
    MODIFY COLUMN foto_url VARCHAR(500) NULL,
    MODIFY COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;

CREATE INDEX idx_barberos_activo ON barberos(activo);

-- Equipo inicial. Se insertan solo los nombres que todavía no existen.
INSERT INTO barberos (id, nombre, especialidad, foto_url, activo)
SELECT UNHEX(REPLACE('11111111-1111-1111-1111-111111111111', '-', '')),
       'Alejandro', 'Especialista en Fade',
       'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=800&h=1000&fit=crop', 1
WHERE NOT EXISTS (SELECT 1 FROM barberos WHERE LOWER(nombre) = LOWER('Alejandro'));

INSERT INTO barberos (id, nombre, especialidad, foto_url, activo)
SELECT UNHEX(REPLACE('22222222-2222-2222-2222-222222222222', '-', '')),
       'David', 'Barbas clásicas y rituales',
       'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=800&h=1000&fit=crop', 1
WHERE NOT EXISTS (SELECT 1 FROM barberos WHERE LOWER(nombre) = LOWER('David'));

INSERT INTO barberos (id, nombre, especialidad, foto_url, activo)
SELECT UNHEX(REPLACE('33333333-3333-3333-3333-333333333333', '-', '')),
       'Thomas Shelby', 'Cortes clásicos y estilo ejecutivo',
       'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=800&h=1000&fit=crop', 1
WHERE NOT EXISTS (SELECT 1 FROM barberos WHERE LOWER(nombre) = LOWER('Thomas Shelby'));
