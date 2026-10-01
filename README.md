# budget-tracker-api

Backend for the household budget tracker. Java 21, Spring Boot 3.5, Maven, Postgres (Supabase), deployed on Railway.

## Layout (dependencies point inward)

```
api            controllers + DTOs
application    use cases and ports
domain         pure business model/rules
infrastructure JPA, Teller client, schedulers, config
```

## Run locally (production-like, no cloud services)

Prereqs: Docker, Java 21, Maven. Uses plain Postgres 17 in Docker and the `local` profile, which **disables authentication** (a guard refuses to start if the database isn't on localhost).

```bash
docker compose up -d                       # Postgres on localhost:5433
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
curl localhost:8080/actuator/health        # {"status":"UP"}
```

Flyway creates the `budget` schema and runs migrations on startup, exactly as in production.
Then run the frontend from `budget-tracker-web` (`npm run dev`, with `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`).

Reset the local database: `docker compose down -v && docker compose up -d`.

To run the real (non-local) security chain locally, leave the profile off and set the variables from `.env.example`.

## Tests

```bash
mvn verify   # Testcontainers starts Postgres; Docker must be running
```

## Deploy (Railway)

Railway builds from the `Dockerfile`. Set the variables from `.env.example`. Health check path: `/actuator/health`.
