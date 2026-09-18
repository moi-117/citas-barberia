-- Permite que el navegador del cliente recuerde sus citas sin exigir cuenta
-- ni un código que la persona tenga que memorizar.

ALTER TABLE citas
    ADD COLUMN consulta_token VARCHAR(36) NULL AFTER estado;

-- Las citas históricas reciben también un token privado.
UPDATE citas
SET consulta_token = UUID()
WHERE consulta_token IS NULL OR TRIM(consulta_token) = '';

ALTER TABLE citas
    MODIFY COLUMN consulta_token VARCHAR(36) NOT NULL;

CREATE UNIQUE INDEX uk_citas_consulta_token ON citas(consulta_token);
