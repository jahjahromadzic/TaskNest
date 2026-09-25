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
- [Reviews and reputation](#reviews-and-reputation)
- [Messaging](#messaging)
- [Asynchronous processing](#asynchronous-processing)
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
- **Work execution** — the tasker reports start and completion, the client
  confirms and closes. Confirmed work raises the tasker's completed-job count.
- **Messaging** — every offer opens a conversation between the tasker and the
  client, with unread counts and read receipts.
- **Reviews and reputation** — both parties review each other after a task is
  closed, and the tasker's average rating is kept on their profile.
- **Notifications** — taskers are notified when a matching task is published and
  clients when their task expires, asynchronously over RabbitMQ and by email.
- **Scheduled expiry** — a scheduler closes published tasks once their deadline
  passes.
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
| `MAIL_FROM` | `noreply@tasknest.ba` | Sender address on notification emails |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Comma-separated origins allowed to call the API |
| `TASK_EXPIRY_ENABLED` | `true` | Set to `false` to disable the expiry scheduler |
| `TASK_EXPIRY_INTERVAL_MS` | `60000` | Delay between two expiry passes |

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
| POST | `/{id}/start` | Assigned tasker | Report that work has started |
| POST | `/{id}/complete` | Assigned tasker | Report that work is finished |
| POST | `/{id}/close` | Client | Confirm the finished work and close the task |
| GET | `/mine` | Client | Tasks posted by the caller, drafts included |
| GET | `/matching` | Tasker | Published tasks matching the caller's coverage |
| GET | `/assigned` | Tasker | Tasks assigned to the caller |

Listing endpoints accept `page`, `size` and `sort`. The maximum page size is 50.
Sortable fields are `publishedAt`, `createdAt`, `updatedAt`, `expiresAt`,
`budget`, `title` and `status`. An unsupported sort field returns `400`.

Paged responses use the following shape:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 8,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

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

### Reviews — `/api`

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/tasks/{taskId}/reviews` | Client or assigned tasker | Review the other party on a closed task |
| GET | `/users/{userId}/reviews` | Public | Reviews a user has received, newest first |

### Conversations — `/api/conversations`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/` | Authenticated | The caller's conversations, latest activity first, with unread counts |
| GET | `/unread-count` | Authenticated | Unread messages across all conversations: `{ "count": 3 }` |
| GET | `/{id}/messages` | Participant | Messages, oldest first |
| POST | `/{id}/messages` | Participant | Send a message |
| POST | `/{id}/read` | Participant | Mark the other party's messages as read |

### Notifications — `/api/notifications`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/` | Authenticated | The caller's notifications, newest first |
| GET | `/unread-count` | Authenticated | Number of unread notifications: `{ "count": 3 }` |
| POST | `/{id}/read` | Authenticated | Mark one notification as read |

Notifications belong to their recipient: reading someone else's returns `403`.
Marking an already-read notification again succeeds and changes nothing.

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

### Work execution

Once an offer is accepted, the task moves through execution in three steps:

```
ASSIGNED ──start──► IN_PROGRESS ──complete──► COMPLETED ──close──► CLOSED
         (tasker)               (tasker)                (client)
```

The split between `complete` and `close` is deliberate. `COMPLETED` is the
tasker's claim that the work is done; `CLOSED` is the client confirming it. Both
steps are needed because they are taken by different parties — without that, one
of the two states would carry no information.

Access follows from the same split. `start` and `complete` are restricted to the
tasker whose offer was accepted, not to any account holding the tasker role;
`close` is restricted to the task owner. Anyone else receives `403`.

Because completion is self-reported, it changes nothing on the tasker's
reputation. The `completedJobsCount` on the tasker profile is raised only on
`close`, where the client confirms the work — otherwise a tasker could inflate it
without performing a single job.

Each transition notifies the other party: `TASK_STARTED` and `TASK_COMPLETED` go
to the client, `TASK_CLOSED` to the tasker.

**Known limitation:** nothing forces a client to close a completed task. A client
who never closes leaves the tasker's count unchanged for good. Automatic closing
after a grace period is the intended fix and is not implemented yet.

## Reviews and reputation

A closed task can be reviewed by both parties: the client reviews the tasker and
the tasker reviews the client. The request carries only a rating from 1 to 5 and
an optional comment — who is being reviewed is derived from the task's accepted
offer, never taken from the request, so nobody can rate a user they never worked
with. Anyone who is neither party receives `403`.

Only `CLOSED` tasks can be reviewed. `COMPLETED` is not enough: it is the tasker's
own claim, so allowing it would let a tasker report an invented completion and
immediately rate the client.

Each party may leave one review per task, enforced both in the service and by the
`uq_reviews_task_reviewer` constraint — the service check alone cannot stop two
simultaneous requests, and a constraint violation is translated into the same
error rather than leaking as a `500`.

Reviews cannot be edited or deleted. A review that can be revised after seeing
the other side's is an invitation to retaliate, so immutability is the feature.

The tasker's `averageRating` is cached on the profile and recomputed after each
new review. The recomputation takes an exclusive lock on the profile row before
reading the average, because the operation is read-modify-write: without the lock,
two reviews of the same tasker arriving together both read the average before
either commits, and the second overwrites the first with the value of a single
review. Clients have no profile, so their average is computed on request instead.

**Known limitation:** there is no deadline for reviewing — a task closed a year
ago can still be reviewed today.

## Messaging

Every offer opens a conversation between the tasker who made it and the client
who owns the task, so the two can agree on details before the client decides.
Participants are derived from the offer and its task — role does not matter, and
the same person can be the client in one conversation and the tasker in another.
Anyone else receives `403`.

A conversation is archived when its offer stops being live: withdrawn by the
tasker, rejected when another offer is accepted, or when the task is cancelled
or closed. An archived conversation remains readable but accepts no new
messages — otherwise a rejected tasker could keep writing to the client
indefinitely.

Reading messages does not change them. Marking them read is a separate call,
which also clears the conversation's notification. Only the other party's
messages are marked; one's own messages are never "unread".

New messages notify the recipient at most once per conversation until that
notification is read, so a conversation of fifty messages produces one
notification rather than fifty.

## Asynchronous processing

Notifications are produced off the request thread. A state change publishes a
Spring application event, which is forwarded to RabbitMQ only after the database
transaction commits, so a rolled back publication sends no message. Consumers
then write the notification row and send the email.

| Event | Routing key | Consumer writes |
|---|---|---|
| Task published | `task.published` | One notification per tasker covering the task's category and municipality |
| Task expired | `task.expired` | One notification to the task owner |

Work-execution and review notifications (`TASK_STARTED`, `TASK_COMPLETED`,
`TASK_CLOSED`, `REVIEW_RECEIVED`, `NEW_MESSAGE`) are written synchronously instead: they have a
single recipient and send no email, and writing them in the same transaction as
the state change means the notification cannot be missing while the change is
visible.

Both queues are durable and bound to the `tasknest.events` topic exchange.
Email delivery is best-effort: a failing address is logged and skipped rather
than failing the batch, because an exception would make the broker redeliver the
message and duplicate the notifications.

### Scheduled expiry

A scheduler moves published tasks past their deadline to `EXPIRED` and notifies
their owners. It runs every 60 seconds by default and only triggers the service
method, so the rule itself is tested with a fixed clock instead of by waiting.
The expiry filters in the listings and in offer submission remain in place: a
window between the deadline and the next pass exists no matter how often the
scheduler runs.

## Testing

```bash
./mvnw verify
```

The suite contains **230 tests** and requires no manual setup — Testcontainers
starts PostgreSQL and RabbitMQ automatically.

| Type | Count | Scope |
|---|---|---|
| Unit | 112 | Service business rules and the task state machine |
| Integration | 118 | Authentication, authorisation, the task lifecycle, concurrency, JPQL queries, reviews, messaging, CORS, the notification pipeline |

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
├── messaging/      RabbitMQ events, publisher, listeners, mailers
├── repository/     Spring Data repositories and query projections
├── scheduler/      Scheduled task expiry
├── security/       JWT filter, principal, authentication entry points
└── service/        Business logic

src/main/resources/
├── db/changelog/   Liquibase migrations
└── application*.yml
```

## Roadmap

- [ ] Real-time message delivery over WebSocket
- [ ] Administration: task moderation and tasker verification
- [ ] Email verification, password reset, rate limiting
- [ ] Angular frontend
- [ ] Application Dockerfile

## License

Developed as part of a software academy programme.
