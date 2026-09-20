# TaskNest

![build](https://github.com/jahjahromadzic/TaskNest/actions/workflows/build.yml/badge.svg)

Marketplace za lokalne usluge. Klijent objavi oglas za posao, taskeri koji pokrivaju
tu kategoriju i opštinu pošalju ponude, klijent prihvati jednu.

Backend je Spring Boot REST API. Frontend (Angular) je u planu.

---

## Tehnologije

| | |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| Baza | PostgreSQL 17 |
| Migracije | Liquibase (32 changeseta, 16 tabela) |
| ORM | Hibernate 7, `ddl-auto: validate` |
| Sigurnost | Spring Security, JWT (jjwt 0.12.6) |
| Dokumentacija | springdoc OpenAPI |
| Testovi | JUnit 5, Mockito, AssertJ, Testcontainers |

---

## Pokretanje

Potrebni su **Java 21** i **Docker**.

```bash
docker compose up -d
```

Diže Postgres (5432), RabbitMQ (5672, konzola 15672) i Mailpit (1025, web 8025).

```bash
./mvnw spring-boot:run
```

Aplikacija sluša na `http://localhost:8080`. Liquibase sam kreira šemu i ubaci
početne podatke (role, kategorije, opštine) pri prvom pokretanju.

| | |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Mailpit (pregled mailova) | http://localhost:8025 |

---

## Konfiguracija

Podrazumijevani profil je `dev` i radi bez ijedne varijable okruženja.

| Varijabla | Default (dev) | Napomena |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tasknest` | |
| `DB_USERNAME` / `DB_PASSWORD` | `tasknest` / `tasknest` | |
| `JWT_SECRET` | dev-only vrijednost | **u `prod` profilu obavezna** |
| `JWT_EXPIRATION_MINUTES` | 60 | vijek access tokena |
| `REFRESH_TOKEN_EXPIRATION_DAYS` | 30 | vijek refresh tokena |

Profil `prod` namjerno **nema** podrazumijevanu vrijednost za `JWT_SECRET` — ako
varijabla nije postavljena, aplikacija pada pri startu. Tiše zlo bilo bi startovati
s ključem koji je javno u repozitoriju.

```bash
JWT_SECRET=... ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## API

Autentikacija je Bearer JWT. Access token traje 60 minuta, refresh 30 dana.

### Auth — `/api/auth`

| Metoda | Putanja | Pristup |
|---|---|---|
| POST | `/register` | javno |
| POST | `/login` | javno |
| POST | `/refresh` | javno (refresh token je kredencijal) |
| POST | `/logout` | javno |
| POST | `/activate-tasker` | prijavljen |

### Oglasi — `/api/tasks`

| Metoda | Putanja | Pristup |
|---|---|---|
| GET | `/` | javno — lista, filteri `categoryId`, `municipalityId` |
| GET | `/{id}` | javno (nacrt vidi samo vlasnik) |
| POST | `/` | CLIENT |
| POST | `/{id}/publish` | CLIENT |
| POST | `/{id}/cancel` | CLIENT |
| GET | `/mine` | CLIENT |
| GET | `/matching` | TASKER — oglasi u njegovim kategorijama i opštinama |
| GET | `/assigned` | TASKER — poslovi dobijeni prihvaćenom ponudom |

### Ponude — `/api`

| Metoda | Putanja | Pristup |
|---|---|---|
| POST | `/tasks/{taskId}/offers` | TASKER |
| GET | `/tasks/{taskId}/offers` | prijavljen, vlasnik oglasa |
| POST | `/offers/{offerId}/accept` | CLIENT, vlasnik oglasa |
| POST | `/offers/{offerId}/withdraw` | TASKER, vlasnik ponude |
| GET | `/offers/mine` | TASKER |

### Profil taskera — `/api/tasker-profiles`

| Metoda | Putanja | Pristup |
|---|---|---|
| GET | `/me` | TASKER |
| PUT | `/me` | TASKER |
| PUT | `/me/categories` | TASKER — zamjenjuje pokrivenost |
| PUT | `/me/municipalities` | TASKER — zamjenjuje pokrivenost |
| GET | `/{id}` | prijavljen |

### Šifarnici — `/api`

| Metoda | Putanja | Pristup |
|---|---|---|
| GET | `/categories` | javno |
| GET | `/municipalities` | javno |

Sve greške vraćaju `application/problem+json` ([RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807)):

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Offers can only be submitted on published tasks",
  "instance": "/api/tasks/ac140a03-.../offers"
}
```

---

## Arhitektura

```
controller/   REST, @PreAuthorize provjera role
service/      poslovna pravila, provjera vlasništva, transakcije
domain/       TaskStateMachine — čista logika, bez Springa
repository/   Spring Data JPA + JPQL projekcije
entity/       JPA entiteti, mapirani na Liquibase šemu
dto/          request/response recordi
exception/    tipizovani izuzeci + GlobalExceptionHandler
security/     JWT filter, UserPrincipal, ProblemDetail handleri
```

### Odluke koje vrijedi znati

**Šema je izvor istine, ne entiteti.** Liquibase definiše strukturu, `ddl-auto: validate`
znači da aplikacija ne startuje ako se entitet i tabela raziđu. Indeksi su birani po
stvarnim pristupnim putevima, ne paušalno, a `CHECK` ograničenja (`rating BETWEEN 1 AND 5`,
`price >= 0`) čuvaju bazu i kad kod pogriješi.

**Životni ciklus oglasa je state machine.** `TaskStateMachine` je statička, čista
klasa — 9 stanja i dozvoljeni prelazi, bez Springa, testirana u izolaciji. Servisi
nikad ne postavljaju status bez provjere prelaza.

**Prihvatanje ponude je zaštićeno od konkurentnosti.** `@Version` na `Task` i `Offer`
plus namjeran redoslijed upisa: task se upisuje i flushuje **prije** ijedne ponude.
Bez toga su dvije paralelne transakcije zaključavale redove u `offers` ukršteno i
Postgres je javljao deadlock umjesto konflikta verzija. `OfferConcurrencyTest` to
dokazuje — tvrdi tip izuzetka i eksplicitno odbija deadlock.

**Refresh tokeni se rotiraju.** Svaka upotreba troši token i vraća novi. Ako već
iskorišten token stigne ponovo, to je moguća krađa i **svi** tokeni tog korisnika se
opozivaju (preporuka OAuth 2.0 Security BCP-a). Opoziv ide u zasebnoj transakciji,
jer bi se inače rollbackovao zajedno s izuzetkom koji odbija zahtjev.

**Liste ne učitavaju entitete.** Matching i listanje koriste JPQL projekcije
(`TaskSummaryResponse`, `TaskNotificationTarget`) — jedan upit umjesto N+1 kroz lazy
veze. Sortiranje je ograničeno bijelom listom polja, veličina strane na 50.

**Autorizacija je na dva sloja.** Rola se provjerava na kontroleru (`@PreAuthorize`),
vlasništvo u servisu — jer će servise zvati i RabbitMQ listener i scheduler, bez
`SecurityContext`-a. Status naloga se provjerava i pri prijavi i na **svakom** zahtjevu
kroz JWT filter, da suspenzija djeluje odmah, a ne po isteku tokena.

---

## Testovi

```bash
./mvnw verify
```

**132 testa**, bez ručne pripreme — Testcontainers sam diže Postgres.

| Vrsta | Broj | Šta pokriva |
|---|---|---|
| Unit (Mockito) | 67 | poslovna pravila servisa, state machine |
| Integracioni (Testcontainers) | 65 | auth tok, autorizacija, konkurentnost, JPQL upiti |

Nekoliko testova postoji zbog konkretnih bugova i namjerno bi pao ako se
regresija vrati: deadlock pri paralelnom prihvatanju ponuda, ponovna upotreba
refresh tokena, 401 vs 403 za neprijavljenog korisnika, istekao oglas u javnoj
listi, deaktivirana kategorija u matchingu.

CI vrti isti `verify` iz svježeg klona na svaki push.

---

## Roadmap

Urađeno: šema i entiteti, kompletan auth (JWT, refresh s rotacijom, role, status
naloga), oglasi i ponude sa state machine i optimistic lockingom, profil taskera s
pokrivenošću, listanje i matching, OpenAPI, CI.

Predstoji:

- [ ] RabbitMQ notifikacije (objava oglasa → taskeri koji ga pokrivaju)
- [ ] Scheduler koji istekle oglase prebacuje u `EXPIRED`
- [ ] Chat: `Conversation`, `Message`, WebSocket
- [ ] Ocjene (`Review`) i prosječna ocjena taskera
- [ ] Admin: uklanjanje neprikladnih oglasa, verifikacija taskera
- [ ] Verifikacija emaila, reset lozinke, rate limiting
- [ ] Angular frontend
- [ ] Dockerfile za aplikaciju

Poznata ograničenja u trenutnom stanju:

- `TaskResponse` i `OfferResponse` se grade iz entiteta i diraju lazy veze — za
  pojedinačne endpointe je to prihvatljivo, liste već koriste projekcije
- `Page` se serijalizuje u Springovom obliku, koji Spring Data označava kao
  nestabilan; oblik treba fiksirati prije nego se Angular veže na njega
- `REMOVED` oglas je i dalje dostupan direktnim linkom — čeka admin tok koji
  uopšte postavlja taj status
- `averageRating` i `completedJobsCount` na profilu taskera su denormalizovani bez
  definisanog mjesta ažuriranja — rješava se uz `Review`

---

## Licenca

Projekat je rađen u okviru softverske akademije.
