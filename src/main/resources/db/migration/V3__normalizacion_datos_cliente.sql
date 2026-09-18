-- Normaliza datos heredados para que la lógica actual y las restricciones
-- comparen teléfonos/correos de la misma forma.
UPDATE usuarios
SET email = LOWER(TRIM(email))
WHERE email IS NOT NULL;

UPDATE usuarios
SET telefono = REGEXP_REPLACE(telefono, '[^0-9]', '');

UPDATE usuarios
SET telefono = SUBSTRING(telefono, 3)
WHERE CHAR_LENGTH(telefono) = 12 AND telefono LIKE '57%';
