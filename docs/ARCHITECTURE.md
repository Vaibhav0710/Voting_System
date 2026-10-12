# Architecture

_Last updated: 2026-10-10. Describes the code as it is; planned items are marked._

## 1. Overview
```
                Clients (HTTP + JWT)
                       │
               ┌───────▼────────┐   lb:// routes, JWT check (calls user-service /auth/validate),
               │  api-gateway   │   Redis rate limit per user/IP
               │     :8080      │
               └───────┬────────┘
     ┌────────────┬────┴───────┬──────────────┐                ┌───────────────┐
┌────▼────┐ ┌─────▼─────┐ ┌────▼─────┐ ┌──────▼──────┐          │ eureka-server │
│  user   │ │ candidate │ │  voting  │ │   result    │◄─register─┤    :8761      │
│  :8081  │ │   :8082   │ │  :8083   │ │   :8084     │          └───────────────┘
└────┬────┘ └─────┬─────┘ └──┬────┬──┘ └──┬───────┬──┘
     │PG          │PG   Feign│    │PG     │Redis  │Feign (names, audit)
userservice_db candidate-  │  voting_service_db     │
               service_db  │  + Redis               │
                     ▲      │                       │
                     └──────┘ validate candidate    │
      ═══════════════ Kafka ═════════════════════════╪═══
      candidate.created / status-changed / deleted ──► voting (status cache)
      vote.cast ─────────────────────────────────────► result (tallies, SSE)
      vote.verified ─────────────────────────────────► (no consumer yet)
```

## 2. Components
| Service | Owns | Talks to |
|---|---|---|
| **eureka-server** | Service registry (`@EnableEurekaServer`, does not self-register) | — |
| **api-gateway** | Single entry point; routes `/api/v1/auth/**`, `/users/**`, `/candidates/**`, `/votes/**`, `/results/**`. `AuthenticationFilter` (all routes) strips client-sent identity headers, lets exact open endpoints through, verifies other tokens via user-service, applies coarse role rules and adds verified `loggedInUser`/`role` headers; `RequestRateLimiter` (Redis token bucket, key `user:<id>` or `ip:<addr>`); per-route circuit breakers with 503 JSON fallbacks; CORS from `CORS_ALLOWED_ORIGINS` | user-service, Redis |
| **user-service** | Users, BCrypt passwords, JWT issuing and validation, admin bootstrap | Postgres |
| **candidate-service** | Candidates (status, soft delete), validation endpoint (never cached), Redis read caches, after-commit `candidate.*` events, demo data seeder | Postgres, Redis, Kafka |
| **voting-service** | Votes: idempotency, double-vote defence, hashing, chain, verification, counts, `vote.*` events, candidate status cache | Postgres, Redis, Kafka, candidate-service (Feign) |
| **result-service** | Live tallies in Redis, results/summary, SSE live feed, admin audit trail | Redis, Kafka, candidate-service + voting-service (Feign) |

Every service except eureka validates the JWT itself (shared HS256 secret), so it does not depend on the gateway for security.

## 3. Main data flows
**Cast a vote** (`POST /api/v1/votes`, `{candidateId, electionId, idempotencyKey}`)
1. Gateway: token valid (user-service `/auth/validate`) and rate limit → forwards to voting-service.
2. `VoteController` needs `ROLE_VOTER`; the user id comes from the JWT `userId` claim.
3. `VoteService.castVote` (one DB transaction):
   1. **Idempotent replay:** Redis `vot:idempotency:{userId}:{key}` → vote id; fallback DB lookup by (key, user). Hit → original receipt, HTTP 200.
   2. **Fast duplicate check:** Redis `vot:voted:{userId}:{electionId}` → 409.
   3. **Candidate check:** `CandidateValidator` → Feign `GET /candidates/{id}/validate`, wrapped as Retry(3) → CircuitBreaker → Bulkhead(50). Not valid → 400. Service down → fallback to the Redis status cache; nothing cached → 503.
   4. **Chain:** if enabled, lock the election's `election_chain_heads` row (`SELECT … FOR UPDATE`) and use its `last_hash` as `prev_hash`.
   5. Hash = `SHA-256(userId + candidateId + electionId + epochSecondsUTC + (prevHash ?: ""))`; advance the chain head.
   6. `saveAndFlush`; a unique violation → 409.
   7. Publish an in-process `VoteRecordedEvent`.
4. **After commit** (`VoteCastSideEffects`): set the Redis voted flag and the idempotency key (TTL 24 h), publish `vote.cast` (key = electionId).
5. Response 201 `{voteId, voteHash, timestamp, message, replay:false}`.

**Results:** result-service `VoteEventConsumer` → Lua script on Redis (`SADD res:seen:{e} voteId` and, only if new, `HINCRBY res:votes:{e}` + `INCR res:total:{e}`) → push SSE `result-update` to subscribers. Reads enrich counts with names from candidate-service (`/candidates/election/{id}`, retry + circuit breaker, empty names on failure).

**Audit:** `GET /results/election/{id}/audit` (ADMIN) → `AuditTrailService` pages voting-service `/votes/election/{id}` (500 per page), forwarding the caller's JWT; a voting-service failure → 503 (never an empty audit).

**Candidate status changes:** candidate-service publishes `candidate.*` → voting `CandidateEventConsumer` updates the `vot:candidate:{id}` hash used by the fallback.

## 4. Contracts
**Kafka topics** (1 partition, replication 1, JSON):
| Topic | Producer | Key | Payload | Consumer |
|---|---|---|---|---|
| `vote.cast` | voting | electionId | `{voteId, userId, candidateId, electionId, voteHash, timestamp}` | result |
| `vote.verified` | voting | none | `{voteId, hashValid, status, storedHash, computedHash, message}` | none yet |
| `candidate.created` / `candidate.status-changed` / `candidate.deleted` | candidate (after commit) | candidateId | `{id, name, party, electionId, status, createdAt, updatedAt}` | voting |

Consumers ignore Java type headers and use `ErrorHandlingDeserializer` (bad records are logged and skipped).
Producers send plain JSON without Java type headers. Both producers send only after the DB commit and log (not throw) broker failures, with `max.block.ms` 5 s.

**Redis keys:** see `DATABASE.md` §5.

## 5. Tech stack
Java 17 (runs on JDK 21 locally) · Spring Boot 3.3.6 · Spring Cloud 2023.0.3 (Gateway, Netflix Eureka, OpenFeign,
LoadBalancer) · Spring Security 6 + JJWT 0.12.6 · Spring Data JPA / Hibernate 6 · PostgreSQL (H2 in tests) ·
Spring Kafka (Confluent cp-kafka 7.4.0 + Zookeeper in compose) · Redis (Lettuce) · Resilience4j 2.2.0 ·
springdoc-openapi 2.5.0 · Micrometer (Prometheus registry, Brave tracing → Zipkin) · logstash-logback-encoder 7.4 · Lombok · Maven.

## 6. Key decisions
| # | Decision | Why | Rejected |
|---|---|---|---|
| 1 | Multi-repo, one GitHub repo per service (submodules) | Independent CI/CD and versioning | Monorepo |
| 2 | Feign for validation, Kafka for events | A vote needs a synchronous candidate answer; results can lag | All-sync or all-async |
| 3 | Double-vote defence = idempotency → Redis flag → DB unique | Fast path plus an absolute guarantee | Redis only (lossy) |
| 4 | Chaining **on by default**, serialised per election by a row lock on `election_chain_heads` | Keeps the existing behaviour and ordering proof; elections never block each other; can be turned off (`VOTING_CHAIN_ENABLED=false`) | Global lock; unlocked "read last vote" (it forked under load) |
| 5 | Redis/Kafka side effects after commit (`@TransactionalEventListener`) | Never announce a rolled-back vote | Sending inside the transaction |
| 6 | Result service has no DB; idempotent Lua counting | Voting DB is the source of truth; redeliveries must not double-count | Separate results DB |
| 7 | Every service validates the JWT itself | Defence in depth; services are safe even if reached directly | Trusting gateway headers |
| 8 | Fallback to cached candidate status, else 503 | Availability without accepting votes for unknown candidates | Fail open |
| 9 | Admin only via env-configured bootstrap | Public registration must not grant privileges | Role field in registration |

## 7. Known limits
- No election entity: no voting window and no eligibility list.
- `vote.cast` is published after commit but not transactionally (no outbox). A crash between commit and send loses the event for results (the voting DB still has the vote).
- Gateway calls user-service for every authenticated request (extra hop). Local JWT verification at the gateway would remove it.
- A gateway timeout (5 s) on `POST /votes` returns 503 even if the vote was stored; clients must retry with the same idempotency key.
- result-service Redis keys are not cluster hash-tagged (fine for single-node Redis).
- Hibernate `ddl-auto=update` instead of versioned migrations.
