# SMP Backend Demo

Backend de prueba para correr el frontend SMP (`smp-frontend`) en local mientras no está disponible el
backend real. Cubre todas las pantallas del frontend: sesión y cuenta, Gestión (usuarios, roles y módulos),
Consultas y Movimientos FUP (nuevo ingreso, baja y autorización). Todos los datos son sintéticos.

> Es un repositorio aparte y solo de prueba. El repo del frontend no debe hacer referencia a él: si hiciera
> falta un archivo local en el frontend para usarlo, se ignora con `smp-frontend/.git/info/exclude`, no con
> su `.gitignore`.

## Requisitos

- Java 21 (por ejemplo Eclipse Adoptium / Temurin)
- PostgreSQL 15 o posterior, corriendo en `localhost:5432`
- Para el frontend: Node.js y el repo `smp-frontend`
- Opcional: IntelliJ IDEA (Maven viene incluido en el proyecto con `mvnw`)

## Puesta en marcha

### 1. Crear el usuario y la base

Abre [`db/crear-base.sql`](db/crear-base.sql), **cambia la contraseña** y ejecútalo una vez como `postgres`:

- en pgAdmin: clic derecho en la base `postgres` → *Query Tool* → pega el contenido → ejecutar; o
- en una terminal: `psql -U postgres -f db/crear-base.sql`

Crea el usuario `smp_demo` y la base `smp_demo`, con `smp_demo` como dueño.

> **No ejecutes a mano los SQL de `src/main/resources/db/migration`.** Las tablas y los datos los carga el
> backend la primera vez que arranca (Flyway). Si se crean a mano quedan con otro dueño y Flyway no sabe que
> ya existen (ver *Problemas comunes*).

### 2. Configurar la conexión

El backend lee la contraseña de la base de la variable de entorno `DB_PASSWORD`; no se guarda en ningún
archivo del repo. El usuario es `smp_demo` por omisión (`DB_USER` para cambiarlo) y la URL
`jdbc:postgresql://localhost:5432/smp_demo` (`DB_URL` para cambiarla).

**En IntelliJ:** *Run → Edit Configurations… → SmpBackendDemoApplication → Environment variables*:

```
DB_USER=smp_demo;DB_PASSWORD=tu-contraseña
```

Sin espacios antes ni después de cada valor: un espacio al final de la contraseña hace que PostgreSQL la
rechace. La configuración de IntelliJ vive en `.idea/`, que está en el `.gitignore`.

**En PowerShell:**

```powershell
$env:DB_USER = 'smp_demo'
$env:DB_PASSWORD = Read-Host 'Contraseña de smp_demo'
```

### 3. Arrancar el backend

Desde IntelliJ, ejecuta `SmpBackendDemoApplication`. Desde PowerShell, en la carpeta del proyecto:

```powershell
.\mvnw.cmd spring-boot:run
```

La primera vez, Flyway crea las tablas (verás líneas `Migrating schema "public" to version ...`). Está listo
cuando aparece `Started SmpBackendDemoApplication`. La API queda en `http://localhost:8080`.

### 4. Arrancar el frontend y entrar

En el repo `smp-frontend`:

```powershell
npm install   # solo la primera vez
npm start
```

Abre `http://localhost:4200` y entra con **`demo` / `Demo123!`**. El frontend ya apunta a
`http://localhost:8080` en desarrollo (`environment.development.ts`) y el backend permite ese origen (CORS).

### 5. (Opcional) Correr las pruebas

Con las mismas variables de entorno:

```powershell
.\mvnw.cmd test
```

Son 18 pruebas. Las que escriben datos corren en una transacción que se revierte, así que no ensucian la base
y se pueden repetir.

## Usuarios de prueba

Todos con la contraseña **`Demo123!`**. El backend se la pone al arrancar a los usuarios de las migraciones
que todavía no tienen contraseña (`demo.seed.password` en `application.yml`).

| Usuario | Rol | Para probar |
|---|---|---|
| `demo` | Administrador | Todo: Gestión, Consultas, Movimientos y las 10 etapas |
| `capturista` | Capturista (nivel EDUCACIÓN BÁSICA) | Nuevo ingreso, baja y firmar la etapa *registro* |
| `autorizador` | Autorizador | Etapas SANE, USICAMM, propone, otorga, autoriza, FONE y pagos |
| `informatica` | Informática | Etapas de Informática; es el único que puede **rechazar** |
| `nuevo` | Capturista | Cuenta sin verificar y con cambio de contraseña obligatorio |

### Correos simulados

El demo no envía correos; los escribe en la consola del backend:

- **Código de verificación** (al entrar con `nuevo`):
  `[DEMO] Código de verificación para nuevo: 123456`. Para que sea siempre el mismo, agrega
  `DEMO_CODIGO_FIJO=123456` a las variables de entorno.
- **Liga de recuperación** (*¿Olvidaste tu contraseña?*):
  `[DEMO] Liga de recuperación para capturista: http://localhost:4200/recuperar?folio=...&ts=...`.
  Ábrela en el navegador; dura 15 minutos.

Si cambias la contraseña de `nuevo` o de otro usuario y quieres volver al estado inicial, ve a
*Volver a empezar*.

## Datos para Movimientos

- **Plazas vacantes**: `13DPR0004A004`, `13DPR0005A005`, `13DPR0006A006`, `13DPR0007B007`, `13DPR0010B010`
  (busca `13DPR0`). Las que están "En trámite" no se pueden capturar.
- **Centros de trabajo**: `13DST0001A`, `13DST0002B`, `13DPR0100X`.
- **RFC/CURP válidos para un nuevo ingreso**: cualquiera con formato correcto que no exista, por ejemplo
  RFC `PERJ950615AB1` y CURP `PERJ950615HHGRRN04` (los 10 primeros caracteres deben coincidir).
- **CLABE válida de ejemplo**: `002180700000000011` (el último dígito es el verificador).
- **Reingreso**: `IAPA800303EF3` (ANA) tiene el folio `100001` en trámite, así que la búsqueda dice que no se
  puede capturar; `DLOJ900101AB1` (JUAN) tiene dos altas activas.
- **Folio 100001**: reingreso con *registro* firmado y un rechazo previo; espera en propone (y SANE/USICAMM).
- **Folio 90010**: del legacy (captura 1900-01-01, sin foto previa); espera en otorga y no se puede dar de baja.
- **Domicilio**: las 32 entidades del INEGI, los 84 municipios de Hidalgo y algunos de otras entidades;
  localidades solo de unos municipios (Pachuca, Mineral de la Reforma, Tulancingo, ...). Sin localidades el
  frontend deja el campo deshabilitado.

### Flujo de un folio

1. `capturista` registra un nuevo ingreso: folio en estatus **A**, plazas "En trámite", empleado inactivo.
2. `capturista` firma **registro** en Autorización de movimientos: el folio pasa a **F**.
3. `autorizador` firma propone, otorga y autoriza; `informatica` firma informática (validar); `autorizador`
   FONE; `informatica` informática; `autorizador` pagos. Saltarse una etapa obligatoria responde
   "Falta firmar la etapa 'x'". SANE y USICAMM son opcionales.
4. Al firmar **pagos** el folio pasa a **P**: las plazas quedan ocupadas y el empleado activo (se ve en Consultas).
5. `informatica` puede rechazar en cualquier momento antes de pagos: se borran las firmas y regresa a registro.
6. Mientras no esté procesado, `capturista` puede darlo de baja: se restauran las plazas y, si el folio creó al
   empleado, se borra.

## Volver a empezar

Para regresar todos los datos al estado inicial, borra y vuelve a crear la base (como `postgres`) y arranca el
backend; Flyway la carga de nuevo:

```sql
DROP DATABASE smp_demo;
CREATE DATABASE smp_demo OWNER smp_demo;
```

## Problemas comunes

| Mensaje en la consola del backend | Causa y solución |
|---|---|
| `The server requested SCRAM-based authentication, but no password was provided` | Falta `DB_PASSWORD`. En IntelliJ las variables de PowerShell no cuentan: ponlas en la configuración de ejecución (paso 2). |
| `la autentificación password falló para el usuario "postgres"` | No llegó `DB_USER` o tiene otro valor; revisa que diga `DB_USER=smp_demo`. |
| `la autentificación password falló para el usuario "smp_demo"` | La contraseña no coincide con la de `db/crear-base.sql`. Revisa que no tenga espacios al final. |
| `permiso denegado a la tabla ...` | Las tablas se crearon a mano con otro usuario. Lo más simple: *Volver a empezar*. |
| `Found non-empty schema(s) "public" but no schema history table` | Se ejecutaron los SQL de migración a mano. *Volver a empezar*, o arranca una vez con `--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=3` si corriste V1–V3 completos. |
| En el login del frontend: "Verifique su usuario y contraseña" | El usuario es `demo` (no `admin`) y la contraseña `Demo123!`, con D mayúscula y `!` al final. |
| En el frontend: "Sin conexión" | El backend no está corriendo o no está en el puerto 8080. |

## Migraciones

En `src/main/resources/db/migration`, las corre Flyway en orden:

| Migración | Contenido |
|---|---|
| `V1__create_demo_consultation_schema.sql` | Empleados, plazas, centros de trabajo y catálogos de Consultas |
| `V2__seguridad_y_gestion.sql` | Usuarios, roles, módulos (con los paths del frontend), íconos, niveles y etapas FUP |
| `V3__fup_movimientos.sql` | Catálogos de captura (INEGI, motivos, rechazos), plazas vacantes, folios y firmas |
| `V4__corrige_clabe_demo.sql` | Corrige una CLABE de ejemplo en bases creadas con una versión anterior de V3 |

Una migración ya aplicada no se edita: los cambios van en una nueva (`V5__...`).

## Endpoints

Todas las respuestas usan `{ success, message, data, count }`. Las reglas de negocio responden 409 con
`message`; sin el módulo o la bandera, 403. Como en el back real, varios ids van en headers (`userId`,
`roleId`, `moduleId`) y la cuenta recibe `token`, `ts`, `password` y `username` en headers.

| Área | Endpoints |
|---|---|
| Sesión | `POST /login`, `POST /users/logout` (cierra todas las sesiones del usuario), `GET /users/moduleByUser` |
| Cuenta | `POST /users/validateToken`, `POST /users/changePassword`, `GET /users/me`; sin sesión: `POST /users/sendChangePassword`, `GET /users/validatePasswordRecovery`, `POST /users/changePasswordRecovery` |
| Usuarios | `GET /users`, `GET /users/niveles`, `GET /users/user`, `POST /users`, `PATCH /users`, `PATCH /users/softdeleted` |
| Roles | `GET /roles`, `GET /roles/role`, `POST /roles`, `PATCH /roles`, `PATCH /roles/softdeleted`, `GET`/`PUT /roles/etapas` |
| Módulos | `GET /modules`, `GET /modules/module`, `POST /modules`, `PATCH /modules`, `PATCH /modules/softdeleted`, `GET /icons` |
| Consultas | `GET /empleados/opciones`, `/consultas/empleados`, `/consultas/plazas`, `/consultas/centros-trabajo`, `/consultas/categorias`, `/consultas/conceptos`, `/consultas/compatibilidad/{id}` (con sus detalles) |
| Nuevo ingreso | `GET /fup/movimientos/nuevo-ingreso/{catalogos, plazas, centros-trabajo, municipios, localidades, busqueda}`, `POST /fup/movimientos/nuevo-ingreso` (multipart `datos` + `clabePdf`) |
| Baja | `GET /fup/movimientos/baja`, `GET`/`DELETE /fup/movimientos/baja/{acuerdo}` |
| Autorización | `GET /fup/movimientos/autorizacion/{etapas, pendientes, motivos-rechazo, {acuerdo}}`, `POST /fup/movimientos/{acuerdo}/aprobar/{etapa}`, `POST /fup/movimientos/{acuerdo}/rechazar` |

## Qué se simplifica respecto al back real

- Las sesiones (tokens activos y revocados) viven en memoria: al reiniciar el backend todas se cierran y hay
  que volver a entrar.
- Un módulo cubre un movimiento por `todos_movimientos` o por `core_module_movimientos`; aprobar pide
  AUTORIZAR y dar de baja pide ELIMINAR en todos los movimientos del folio.
- La etapa en la que espera un folio se calcula con sus firmas (las obligatorias en orden; SANE y USICAMM no
  bloquean). "Días en etapa" se cuenta desde la firma anterior más reciente.
- El PDF de la CLABE se guarda en la base (`fup_clabe_pdf`); no hay endpoint para descargarlo.
- No hay lógica de nómina: procesar en Pagos solo ocupa las plazas y activa al empleado.
- La clave de firma del JWT y las contraseñas de ejemplo son solo para desarrollo.
#   s m p - b a c k e n d - d e m o  
 