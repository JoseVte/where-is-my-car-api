# Where Is My Car

[English](README.md) | Español

API REST para guardar dónde has aparcado el coche. Los clientes previstos son una app Android y una web en Angular.

> **Estado: trabajo en curso (WIP).** La API cambia con frecuencia y todavía no hay versión estable. Mira la sección [Estado actual](#estado-actual) para saber qué funciona y qué falta.

## Stack

* Java 25 y Spring Boot 4.1
* Spring Security como resource server (validación de JWT)
* Spring Data JPA con MySQL
* Firebase Authentication (login y registro) y Firebase Admin SDK
* Docker Compose para la base de datos
* springdoc (Swagger UI)

## Requisitos

* JDK 25
* Docker
* Un proyecto de Firebase con **Email/Password** activado en Authentication

## Configuración

Toda la configuración y los secretos salen de un archivo `.env` en la raíz (ignorado por Git). Parte de la plantilla:

```bash
cp .env.example .env
```

| Variable | Descripción |
|---|---|
| `APP_DEBUG` | `true` activa el modo debug de Spring (solo desarrollo) |
| `APP_SEED_ENABLED` | `true` ejecuta el seeder al arrancar la app |
| `APP_CORS_ALLOWED_ORIGINS` | Orígenes permitidos para CORS, separados por comas |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` | Credenciales de MySQL |
| `FIREBASE_PROJECT_ID` | Id del proyecto de Firebase |
| `FIREBASE_API_KEY` | Web API key (Firebase Console, Project settings, General) |
| `FIREBASE_CREDENTIALS_PATH` | Ruta a la service account, por defecto `file:./firebase-credentials.json` |
| `SEED_EMAIL`, `SEED_PASSWORD` | Usuario de prueba que crea el seeder |

Además necesitas la clave de una service account de Firebase guardada como `firebase-credentials.json` en la raíz (Firebase Console, Project settings, Service accounts, Generate new private key). Este archivo es secreto y está en el `.gitignore`.

## Ejecutar

### Desde el IDE o con Maven

Con el JDK 25 activo (`JAVA_HOME` apuntando a él):

```bash
./mvnw spring-boot:run
```

Spring Boot levanta MySQL por su cuenta con `compose.yaml` y se conecta a él. La base de datos queda en `127.0.0.1:3307` y no se para al cerrar la app.

La API escucha en `http://localhost:8080` y la documentación Swagger está en `http://localhost:8080/swagger-ui.html`.

### Todo en Docker

```bash
docker compose --profile app up --build
```

Levanta MySQL y la API como contenedores. No uses esta opción a la vez que la anterior, porque las dos usan el puerto 8080.

## Tests

```bash
./mvnw clean test
```

Los tests de integración usan un MySQL real que levanta Testcontainers (Docker tiene que estar arrancado). Firebase se simula, así que no hace falta el `.env` ni las credenciales.

## Seeder

Crea un usuario de prueba (en Firebase y en MySQL) con categorías y ubicaciones guardadas de ejemplo:

```bash
./seed.sh
```

Es un comando que siembra y termina. Se puede ejecutar varias veces sin duplicar datos. Usa `SEED_EMAIL` y `SEED_PASSWORD` del `.env`, y sincroniza la contraseña de la cuenta de Firebase con ese valor. No lo ejecutes contra un proyecto de producción.

## Autenticación

La API no emite tokens propios. Valida directamente los **ID tokens de Firebase** (cabecera `Authorization: Bearer <idToken>`), comprobando la firma con las claves públicas de Google, el issuer y la audiencia del proyecto.

1. **Registro:** `POST /api/auth/register` crea el usuario en Firebase y en la base de datos, y devuelve los tokens de Firebase.
2. **Login:** lo hace el cliente con el SDK de Firebase (email y contraseña, Google, etc.).
3. **Primer acceso con otros proveedores:** `GET /api/me` crea el usuario local si todavía no existe, con las categorías por defecto. Las llamadas siguientes no las vuelven a crear, aunque el usuario las haya borrado.

El identificador del usuario en la base de datos (`users.uuid`) es el UID de Firebase.

El registro está limitado por IP (5 por hora por defecto, configurable con `REGISTER_RATE_LIMIT_CAPACITY` y `REGISTER_RATE_LIMIT_PERIOD`). Por encima del límite la API responde 429 con la cabecera `Retry-After`.

### Probar con Postman

Obtén un token con la API REST de Firebase:

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={{FIREBASE_API_KEY}}
```

```json
{ "email": "seed@example.com", "password": "tu-password", "returnSecureToken": true }
```

Guarda `idToken` de la respuesta y úsalo como Bearer token en el resto de peticiones. Caduca a la hora.

## Endpoints

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | No | Registra un usuario y devuelve sus tokens de Firebase |
| GET | `/api/me` | Sí | Crea o actualiza el usuario local a partir del token y lo devuelve |
| GET | `/api/categories` | Sí | Lista las categorías del usuario autenticado |
| GET | `/api/categories/{id}` | Sí | Devuelve una categoría |
| POST | `/api/categories` | Sí | Crea una categoría (nombre único por usuario) |
| PUT | `/api/categories/{id}` | Sí | Reemplaza una categoría. Las categorías por defecto conservan su nombre (un recurso `@string/` que traducen las apps) |
| DELETE | `/api/categories/{id}` | Sí | Borra una categoría y todas sus ubicaciones guardadas |
| GET | `/api/saved-locations` | Sí | Lista las ubicaciones guardadas, de la más reciente a la más antigua. Filtro opcional `?categoryId=` |
| GET | `/api/saved-locations/{id}` | Sí | Devuelve una ubicación guardada |
| POST | `/api/saved-locations` | Sí | Guarda una ubicación (requiere una categoría del usuario) |
| PUT | `/api/saved-locations/{id}` | Sí | Reemplaza una ubicación guardada (requiere una categoría del usuario) |
| DELETE | `/api/saved-locations/{id}` | Sí | Borra una ubicación guardada |

Los mensajes de error se devuelven en el idioma de la cabecera `Accept-Language` (inglés por defecto, también español).

## Estructura

```
src/main/java/io/josrom/whereismycar
├── component    Cliente REST para iniciar sesión en Firebase
├── config       Seguridad (JWT, CORS) y configuración de Firebase
├── controllers  Endpoints
├── dto          Peticiones y respuestas
├── entity       Entidades JPA (User, Category, SavedLocation)
├── repository   Repositorios Spring Data
├── seeder       Datos de desarrollo
├── service      Lógica de negocio
└── utils        Utilidades de respuesta
```

## Estado actual

Implementado:

* Registro y validación de tokens de Firebase
* Usuarios, categorías y ubicaciones guardadas (CRUD, limitado al usuario autenticado)
* Mensajes de error traducidos (inglés y español)
* Seeder de desarrollo y ejecución con Docker
* Tests automáticos (de integración con Testcontainers y unitarios)

Pendiente:

* PATCH (actualizaciones parciales)
* `addressData` se guarda como objeto Java serializado, no como JSON
* Comprobación de tokens revocados de Firebase
* Migraciones de base de datos (ahora Hibernate usa `ddl-auto=update`)
* Configuración para producción
