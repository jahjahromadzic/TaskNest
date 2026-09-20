# TaskNest

![build](https://github.com/jahjahromadzic/TaskNest/actions/workflows/build.yml/badge.svg)

A marketplace for local services. Clients post tasks, taskers submit offers, and
clients accept one.

This repository contains the backend REST API. An Angular frontend is planned.

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Task lifecycle](#task-lifecycle)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)

## Features

- **Accounts and roles** — every account starts as a client and can activate the
  tasker role from within the application.
- **Authentication** — JWT access tokens with long-lived refresh tokens, token
  rotation, and reuse detection. Account suspension takes effect immediately.
- **Task management** — clients create, publish and cancel tasks. A state machine
  governs the allowed transitions.
- **Offers** — taskers submit offers on published tasks. When a client accepts
  one, the task is assigned and the remaining offers are rejected automatically.
- **Tasker coverage** — taskers select the categories and municipalities they
  serve, which drives task matching.
- **Task discovery** — a public listing with filters, a personalised feed of
  matching tasks for taskers, and separate views for posted and assigned work.
- **Reference data** — categories and municipalities exposed as public endpoints.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Database | PostgreSQL 17 |
| Migrations | Liquibase (32 changesets, 16 tables) |
| Persistence | Spring Data JPA, Hibernate 7 (`ddl-auto: validate`) |
| Security | Spring Security, JWT (jjwt 0.12.6) |
| Messaging | RabbitMQ |
| Mail | Spring Mail, Mailpit for local development |
| Documentation | springdoc OpenAPI |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers |
| Build | Maven |
| CI | GitHub Actions |

## Getting started

### Prerequisites

- Java 21
- Docker

### Run

Start the infrastructure:

```bash
docker compose up -d
```

This starts PostgreSQL, RabbitMQ and Mailpit.

Start the application:

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`. Liquibase creates the schema and
seeds reference data on first run.

### Local endpoints

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI specification | http://localhost:8080/v3/api-docs |
| Health check | http://localhost:8080/actuator/health |
| Mailpit web interface | http://localhost:8025 |
| RabbitMQ management | http://localhost:15672 |

### Ports

| Service | Port |
|---|---|
| Application | 8080 |
| PostgreSQL | 5432 |
| RabbitMQ | 5672 (management 15672) |
| Mailpit | 1025 (web 8025) |

## Configuration

The default profile is `dev` and runs without any environment variables.

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tasknest` | JDBC connection string |
| `DB_USERNAME` | `tasknest` | Database user |
| `DB_PASSWORD` | `tasknest` | Database password |
| `JWT_SECRET` | development value | HMAC signing key. Required in `prod` |
| `JWT_EXPIRATION_MINUTES` | `60` | Access token lifetime |
| `REFRESH_TOKEN_EXPIRATION_DAYS` | `30` | Refresh token lifetime |
| `RABBITMQ_HOST` | `localhost` | |
| `RABBITMQ_PORT` | `5672` | |
| `MAIL_HOST` | `localhost` | |
| `MAIL_PORT` | `1025` | |

### Profiles

| Profile | Purpose |
|---|---|
| `dev` | Default. SQL logging enabled, development signing key |
| `prod` | Requires `JWT_SECRET`; the application fails to start without it |
| `test` | Used by the test suite with a Testcontainers database |

Running with the production profile:

```bash
JWT_SECRET=<your-secret> ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## API reference

All endpoints are prefixed with `/api`. Authenticated requests use a bearer token:

```
Authorization: Bearer <access-token>
```

The interactive documentation is available at `/swagger-ui.html` while the
application is running.

### Authentication — `/api/auth`

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/register` | Public | Create an account and receive a token pair |
| POST | `/login` | Public | Authenticate and receive a token pair |
| POST | `/refresh` | Public | Exchange a refresh token for a new pair |
| POST | `/logout` | Public | Revoke a refresh token |
| POST | `/activate-tasker` | Authenticated | Activate the tasker role |

### Tasks — `/api/tasks`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/` | Public | List published tasks. Filters: `categoryId`, `municipalityId` |
| GET | `/{id}` | Public | Task details. Drafts are visible to the owner only |
| POST | `/` | Client | Create a task as a draft |
| POST | `/{id}/publish` | Client | Publish a draft |
| POST | `/{id}/cancel` | Client | Cancel a task |
| GET | `/mine` | Client | Tasks posted by the caller, drafts included |
| GET | `/matching` | Tasker | Published tasks matching the caller's coverage |
| GET | `/assigned` | Tasker | Tasks assigned to the caller |

Listing endpoints accept `page`, `size` and `sort`. The maximum page size is 50.
Sortable fields are `publishedAt`, `createdAt`, `updatedAt`, `expiresAt`,
`budget`, `title` and `status`.

### Offers — `/api`

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/tasks/{taskId}/offers` | Tasker | Submit an offer |
| GET | `/tasks/{taskId}/offers` | Task owner | List offers received on a task |
| POST | `/offers/{offerId}/accept` | Task owner | Accept an offer and assign the task |
| POST | `/offers/{offerId}/withdraw` | Offer owner | Withdraw a pending offer |
| GET | `/offers/mine` | Tasker | Offers submitted by the caller |

### Tasker profiles — `/api/tasker-profiles`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/me` | Tasker | Own profile |
| PUT | `/me` | Tasker | Update headline and bio |
| PUT | `/me/categories` | Tasker | Replace the covered categories |
| PUT | `/me/municipalities` | Tasker | Replace the covered municipalities |
| GET | `/{id}` | Authenticated | Public view of a tasker profile |

### Reference data — `/api`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/categories` | Public | Active service categories |
| GET | `/municipalities` | Public | Municipalities, ordered by name |

### Error responses

Errors are returned as `application/problem+json`
([RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807)):

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Offers can only be submitted on published tasks",
  "instance": "/api/tasks/ac140a03-a07d-1259-81a0-7da2766a0004/offers"
}
```

| Status | Meaning |
|---|---|
| 400 | Validation error or business rule violation |
| 401 | Missing, invalid or expired token |
| 403 | Authenticated but not permitted, or account not active |
| 404 | Resource does not exist or is not visible to the caller |
| 409 | Invalid state transition or concurrent modification |

## Task lifecycle

| State | Meaning |
|---|---|
| `DRAFT` | Created but not yet visible to taskers |
| `PUBLISHED` | Open for offers |
| `ASSIGNED` | An offer was accepted |
| `IN_PROGRESS` | Work has started |
| `COMPLETED` | Work finished |
| `CLOSED` | Finalised after completion |
| `CANCELLED` | Withdrawn by the client |
| `EXPIRED` | Publication window elapsed without assignment |
| `REMOVED` | Taken down by an administrator |

Allowed transitions:

| From | To |
|---|---|
| `DRAFT` | `PUBLISHED`, `CANCELLED` |
| `PUBLISHED` | `ASSIGNED`, `EXPIRED`, `CANCELLED`, `REMOVED` |
| `ASSIGNED` | `IN_PROGRESS`, `CANCELLED`, `REMOVED` |
| `IN_PROGRESS` | `COMPLETED`, `CANCELLED` |
| `COMPLETED` | `CLOSED` |
| `CLOSED`, `CANCELLED`, `EXPIRED`, `REMOVED` | terminal, no further transitions |

Any other transition is rejected with `409 Conflict`.

## Testing

```bash
./mvnw verify
```

The suite contains **132 tests** and requires no manual setup — Testcontainers
starts a PostgreSQL instance automatically.

| Type | Count | Scope |
|---|---|---|
| Unit | 67 | Service business rules and the task state machine |
| Integration | 65 | Authentication, authorisation, concurrency, JPQL queries |

GitHub Actions runs the same command on every push and pull request.

## Project structure

```
src/main/java/ba/tfb/tasknest/
├── config/         Security and OpenAPI configuration
├── controller/     REST controllers
├── domain/         Task state machine
├── dto/            Request and response records
├── entity/         JPA entities and enums
├── exception/      Application exceptions and the global handler
├── repository/     Spring Data repositories and query projections
├── security/       JWT filter, principal, authentication entry points
└── service/        Business logic

src/main/resources/
├── db/changelog/   Liquibase migrations
└── application*.yml
```

## Roadmap

- [ ] RabbitMQ notification pipeline
- [ ] Scheduled expiry of published tasks
- [ ] Messaging between client and tasker over WebSocket
- [ ] Reviews and tasker ratings
- [ ] Administration: task moderation and tasker verification
- [ ] Email verification, password reset, rate limiting
- [ ] Angular frontend
- [ ] Application Dockerfile

## License

Developed as part of a software academy programme.
