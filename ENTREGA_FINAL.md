# Entrega final — RAND Barbería

## Funciones disponibles

- Página pública con catálogo de servicios y barberos activos.
- Reserva de cita por servicio, barbero, fecha y horario disponible.
- Validación en servidor de fecha futura, jornada, descansos, duración, barbero/servicio activos y solapamientos.
- Protección de doble envío e idempotencia para recuperar una reserva cuando se pierde la respuesta.
- Seguimiento privado de reservas nuevas desde el mismo navegador.
- Registro e inicio de sesión de clientes con contraseñas BCrypt.
- Panel ADMIN para consultar la agenda, filtrar por fecha, confirmar, cancelar o completar citas, y gestionar servicios, barberos y horarios semanales con descansos.
- Autorización por roles, CSRF, CORS de lista cerrada, cabeceras de seguridad y límites básicos para login y escrituras públicas.

## Requisitos de ejecución

- Java 17.
- MySQL Server 8.0 o posterior.
- Maven 3.9 o el Maven integrado de IntelliJ IDEA para compilar.
- Node.js solo para ejecutar la prueba JavaScript opcional.

La aplicación usa MySQL en ejecución normal y H2 aislado únicamente en la suite de pruebas. Flyway es el único componente que modifica el esquema; Hibernate lo valida al arrancar.

## Configuración necesaria

`src/main/resources/application.properties` contiene valores adecuados para desarrollo local. Define `DB_PASSWORD` fuera del repositorio antes de iniciar. En IntelliJ, añádela en las variables de entorno de la configuración de ejecución.

El archivo [.env.example](.env.example) documenta las variables disponibles, pero Spring Boot no carga automáticamente un archivo `.env`: sus valores deben configurarse en IntelliJ, en el sistema operativo, en el servicio de despliegue o en un gestor de secretos.

Variables principales:

| Variable | Uso |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Usa `prod` en el servidor. |
| `DB_URL` | URL JDBC de MySQL. Obligatoria en producción. |
| `DB_USERNAME` | Usuario MySQL dedicado; `root` se rechaza en producción. |
| `DB_PASSWORD` | Secreto de la base de datos; no se guarda en el proyecto. |
| `ALLOWED_ORIGINS` | Orígenes HTTPS exactos separados por comas. Déjala vacía cuando la web y el API compartan origen. |
| `BOOTSTRAP_ADMIN` | Déjalo en `false`. Úsalo de forma puntual para crear o rotar el primer ADMIN. |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD`, `APP_ADMIN_TELEFONO` | Requeridas solo si `BOOTSTRAP_ADMIN=true`. |
| `LOGIN_MAX_ATTEMPTS`, `LOGIN_WINDOW_MINUTES` | Límite temporal de fallos de inicio de sesión. |
| `PUBLIC_WRITE_MAX_REQUESTS`, `PUBLIC_WRITE_WINDOW_MINUTES` | Límite de registro y reserva pública por IP. |

## Ejecución local

1. Inicia MySQL y verifica que exista `barberia_db`.
2. Define `DB_PASSWORD` en tu entorno o en IntelliJ.
3. Abre `pom.xml` como proyecto Maven y selecciona Java 17.
4. Ejecuta `CitasApplication` o, desde una terminal con Maven disponible, ejecuta `mvn spring-boot:run`.
5. Abre `http://localhost:8080/`.

Para una instalación nueva que necesite el primer administrador, define temporalmente `BOOTSTRAP_ADMIN=true` y las tres variables `APP_ADMIN_*`. Inicia una vez y vuelve a dejar `BOOTSTRAP_ADMIN=false`.

## Pasos de despliegue

1. Crea una copia verificable de MySQL antes de publicar y define un procedimiento de restauración.
2. Crea `barberia_db` con codificación UTF-8 y un usuario MySQL dedicado. Si Flyway se ejecutará al iniciar, ese usuario necesita los permisos necesarios para aplicar las migraciones versionadas.
3. Configura las variables de producción mediante el entorno del servidor o un gestor de secretos. No subas archivos con contraseñas.
4. Establece `SPRING_PROFILES_ACTIVE=prod`, un `DB_URL` con TLS de MySQL cuando corresponda, un `DB_USERNAME` no privilegiado, `DB_PASSWORD` segura y `ALLOWED_ORIGINS` con los dominios públicos exactos.
5. Genera el artefacto con `mvn clean package` y publica `target/citas-core-1.0.0.jar`.
6. Ejecuta `java -jar citas-core-1.0.0.jar`. Flyway validará y aplicará las migraciones pendientes antes de que Hibernate valide el esquema.
7. Sirve la aplicación detrás de HTTPS. El perfil `prod` marca la cookie de sesión como `Secure`; configura el proxy para reenviar las cabeceras estándar de HTTPS.
8. Comprueba después del despliegue la página pública, el catálogo, la disponibilidad, el login ADMIN y el panel. Conserva copias de seguridad y revisa los logs del servidor.

## Pruebas realizadas

- `mvn clean package`: compilación Java, empaquetado y **57 pruebas** aprobadas; 0 fallos, 0 errores y 0 omitidas.
- `node --test src/test/js/reserva-envio.test.cjs`: **2 pruebas** aprobadas para sintaxis y prevención de doble envío/reintento de reserva.
- Validación sintáctica de los scripts embebidos de `index.html`, `login.html` y `panel.html`: correcta.
- Inicio del JAR contra MySQL 8 real: conexión correcta, Flyway validó **8 migraciones**, esquema en versión **8** y Hibernate arrancó sin errores.
- Comprobación HTTP sin modificar datos: inicio, login, token CSRF, catálogo de servicios y barberos devolvieron `200`; disponibilidad devolvió horarios válidos; la agenda administrativa sin sesión devolvió `401`.

## Limitaciones conocidas

- No hay vacaciones, festivos ni excepciones por fecha para un barbero.
- El cliente consulta sus citas desde el navegador, pero todavía no puede cancelarlas ni reprogramarlas por sí mismo.
- Las citas no almacenan una copia histórica de precio, duración y nombres comerciales; un cambio posterior del servicio afecta su interpretación histórica.
- Los límites de login y escrituras viven en memoria de la instancia. Una instalación con varias réplicas debe aplicar límites compartidos en el proxy o en una infraestructura común.
- El token privado de consulta de cita permanece en la ruta y en `localStorage`; no tiene expiración ni rotación.
- El frontend aún usa Tailwind mediante CDN y conserva estilos/utilidades repetidos entre páginas. Antes de un despliegue público de alto tráfico conviene compilar el CSS y extraer lo compartido.
