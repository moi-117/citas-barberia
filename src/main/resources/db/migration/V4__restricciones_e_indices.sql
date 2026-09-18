-- Un correo identifica como máximo una cuenta. Si una base antigua tiene
-- duplicados reales, Flyway detiene el arranque en vez de cambiar datos en silencio.
ALTER TABLE usuarios
    ADD CONSTRAINT uk_usuario_email UNIQUE (email);

CREATE INDEX idx_cita_estado_fecha ON citas (estado, fecha_hora_inicio);
