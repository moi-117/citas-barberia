-- Corrige bases creadas con versiones viejas, donde password era NOT NULL.
-- Los clientes invitados pueden reservar sin crear cuenta y por eso password debe aceptar NULL.
ALTER TABLE usuarios
    MODIFY COLUMN password VARCHAR(255) NULL;

-- Normaliza correos vacíos para que se comporten como ausencia de correo.
UPDATE usuarios
SET email = NULL
WHERE email IS NOT NULL AND TRIM(email) = '';
