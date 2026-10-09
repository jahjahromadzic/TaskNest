# TaskNest

![build](https://github.com/jahjahromadzic/TaskNest/actions/workflows/build.yml/badge.svg)

A marketplace for local services. Clients post tasks, taskers submit offers, and
clients accept one.

This repository contains the Spring Boot REST API and, in `frontend/`, the Angular
single-page application that uses it.

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Frontend](#frontend)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Task lifecycle](#task-lifecycle)
- [Task photos](#task-photos)
- [Reviews and reputation](#reviews-and-reputation)
- [Messaging](#messaging)
- [Real-time updates](#real-time-updates)
- [Administration](#administration)
- [Asynchronous processing](#asynchronous-processing)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Roadmap](#roadmap)

## Features

- **Accounts and roles** — every account starts as a client and can activate the
  tasker role from within the application. On the settings page a user changes their
  name and phone, and their password after confirming the current one.
- **Authentication** — JWT access tokens with long-lived refresh tokens, token
  rotation, and reuse detection. Refresh tokens are stored only as SHA-256 hashes,
  so a copy of the database does not contain usable tokens. Account suspension
  takes effect immediately.
- **Task management** — clients create, publish, edit and cancel tasks. A state machine
  governs the allowed transitions. A task can be edited while it is a draft or open for
  offers, and taskers who already sent an offer are told about the change.
- **Offers** — taskers submit offers on published tasks. When a client accepts
  one, the task is assigned and the remaining offers are rejected automatically.
  While an offer waits, the tasker can change its price, for example after agreeing
  on a new one in the chat; the client is notified and the price locks once accepted.
- **Tasker coverage** — taskers select the categories and municipalities they
  serve, which drives task matching.
- **Task discovery** — a public listing with a text search and filters, a personalised feed of
  matching tasks for taskers, and separate views for posted and assigned work.
- **Work execution** — the tasker reports start and completion, the client
  confirms and closes. Confirmed work raises the tasker's completed-job count.
- **Messaging** — every offer opens a conversation between the tasker and the
  client, with unread counts and read receipts.
- **Task photos** — up to five photos per task, scaled down in the browser and
  cleaned of location data on the server.
- **Reviews and reputation** — both parties review each other after a task is
  closed, and the tasker's average rating is kept on their profile. Clients have a
  profile too, so a tasker can check a client's rating, reviews and past hires before
  sending an offer. Both sides live on one profile page with an "As tasker" / "As client"
  switch.
- **Becoming a tasker** — a client fills in a headline, categories and municipalities first;
  the tasker role is granted only when that profile is saved, so there are no taskers
  without a trade or an area.
- **Notifications** — taskers are notified when a matching task is published and
  clients when their task expires, asynchronously over RabbitMQ and by email.
  Clients hear about every new offer and taskers when they are hired.
- **Scheduled expiry** — a scheduler closes published tasks once their deadline
  passes.
- **Administration** — suspension, tasker verification and task removal, with
  the first administrator promoted from configuration.
- **Reports** — any signed-in user can report a task or another user (from the task
  page, a profile or a chat) with a reason. The admin panel lists the reports, and
  removing the task or suspending the user resolves every open report about them.
- **Reference data** — fourteen categories, each with a stable `slug`, and all 143
  municipalities of Bosnia and Herzegovina under their local names, grouped by region (the ten
  cantons, Republika Srpska and Brčko District), exposed as public endpoints. Everywhere a place is
  chosen the region comes first and then its municipalities, so the task list can also be filtered by a
  whole canton.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Database | PostgreSQL 17 |
| Migrations | Liquibase (46 changesets, 18 tables) |
| Persistence | Spring Data JPA, Hibernate 7 (`ddl-auto: validate`) |
| Security | Spring Security, JWT (jjwt 0.12.6) |
| Messaging | RabbitMQ |
| Real-time | WebSocket with STOMP (Spring simple broker), `@stomp/stompjs` in the browser |
| Mail | Spring Mail, Mailpit for local development |
| Documentation | springdoc OpenAPI |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers |
| Build | Maven |
| Frontend | Angular 22, TypeScript, RxJS, Tailwind CSS 4, Lucide icons |
| Frontend testing | Vitest |
| CI | GitHub Actions |

## Getting started

### Prerequisites

- Java 21
- Docker
- Node.js 24.15 or newer, for the frontend

### Run everything with one command

```bash
docker compose --profile app up -d --build --wait
```

`--wait` returns only when every container is healthy, so the application is ready the
moment the command finishes. This builds and starts the whole application: PostgreSQL, RabbitMQ, Mailpit, the
backend and the frontend. Open `http://localhost:4200`; emails land in Mailpit at
`http://localhost:8025`. The frontend container is nginx: it serves the built Angular
app and forwards `/api` and the `/ws` WebSocket to the backend, so the browser sees
one origin. The backend runs the `dev` profile, so an empty database is filled with
demo data. Stop it with `docker compose --profile app down`.

### Run for development

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

### Demo data

In the `dev` profile the application also fills an empty database with demo data
on startup. The content is in Bosnian, as real users in Sarajevo would write it:
fifteen accounts, forty-two open tasks with offers (at least three in every category), and a task in every other status
(draft, assigned with an unread message, in progress, completed and waiting for the
client, closed with reviews in both directions, cancelled, expired and removed by a
moderator). Every task has a real street address in its municipality with coordinates written into
the seeder, so seeding never calls the geocoder. Pending offers come with chats, so an offer can be accepted from the
conversation, and every account except the admin has notifications. Dates are
relative to the moment of seeding, so the tasks are fresh and none of them is
picked up by the expiry or deadline schedulers.

Every demo account uses the password `demo12345` (`DEMO_DATA_PASSWORD`).

| Account | Roles | Story |
|---|---|---|
| `amra@demo.tasknest.ba` | client | Open tasks, an assigned task with an unread message, a task in progress, a closed and reviewed task, a draft, and a chat on a pending offer |
| `emina@demo.tasknest.ba` | client | Open tasks, a job finished by Tarik waiting for her confirmation, two closed jobs and an expired task |
| `haris@demo.tasknest.ba` | client | Open tasks, a chat with Adnan about moving, a closed job and a cancelled task |
| `lejla@demo.tasknest.ba` | client, admin | Administration |
| `nermin@demo.tasknest.ba` | client, suspended | A task removed by a moderator as spam |
| `emir@demo.tasknest.ba` | client, tasker | Plumbing, electrical, heating and appliances, verified; also hired Selma as a client |
| `selma@demo.tasknest.ba` | client, tasker | Cleaning, painting and gardening, verified, rating 5.00 |
| `adnan@demo.tasknest.ba` | client, tasker | Moving, furniture assembly, carpentry and locks, rating 4.50 |
| `tarik@demo.tasknest.ba` | client, tasker | Electrical, painting, air conditioning and computers, new on TaskNest |
| `mirza@demo.tasknest.ba` | client, tasker | Carpentry, tiling and locks, verified |
| `alen@demo.tasknest.ba` | client, tasker | Heating, air conditioning and appliances, new on TaskNest |
| `lamija@demo.tasknest.ba` | client, tasker | Gardening, cleaning and computers, verified |
| `dzenana@demo.tasknest.ba` | client | Open tasks, a chat with Mirza about a basement lock, a closed job |
| `kenan@demo.tasknest.ba` | client | Open tasks, a chat with Alen about a boiler service, a closed job |
| `sanela@demo.tasknest.ba` | client | Open tasks in heating, gardening, carpentry and computers |

The seeder runs only once: it does nothing when `amra@demo.tasknest.ba` already
exists. To start again from a clean database, remove the Docker volume:

```bash
docker compose down -v
docker compose up -d
```

Demo data is off by default in every other profile; the seeder refuses to start
when it is enabled without a password.

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

## Frontend

The Angular application lives in `frontend/`. Start the backend first, then:

```bash
cd frontend
npm install
npm start
```

The application is available at `http://localhost:4200`. The development server
forwards every `/api` request to `http://localhost:8080` (`proxy.conf.json`), so the
browser sees one origin. That matters because the refresh token travels in a
`SameSite=Strict` cookie.

| Command | Purpose |
|---|---|
| `npm start` | Development server with live reload |
| `npm run build` | Production build into `frontend/dist/` |
| `npm test -- --watch=false` | Run the test suite once |
| `npm run api:types` | Regenerate `src/app/api/schema.ts` from the running backend's OpenAPI document |

### How it works

- **Types from the API** — request and response types are generated from
  `/v3/api-docs`, so a renamed backend field breaks the frontend build instead of
  failing at runtime.
- **Session** — the access token is kept in memory only, never in `localStorage`.
  The refresh token is an `httpOnly` cookie the page cannot read. On startup the
  application calls `/api/auth/refresh` before the first page renders, so a reload
  keeps the user signed in.
- **Interceptor** — every API call gets the bearer token. A token about to expire
  is renewed before the call, and a `401` triggers one renewal and a retry. Calls
  that fail together share a single renewal, because the backend treats a reused
  refresh token as theft. Browser tabs take turns through the Web Locks API.
- **Guards** — pages are protected by login and by role (tasker, admin). A visitor
  is sent to the login page and returned to the requested page afterwards; the
  return address must stay inside the application.
- **Task list** — the search text, filters, sorting and the page number live in the URL, so a
  filtered list can be shared, reloaded and navigated with the back button. The search
  runs 300 ms after the user stops typing, so a word costs one request, not one per letter.
- **Live updates** — one WebSocket connection per signed-in user delivers new
  messages, read receipts and notifications. Unread counts are fetched again on
  every live event and after every reconnect, so an event missed while offline
  cannot leave a wrong badge behind. In development the Angular proxy forwards
  `/ws` to the backend.
- **Two languages** — the whole interface is available in English and Bosnian, switched with one header button
  without a reload. Texts live in two dictionaries, `i18n/en.ts` and `i18n/bs.ts`; the Bosnian one must have
  exactly the same keys, and every key used in a template is type-checked, so a missing or misspelled
  translation fails the build. Plurals follow the Bosnian rules (1 dan, 2 dana, 5 dana), dates and prices are
  formatted per language, and the choice is remembered. The API stays in English: the frontend translates the
  fixed set of server messages and notification texts, and passes anything unknown through unchanged.
- **Light and dark theme** — one header button switches between them with a circular reveal that starts
  at the button. Colours are CSS variables, so the dark theme redefines the palette once instead of adding
  a dark variant to every template. Without a saved choice the app follows the system setting, and a small
  script in `index.html` applies the theme before the first paint, so the page never flashes white.
- **Icons** — Lucide icon data is drawn by one small component, so an icon costs a
  few hundred bytes and loads only with the page that uses it.

### Structure

```
frontend/src/app/
├── api/          Generated OpenAPI types and short aliases
├── auth/         Session service, interceptor, guards, login and sign-up layout
├── components/   Reusable pieces: task card, pagination, dropdown, category icon
├── i18n/         Dictionaries, translate pipe, language switch, server message translation
├── layout/       Header and mobile bottom navigation
├── pages/        One folder per route
├── services/     HTTP services per backend area
├── shared/       Toasts, error parsing, formatting helpers
└── theme/        Light and dark theme service and switch
```

### Status

| Phase | Scope | Status |
|---|---|---|
| 1 | Application shell, login and sign-up, session renewal, guards, public task list | Done |
| 2 | Client flow: post tasks, review offers, accept, confirm and close, review, public client profile | Done |
| 3 | Tasker flow: profile, matching tasks, offers, work execution, public profile | Done |
| 4 | Messages and notifications, delivered live over WebSocket | Done |
| 5 | Administration: overview, users, tasker verification, task moderation | Done |

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
| `REFRESH_COOKIE_SECURE` | `true` | Send the refresh cookie over HTTPS only; browsers also accept it on `localhost` |
| `RABBITMQ_HOST` | `localhost` | |
| `RABBITMQ_PORT` | `5672` | |
| `MAIL_HOST` | `localhost` | |
| `MAIL_PORT` | `1025` | |
| `MAIL_USERNAME` | empty | SMTP login, needed by real mail providers |
| `MAIL_PASSWORD` | empty | SMTP password or app password |
| `MAIL_SMTP_AUTH` | `false` | Log in to the SMTP server |
| `MAIL_SMTP_STARTTLS` | `false` | Upgrade the connection to TLS and refuse to send without it |
| `MAIL_TIMEOUT_MS` | `5000` | Connect, read and write timeout towards the SMTP server |
| `MAIL_FROM` | `noreply@tasknest.ba` | Sender address on notification emails |
| `RABBITMQ_RETRY_MAX_ATTEMPTS` | `3` | Attempts per message before it goes to the dead-letter queue |
| `RABBITMQ_RETRY_INITIAL_INTERVAL` | `1s` | Pause before the second attempt; it doubles after each failure |
| `PHOTOS_DIRECTORY` | `data/photos` | Where uploaded task photos are stored |
| `APP_ADMIN_EMAIL` | empty | Existing account promoted to administrator at startup |
| `DEMO_DATA_ENABLED` | `true` in `dev`, `false` otherwise | Fill an empty database with demo accounts and tasks |
| `DEMO_DATA_PASSWORD` | `demo12345` in `dev` | Password of every demo account |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Comma-separated origins allowed to call the API |
| `FRONTEND_URL` | `http://localhost:4200` | Base address used in the password reset link |
| `PASSWORD_RESET_EXPIRATION_MINUTES` | `30` | Lifetime of a password reset link |
| `LOGIN_ATTEMPTS_PER_EMAIL` | `5` | Failed logins allowed per email within the login window |
| `LOGIN_ATTEMPTS_PER_ADDRESS` | `20` | Failed logins allowed per client address within the login window |
| `LOGIN_WINDOW_MINUTES` | `15` | Length of the login window |
| `RESET_REQUESTS_PER_EMAIL` | `3` | Reset links sent per email within the reset window |
| `RESET_REQUESTS_PER_ADDRESS` | `10` | Reset requests accepted per client address within the reset window |
| `RESET_WINDOW_MINUTES` | `60` | Length of the reset window |
| `FORWARD_HEADERS_STRATEGY` | `native` | Trust `X-Forwarded-For` only from internal proxies, so the login limit sees the real client address behind nginx |
| `TASK_EXPIRY_ENABLED` | `true` | Set to `false` to disable the expiry scheduler |
| `TASK_EXPIRY_INTERVAL_MS` | `60000` | Delay between two expiry passes |
| `TASK_DEADLINES_ENABLED` | `true` | Set to `false` to disable the deadline scheduler |
| `TASK_ASSIGNMENT_START_DAYS` | `14` | Days an assigned task may wait for work to start before it reopens |
| `TASK_COMPLETION_CLOSE_DAYS` | `7` | Days a completed task waits for the client before it closes |

### Profiles

| Profile | Purpose |
|---|---|
| `dev` | Default. SQL logging enabled, development signing key, demo data |
| `prod` | Requires `JWT_SECRET`; the application fails to start without it |
| `test` | Used by the test suite with a Testcontainers database |

Locally all mail goes to Mailpit, which accepts anything. A real provider only
needs environment variables, for example Gmail with an app password:

```bash
MAIL_HOST=smtp.gmail.com MAIL_PORT=587 MAIL_USERNAME=you@gmail.com MAIL_PASSWORD=<app-password> \
MAIL_SMTP_AUTH=true MAIL_SMTP_STARTTLS=true MAIL_FROM=you@gmail.com
```

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
| POST | `/login` | Public | Authenticate and receive a token pair. After 5 wrong passwords for one email, or 20 from one address, within 15 minutes, login answers `429` with `Retry-After` until the oldest failure leaves the window |
| POST | `/refresh` | Public | Exchange the refresh-token cookie for a new access token; rotates the cookie |
| POST | `/logout` | Public | Revoke the refresh token and clear its cookie |
| POST | `/password-reset/request` | Public | Email a one-time link to choose a new password. Always answers `202`, so the response never reveals whether an account exists; at most 3 links per email and 10 per address are sent per hour |
| POST | `/password-reset/confirm` | Public | Set a new password with the token from the link. The token works once and expires after 30 minutes; every existing session of the account is signed out |
| POST | `/activate-tasker` | Authenticated | Become a tasker: grants the tasker role and saves the headline, optional bio, categories and municipalities in one transaction. A headline, at least one category and one municipality are required, and an unknown category or municipality leaves the account unchanged |

The refresh token never appears in a response body. Register, login and refresh
set it as a cookie that is `HttpOnly` (unreadable by JavaScript, so an injected
script cannot steal it), `SameSite=Strict` (not sent with requests started from
another site) and scoped to `/api/auth` (not sent with any other request). The
access token is returned in the body and kept in memory by the client. Because
of `SameSite=Strict`, the frontend has to be served from the same site as the
API, which in development is done by the Angular dev-server proxy.

**Password reset.** The reset token is 32 random bytes sent only in the email; the
database keeps its SHA-256 hash, like the refresh token, so a leaked database does not
leak working links. Asking again invalidates the previous link.

**Login throttling.** Failed logins are counted in memory per email and per client
address in a sliding 15-minute window. Counting per email stops guessing one
password; counting per address stops trying one password on many accounts. A
successful login clears the count for that email. The counters live in the
application, which fits a single instance; several instances would share them
through Redis.

### Account — `/api/account`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/` | Authenticated | The caller's own email, name, phone and the date they joined |
| PUT | `/` | Authenticated | Change the first name, last name and phone. The email is the login and stays fixed |
| POST | `/password` | Authenticated | Change the password with `currentPassword` and `newPassword`. A wrong current password answers `400` and counts as a failed login, so it is throttled the same way. Every other session is signed out, and this device gets a fresh token pair |

When the password changes, here or through a reset link, the account's refresh tokens are deleted rather than
marked as revoked. A revoked token that comes back is treated as stolen and signs
out every session, so an old browser tab refreshing a revoked token would also sign
out the device that just changed the password. A deleted token is simply unknown.

### Tasks — `/api/tasks`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/` | Public | List published tasks. Filters: `q` (text, at most 100 characters), `categoryId`, `region` (a canton, Republika Srpska or Brčko District), `municipalityId` |
| GET | `/{id}` | Public | Task details, including the hired tasker. Drafts are visible to the owner only. The street address and its coordinates are shown only to the owner and the hired tasker; everyone else sees the municipality |
| POST | `/` | Client | Create a task as a draft. The street address is required and is turned into coordinates through OpenRouteService; an address that cannot be found is refused with 400 |
| PUT | `/{id}` | Client | Change the title, description, category, municipality, address and budget of a draft or a task open for offers. The address is looked up again only when it or the municipality changed. Taskers with a pending offer are notified, unless nothing changed |
| POST | `/{id}/publish` | Client | Publish a draft |
| POST | `/{id}/cancel` | Client | Cancel a task |
| POST | `/{id}/reopen` | Client | Release the assigned tasker and reopen the task |
| POST | `/{id}/start` | Assigned tasker | Report that work has started |
| POST | `/{id}/complete` | Assigned tasker | Report that work is finished |
| POST | `/{id}/close` | Client | Confirm the finished work and close the task |
| GET | `/mine` | Client | Tasks posted by the caller, drafts included. Optional repeated `status` filter |
| GET | `/mine/counts` | Client | Number of the caller's tasks in every status, from one grouped query |
| GET | `/matching` | Tasker | Published tasks matching the caller's coverage |
| GET | `/assigned` | Tasker | Tasks assigned to the caller |

**Search.** `q` looks for the text in the title and the description, ignoring
letter case and the Bosnian letters č, ć, š, đ and ž, so `ciscenje` finds
"Čišćenje stana". The `%` and `_` characters are searched as plain text rather than
as SQL wildcards.

**Editing.** The task row is locked for writing while it is edited, and submitting
an offer waits for that lock, so an offer cannot slip in between the edit and the
notifications and miss the news.

Listing endpoints accept `page`, `size` and `sort`. The maximum page size is 50.
Sortable fields are `publishedAt`, `createdAt`, `updatedAt`, `expiresAt`,
`budget`, `title` and `status`. An unsupported sort field returns `400`. Tasks
without a value in the sorted field, such as tasks without a budget, always come
last in both directions.

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
| GET | `/tasks/{taskId}/offers` | Task owner | List offers received on a task, each with the tasker's rating, review count, completed jobs and verification, from a single query |
| GET | `/tasks/{taskId}/offers/mine` | Tasker | The caller's own offer on a task, or `204` when there is none |
| POST | `/offers/{offerId}/accept` | Task owner | Accept an offer and assign the task. An optional body `{ "expectedPrice": 60 }` names the price the client saw; if the tasker has changed it since, the answer is `409` and nothing is accepted |
| PUT | `/offers/{offerId}` | Offer owner | Change the price of a pending offer on a task that is still open. The client gets an `OFFER_UPDATED` notification with the old and the new price |
| POST | `/offers/{offerId}/withdraw` | Offer owner | Withdraw a pending offer, or back out of an accepted one |
| GET | `/offers/mine` | Tasker | Offers submitted by the caller |

**Changing a price.** A client must never hire at a price they did not see. The
frontend sends the price it shows with every acceptance, so an acceptance made from a
stale page is refused with `409`. When the change and the acceptance happen at the
same moment, the `@Version` column on the offer lets only one of them commit. After
acceptance the offer is no longer pending, so its price is locked.

### Tasker profiles — `/api/tasker-profiles`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/me` | Tasker | Own profile |
| PUT | `/me` | Tasker | Update headline and bio |
| PUT | `/me/categories` | Tasker | Replace the covered categories |
| PUT | `/me/municipalities` | Tasker | Replace the covered municipalities |
| GET | `/{id}` | Authenticated | Public view of a tasker profile |
| GET | `/users/{userId}` | Authenticated | The same view, looked up by the tasker's user id as offers carry it |

### Reference data — `/api`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/categories` | Public | Active service categories |
| GET | `/municipalities` | Public | All municipalities of Bosnia and Herzegovina with their region, ordered by name |

### Task photos — `/api`

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/tasks/{taskId}/photos` | Task owner | Add a photo (multipart field `file`) while the task is a draft or open for offers; at most 5 per task and 5 MB per photo |
| DELETE | `/tasks/{taskId}/photos/{photoId}` | Task owner | Remove a photo; the next one becomes the cover |
| GET | `/photos/{photoId}` | Public | The photo itself, cached by the browser for a year. Photos of a task removed by a moderator answer `404` |

The task response lists its photos, and every task card carries `coverPhotoId`, the
first photo, so lists show a thumbnail without an extra request.

### Reviews — `/api`

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/tasks/{taskId}/reviews` | Client or assigned tasker | Review the other party on a closed task |
| GET | `/tasks/{taskId}/reviews` | Authenticated | Both reviews of a task, oldest first |
| GET | `/users/{userId}/reviews` | Public | Reviews a user has received, newest first. `as=CLIENT` or `as=TASKER` keeps only the reviews received in that role; a user who is both gets reviews from both sides. The task and reviewer are fetched with the page, so the query count does not grow with the number of reviews |

### Client profiles — `/api/users/{userId}`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/client-profile` | Authenticated | Name, member since, average rating and number of reviews received as a client, and counts of posted tasks, hires, finished jobs and cancelled tasks. Email and phone are never included |
| GET | `/hires` | Authenticated | Tasks where the client accepted an offer, newest assignment first, with the tasker who took the job |

### Conversations — `/api/conversations`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/` | Authenticated | The caller's conversations, latest activity first, with the offer and its status, the caller's role in the conversation (client or tasker), a preview of the last message and unread counts. The query count stays the same however many conversations there are |
| GET | `/by-offer/{offerId}` | Participant | The conversation that belongs to an offer, so the task page can open it |
| GET | `/unread-count` | Authenticated | Unread messages across all conversations: `{ "count": 3 }` |
| GET | `/{id}/messages` | Participant | Messages in pages from the newest: page 0 holds the latest messages, and every page reads oldest to newest |
| POST | `/{id}/messages` | Participant | Send a message |
| POST | `/{id}/read` | Participant | Mark the other party's messages as read |

### Administration — `/api/admin`

All endpoints require the `ADMIN` role.

| Method | Path | Description |
|---|---|---|
| GET | `/stats` | Overview for the panel: users, suspended users, taskers, taskers not yet verified, open and removed tasks, open reports |
| GET | `/reports` | Reports, newest first, with the reporter, the reported task or user and how many open reports that target has. Filter: `status` (`OPEN`, `RESOLVED`, `DISMISSED`) |
| POST | `/reports/{id}/dismiss` | Dismiss an open report without acting on it |
| GET | `/users` | Users, newest first, with their tasker profile id and verified mark. Filters: `status`, `role`, `search` (email or name) |
| GET | `/tasks` | Tasks for moderation, newest first, with the owner's name and email. Drafts are never shown. Filters: `status`, `search` (title, owner name or email) |
| POST | `/users/{id}/suspend` | Suspend an account and revoke its refresh tokens |
| POST | `/users/{id}/reactivate` | Restore a suspended account |
| POST | `/tasker-profiles/{id}/verify` | Mark a tasker as verified |
| POST | `/tasker-profiles/{id}/unverify` | Remove the verified mark |
| POST | `/tasks/{id}/remove` | Remove a task; body `{ "reason": "..." }` is required. Resolves every open report about the task |

### Reports — `/api`

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/tasks/{taskId}/reports` | Authenticated | Report a task. Body: `reason` (`SPAM`, `FRAUD`, `INAPPROPRIATE`, `NO_SHOW`, `OTHER`) and an optional `comment` of up to 500 characters, required for `OTHER` |
| POST | `/users/{userId}/reports` | Authenticated | Report a user, with the same body |

Nobody can report their own task or themselves, a draft is invisible to everyone but
its owner, and a removed task or a suspended account cannot be reported again. One
person has at most one open report on the same task or user: a partial unique index
(`WHERE status = 'OPEN'`) enforces it in the database, so two identical reports sent
at the same moment still create only one. After a dismissal the same person may
report again. Each user can send at most ten reports in 24 hours, so reports cannot
be used to flood the administrators.

### Notifications — `/api/notifications`

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/` | Authenticated | The caller's notifications, newest first |
| GET | `/unread-count` | Authenticated | Number of unread notifications: `{ "count": 3 }` |
| POST | `/{id}/read` | Authenticated | Mark one notification as read |
| POST | `/read-all` | Authenticated | Mark all of the caller's notifications as read: `{ "marked": 3 }` |

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
| `ASSIGNED` | `IN_PROGRESS`, `PUBLISHED`, `CANCELLED`, `REMOVED` |
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

A completed task that the client never closes is closed automatically after
seven days and credited to the tasker, so a silent client cannot hold back the
tasker's record. See [Deadlines](#deadlines).

### Reopening an assigned task

An assigned task can go back on the market before work starts, from either side:
the tasker backs out by withdrawing their accepted offer, or the client releases
a tasker who did not turn up. Either way the task returns to `PUBLISHED` with a
fresh 30-day window, since a task assigned close to its deadline would otherwise
expire as soon as it reopened.

The offers rejected when the tasker was chosen become active again and their
conversations reopen, so the client can pick someone else straight away. This is
not only a convenience: a tasker can make one offer per task, so without it the
most interested taskers could never bid on the task again. The dropped tasker's
offer ends as `WITHDRAWN` or `REJECTED` and cannot be renewed.

Backing out is recorded on the tasker's profile as `withdrawnJobsCount`, and so is
a missed start deadline (see Deadlines). A client's release is not, because it cannot be verified and would let an unhappy
client penalise a tasker. Once work is `IN_PROGRESS` the task can no longer be
reopened; cancellation remains available to the client.

## Task photos

A client can add up to five photos to a task while it is a draft or open for
offers. The browser prepares each photo before uploading it: it applies the
camera's rotation, scales it down to 1920 pixels on the longest side and saves it
as JPEG, so a 6 MB phone photo travels as a few hundred kilobytes.

The server does not trust that preparation. It checks the real file type from the
first bytes (a renamed script is refused whatever its name), reads the image size
from the header and refuses anything over 40 megapixels before decoding it, so a
tiny file that claims huge dimensions cannot exhaust the memory. It then decodes
and re-encodes the image, which drops every piece of metadata; a phone writes the
GPS position into the photo, and leaving it in would publish the client's address.

Files live on disk, behind a `PhotoStorage` interface so they can move to object
storage such as S3 without touching the rest of the code; the database keeps only
the row with the size and order. The task row is locked while photos change, so
two uploads at the same moment cannot exceed five. Photos are served by a random,
unguessable id that browsers cache for a year.

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
new review. Every tasker is also a client, so the average counts only the reviews
received as a tasker, on other people's tasks; a review received as a client shows
on the client profile and never moves the tasker rating. The recomputation takes an exclusive lock on the profile row before
reading the average, because the operation is read-modify-write: without the lock,
two reviews of the same tasker arriving together both read the average before
either commits, and the second overwrites the first with the value of a single
review. Clients have no profile, so their average is computed on request, from the reviews they
received on their own tasks, when their client profile is opened.

**Known limitation:** there is no deadline for reviewing — a task closed a year
ago can still be reviewed today.

## Messaging

Every offer opens a conversation between the tasker who made it and the client
who owns the task, so the two can agree on details before the client decides.
The offer sits at the top of the chat: the client can accept it right there, the
tasker sees where it stands and jumps to the task, and both see the change live
when the offer is accepted.
Participants are derived from the offer and its task — role does not matter, and
the same person can be the client in one conversation and the tasker in another.
Anyone else receives `403`.

A conversation is archived when its offer stops being live: withdrawn by the
tasker, rejected when another offer is accepted, or when the task is cancelled
or closed. If an assigned task is reopened, the conversations of the offers that
become active again are reopened with them. An archived conversation remains readable but accepts no new
messages — otherwise a rejected tasker could keep writing to the client
indefinitely.

Reading messages does not change them. Marking them read is a separate call,
which also clears the conversation's notification. Only the other party's
messages are marked; one's own messages are never "unread".

New messages notify the recipient at most once per conversation until that
notification is read, so a conversation of fifty messages produces one
notification rather than fifty.

## Real-time updates

The browser opens one STOMP connection to `/ws` after login. The handshake is
public, because browsers cannot add headers to a WebSocket request; the access
token travels in the STOMP `CONNECT` frame instead and is checked like any API
call, including the account status. A connection without a valid token is
refused.

| Queue | Receives |
|---|---|
| `/user/queue/messages` | Every new message in the user's conversations, including their own, so other open tabs stay in sync |
| `/user/queue/reads` | The other party read the conversation; the sender's ticks turn green |
| `/user/queue/notifications` | Every new notification, the moment it is stored |

Clients may subscribe only to their own `/user/queue/...` destinations and cannot
send anything; any other frame closes the connection. Events are published only
after the database transaction commits, so a rolled back change is never pushed.
If a push fails, the change itself still stands; the client catches up by
reloading its counts after reconnecting.

## Administration

There is no way to register as an administrator. An existing account is promoted
at startup when its email matches `APP_ADMIN_EMAIL`: register normally, set the
variable, restart. No password ever passes through configuration, the
promotion is idempotent, and an email with no matching account only logs a
warning.

**Suspension** takes effect on the suspended user's very next request, because
the JWT filter reads the account status from the database on every request.
Refresh tokens are revoked as well, so a reactivated user has to sign in again.
Nothing the user owns is cancelled — suspension is reversible and cancellation is
not — but an offer from a suspended tasker can no longer be accepted, since the
tasker could not sign in to do the work. Administrators cannot suspend
themselves or each other through the API, so a single stolen admin token cannot
lock out every other administrator.

**Removing a task** is possible while it is `PUBLISHED` or `ASSIGNED`. Its offers
are rejected, their conversations archived, and the owner receives the reason in
a `TASK_REMOVED` notification. Work already `IN_PROGRESS` cannot be removed: the
tasker is already on site, and a removed job could never be closed or reviewed.

**Reports** close themselves when the admin acts: removing a task or suspending a user
marks every open report about them as resolved, with the admin's name and the time.
A report that needs no action is dismissed. The reported person never learns who
reported them.

**Known limitation:** administrative actions are logged with the acting admin's
id but not stored in the database, so there is no audit trail to query.

## Asynchronous processing

Notifications are produced off the request thread. A state change publishes a
Spring application event, which is forwarded to RabbitMQ only after the database
transaction commits, so a rolled back publication sends no message. Consumers
then write the notification row and send the email.

| Event | Routing key | Consumer writes |
|---|---|---|
| Task published | `task.published` | One notification per tasker covering the task's category and municipality, unless the task was cancelled or removed before the event arrived |
| Task expired | `task.expired` | One notification to the task owner |

Offer, work-execution and review notifications (`NEW_OFFER`, `OFFER_ACCEPTED`, `TASK_STARTED`, `TASK_COMPLETED`,
`TASK_CLOSED`, `REVIEW_RECEIVED`, `NEW_MESSAGE`, `TASK_REMOVED`,
`TASKER_WITHDREW`, `ASSIGNMENT_RELEASED`, `OFFER_REACTIVATED`,
`ASSIGNMENT_EXPIRED`, `TASK_AUTO_CLOSED`) are written synchronously instead: they have a
single recipient and send no email, and writing them in the same transaction as
the state change means the notification cannot be missing while the change is
visible.

Both queues are durable and bound to the `tasknest.events` topic exchange.
Email delivery is best-effort: a failing address is logged and skipped rather
than failing the batch, because an exception would make the broker redeliver the
message and duplicate the notifications.

**Retries and dead letters.** A consumer that throws gets the message again up to
three times, waiting one second and then two between attempts, which rides out a
short database or network hiccup. If the third attempt also fails, the message is
rejected without requeueing and RabbitMQ moves it through the `tasknest.events.dlx`
exchange into a dead-letter queue (`tasknest.task-published.dlq`,
`tasknest.task-expired.dlq`). Without this, a message that can never succeed would
be redelivered forever and block the queue. A message that cannot even be parsed
skips the retries and goes to the dead-letter queue straight away. Parked messages
can be inspected in the RabbitMQ management UI and moved back once the cause is
fixed.

### Scheduled expiry

A scheduler moves published tasks past their deadline to `EXPIRED` and notifies
their owners. It runs every 60 seconds by default and only triggers the service
method, so the rule itself is tested with a fixed clock instead of by waiting.
The expiry filters in the listings and in offer submission remain in place: a
window between the deadline and the next pass exists no matter how often the
scheduler runs.

### Deadlines

Every state a task can wait in eventually ends on its own, so no task depends on
someone remembering to click:

| Waiting in | Nobody acts for | What happens |
|---|---|---|
| `PUBLISHED` | 30 days | expires |
| `ASSIGNED` | 14 days without work starting | reopens like a release by the client, and counts as a withdrawal for the tasker |
| `COMPLETED` | 7 days without the client closing | closes and is credited to the tasker |

A reopened task gets a new 30-day window, so if nobody takes it up it still ends
by expiring. An automatic reopen is recorded in the tasker's `withdrawnJobsCount`,
and the tasker is told so in the notification: starting the work is the tasker's
own step, and fourteen days without it means the job was in practice abandoned.

The deadline scheduler processes each task in its own transaction. A task that
changes between being listed and being processed, for example because work
started in that moment, is skipped or rejected by `@Version` on its own, and the
remaining tasks are still handled. The periods are configurable.

## Testing

```bash
./mvnw verify
```

The suite contains **419 tests** and requires no manual setup — Testcontainers
starts PostgreSQL and RabbitMQ automatically.

| Type | Count | Scope |
|---|---|---|
| Unit | 146 | Service business rules, the task state machine, the login attempt limiter and the geocoder against a simulated OpenRouteService |
| Integration | 273 | Authentication, password reset, login throttling, account settings, task editing and search, task addresses and who may see them, reports, offer price changes, task photos, dead-letter queues, authorisation, the task lifecycle, concurrency, JPQL queries, reviews, client profiles, messaging, administration, CORS, the notification pipeline, real-time pushes over WebSocket, demo data |

Every integration test starts from an empty database: one `TRUNCATE ... CASCADE` after each test
clears all application tables at once. Clearing them table by table left a window in which the
RabbitMQ listener, which runs on its own thread, could still write a notification for a user that was
about to be deleted. The listener also ignores events for tasks that are no longer published, so an event
left over from an earlier test cannot reach the next one. The suite passes in random class order
(`./mvnw verify -Dsurefire.runOrder=random`).

The frontend has its own suite of **272 tests** (Vitest), covering the session
service, token renewal and the interceptor, the route guards, the login form, the
header, the task list and its search, task details, posting and editing a task, account settings, reports and the admin reports tab, the client's own tasks, offers
and hiring, cancelling, reopening and closing a task, reviews, becoming a tasker and editing the tasker profile, sending and withdrawing offers,
starting and finishing a job, the tasker dashboard, the user profile with its tasker and client sides, the notification bell and page, the messages page and chat helpers, live updates over WebSocket, the admin panel, translations and plural rules, the theme switch, the confirmation dialog, the dropdown and its search, the region picker, the progress
timeline, date helpers and the category icons. The server is simulated with Angular's
`HttpTestingController`.

```bash
cd frontend
npm test -- --watch=false
```

GitHub Actions runs both suites in parallel on every push and pull request: the
backend with `./mvnw verify`, the frontend with a clean `npm ci`, a production
build and the tests.

## Project structure

```
src/main/java/ba/tfb/tasknest/
├── bootstrap/      Administrator promotion and demo data at startup
├── config/         Security, WebSocket and OpenAPI configuration
├── controller/     REST controllers
├── domain/         Task state machine
├── dto/            Request and response records
├── entity/         JPA entities and enums
├── exception/      Application exceptions and the global handler
├── messaging/      RabbitMQ events, publisher, listeners, mailers
├── realtime/       WebSocket authentication and pushes after commit
├── repository/     Spring Data repositories and query projections
├── scheduler/      Scheduled expiry and deadlines
├── security/       JWT filter, principal, authentication entry points
└── service/        Business logic

src/main/resources/
├── db/changelog/   Liquibase migrations
└── application*.yml

frontend/           Angular application, see Frontend
```

## Roadmap

- [x] Real-time message delivery over WebSocket
- [x] Password reset by email and login throttling
- [ ] Email verification
- [x] Angular frontend: client, tasker, messaging and administration
- [x] Dockerfiles and one-command start with Docker Compose

## License

Developed as part of a software academy programme.
