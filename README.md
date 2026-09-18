# RAND Barbería — producto final

Sistema de reservas para barbería construido con Spring Boot 3.3, Java 17, MySQL y frontend HTML/Tailwind.

## Qué quedó protegido

- Autenticación con sesión real de Spring Security.
- Roles `ADMIN` y `CLIENTE` aplicados en backend.
- Panel administrativo protegido también del lado servidor.
- CSRF activo en POST/PATCH/logout.
- Contraseñas BCrypt con coste 12 para nuevas cuentas y límite seguro de 72 bytes.
- Login conectado al usuario y rol persistidos, con bloqueo temporal tras intentos fallidos.
- Política CORS de lista cerrada y cabeceras CSP, `nosniff`, anti-frame, referrer y permisos del navegador.
- Registro público siempre crea `CLIENTE`.
- Reservas públicas sin contraseñas inventadas.
- CORS abierto eliminado.
- Prevención de XSS en contenido dinámico del frontend.
- Validación de DTOs y errores JSON controlados.
- No se exponen stack traces al navegador.
- Cookies `HttpOnly`, `SameSite=Lax`; en perfil `prod` también `Secure`.

## Reservas

- Atención: lunes a sábado, 08:00–20:00.
- Horarios en intervalos de 15 minutos.
- El backend rechaza horarios manipulados que no pertenezcan a esa malla.
- La duración real del servicio se usa para detectar solapamientos.
- Bloqueo pesimista del barbero durante `validar + insertar`, evitando que dos peticiones simultáneas tomen el mismo espacio.
- Estados: `PENDIENTE`, `CONFIRMADA`, `CANCELADA`, `COMPLETADA`.
- Bloqueo optimista al actualizar una cita.
- Sección **Mis citas**: el navegador recuerda de forma local las reservas creadas desde ese dispositivo y consulta su estado mediante un token aleatorio privado. El cliente no tiene que memorizar ni escribir códigos.
- El endpoint público de seguimiento expone solo servicio, barbero, fecha/hora y estado; no devuelve teléfono ni correo.

## Base de datos: ya no hay que hacer ALTER TABLE manualmente

El proyecto usa **Flyway**. La estructura de la base queda versionada dentro de:

`src/main/resources/db/migration/`

Migraciones incluidas:

- `V1__esquema_inicial.sql`: esquema para instalaciones nuevas.
- `V2__compatibilidad_clientes_invitados.sql`: convierte `usuarios.password` a nullable, corrigiendo el error `Column 'password' cannot be null`.
- `V3__normalizacion_datos_cliente.sql`: normaliza correo y teléfono heredados.
- `V4__restricciones_e_indices.sql`: correo único e índices adicionales.
- `V5__gestion_de_barberos.sql`: estado activo/inactivo y equipo inicial.
- `V6__consulta_privada_de_citas.sql`: token privado para recordar citas en el navegador sin cuentas ni códigos memorizables.
- `V7__idempotencia_reservas.sql`: claves idempotentes para reintentos seguros de reservas.
- `V8__administracion_servicios_y_horarios.sql`: estado de los servicios y horarios semanales por barbero.

Para una base antigua del proyecto, `baseline-on-migrate=true` permite que Flyway adopte la base existente y aplique las migraciones posteriores sin borrar tus datos.

Hibernate está en:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Es decir: **Flyway modifica el esquema; Hibernate solo comprueba que el esquema sea correcto**. Si algo no coincide, la aplicación no arranca silenciosamente con una base incorrecta.

## Requisitos

- Java 17
- MySQL 8+
- Maven 3.9+ o Maven integrado de IntelliJ

## Primera ejecución local

1. Inicia MySQL.
2. Abre el `pom.xml` como proyecto Maven en IntelliJ.
3. Verifica Project SDK = Java 17.
4. Por defecto se usa la base `barberia_db` y el usuario local `root`.
5. Define `DB_PASSWORD` en las variables de entorno de la configuración de ejecución de IntelliJ. La contraseña no se guarda en el repositorio.
6. Ejecuta `CitasApplication.java`.
7. Abre `http://localhost:8080/`.

### Administrador local inicial

La creación automática está desactivada. Si una instalación nueva necesita crear el primer administrador, configura temporalmente `BOOTSTRAP_ADMIN=true`, `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` y `APP_ADMIN_TELEFONO`, arranca una vez y vuelve a dejar `BOOTSTRAP_ADMIN=false`. La contraseña debe tener al menos 12 caracteres y no puede ser un valor de ejemplo. El mismo procedimiento permite rotar la contraseña de un administrador existente con ese correo, sin cambiar su rol ni sus demás datos.

## Producción

Activa:

```text
SPRING_PROFILES_ACTIVE=prod
```

y define al menos:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

Hay un ejemplo en `.env.example`.

No publiques una base de datos con el usuario `root` y sirve la web detrás de HTTPS. El perfil `prod` fuerza la cookie de sesión como `Secure` y rechaza usuarios `root`, contraseñas vacías o de ejemplo y orígenes CORS que no usen HTTPS.

`ALLOWED_ORIGINS` acepta una lista separada por comas de orígenes exactos, por ejemplo `https://barberia.example`. Déjala vacía cuando frontend y backend se sirvan desde el mismo origen. No admite comodines ni rutas.

## Pruebas automáticas

El proyecto contiene pruebas de integración con una H2 aislada de MySQL:

- reserva de cliente invitado con `password = NULL`;
- bloqueo de doble reserva;
- rechazo de horarios manipulados;
- endpoints públicos;
- API administrativa bloqueada para anónimos y CLIENTE;
- API administrativa disponible para ADMIN.
- consulta pública de una cita mediante token privado sin exponer datos personales.

Ejecuta:

```bash
mvn test
```

Antes de desplegar, también ejecuta:

```bash
mvn clean package
```

## Comprobación funcional rápida

1. Abrir `/` y comprobar servicios/barberos.
2. Reservar una cita.
3. Intentar reservar el mismo barbero en el mismo horario: debe responder conflicto.
4. Entrar como ADMIN.
5. Ver la cita en `/panel.html`.
6. Confirmar una cita con **Confirmar** y verificar `CONFIRMADA` en **Mis citas**. Después, **Enviar WhatsApp** abre el mensaje preparado si el número es compatible.
7. Cancelar otra cita con **Cancelar cita** y comprobar el estado `CANCELADA`, incluso con un teléfono de 7 dígitos.
8. Completar una cita confirmada.
9. Cerrar sesión e intentar abrir `/panel.html`: debe redirigir al login.

## Nota de alcance

El sistema ya tiene controles importantes para un producto real, pero cualquier despliegue comercial debe acompañarse de HTTPS, copias de seguridad de MySQL, monitoreo de logs, credenciales seguras y un proveedor real de infraestructura. Si en el futuro se agregan pagos, recuperación de contraseña, envío **automático** por WhatsApp/correo o múltiples sucursales, esas funciones requieren sus propias integraciones y pruebas. La versión actual usa `wa.me`: abre WhatsApp con el mensaje preparado, pero el administrador pulsa Enviar manualmente y no necesita una API paga.

## Gestión de barberos

El panel ADMIN incluye gestión del equipo en `/panel.html`:

- Crear barberos con nombre, especialidad y URL de foto.
- Editar sus datos.
- Activar o desactivar barberos.
- Eliminar únicamente barberos sin citas asociadas.

Los barberos inactivos no aparecen en el sitio público ni pueden recibir nuevas reservas, pero se conservan en las citas históricas. La migración `V5__gestion_de_barberos.sql` incorpora este comportamiento y agrega el equipo inicial de tres barberos (Alejandro, David y Thomas Shelby) sin duplicar registros existentes.

La configuración de los horarios individuales y descansos se incorpora con `V8__administracion_servicios_y_horarios.sql`.


## Seguimiento del cliente y WhatsApp

Después de una reserva, el frontend guarda únicamente un token aleatorio de consulta en `localStorage`. Ese token funciona como credencial técnica del navegador, pero nunca se muestra como código que el usuario deba recordar. La sección **Mis citas** se actualiza al volver a la pestaña, cada minuto mientras está abierta y también mediante el botón **Actualizar estado**.

En el panel ADMIN:

- `Confirmar` y `Cancelar cita` cambian el estado sin depender de WhatsApp.
- `Enviar WhatsApp` es una acción independiente disponible en citas confirmadas/canceladas con un número compatible.
- Las citas confirmadas/canceladas permiten reenviar el mensaje.
- RAND no envía WhatsApp automáticamente en esta versión: el administrador revisa el texto y pulsa **Enviar**. Esto evita depender de WhatsApp Business Platform para la primera versión.

Las reservas hechas antes de esta versión siguen existiendo en la base de datos, pero no aparecerán automáticamente en **Mis citas** de un navegador porque ese dispositivo nunca recibió su token privado. Las reservas nuevas sí quedan recordadas.

## Correcciones de validación e historial (septiembre de 2026)

- Una reserva pública reutiliza un cliente solo si nombre, correo y teléfono coinciden; nunca modifica sus datos anteriores. Las correcciones de identidad requieren atención de la barbería. No se permite convertir un invitado en cuenta desde el registro público sin verificar identidad; actualmente no existe un flujo automático de verificación.
- Registro y reserva validan la cantidad de dígitos del teléfono después de normalizarlo.
- Registro y login rechazan contraseñas que superen 72 bytes UTF-8, para impedir truncamiento de BCrypt. Los hashes existentes no se modifican. Las cuentas antiguas que usaran contraseñas superiores a ese límite requieren un restablecimiento verificado; su longitud original no puede deducirse del hash.
- El precio máximo entero es 99.999.999 COP, compatible con `DECIMAL(10,2)`.
- Los métodos HTTP no soportados devuelven 405 y cabecera `Allow`.
- `CorreccionesIntegrationTest` cubre estos casos y la preservación del historial, además de las pruebas existentes.
