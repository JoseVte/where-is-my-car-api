# CLAUDE.md

REST API (Java 25, Spring Boot 4.1) to save where a car was parked. Clients will be an Android app and an Angular web app. The project is a work in progress. See `README.md` (English) and `README.es.md` (Spanish) for user facing docs.

## Commands

Always use JDK 25. The default shell may have an older JRE, so set it first:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
./mvnw -q clean compile        # compile
./mvnw clean test              # tests (needs Docker for Testcontainers)
./mvnw spring-boot:run         # run the API (starts MySQL through compose.yaml)
./seed.sh                      # seed dev data and exit
docker compose --profile app up --build   # API and MySQL fully in Docker
```

## Deployment

* Production: Forge server (1 GB Linode shared with other projects), API as a daemon behind Nginx. `.github/workflows/ci-deploy.yml` tests, builds the jar and deploys it over SSH on every push to `main`. Details in `deploy/README.md`.
* Memory is tight: keep the JVM flags of the daemon, `TOMCAT_MAX_THREADS` and `DB_POOL_SIZE` small. Never build on the server.
* The repository is public: never commit the server IP, host keys or anything from `.env`.

## Tests

* Integration tests extend `support/IntegrationTest`: full context, a real MySQL started once by Testcontainers (`@ServiceConnection`), `FirebaseApp`, `FirebaseAuth` and `FirebaseSignInClient` mocked, and the database emptied before each test. Authenticate requests with `as("uid")` (a fake JWT, so no Firebase signature is checked). Keep MySQL, not H2: the case insensitive unique label depends on MySQL collation.
* Profile `test` (`src/test/resources/application-test.properties`) makes tests independent from `.env` and the Firebase credentials. They must keep passing without those files (CI).
* Adding `@MockitoBean`/`@MockitoSpyBean` in a single test class creates a new Spring context (slower). Prefer the mocks already in the base class.
* `java.util.Date` keeps milliseconds only. Truncate `Instant.now()` when comparing.
* Every new endpoint needs tests for: happy path, validation (400), ownership (another user's id gives 404), and 401 without a token.

## Architecture

Package root: `io.josrom.whereismycar`. Layers: `controllers`, `service`, `repository`, `entity`, `dto`, `config`, `component`, `seeder`, `utils`.

* **Auth is entirely Firebase.** The API does not issue tokens. `config/SecurityConfig` validates Firebase ID tokens as a resource server (Google JWK set, issuer `https://securetoken.google.com/<projectId>`, audience `<projectId>`). The authenticated user id is `jwt.getSubject()`, which is the Firebase UID.
* `users.uuid` is the Firebase UID. Never generate it locally.
* `POST /api/auth/register` creates the user with the Admin SDK, saves it locally (rolls the Firebase user back if the save fails), then signs in through the Firebase REST API (`component/FirebaseSignInClient`) to return the ID token. The Admin SDK cannot mint ID tokens.
* Registration is rate limited per client IP (`component/RegistrationRateLimiter`, Bucket4j in memory, 5 per hour by default, 429 with `Retry-After`). Tests reset it in `IntegrationTest`. Behind a reverse proxy set `server.forward-headers-strategy=native`, or every client shares the proxy IP.
* `GET /api/me` upserts the local user from the token claims (needed for users who sign in with Google, Apple, etc.). A new user gets the default categories, like on register. They are only created with the user, so categories the user deletes never come back. Their labels are Android string resources (`@string/category_home`, `@string/category_favorites`, `@string/category_others`) that the apps translate, and `update` keeps them: only icon and color can change.
* Every query on user data must filter by the authenticated user (`findByUserUuid`). Authenticating without filtering would expose every user's data.
* Every saved location has a category of its user (`categoryId` is required on create and update). Deleting a category deletes its saved locations.
* All data lives in MySQL. Firestore is no longer used (the Admin SDK is only for Auth).
* Return a response DTO instead of an entity when the entity has lazy relations (see `SavedLocationResponse`): Jackson cannot serialize Hibernate proxies.

## Configuration and secrets

* All config comes from `.env` (git ignored). `application.properties` only holds placeholders like `${FIREBASE_API_KEY}`. Spring reads `.env` through `spring.config.import`, and `docker compose` reads the same file. Keep `.env.example` in sync when adding variables.
* Never commit `.env`, `firebase-credentials.json` or `google-services.json`, and never hardcode secrets in `application.properties`, `compose.yaml` or code. Check `.gitignore` before adding new secret files.
* `FIREBASE_PROJECT_ID` and `FIREBASE_API_KEY` have no defaults on purpose, so a missing value fails fast.
* The database comes from `compose.yaml`, published on the fixed host port 3307. `spring.datasource.*` points to `127.0.0.1:3307` with the `DB_*` variables, so the app connects even when Spring Boot's docker compose support is not active (some IDE launches). Inside Docker, `SPRING_DATASOURCE_*` overrides it. `lifecycle-management=start-only` keeps MySQL running after the app stops.
* The `api` service in `compose.yaml` has `profiles: ['app']` so Spring's compose support does not start it (it would clash with the IDE run on port 8080).

## Gotchas learned the hard way

* **Lombok needs `annotationProcessorPaths`** in `maven-compiler-plugin` (already in `pom.xml`). Since JDK 23, javac does not run annotation processors from the classpath, so Maven builds fail without it. Lombok does not support JDK 27 yet, so stay on 25.
* **Stale `target/`**: errors like `ClassFormatError: Invalid method Code length 0` or `Unresolved compilation problem` mean mixed IDE and Maven output. Run `./mvnw clean compile`.
* **`/error` must stay in the `permitAll` list**, otherwise validation errors (400) and other errors show up as 401.
* **Spring Boot 4 does not provide a `RestClient.Builder` bean** without an extra starter, so `RestClient.create()` is used.
* **Do not use arrays for JPA relations** (`SavedLocation[]`). Hibernate treats them as indexed collections and fails to load. Use `List` with `mappedBy`. `Category.savedLocations` is `@JsonIgnore` to avoid serialization loops.
* `addressData` is a JSON column (`@JdbcTypeCode(SqlTypes.JSON)`). It used to be a serialized Java map in a `VARBINARY(255)`, which overflowed with real addresses (500 "HibernateExceptionTranslator"). Check column sizes match the request validation (`note` is 500).
* `ddl-auto=update` is only acceptable for development. It never changes the type or length of an existing column (those need a manual `ALTER TABLE`), and adding a `NOT NULL` column to a table with rows fails.
* The seeder syncs the Firebase password of `SEED_EMAIL` with `SEED_PASSWORD` on every run. It creates real accounts in the configured Firebase project, so never run it against production.

## Conventions

* Keep controllers thin (see `CategoryController`): validate with `@Valid` on a request record, delegate to a service, return the entity or DTO directly. Business rules and lookups scoped to the user live in the service. Request records own normalization and the mapping to the entity (`toEntity`).
* Signal errors by throwing `NotFoundException`, `ConflictException` (or another `ApiException`). `controllers/ApiExceptionHandler` turns them into `{"error": {...}}`, validation errors into `{"errors": {"field": "message"}}`, and unexpected errors into a 500 without leaking details. Do not add try/catch blocks in controllers.
* Responses are translated from `Accept-Language` (English default, Spanish supported, see `config/LocaleConfig`). Exceptions take a message key, never literal text. Add every new key to both `messages.properties` and `messages_es.properties`. Custom validation messages use `message = "{key}"`.
* New endpoints are authenticated by default. Make an endpoint public only by adding it explicitly to `SecurityConfig`.
* Use English for code, comments, commit messages and the main README. The Spanish README (`README.es.md`) must be updated together with `README.md`.
* In documentation, do not use dashes as punctuation. Rephrase with periods, commas or parentheses.
* Verify changes by compiling and running before saying they work. Say clearly when something was not run.

## Known pending work

PATCH endpoints, tests for `FirebaseSignInClient` (it builds its own `RestClient`), revoked token checks, database migrations, production configuration.
