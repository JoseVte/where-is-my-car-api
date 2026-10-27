# Where Is My Car

English | [Español](README.es.md)

REST API to save where you parked your car. The planned clients are an Android app and an Angular web app.

> **Status: work in progress (WIP).** The API changes often and there is no stable version yet. See [Current status](#current-status) to know what works and what is missing.

## Stack

* Java 25 and Spring Boot 4.1
* Spring Security as a resource server (JWT validation)
* Spring Data JPA with MySQL
* Firebase Authentication (login and registration) and the Firebase Admin SDK
* Docker Compose for the database
* springdoc (Swagger UI)

## Requirements

* JDK 25
* Docker
* A Firebase project with **Email/Password** enabled in Authentication

## Configuration

All configuration and secrets come from a `.env` file in the project root (ignored by Git). Start from the template:

```bash
cp .env.example .env
```

| Variable | Description |
|---|---|
| `APP_DEBUG` | `true` enables Spring's debug mode (development only) |
| `APP_SEED_ENABLED` | `true` runs the seeder when the app starts |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed CORS origins, separated by commas |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` | MySQL credentials |
| `FIREBASE_PROJECT_ID` | Firebase project id |
| `FIREBASE_API_KEY` | Web API key (Firebase Console, Project settings, General) |
| `FIREBASE_CREDENTIALS_PATH` | Path to the service account, defaults to `file:./firebase-credentials.json` |
| `SEED_EMAIL`, `SEED_PASSWORD` | Test user created by the seeder |

You also need a Firebase service account key saved as `firebase-credentials.json` in the project root (Firebase Console, Project settings, Service accounts, Generate new private key). This file is a secret and is listed in `.gitignore`.

## Running

### From the IDE or with Maven

With JDK 25 active (`JAVA_HOME` pointing to it):

```bash
./mvnw spring-boot:run
```

Spring Boot starts MySQL on its own using `compose.yaml` and connects to it. The database is available at `127.0.0.1:3307` and is not stopped when the app shuts down.

The API listens on `http://localhost:8080` and the Swagger documentation is at `http://localhost:8080/swagger-ui.html`.

### Everything in Docker

```bash
docker compose --profile app up --build
```

This starts MySQL and the API as containers. Do not use it at the same time as the option above, because both use port 8080.

## Tests

```bash
./mvnw clean test
```

Integration tests run against a real MySQL started with Testcontainers (Docker must be running). Firebase is mocked, so no `.env` or credentials are needed.

## Seeder

Creates a test user (in Firebase and in MySQL) with sample categories and saved locations:

```bash
./seed.sh
```

It is a command that seeds and exits. You can run it several times without duplicating data. It uses `SEED_EMAIL` and `SEED_PASSWORD` from `.env`, and syncs the Firebase account password with that value. Do not run it against a production project.

## Authentication

The API does not issue its own tokens. It validates **Firebase ID tokens** directly (`Authorization: Bearer <idToken>` header), checking the signature against Google's public keys, the issuer and the project audience.

1. **Registration:** `POST /api/auth/register` creates the user in Firebase and in the database, and returns the Firebase tokens.
2. **Login:** done by the client with the Firebase SDK (email and password, Google, etc.).
3. **First access with other providers:** `GET /api/me` creates the local user if it does not exist yet, with the default categories. Later calls never create them again, even if the user deleted them.

The user id in the database (`users.uuid`) is the Firebase UID.

Registration is limited per IP (5 per hour by default, configurable with `REGISTER_RATE_LIMIT_CAPACITY` and `REGISTER_RATE_LIMIT_PERIOD`). Above the limit the API answers 429 with a `Retry-After` header.

### Testing with Postman

Get a token with the Firebase REST API:

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={{FIREBASE_API_KEY}}
```

```json
{ "email": "seed@example.com", "password": "your-password", "returnSecureToken": true }
```

Keep the `idToken` from the response and use it as the Bearer token in the other requests. It expires after one hour.

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | No | Registers a user and returns their Firebase tokens |
| GET | `/api/me` | Yes | Creates or updates the local user from the token and returns it |
| GET | `/api/categories` | Yes | Lists the authenticated user's categories |
| GET | `/api/categories/{id}` | Yes | Returns one category |
| POST | `/api/categories` | Yes | Creates a category (unique label per user) |
| PUT | `/api/categories/{id}` | Yes | Replaces a category. Default categories keep their label (a `@string/` resource the apps translate) |
| DELETE | `/api/categories/{id}` | Yes | Deletes a category and all its saved locations |
| GET | `/api/saved-locations` | Yes | Lists saved locations, newest first. Optional `?categoryId=` filter |
| GET | `/api/saved-locations/{id}` | Yes | Returns one saved location |
| POST | `/api/saved-locations` | Yes | Saves a location (a category of the user is required) |
| PUT | `/api/saved-locations/{id}` | Yes | Replaces a saved location (a category of the user is required) |
| DELETE | `/api/saved-locations/{id}` | Yes | Deletes a saved location |

Error messages are returned in the language of the `Accept-Language` header (English by default, Spanish supported).

## Structure

```
src/main/java/io/josrom/whereismycar
├── component    REST client to sign in against Firebase
├── config       Security (JWT, CORS) and Firebase configuration
├── controllers  Endpoints
├── dto          Requests and responses
├── entity       JPA entities (User, Category, SavedLocation)
├── repository   Spring Data repositories
├── seeder       Development data
├── service      Business logic
└── utils        Response helpers
```

## Current status

Implemented:

* Registration and Firebase token validation
* Users, categories and saved locations (CRUD, scoped to the authenticated user)
* Translated error messages (English and Spanish)
* Development seeder and Docker execution
* Automated tests (integration with Testcontainers and unit)

Pending:

* PATCH (partial updates)
* `addressData` is stored as a serialized Java object, not as JSON
* Check for revoked Firebase tokens
* Database migrations (Hibernate currently uses `ddl-auto=update`)
* Production configuration
