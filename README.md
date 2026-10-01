# budget-tracker-api

Backend for the household budget tracker. Java 21, Spring Boot 3.5, Maven, Postgres (Supabase), deployed on Railway.

## Layout (dependencies point inward)

```
api            controllers + DTOs
application    use cases and ports
domain         pure business model/rules
infrastructure JPA, Teller client, schedulers, config
```

## Run locally

```bash
# needs a Postgres; env vars as in .env.example
export DATABASE_URL=jdbc:postgresql://localhost:5432/postgres DATABASE_USERNAME=postgres DATABASE_PASSWORD=postgres
mvn spring-boot:run
curl localhost:8080/actuator/health
```

## Tests

```bash
mvn verify   # Testcontainers starts Postgres; Docker must be running
```

## Deploy (Railway)

Railway builds from the `Dockerfile`. Set the variables from `.env.example`. Health check path: `/actuator/health`.
# budget-tracker-api
