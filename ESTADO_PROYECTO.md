# Estado del proyecto — Citas Barbería

Fecha de revisión: 17 de septiembre de 2026  
Alcance de la auditoría inicial: arquitectura, reservas, persistencia, seguridad y preparación para producción. Se excluyó una auditoría visual exhaustiva. La sesión posterior de implementación se limitó a reservas y se documenta al final.

## Estado general

El proyecto es un MVP funcional con una base técnica razonable: usa Spring Boot, seguridad por sesión, BCrypt, CSRF, validación de DTOs, Flyway, transacciones y bloqueo de reservas por barbero. Las credenciales administrativas conocidas fueron retiradas y la cuenta local existente fue rotada. Todavía no debe considerarse listo para producción: faltan reglas operativas indispensables de agenda e infraestructura externa como HTTPS, secretos del entorno y un usuario MySQL dedicado.

## Arquitectura actual

- **Aplicación:** monolito Spring Boot 3.3.4 sobre Java 17.
- **Frontend:** páginas HTML independientes (`index.html`, `login.html` y `panel.html`) con CSS y JavaScript incluidos dentro de cada archivo. Consumen la API mediante `fetch`.
- **Backend:** controladores REST, una capa de servicio principal para citas y repositorios Spring Data JPA. La separación por capas es parcial: algunos controladores contienen reglas y acceso directo a repositorios.
- **Seguridad:** autenticación Spring Security contra usuarios persistidos, sesiones con protección contra fijación, BCrypt coste 12 para hashes nuevos, CSRF mediante cookie y autorización por roles `ADMIN` y `CLIENTE`. La creación de citas y la consulta de disponibilidad siguen siendo públicas; las escrituras públicas tienen límite configurable por IP.
- **Reservas:** cada cita relaciona cliente, servicio y barbero. La duración se toma del servicio. Se valida jornada, fecha futura, intervalos de 15 minutos y solapamientos. La creación bloquea pesimistamente al barbero y la entidad cita tiene versión optimista.
- **Base de datos:** MySQL en ejecución normal; H2 en pruebas. El esquema se administra con Flyway mediante ocho migraciones. Hibernate usa `ddl-auto=validate`. V7 agrega idempotencia y V8 añade el ciclo de vida de servicios y los horarios semanales por barbero sin eliminar datos existentes.
- **Administración:** el panel permite consultar citas (incluido filtro diario en servidor), cambiar sus estados, gestionar barberos y servicios, y configurar horarios semanales y descansos por barbero. No existe un portal operativo específico para barberos.
- **Producción:** existe un perfil `prod` basado en variables de entorno, pero faltan controles de arranque y capacidades operativas necesarias para un despliegue confiable.

## Problemas críticos y riesgos priorizados

1. **[Resuelto] La cuenta administrativa conocida ya no se crea por defecto.** El bootstrap está desactivado, las credenciales salieron de la configuración, producción exige secretos externos y usuario MySQL no root, y la cuenta local existente fue rotada. El bootstrap explícito puede crear o rotar el primer administrador y debe desactivarse después.

2. **[Parcialmente resuelto] La agenda representa jornada semanal y descansos por barbero.** Cada barbero puede definir días activos, inicio, fin y un descanso diario dentro de la jornada general de lunes a sábado, 08:00–20:00. Disponibilidad y reserva usan la misma regla. Siguen pendientes festivos, ausencias excepcionales y vacaciones.

3. **[Alto] Las citas no conservan los datos comerciales históricos.** La cita apunta al servicio, barbero y cliente actuales, pero no guarda el precio acordado ni copias de los nombres relevantes. Un cambio posterior de precio o nombre altera la interpretación de citas pasadas y hace imposible calcular ingresos históricos confiables.

4. **[Alto] Las migraciones y la concurrencia no se prueban con el motor de producción.** Las pruebas usan H2 y desactivan Flyway. Por ello no verifican diferencias de SQL, índices, bloqueos ni migraciones sobre MySQL. Las pruebas actuales reducen el riesgo lógico, pero no demuestran el comportamiento real bajo concurrencia en producción.

5. **[Parcialmente resuelto] Protección contra abuso.** El login bloquea temporalmente la combinación IP/cuenta tras cinco fallos y las escrituras públicas de registro y reserva tienen límite configurable por IP. La idempotencia distingue reintentos de nuevas reservas. Los límites viven en memoria y son adecuados para la instancia única actual; un despliegue con varias instancias necesitaría almacenamiento compartido o límites en el proxy.

6. **[Alto] El cliente no puede cancelar ni reprogramar una cita.** El token público permite consultar la reserva, pero los cambios de estado son administrativos. Además de empeorar la experiencia, esto obliga al negocio a gestionar manualmente cambios comunes y deja ocupados horarios que el cliente ya no utilizará.

7. **[Resuelto para la primera versión] Los servicios tienen ciclo de vida administrable.** El administrador puede crear, editar, activar y desactivar servicios. Los inactivos dejan de ofrecerse y el backend impide reservarlos, sin borrar el historial.

8. **[Parcialmente resuelto] La consulta administrativa admite filtro diario en servidor.** La vista “hoy” continúa siendo directa y el API acepta `fecha`. El listado completo aún no tiene paginación; con un volumen alto aumentarán el tiempo de respuesta, el uso de memoria y la exposición de información personal.

9. **[Medio] La estrategia de inicialización de base de datos puede ocultar esquemas incompatibles.** `baseline-on-migrate=true` puede marcar como versión inicial una base existente sin comprobar que su estructura coincida exactamente. Esto eleva el riesgo de fallos tardíos al adoptar o restaurar una base de datos.

10. **[Medio] El registro y login de clientes no forman un flujo de producto completo.** La cuenta de cliente no ofrece perfil, historial global, recuperación de contraseña ni una forma clara de asociar reservas hechas como invitado. En su estado actual agrega superficie de seguridad y mantenimiento con poco beneficio funcional.

11. **[Medio] Las estadísticas pueden inducir decisiones incorrectas.** “Servicios populares” cuenta citas no canceladas de todo el historial, incluidas futuras o pendientes, sin período configurable. No existe una fuente fiable de ingresos porque el precio no queda registrado en la cita.

12. **[Medio, parcialmente resuelto] Los enlaces privados de citas tienen riesgos de exposición.** Los tokens continúan en `localStorage` y en la ruta de consulta, no caducan ni pueden rotarse. Ya existe CSP y cabeceras de navegador para reducir el impacto de una inyección o carga externa no prevista.

13. **[Medio] La separación de responsabilidades es inconsistente.** `AuthController`, `BarberoAdminController` y partes de `CitaController` mezclan HTTP, reglas de negocio y persistencia. Además, políticas como jornada, zona horaria e intervalo están codificadas dentro del servicio. Esto dificulta probar y cambiar reglas del negocio sin introducir regresiones.

14. **[Medio] El frontend concentra demasiado código y repite utilidades.** Los tres HTML contienen grandes bloques de estilos y JavaScript; funciones de CSRF, solicitudes, errores, fechas y escape se repiten. Esta estructura aumenta el costo de corregir errores de forma consistente y dificulta aplicar una política de seguridad de contenido estricta.

15. **[Bajo] Hay elementos claramente obsoletos o prescindibles.** `prueba.http` conserva identificadores y fechas rígidas que ya no representan escenarios válidos; también quedan comentarios como “nuevo campo” o “nueva consulta” que dejaron de aportar contexto. Deben eliminarse o convertirse en pruebas reproducibles cuando comience la fase de limpieza.

## Controles existentes que conviene conservar

- Validación de fecha futura, jornada, duración y solapamientos en el backend.
- Bloqueo pesimista por barbero durante la reserva y versión optimista en citas.
- Restricción única para el token de consulta y restricciones de identidad de clientes.
- Contraseñas con BCrypt, sesiones con protección contra fijación y CSRF habilitado.
- DTOs de entrada con validaciones y manejo centralizado de excepciones sin exponer detalles internos.
- Flyway como propietario del esquema y `ddl-auto=validate` en ejecución normal.

## Próximos pasos

1. **Acceso administrativo — completado:** credenciales predeterminadas eliminadas, secretos externos obligatorios, contraseña local rotada y validación de producción implementada.
2. **Operaciones públicas — parcialmente completado:** login, registro y creación de reservas ya tienen límites. La disponibilidad permanece pública y sin límite específico porque no expone datos personales; conviene aplicar límites adicionales en el proxy público si aparece abuso real.
3. **Modelar la operación real — parcialmente completado:** horarios por barbero y descansos ya comparten reglas entre disponibilidad y reserva. Quedan excepciones por fecha, festivos, ausencias y vacaciones.
4. **Preservar el historial:** guardar en la cita precio, duración y nombres comerciales acordados; definir una migración compatible con los datos existentes.
5. **Validar MySQL de verdad:** ejecutar migraciones y pruebas de integración/concurrencia contra una instancia MySQL desechable antes de publicar.
6. **Completar los flujos principales:** permitir cancelación y reprogramación seguras, con límites temporales y liberación inmediata del horario.
7. **Completar la administración — parcialmente completado:** servicios, barberos, estados de cita, filtro diario y horarios ya son operables. Quedan paginación y filtros adicionales para volúmenes altos.
8. **Definir el modelo de clientes:** implementar perfil, historial y recuperación de cuenta, o retirar temporalmente el registro público hasta que aporte valor real.
9. **Reducir exposición de tokens:** evitar secretos en rutas y almacenamiento persistente cuando sea posible; agregar expiración o rotación y una CSP compatible con el frontend.
10. **Refactorizar después de estabilizar reglas:** mover lógica a servicios, externalizar políticas de agenda y dividir recursos frontend compartidos.
11. **Preparar operación en producción:** agregar health/readiness checks, logs estructurados, métricas mínimas, política de copias de seguridad y un procedimiento probado de restauración y despliegue.
12. **Limpiar al final:** retirar archivos de prueba obsoletos, comentarios caducados y código duplicado únicamente después de confirmar dependencias.

## Evidencia disponible al cerrar la auditoría inicial

Al cerrar la auditoría inicial estaban registradas 24 pruebas aprobadas: 3 de administración de barberos, 13 de correcciones e integración, 2 de reservas y 6 de seguridad. Durante aquella sesión de análisis no se repitieron las pruebas ni se alteraron archivos de código, configuración, migraciones o datos; solamente se creó este documento. Los resultados posteriores están en la siguiente sección.

## Implementación posterior: integridad de reservas — 17 de septiembre de 2026

Esta sección reemplaza el estado de reservas de la auditoría inicial. No se realizó otra auditoría general ni se trabajó en diseño, dashboard, autenticación, estadísticas o marketing.

### Terminado

- **Concurrencia:** la transacción de reserva usa `READ_COMMITTED` y bloqueo pesimista del barbero hasta el commit. La consulta de solapamiento ve las reservas confirmadas por transacciones anteriores. El bloqueo también se usa al modificar/desactivar/eliminar un barbero para evitar actualizaciones basadas en una lectura anterior a una reserva concurrente.
- **Solapamientos:** los intervalos se tratan como inicio inclusivo y fin exclusivo. Una cita puede empezar exactamente cuando termina otra; no puede ocupar ninguna parte de su duración. Las citas canceladas liberan espacio. La disponibilidad también detecta registros que empiezan el día anterior y siguen ocupando la jornada consultada.
- **Fecha y hora:** el servicio valida futuro usando un reloj explícito de Bogotá. Se retiró `@Future` del DTO porque interpretaba una hora local usando el reloj/zona del servidor. Se mantienen los controles de domingo, jornada 08:00–20:00, misma fecha y comienzos cada 15 minutos, sin segundos ni fracciones.
- **Duración:** el servidor calcula el final a partir del servicio; rechaza duraciones corruptas o incompatibles con los límites existentes (5–240 minutos, múltiplos de 5). No acepta que el servicio exceda la hora de cierre.
- **Barbero:** una nueva cita requiere barbero existente y activo, comprobado dentro del bloqueo transaccional.
- **Doble envío:** el formulario tiene una guarda en memoria además del botón deshabilitado. Genera un UUID v4 con `crypto.getRandomValues`, compatible con el acceso por HTTP en la red local, y conserva la clave para reintentar el mismo contenido tras una respuesta perdida.
- **Idempotencia persistente:** `POST /api/citas` acepta `Idempotency-Key`. La misma clave y los mismos datos normalizados recuperan la cita existente, incluso si fue cancelada o ya pasó. Cambiar los datos con esa clave devuelve 409. La clave tiene unicidad en base de datos y una huella SHA-256 del contenido; no se reutiliza el token de consulta como clave. La cabecera es opcional para conservar compatibilidad: las solicitudes antiguas siguen protegidas contra solapamientos, pero no recuperan una respuesta perdida mediante idempotencia.
- **Errores:** claves inválidas y fechas fuera del rango de MySQL devuelven 400. Conflictos de horario/datos devuelven 409. Fallos temporales de bloqueo devuelven 503 con `Retry-After: 2`. Un fallo de integridad al guardar revierte toda la transacción y conserva su causa en el registro del servidor.
- **Datos existentes:** V7 agrega dos columnas opcionales y una restricción única; no borra citas, clientes ni tokens anteriores. No se aplicaron cambios a una base de datos real durante las pruebas.

La decisión sobre aislamiento se apoya en la [documentación de MySQL sobre niveles de aislamiento](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html): bajo `REPEATABLE READ`, las lecturas consistentes pueden reutilizar la vista de la primera lectura; `READ COMMITTED` obtiene una vista nueva por consulta.

### Pruebas y compilación

- `mvn -Dtest=ReservaIntegrationTest,ReservaIntegridadIntegrationTest,ReservaMigracionTest,ReservaErroresTest,BarberoAdminIntegrationTest,CorreccionesIntegrationTest#reservaPublicaNoModificaPerfilNiHistorial+clientePuedeRepetirReservaConDatosNormalizados+telefonoCortoPermiteConfirmarYCancelarCita package`: **29 pruebas aprobadas, 0 fallos, 0 errores**; JAR generado correctamente.
- Ocho clientes simultáneos para el mismo horario: **1 reserva creada, 7 conflictos, 1 cliente persistido**.
- Ocho reintentos simultáneos con la misma clave: **8 respuestas válidas con un mismo token y 1 sola cita persistida**.
- Casos cubiertos: pasado, zona horaria del servidor diferente, domingo, horas fuera de jornada, cierre exacto, duración, intervalos manipulados, solapamientos, citas consecutivas, cancelación y recuperación idempotente, barbero inactivo, duración inválida, fechas malformadas, claves alteradas y bloqueos temporales.
- V7 probada sobre una tabla anterior de prueba en H2: conserva registros y rechaza claves duplicadas. No equivale a ejecutar toda la cadena de Flyway sobre MySQL.
- `node --test src/test/js/reserva-envio.test.cjs`: **2 pruebas aprobadas**. Verifica sintaxis del JavaScript y simula doble envío, pérdida de respuesta, reutilización de clave y cambio de datos. No fue una prueba visual en navegador.
- Después del ajuste final que limita la consulta diaria a la hora de cierre (sin calcular el día posterior fuera del rango admitido), se repitieron únicamente los **2 casos afectados de disponibilidad**, ambos aprobados, y se volvió a generar el JAR con **BUILD SUCCESS**.

### Archivos de esta entrega

- Backend: `CitaServiceImpl`, `CitaService`, `CitaRepository`, `Cita`, `CitaRequestDTO`, `CitaController`, `BarberoAdminController` y `GlobalExceptionHandler`.
- Nuevos: `IdempotenciaReserva`, `RelojReservasConfig`, `ReservaInvalidaException`, `ReservaPersistenciaException` y `V7__idempotencia_reservas.sql`.
- Frontend: únicamente el envío de reservas en `src/main/resources/static/index.html`.
- Pruebas nuevas: `ReservaIntegridadIntegrationTest`, `ReservaMigracionTest`, `ReservaErroresTest` y `src/test/js/reserva-envio.test.cjs`.

### Pendiente y límites reales

1. **Completado posteriormente:** V7 se aplicó y la concurrencia se verificó contra MySQL 8 real; una solicitud creó la cita y las simultáneas conflictivas fueron rechazadas.
2. **Completado posteriormente:** la disponibilidad usa horario semanal y descanso individual por barbero dentro de la jornada general. Festivos, ausencias y vacaciones siguen pendientes.
3. El reintento del navegador conserva su clave mientras la página siga abierta. Tras recargar, el horario continúa protegido contra duplicados, pero recuperar automáticamente una respuesta perdida requiere conservar la clave entre recargas.
4. La exclusión de solapamientos depende de utilizar la transacción de reservas y el bloqueo de barbero. Una escritura SQL directa que omita esa lógica no tiene una restricción de exclusión de intervalos en MySQL.
5. La seguridad del perfil de producción, las credenciales administrativas y los límites básicos contra abuso se atendieron en la sesión posterior. Precios históricos y autoservicio de cancelación/reprogramación permanecen pendientes fuera del alcance de seguridad.

## Implementación posterior: seguridad — 17 de septiembre de 2026

El trabajo se limitó a seguridad; no se modificaron el diseño, las estadísticas ni las reglas de agenda.

### Terminado

- **Autenticación:** el login usa `AuthenticationManager` y `UserDetailsService` de Spring Security sobre los usuarios persistidos. Spring ya no crea un usuario temporal. La respuesta nunca contiene el hash ni la contraseña.
- **Contraseñas:** las cuentas nuevas requieren al menos 12 caracteres y respetan el límite BCrypt de 72 bytes. Los hashes nuevos usan BCrypt coste 12; los anteriores continúan siendo verificables. La contraseña administrativa conocida de MySQL fue rotada y se confirmó que la anterior devuelve 401.
- **Administrador inicial:** está desactivado por defecto. Solo actúa con `BOOTSTRAP_ADMIN=true` y variables completas; rechaza claves de ejemplo. Puede rotar la contraseña del administrador existente sin cambiar identidad ni rol.
- **Intentos de acceso:** cinco fallos de login para una misma combinación IP/correo producen bloqueo temporal y respuesta 429 con `Retry-After`; un inicio correcto limpia sus fallos.
- **Roles y permisos:** los endpoints administrativos siguen requiriendo `ADMIN`; un `CLIENTE` autenticado recibe 403. El registro público fuerza siempre el rol `CLIENTE`, aunque se envíe un campo de rol manipulado.
- **CSRF y sesiones:** CSRF permanece activo para cambios de estado y logout. La sesión cambia de identificador al autenticar, usa cookie `RANDSESSION`, `HttpOnly` y `SameSite=Lax`; producción conserva `Secure=true`.
- **CORS:** lista cerrada mediante `ALLOWED_ORIGINS`. Vacía significa mismo origen. Se rechazan comodines, rutas y, en producción, orígenes sin HTTPS. Las credenciales solo se habilitan para orígenes exactos permitidos.
- **Cabeceras:** CSP compatible con los recursos actuales, `X-Content-Type-Options`, bloqueo de frames, `Referrer-Policy: no-referrer` y `Permissions-Policy` para cámara, micrófono, geolocalización y pagos.
- **Abuso público:** registro y creación de reservas están limitados por IP, después de CSRF. Los límites se configuran con variables de entorno y no alteran las consultas públicas.
- **Validación y errores:** login limita tamaños de correo y contraseña; registro eleva el mínimo de contraseña. Los conflictos de registro usan un mensaje uniforme para no revelar si existe un correo o teléfono. Los errores 401/403 son JSON controlado y no incluyen detalles internos.
- **Secretos:** `DB_PASSWORD` ya no tiene valor predeterminado en el repositorio. Producción rechaza `root`, contraseñas vacías/de ejemplo y CORS inseguro. `.env.example` contiene únicamente marcadores y nombres de variables.

Antes de rotar la cuenta se creó un respaldo local de la base de datos, conservado fuera del repositorio. La base mantuvo 4 usuarios y 4 citas, con Flyway en V7.

### Pruebas

- Compilación y empaquetado: **BUILD SUCCESS**.
- **25 pruebas relevantes aprobadas**, 0 fallos y 0 errores: autenticación real, roles, endpoints administrativos, CSRF, CORS, CSP/cabeceras, bloqueo de login, límite de escrituras públicas, validación de producción, rotación del administrador y validaciones de contraseña/identidad.
- Arranque real contra MySQL 8: correcto, esquema V7 actualizado y sin errores.
- Cuenta local: contraseña anterior `401`; contraseña rotada `200`; hash confirmado como BCrypt coste 12.

### Archivos principales

- Modificados: `SecurityConfig`, `AuthController`, `CitasApplication`, `LoginDTO`, `RegistroDTO`, `application.properties`, `application-prod.properties`, `.env.example`, `login.html`, `README.md` y `application-test.properties`.
- Nuevos: `ControlIntentosLogin`, `ConfiguracionSeguraInicio`, `LimitePeticionesPublicasFilter` y sus pruebas específicas.

### Pendiente de infraestructura

1. Configurar en IntelliJ y en el servidor `DB_PASSWORD`; para producción también `DB_USERNAME` dedicado, `DB_URL`, perfil `prod` y HTTPS.
2. Mantener `BOOTSTRAP_ADMIN=false` después de cualquier creación o rotación. La contraseña rotada no quedó guardada en el repositorio.
3. Los límites son locales a una instancia. Si la aplicación escala horizontalmente, moverlos al proxy/API gateway o a almacenamiento compartido.
4. Los tokens privados de citas aún se guardan en el navegador y aparecen en la ruta; expiración, rotación o un área autenticada del cliente quedan para una versión posterior.
5. Recuperación de contraseña y verificación de correo aún no existen; requieren un canal de entrega confiable y quedan fuera de esta sesión.

## Implementación posterior: administración operativa — 17 de septiembre de 2026

El trabajo se limitó a las funciones administrativas necesarias para operar la primera versión. Se reutilizaron el panel, los estados de cita y la gestión de barberos existentes; no se agregaron estadísticas, dependencias ni refactorizaciones generales.

### Terminado

- **Citas:** el ADMIN puede ver la agenda completa, consultar una fecha mediante `GET /api/citas?fecha=AAAA-MM-DD` y usar los estados existentes `PENDIENTE`, `CONFIRMADA`, `COMPLETADA` y `CANCELADA` con las transiciones ya validadas por backend.
- **Servicios:** gestión protegida bajo `/api/admin/servicios` para listar, crear, editar, activar y desactivar. Un servicio inactivo no aparece en la reserva pública y tampoco puede reservarse enviando manualmente su identificador. No se eliminan servicios con historial.
- **Barberos:** se conservó la gestión existente para crear, editar, activar, desactivar y eliminar únicamente si no tiene citas. El panel incorpora acceso a su horario.
- **Horarios:** cada barbero puede configurar los siete días, con día activo, inicio, fin y un descanso opcional. El horario debe permanecer dentro de la jornada general 08:00–20:00, en intervalos de 15 minutos; el domingo permanece no laborable.
- **Integridad de agenda:** disponibilidad y creación de citas consultan la misma configuración. La reserva rechaza horas fuera del turno, días no laborables y solapamientos con descansos. La actualización del horario usa el mismo bloqueo pesimista del barbero que una reserva y se rechaza si dejaría fuera una cita futura pendiente o confirmada.
- **Seguridad:** todas las operaciones nuevas están bajo `/api/admin/**`, por lo que requieren rol `ADMIN`, sesión válida y CSRF. Un usuario `CLIENTE` recibe 403. No se modificaron CORS, autenticación ni las protecciones de sesión.
- **Panel:** la interfaz existente ahora lista y administra servicios, permite abrir el horario de cada barbero y conserva la agenda y los cambios de estado existentes. Se añadieron estados de carga y bloqueo del botón durante guardados.

### Base de datos y migración

- `V8__administracion_servicios_y_horarios.sql` agrega `servicios.activo`, su índice y `horarios_barbero` con unicidad por barbero/día y clave foránea con eliminación en cascada.
- V1–V7 no fueron modificadas. Los servicios existentes quedaron activos mediante el valor predeterminado; los barberos sin configuración conservan automáticamente la jornada general anterior.
- Antes de aplicar V8 se creó un respaldo local, conservado fuera del repositorio.
- V8 se aplicó correctamente sobre MySQL 8 real y Hibernate validó el esquema al arrancar. La base conservó **4 usuarios, 4 citas, 4 servicios y 3 barberos**. Los datos temporales de comprobación fueron eliminados; `horarios_barbero` quedó sin filas porque los barberos existentes continúan usando su horario predeterminado.

### Pruebas

- Suite completa Maven: **57 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**.
- Pruebas nuevas: CRUD lógico de servicios, exclusión pública de inactivos, permisos de CLIENTE, horario semanal, descanso, reserva válida, reserva rechazada durante descanso, filtro de citas por fecha y cambio administrativo de estado.
- MySQL real por HTTP: login ADMIN 200, guardado de horario 200, reserva durante descanso 409, reserva válida creada como `PENDIENTE`, consulta por fecha correcta y cambio a `CONFIRMADA`. Después se eliminaron el barbero, servicio, cliente y cita temporales.
- JavaScript del panel validado con `node --check`; empaquetado Maven posterior con pruebas omitidas: correcto.

### Archivos principales

- Nuevos: `HorarioBarbero`, `HorarioBarberoRepository`, `HorarioBarberoService`, `HorarioBarberoAdminController`, DTOs de horario, `ServicioAdminService`, `ServicioAdminController`, `ServicioEstadoRequestDTO`, V8 y `AdministracionOperativaIntegrationTest`.
- Modificados: `Servicio`, `ServicioRepository`, `CitaRepository`, `CitaService`, `CitaServiceImpl`, `CitaController` y `src/main/resources/static/panel.html`.

### Pendiente real

1. Excepciones por fecha, festivos, ausencias y vacaciones. No son necesarias para esta primera etapa y no se implementaron.
2. Paginación y más filtros para la agenda cuando el volumen de citas crezca.
3. Precio y nombres históricos dentro de la cita; hoy un cambio posterior en un servicio afecta la interpretación histórica.
4. Reprogramación y cancelación segura por el cliente.

## Implementación posterior: frontend y experiencia de usuario — 17 de septiembre de 2026

Esta etapa modificó únicamente `index.html`, `login.html` y `panel.html`. No se cambiaron controladores, servicios, repositorios, entidades, migraciones ni reglas de negocio.

### Terminado

- **Flujo de reserva:** se añadió contexto breve en cada paso, foco automático al título del paso activo, progreso semántico, resumen accesible y estados seleccionados mediante `aria-pressed`. Los campos del cliente quedaron asociados correctamente con sus etiquetas y el teléfono incluye ayuda contextual.
- **Disponibilidad:** muestra un indicador de carga, comunica `aria-busy`, ofrece una acción de reintento ante errores y descarta respuestas antiguas cuando el usuario cambia rápidamente barbero, fecha o servicio.
- **Mensajes y confirmaciones:** los errores de reserva y acceso reciben foco y se anuncian a tecnologías de asistencia. Quitar una cita del dispositivo pide confirmación y explica que no cancela la reserva. El panel confirma antes de cancelar citas o desactivar barberos y servicios.
- **Navegación:** la página pública incorpora enlace para saltar al contenido y un menú móvil accesible. El panel incorpora navegación rápida fija hacia agenda, servicios y barberos/horarios.
- **Acceso:** login y registro usan pestañas con estado accesible, controles para mostrar u ocultar la contraseña, botones de tamaño táctil y una presentación coherente con la identidad visual de RAND.
- **Panel:** se unificaron tipografía, superficies, foco, botones, espaciado y estados de carga con la identidad premium. La edición semanal de horarios pasa a una columna en móvil para evitar campos comprimidos.
- **Identidad del navegador:** las tres páginas usan un favicon propio embebido, evitando la solicitud inexistente a `/favicon.ico` que generaba ruido de error en el servidor.
- **Responsive y accesibilidad:** no hay desbordamiento horizontal en los tamaños comprobados, los controles interactivos visibles alcanzan al menos 44 px de alto y se respeta `prefers-reduced-motion`.

### Pruebas de interfaz

- **Móvil 390 × 844:** menú principal, navegación a reserva, selección de servicio, barbero, fecha y hora, carga de disponibilidad, resumen, avance entre pasos, asociación de etiquetas, login/registro, visibilidad de contraseña y panel administrativo.
- **Escritorio 1440 × 900:** navegación principal, composición del asistente y resumen, cuadrículas del panel, navegación administrativa, apertura/cancelación de edición de servicio y consulta de los siete días del horario.
- En ambos tamaños se comprobó que `scrollWidth` coincide con el ancho disponible: sin desplazamiento horizontal accidental.
- Consola del navegador: sin errores JavaScript. Permanece únicamente la advertencia conocida de usar Tailwind mediante CDN.
- `node --test src/test/js/reserva-envio.test.cjs`: **2 pruebas aprobadas**.
- Sintaxis JavaScript de las tres páginas validada con `node --check`; empaquetado Maven con pruebas omitidas: correcto.
- Las pruebas del panel fueron de lectura o se cancelaron antes de guardar; no se crearon citas ni se modificaron datos.

### Pendiente real de frontend

1. Sustituir Tailwind CDN por CSS compilado antes del despliegue público para eliminar la dependencia de ejecución y la advertencia de producción.
2. Extraer estilos y utilidades JavaScript compartidos para reducir duplicación entre las tres páginas; se pospuso para evitar una refactorización amplia durante esta etapa.
3. Las vacaciones, reprogramación y cancelación por cliente siguen siendo funcionalidades de producto/backend y no se abordaron aquí.

## Cierre de limpieza y entrega — 17 de septiembre de 2026

Esta fase no agregó funcionalidades ni modificó reglas de negocio. Se limitó a retirar elementos confirmados como obsoletos, reducir advertencias y dejar documentación de operación.

### Limpieza realizada

- Se eliminó `prueba.http`: contenía fecha e identificador rígidos, no tenía referencias dentro del proyecto y ya no representaba una comprobación reproducible.
- Se retiró un import estático sin uso de `AdministracionOperativaIntegrationTest` y comentarios temporales de entidad/repositorio que ya no aportaban contexto.
- Se eliminaron `console.error` de errores que ya tienen feedback visible y accesible en la interfaz. Los bloques `catch` permanecen y muestran el estado de recuperación correspondiente.
- Se retiró la selección explícita de dialecto Hibernate de desarrollo y pruebas. Hibernate detecta el dialecto desde JDBC, por lo que se elimina su advertencia de configuración sin alterar el esquema ni la lógica.
- Se eliminó la repetición de `spring.jpa.open-in-view=false` del perfil `prod`, pues el valor seguro ya se hereda de la configuración base.
- `.env` se agregó a `.gitignore`; `.env.example` quedó agrupado por perfil, base de datos, CORS, bootstrap administrativo y límites. No contiene secretos.
- `README.md` se actualizó para incluir V8, que ya existía y agrega administración de servicios y horarios.

### Configuración y producción

- El perfil `prod` exige `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y rechaza `root`, claves vacías/de ejemplo y orígenes CORS inseguros.
- `ALLOWED_ORIGINS` admite solo orígenes HTTP/HTTPS exactos; en producción deben ser HTTPS. Vacío conserva el mismo origen.
- `BOOTSTRAP_ADMIN` permanece desactivado por defecto. Las variables `APP_ADMIN_*` se usan únicamente para creación o rotación puntual y deben retirarse después.
- Se creó [ENTREGA_FINAL.md](ENTREGA_FINAL.md), con requisitos, variables, ejecución, despliegue, verificaciones y límites reales.

### Verificación final

- `mvn clean package`: **BUILD SUCCESS**, 57 pruebas aprobadas, sin fallos, errores ni pruebas omitidas.
- `node --test src/test/js/reserva-envio.test.cjs`: 2 pruebas aprobadas. Los scripts embebidos de las tres páginas también compilan sintácticamente.
- JAR iniciado contra MySQL 8 real y perfil normal: Flyway validó las 8 migraciones, detectó esquema en versión 8 y no aplicó cambios pendientes. Hibernate arrancó correctamente.
- Comprobación HTTP no destructiva: `/`, `/login.html`, `/api/auth/csrf`, catálogo de servicios y barberos devolvieron 200; disponibilidad devolvió 45 horarios para un día laborable; la agenda sin sesión devolvió 401. No se agregaron ni modificaron datos de la base real.

### Pendientes que siguen siendo reales

1. Excepciones por fecha, festivos, ausencias y vacaciones.
2. Cancelación/reprogramación segura por parte del cliente.
3. Instantáneas históricas de precio, duración y nombres en las citas.
4. Paginación de agenda y límites distribuidos para despliegues con más de una instancia.
5. Expiración o rotación de tokens de consulta y extracción/compilación de recursos frontend compartidos antes de crecer el despliegue público.

## Rediseño visual premium — 17 de septiembre de 2026

Esta etapa actualizó únicamente la presentación del frontend y conservó los contratos, identificadores, endpoints, validaciones, autenticación y reglas de negocio existentes.

### Implementado

- La página pública adopta una dirección editorial propia para RAND: paleta obsidiana, marfil y champagne, tipografía de alto contraste, navegación transparente que gana superficie al desplazarse y botones sólidos de lectura clara.
- El hero usa una pieza audiovisual original de barbería, un mensaje directo y una sola acción principal. La segunda pieza audiovisual se reproduce únicamente al entrar en el área visible para reducir tráfico y trabajo fuera de pantalla.
- Servicios, equipo, galería, reserva y citas fueron recompuestos con jerarquía editorial, mejor ritmo vertical y menos apariencia de plantilla, sin cambiar datos ni comportamiento.
- El flujo de reserva mantiene sus tres pasos, resumen, disponibilidad y manejo de errores, con una presentación más clara en móvil y escritorio.
- Login y registro usan una composición dividida cinematográfica en escritorio y un acceso compacto en móvil, conservando sus formularios y mecanismos de seguridad.
- El panel administrativo recibió una interfaz operativa oscura y sobria, con mejor jerarquía para agenda, servicios, barberos y horarios.
- Se añadieron `media/rand-hero.mp4` y `media/rand-ritual.mp4`, generados específicamente para la identidad RAND y servidos como recursos locales.

### Validación

- Responsive comprobado en 375, 390, 430, 768, 1024 y 1440 px, sin desbordamiento horizontal.
- Navegación, menú móvil, servicios y flujo de reserva comprobados hasta el formulario final, incluida la carga real de disponibilidad; no se envió ninguna cita de prueba.
- Login y registro comprobados en navegador sin enviar credenciales ni crear usuarios.
- Panel revisado en 390 y 1440 px sin modificar datos.
- Consola sin errores JavaScript; permanece la advertencia conocida de Tailwind CDN.
- `mvn clean package`: 58 pruebas aprobadas; las 2 pruebas JavaScript de prevención de doble envío también aprobaron.
- El artefacto final arranca contra MySQL 8, Flyway valida las 8 migraciones y los videos se sirven públicamente sin abrir endpoints protegidos.

### Pendiente real

1. Compilar Tailwind como recurso local antes de una publicación con exigencias estrictas de rendimiento y política de contenido.
2. Sustituir las fotografías remotas restantes por una sesión fotográfica propia de la barbería cuando el negocio disponga del material final.

## Segunda pasada de calidad visual — 17 de septiembre de 2026

Esta etapa se limitó a frontend, UI, UX, responsive y microinteracciones. No se cambiaron endpoints, autenticación, persistencia ni reglas de negocio.

### Refinamientos realizados

- Se redujo el protagonismo del champagne: queda reservado para la firma del hero, selecciones y estados de foco; los rótulos secundarios usan tonos neutros.
- Se ajustaron escala tipográfica, interlineado y espaciado de secciones para evitar títulos sobredimensionados y transiciones bruscas.
- Los botones de portada, acceso, reserva y panel comparten radios discretos, tiempos de transición cortos y movimientos mínimos.
- El encabezado de reservas dejó de fragmentarse en dos columnas y presenta título, explicación y progreso en un orden claro.
- Las opciones del asistente usan superficies planas y una indicación de selección contenida, con menos bordes y elevación.
- Los carruseles de barberos y días quedaron contenidos dentro del asistente. Se corrigió un overflow que en el paso 2 ampliaba la página móvil hasta 812 px.
- El panel administrativo usa métricas 2×2 en móvil, secciones más planas, radios menores y menos sombras, bordes y dorado.
- Login y registro comparten el mismo lenguaje de botones, pestañas, superficies y transiciones de la página principal.
- Las apariciones al hacer scroll se redujeron de 42 a 16 px, sin retrasos escalonados; se mantiene `prefers-reduced-motion`.

### Comprobaciones

- Portada y primer paso de reserva: 375, 390, 430, 768 y 1440 px sin overflow horizontal.
- Segundo paso de reserva: los mismos cinco anchos sin overflow; carruseles internos conservan desplazamiento táctil.
- En 390 px se cargaron 45 horarios en una cuadrícula de dos columnas sin ensanchar la página.
- Login y registro comprobados en 375 y 1440 px; ambos formularios conservan sus campos, estados y navegación.
- Consola del navegador sin errores. No se envió ningún formulario ni se modificaron datos.
- `mvn clean package`: 58 pruebas aprobadas. Las 2 pruebas JavaScript del envío de reservas también aprobaron.

## Dirección de arte cinematográfica — Campaign 001 — 17 de septiembre de 2026

Esta etapa se limitó al frontend. No se modificaron endpoints, autenticación, persistencia, reglas de reservas ni operaciones administrativas.

### Implementado

- La portada se convirtió en una escena de campaña a pantalla completa con entrada breve de marca, video, fotografía de respaldo local, metadatos editoriales y movimiento sutil ligado al desplazamiento.
- Se creó un manifiesto de marca de pantalla completa con composición asimétrica, fotografía arquitectónica propia y principios de precisión, tiempo y presencia.
- La antigua galería genérica se sustituyó por `Campaign 001`: tres piezas visuales con composición editorial, proporciones deliberadas y contenido creado específicamente para RAND.
- La escena intermedia usa una naturaleza muerta de herramientas como respaldo del video y conserva reproducción diferida para no cargar trabajo audiovisual fuera de pantalla.
- Se añadió una línea de progreso de lectura discreta y una apertura de marca que se muestra una sola vez por sesión. Ambas respetan `prefers-reduced-motion`.
- Login y portada ahora comparten el mismo retrato de campaña local, eliminando esa dependencia fotográfica remota y reforzando continuidad visual.

### Recursos creados

- `media/rand-campaign-razor.webp`: retrato vertical de afeitado de precisión.
- `media/rand-atelier.webp`: atelier minimalista de barbería.
- `media/rand-tools.webp`: naturaleza muerta de herramientas profesionales.
- Los tres recursos se optimizaron a WebP y pesan en conjunto menos de 430 KB. Los originales generados se conservaron fuera del artefacto de producción.

### Validación

- Portada validada visualmente en 1440 × 900 y 390 × 844.
- Manifiesto y campaña fotográfica comprobados en escritorio y móvil.
- Responsive verificado en 375, 390, 430, 768 y 1440 px: sin overflow horizontal.
- Flujo real de reserva comprobado hasta el formulario final: selección de servicio, barbero, día y horario contra MySQL real, sin enviar ni crear citas.
- Login comprobado en 390 y 1440 px; sin desbordamiento y con los formularios intactos.
- JavaScript embebido validado sintácticamente. `mvn clean test`: 58 pruebas aprobadas, sin fallos ni errores.
- Aplicación iniciada con Java 17 contra MySQL 8; Flyway validó las 8 migraciones y el esquema permaneció en versión 8.

### Pendiente real

1. Sustituir Tailwind CDN por CSS compilado antes de publicar en producción; el navegador conserva únicamente esa advertencia conocida.
2. Reemplazar fotos de barberos que proceden de URLs externas por retratos reales de la plantilla cuando el negocio haga la sesión fotográfica.
3. Comprimir nuevamente los videos con los archivos maestros finales si el alojamiento elegido necesita un presupuesto de transferencia más estricto.

## Servicios con glassmorphism — 17 de septiembre de 2026

- Se renovó únicamente la sección pública `Nuestros servicios`; la API, administración y selección de servicios dentro de la reserva permanecen sin cambios.
- La sección usa un fondo obsidiana con luz ambiental, retícula sutil y superficies de cristal ahumado acordes con la paleta marfil/champagne de RAND.
- La selección más reservada recibió una pieza destacada y el catálogo completo usa una cuadrícula de paneles translúcidos con jerarquía editorial para nombre, descripción, duración y precio.
- Los reflejos y elevaciones al pasar el cursor son discretos y se desactivan con `prefers-reduced-motion`.
- Se comprobó el contenido real de MySQL: un servicio popular y cuatro servicios activos. No se modificaron datos.
- Validación visual realizada en 390 y 1440 px, sin overflow horizontal ni errores de consola. El JavaScript embebido continúa siendo válido y el JAR se reconstruyó correctamente.

## Mis citas — archivo privado dorado — 18 de septiembre de 2026

- Se renovó únicamente la presentación de `Mis citas`; la consulta por token, actualización periódica y eliminación local conservan su comportamiento.
- La sección usa grafito, oro champagne y relieves internos para proyectar una estética de archivo privado de alta gama.
- Se incorporaron un encabezado dorado, botón metálico con reflejo controlado, placa de estado vacío y tarjetas preparadas para representar servicio, barbero, fecha, hora y estado.
- Los estados pendiente, confirmado, cancelado y completado conservan diferencias cromáticas discretas sin romper la identidad dorada.
- Las animaciones de reflejo y elevación se desactivan con `prefers-reduced-motion`.
- El botón `Actualizar estado` y el estado vacío se probaron en navegador. Responsive validado en 375, 390, 430, 768 y 1440 px sin overflow horizontal.
- No se crearon, modificaron ni eliminaron citas durante esta etapa.

## Reserva — experiencia privada dorada — 18 de septiembre de 2026

- Se renovó por completo la presentación del asistente de reserva sin cambiar endpoints, validaciones, disponibilidad ni reglas de negocio.
- El encabezado editorial combina marfil cálido, tipografía de campaña y una firma dorada; el progreso de tres pasos muestra con claridad estados activo, completado y pendiente.
- El asistente usa un espacio de trabajo oscuro con opciones numeradas para servicios, profesionales, fechas y horarios, acompañado por un resumen fijo con servicio, barbero, momento y total.
- Se mejoraron los estados seleccionados, foco, carga, botones de avance y regreso, campos del cliente y jerarquía de la confirmación.
- Se corrigió la distribución en escritorio para mantener el asistente y el resumen en sus columnas, evitando que las clases anteriores empujaran el resumen fuera de pantalla.
- El flujo real se recorrió hasta la confirmación con datos de MySQL: servicio, barbero, día, disponibilidad y hora. No se envió el formulario ni se creó una cita.
- Responsive validado en 375, 390, 430, 768 y 1440 px, sin overflow horizontal. `Mis citas` también se comprobó en 390 y 1440 px.
- Las 2 pruebas JavaScript de prevención de doble envío aprobaron. El paquete se reconstruyó y la aplicación arrancó correctamente con Java 17, MySQL 8 y Flyway 8.
