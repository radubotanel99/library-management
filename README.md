# Library Management System

Web application for managing a library: catalogue, members, loans, and overdue tracking.

**Stack:** Spring Boot 3 (Java 25) · Angular + Angular Material · PostgreSQL 16 · Flyway · Docker

## Layout

```
/backend     Spring Boot (Maven)
/frontend    Angular
/docs        Functional spec, data model, API contract, build plan
compose.dev.yaml    Dev database only
compose.yaml        Production: app + database
```

## Running

```bash
# Dev database (from repo root)
docker compose -f compose.dev.yaml up -d

# Backend  → localhost:8080
cd backend && mvn spring-boot:run

# Frontend → localhost:4200
cd frontend && npm start
```

Dev database: `localhost:5432`, db/user `library`, password `library_dev_pw`.

See `.claude/CLAUDE.md` for conventions and working rules.
