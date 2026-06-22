# TezYol / TEZGO — Ride-Hailing Platform Backend

Production backend API for **TezYol (TEZGO)**, a ride-hailing (taxi) platform serving real drivers, passengers, dispatch operators, and admins in Uzbekistan. A single Spring Boot 3 service powers the full ride lifecycle — OTP/JWT auth, driver onboarding, real-time matching and dispatch, live taximeter and surge/night-fare pricing, in-app chat, push notifications, and Payme/Click payments — deployed as a systemd-managed JVM service on a VPS behind Nginx.

> Domain comments and most commit messages are in Uzbek (the product language). This README describes the architecture and APIs in English.

---

## Architecture

Classic layered Spring Boot monolith with clean package separation:

```
controller  ->  service  ->  repository (Spring Data JPA)  ->  PostgreSQL
                   |
                   +-- Redis (Lettuce): OTP cache, driver-location GEO cache, rate limiting
                   +-- STOMP / WebSocket: real-time dispatch + live location
                   +-- Firebase Admin SDK: FCM data-only push
                   +-- Flyway: versioned schema migrations
```

- **12 REST controllers** (Auth, Admin, Operator, Driver, Passenger, Payment, Chat, SupportChat, ChannelMessage, Photo, Place, PromoCode) exposing **155 endpoints**, plus STOMP `@MessageMapping` real-time channels.
- **40 services**, **16 JPA entities / 16 repositories**, **53 DTOs** isolating the API contract from the persistence model.
- **Concurrency-safe dispatch:** `TripService` owns the trip state machine and uses JPA **optimistic locking (`@Version`)** for race-free driver assignment — avoiding the double-assign bug common in dispatch systems.
- **Matching engine:** `MatchingService` scores candidate drivers by weighted proximity + activity, with a decline-cooldown and operator-order fast-path.
- **Pricing:** surge engine + timezone-aware automatic night-fare windows + a taximeter lifecycle with pause/resume waiting-fee and idempotent finish.
- **Cross-cutting:** `JwtFilter` + stateless `SecurityConfig`, `CorrelationIdFilter` + `SecurityAuditFilter`, Sentry, Prometheus/Micrometer, Actuator.

## Tech Stack

**Java 17** · **Spring Boot 3.4** (Web, Security, Data JPA, WebSocket, Validation, Data Redis) · **PostgreSQL** · **Flyway** · **Redis** (Lettuce) · **STOMP WebSocket** · **Apache Kafka** (optional) · **Firebase Admin SDK** (FCM) · **Bucket4j** (rate limiting) · **JJWT** · **SpringDoc OpenAPI / Swagger UI** · **Sentry** · **Micrometer + Prometheus** · **JUnit 5 + Testcontainers** · **Lombok** · **Maven** (multi-stage Docker) · **Payme & Click** payments · **Eskiz.uz** SMS.

## At a Glance

| | |
|---|---|
| Production code | ~17,000 LOC across 172 Java files |
| REST endpoints | 155 across 12 controllers |
| Database | 40 Flyway migrations (versioned, live schema) |
| Tests | **265 tests across 62 classes** — incl. an automated penetration test (brute-force, IDOR, JWT-tamper, SQL-injection, rate-limit) and a Testcontainers schema-migration test that boots real PostgreSQL |
| Auth | Stateless JWT (access + refresh), BCrypt(12), role-based (ADMIN / OPERATOR / DRIVER / PASSENGER) |
| Hardening | Full OWASP response headers, Redis + Bucket4j rate limiting, fail-closed token blacklist |
| Ops | Multi-stage non-root Docker image with HEALTHCHECK, graceful shutdown, HikariCP tuning, Prometheus metrics |

## Security

- Stateless JWT with access + refresh tokens; **BCrypt strength 12** password hashing.
- Granular role-based authorization with per-endpoint matchers.
- Full OWASP response-header set (HSTS preload, frame-deny, content-type-options, permissions-policy, referrer-policy).
- Multi-layer rate limiting (Redis-backed OTP + IP, Bucket4j) and a configurable **fail-closed** token blacklist.
- **All credentials externalized** — every value in `application.properties` is an `${ENV}` placeholder; `.env` is gitignored and never committed; only `.env.example` (safe placeholders) is tracked.
- UZ personal-data-law (Buyruq No. 3478) consent-record logging.

## Getting Started

```bash
# 1. Configure environment
cp .env.example .env        # fill DB, JWT, Redis, payment, SMS values

# 2. Run (JDK 17 + Maven wrapper)
./mvnw spring-boot:run

# 3. Tests (Docker required for the Testcontainers schema test)
./mvnw verify
```

API docs are served via Swagger UI (`springdoc-openapi`) when the app is running.

## License

Proprietary — production codebase, shared as an engineering work sample.
