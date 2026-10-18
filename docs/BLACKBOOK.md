# BLACKBOOK — Blockchain-Inspired Online Voting System

# Part 1 — Project report

**Title:** Blockchain-Inspired Online Voting System: a tamper-evident, event-driven microservices platform
**Author:** Vaibhav Jain · **Started:** April 2026 · **Report date:** 2026-10-10
**Repositories:** `Vaibhav0710/Voting_System` (orchestrator) plus one repo per service (see `ARCHITECTURE.md`)

## Abstract
An API-only election platform of six Spring Boot microservices (gateway, registry, users, candidates, voting, results)
communicating through OpenFeign and Apache Kafka. Each vote is hashed with SHA-256 and linked to the previous vote of
its election, forming a per-election hash chain that makes tampering detectable. Double voting is prevented in layers:
an idempotency key, a Redis flag and a database unique constraint, verified under 50 concurrent requests. Results are
aggregated in real time from Kafka events into Redis and streamed to clients with Server-Sent Events.

## Problem statement
Online voting needs at-most-once voting under concurrency and retries, evidence that stored votes were not altered, and
fast results under load. A monolithic CRUD approach struggles with all three.

## Objectives
1. Strict one-vote-per-user-per-election, including under races and client retries.
2. Tamper evidence (per-vote hash, per-election chain, verification APIs).
3. Independently deployable services with their own data stores.
4. Real-time results decoupled from the write path.
5. Graceful degradation when dependencies fail.

## Scope
In scope: registration/login, candidate management, voting, verification, results, audit trail, gateway, discovery.
Out of scope for now: identity proofing, eligibility lists, election scheduling, secret-ballot cryptography, UI.

## Methodology
Iterative milestones (`PROJECT_PLAN.md`): foundation services, then infrastructure, the voting core, Kafka, and results.
Built one service and one step at a time, with test-first development from the 2026-10-10 review onward.

## System design
See `ARCHITECTURE.md` (components, flows, contracts, decisions) and `DATABASE.md` (schemas, Redis keys).

## Modules
| Module | Summary |
|---|---|
| eureka-server | Service registry |
| api-gateway | Routing, token check via user-service, Redis rate limiting |
| user-service | Users, BCrypt, JWT issuing, admin bootstrap, user lookups |
| candidate-service | Candidate lifecycle, validation endpoint, Kafka events, seed data |
| voting-service | Vote casting, idempotency, hash chain, verification, counts, events, candidate status cache, Resilience4j |
| result-service | Idempotent Redis tallies, results/summary, SSE, admin audit trail, Resilience4j |

## Implementation highlights
- **Row-locked hash chain:** `election_chain_heads` row per election, `SELECT … FOR UPDATE`, seeded from existing votes so old chains continue.
- **After-commit side effects:** Redis flags and Kafka `vote.cast` only after the DB transaction commits.
- **Idempotent consumer:** a Lua script adds the vote id to a set and increments the counters only if it is new.
- **Resilience:** Retry(3) → CircuitBreaker → Bulkhead around candidate validation, with a fallback to a Kafka-fed status cache.
- **Defence-in-depth auth:** every service verifies the JWT itself; JSON 401/403; no leaked 500 messages.

## Results
| Evidence | Value |
|---|---|
| Concurrency: 50 parallel votes, same voter | exactly 1 stored, 49 × 409 (`VoteConcurrencyIntegrationTest`) |
| Concurrency: 50 parallel voters, one election | chain valid, 50 votes, 0 broken links |
| Retry + fallback wiring | 3 attempts on 503, then cached-status decision (integration test) |
| Test suites (2026-10-11) | user 26/26, candidate 58/58, voting 54/54, result 28/28 |
| Line coverage (2026-10-14, JaCoCo) | voting 89.6 % (gate 80 %), gateway 95.6 %, user 91.6 %, result 89.0 %, candidate 82.8 % |
| Load test (p95 at 10k concurrent votes) | Gatling simulations written; not yet run (needs Docker) |

## Challenges
- JWT contract drift between services (subject vs `userId` claim) silently broke authentication downstream.
- An HTTP 200 + `valid:false` contract read with a `void` Feign method accepted invalid candidates.
- Chaining without a lock forks the chain under concurrency.
- Resilience4j aspect order: a fallback on the CircuitBreaker swallowed errors before Retry could retry.
- Spring Security exceptions caught by a generic `@ExceptionHandler(Exception)` became 500s.

## Future scope
Milestones 6–9 are done in code (M9 still has to be run under Docker and on a cluster). M10 tooling is written (JaCoCo gate, Postgres
Testcontainers IT, e2e and chaos scripts, Gatling) but the stack-level checks still have to be run on Docker. Also left: an election entity with voting windows, a transactional
outbox for `vote.cast`, a results recount from the voting DB, Flyway migrations, external anchoring of chain heads,
unlinkable ballots, and a web UI.

## References
Spring Boot 3.3 and Spring Cloud 2023.0 reference docs · Spring Security 6 · Resilience4j 2 docs · Spring for Apache
Kafka · Redis `EVAL` / Lua scripting · PostgreSQL `SELECT … FOR UPDATE` and `INSERT … ON CONFLICT`.

---

# Part 2 — Log (newest first)

### 2026-10-18 — One `voting_system` database, one schema per service (no commits)
- **Decision (Vaibhav):** replace the three databases (`userservice_db`, `candidateservice_db`, `voting_service_db`)
  with one `voting_system` database and schemas `user_service`, `candidate_service`, `voting_service`, the same
  everywhere (local, Docker, K8s). `user` was avoided as a schema name because it is reserved in PostgreSQL.
- **Code:** each service's JDBC URL is now `${VOTING_DB_URL}/voting_system?currentSchema=<schema>`; nothing else in
  `application.yml` changed. Only the URL changed because tests set their own H2/Testcontainers URL, so they are
  unaffected. The 2026-10-15 attempt (a `default_schema` property) broke the H2 tests.
- **Infra:** `infra/postgres/init.sql` and the k8s `postgres-init` ConfigMap create the database and the three
  schemas. `scripts/create-local-schemas.sh` became `scripts/create-local-tables.sh`: it no longer creates schemas or
  overrides the URL, and only starts each service once to build tables and seed data. `LOCAL_DB_NAME` removed from `.env`.
- **Local access:** the database is owned by `postgres`, a separate local admin login has all privileges, and the services
  log in as `voting_app` (its own schemas only) instead of the Job project's `jobagent_app`. The old schemas in
  `jobagent` are no longer used and were left in place.
- **Result (verified):** all three test suites pass. `create-local-tables.sh` built `user_service.users`,
  `candidate_service.candidates`, `voting_service.votes` and `voting_service.election_chain_heads` (owner
  `voting_app`). `uq_user_election` and `uq_idempotency_key` are present, with 1 admin and 10 candidates seeded and
  nothing in `public`. `voting_app` is refused by `jobagent`, `expense_manager`, the `public` schema and `CREATE SCHEMA`.
- **Incident:** a PowerShell 5.1 masking line (`-replace` with a script block, unsupported there) printed the new
  `voting_app` password to the terminal. It was rotated at once, and the printed value no longer works.
  Lesson: never echo a line that holds a secret, even through a filter; print only a length or a true/false check.

### 2026-10-15 — Local database on the Job project's PostgreSQL (no commits)
- **Decision (Vaibhav):** use the existing local PostgreSQL 18.6 with the Job project's login `jobagent_app`.
  That login cannot create databases, so the services get **schemas** `userservice`, `candidateservice`,
  `votingservice` in the `jobagent` database. Trade-off recorded in SECURITY.md (one login for both projects).
- **`scripts/create-local-schemas.sh`:** creates the schemas, then starts each service once with two command-line
  overrides (shared database URL, Hikari schema) so Hibernate creates the tables and the seeders run. No service file changed.
- **Result:** `users`, `candidates`, `votes` + `election_chain_heads` in their schemas, constraints
  `uq_user_election` and `uq_idempotency_key` present, 1 admin and 10 candidates seeded, nothing in `public`,
  the Job schema untouched (17 tables). Verified by dropping the three schemas and rebuilding them with the script.
- **Two mistakes caught:** (1) the first version stopped each service on the "Started" log line, before
  `AdminSeeder` (an `ApplicationReadyEvent` listener) ran, so no admin was created; it now waits for the readiness
  probe. (2) Making the schema configurable in `application.yml` with a default of `public` broke 33 tests in
  user- and candidate-service (their H2 names the schema `PUBLIC`); reverted in favour of the command-line overrides.
- **Dropped:** the earlier admin-only setup script (dedicated `voting_app` role and databases), since the
  `postgres` password was not available.


### 2026-10-14 — Batch 8: coverage, Testcontainers, e2e, load and chaos tooling (no commits)
- **JaCoCo 0.8.12 in all six poms:** `mvn verify` writes `target/site/jacoco`. voting-service fails the build below 80 %
  line coverage (a run with the limit at 95 % failed, so the gate is real).
- **voting-service 79.2 % → 89.6 %:** `VoteRedisStore` and `CandidateStatusCache` were mocked in every test, so their
  failure handling had never run. New tests cover key format, TTLs and "Redis down reads as a cache miss". 66 → 85 tests.
- **`VoteRepositoryPostgresIT`** (Testcontainers, PostgreSQL 16.4, failsafe): unique constraint, chain-head lock making a
  second transaction wait, 20 concurrent votes by one voter and by different voters. Skipped without Docker; the test
  bodies were run once against H2 to check the test logic. `ConcurrentTasks` extracted from the H2 concurrency test.
- **`scripts/e2e.sh`, `scripts/chaos.sh`, `scripts/lib.sh`** (bash + curl + jq) and **`load-tests/`** (Gatling 3.11:
  seed voters, cast votes with p95 < 500 ms, double-vote burst). Each load run votes in a fresh election so seeded voters
  are reusable. Details and commands in `TESTING.md` §10.
- **Bugs caught while checking offline:** the default `voters.csv` path depended on the directory Maven started in
  (now pinned to `load-tests/` by the pom); a failed seed run overwrote `voters.csv` with an empty file (now it only
  writes after a complete seed).
- **Flaky under load:** `GatewayObservabilityTest` hit its 5 s read timeout when six builds ran in parallel; it passes alone.
- **Open risk found by reading:** voting-service's Feign client has no explicit timeouts (Spring defaults: 10 s connect,
  60 s read). With 3 retries, a hanging candidate-service could hold a vote far past the gateway's 5 s limit, so the
  client sees 503 while the vote may still be stored (the idempotent retry then returns it). Proposed: about 1 s connect
  and read timeouts for `candidate-service`. Not changed yet; the chaos run should confirm it first.
- **Not verified:** nothing ran against a live stack. Docker, kubectl and jq are not installed on the dev machine.


### 2026-10-14 — Batch 7: Dockerfiles, full docker-compose, Kubernetes (no commits)
- **Dockerfile + `.dockerignore` in all six services:** multi-stage (maven:3.9.9-eclipse-temurin-17 →
  eclipse-temurin:17.0.13_11-jre-alpine), dependencies cached in their own layer, non-root UID 1001,
  `HEALTHCHECK` on `/actuator/health/readiness`, `exec java` so the JVM gets signals.
- **docker-compose.yml rewritten:** Postgres 16.4 (three DBs via `infra/postgres/init.sql`), Redis 7.4, Zookeeper/Kafka
  7.4.0, Eureka, the five services and the gateway, started in order by health checks. Only the gateway is public;
  infra and UIs bind to `127.0.0.1`. Read-only containers, `no-new-privileges`. Secrets from `.env` (`.env.example`
  committed; required vars fail fast).
- **Observability profile:** ELK 8.15.3 (Logstash TCP 5000 → index `voting-logs-*`), Prometheus 2.54.1 scraping all six
  services, Grafana 11.2.2 with a provisioned datasource and the "Voting System — Overview" dashboard (requests, latency,
  5xx, votes by outcome, open circuit breakers), Zipkin 3.4.
- **Kubernetes (`k8s/`, Kustomize):** namespace `voting`, ConfigMap, Secret generated from `k8s/secrets.env`
  (git-ignored), Postgres StatefulSet, Redis, Zookeeper/Kafka, a Deployment + Service per app with startup/readiness/
  liveness probes and a restricted security context, nginx Ingress for `/api` only. Gateway and voting run 2 replicas.
- **Gotcha avoided:** a K8s Service named `kafka` injects `KAFKA_PORT=tcp://…` into pods, which the cp-kafka image
  reads as its own config and fails. `enableServiceLinks: false` on the Kafka and Zookeeper pods.
- **Verified:** every service builds with `mvn clean package -DskipTests` (one jar each); an offline validator parsed all
  30 YAML/JSON documents and cross-checked compose dependencies, Dockerfiles, required env vars, mounts, kustomize
  resources and secret keys (a negative run caught the injected faults).
- **Not verified:** no image was built and nothing was started. `docker compose up`, `kubectl apply -k`, Prometheus
  scraping, log shipping and Zipkin export are all still untested. Docker Desktop is needed first.
- README Quick Start rewritten for compose and K8s; Spring Boot badge corrected to 3.3.6.

### 2026-10-13 — Batch 6: observability and audit logging in all six services (no commits)
- **Bug 12 fixed:** every service exposed `/actuator/prometheus` but none had the Prometheus registry, so it returned
  404 (and 500 in user-service, see below). Added `micrometer-registry-prometheus`; metrics are tagged `application=<service>`.
  Kubernetes health probes (`/actuator/health/liveness|readiness`) are on everywhere; eureka-server got actuator for the first time.
- **Tracing:** Micrometer + Brave with a Zipkin reporter. Trace ids appear in every log line; Feign (`feign-micrometer`),
  Kafka (`observation-enabled`) and the gateway's token check (observed `WebClient`) carry them across services.
  Sampling defaults to 0.0 locally (nothing exported) and is set by `TRACING_SAMPLING` (compose 1.0, `prod` 0.1).
- **Logs:** one shared `logback-spring.xml`: readable by default, JSON with `json-logs`, TCP to Logstash with `logstash`.
  Checked by starting eureka with `json-logs` (one JSON object per line, `service` field). Dropped logback context fields from JSON.
- **Audit (M7.7):** `AuditLogger` (`AUDIT` logger, key=value structured fields) in user (register, login success/failure),
  candidate (create/update/status/delete; actor from the JWT), voting (every vote outcome, verify, chain validation) and
  result (audit trail viewed). Ballot privacy holds: a vote's audit target is the vote or election, never the candidate (tested).
- **Vote metric:** `VoteOutcomeRecorder` = the `votes.cast` counter (success, replay, duplicate, invalid_candidate,
  candidate_service_unavailable, all registered at zero) + audit. Success is recorded after commit in `VoteCastSideEffects`.
- **`prod` profile** in each service: JSON logs, Swagger off (services with springdoc), 10 % trace sampling.
- **Incident — unknown URLs returned 500:** the generic `@ExceptionHandler(Exception.class)` caught Spring's
  `NoResourceFoundException` (and 405/415 errors) and answered 500. **Fix:** exceptions implementing Spring's
  `ErrorResponse` keep their own status (user, candidate, voting, result). One regression test per service.
- **Incident — user-service tests ignored the real config:** its test file was `src/test/resources/application.yml`, which
  replaces the main file on the test classpath. Renamed to `application-test.yml` with `@ActiveProfiles("test")`.
  **Lesson:** use `mvn clean` after renaming resources, because stale copies in `target/` kept the old behaviour.
- user-service config: `show-sql` off; Eureka URL from `EUREKA_URL`.
- Tests: **226 total, all green** (eureka 3, gateway 30, user 31, candidate 63, voting 66, result 33).
- **Not verified:** export to a real Zipkin and Logstash, and Prometheus scraping. Those servers arrive in batch 7.

### 2026-10-12 — Batch 5: api-gateway hardening (no commits)
- **Identity spoofing (bug 10):** clients could send their own `loggedInUser`/`role` headers. On the open results
  route they were never touched and decided the rate-limit bucket. **Fix:** `AuthenticationFilter` now runs on all
  four routes and always strips both headers, then sets them only from a token verified by user-service.
  Rate-limit keys are `user:<id>` or `ip:<addr>`, so user and IP buckets cannot collide.
- **Open-path matching:** `RouterValidator` used `path.contains(openPath)`, so `/api/v1/votes/x/api/v1/results` counted
  as public. Now it uses exact (method, PathPattern) pairs; `/auth/validate` and `/eureka` are no longer open.
- **Bad token reported as an outage:** any non-2xx from user-service (including 401) became "503 Authentication service
  unavailable". The new `TokenValidator` maps 401/403 and unsuccessful bodies → 401, and only real failures → 503.
- **Edge role checks** (`RoleAuthorizer`): `POST /votes` needs VOTER, candidate writes need ADMIN. Services keep
  enforcing their own rules.
- **Resilience (M6.3):** per-route reactive Resilience4j circuit breakers (50 % of 20 calls, 10 s open, 5 s timeout)
  with `FallbackController` → 503 JSON. A timed-out vote may have been stored; idempotent retry returns the receipt.
- **CORS (M7.4):** `globalcors` with origins from `CORS_ALLOWED_ORIGINS` (default `http://localhost:3000`).
- All gateway errors are now JSON `ApiResponse` (the old filter sent empty bodies).
- **Tests: 28 (had none):** filter (spoof stripping, 401/403/503), router rules (including the embedded-path case
  that failed with `contains`), roles, `TokenValidator` against MockWebServer, rate-limit key, and a full-context test for
  CORS preflight (allowed 200 / unknown origin 403), 401 JSON and the fallback.
- **Not verified:** real routing to running services through Eureka, real Redis rate limiting, and a circuit breaker
  opening under real failures. These need the full stack (batches 7–8).

### 2026-10-11 — Batch 4: candidate-service caching, events and hardening (no commits)
- **Incident (pushed state):** batches 1–3 were committed on a detached HEAD inside the user- and voting-service
  submodules, and the root repo was pushed pointing at commits that GitHub did not have. **Fix:** fast-forwarded
  `main` to those commits and pushed. **Rule added to CLAUDE.md:** check a service is on `main` before editing it.
- **Kafka events:** sent only after the DB commit (`TransactionSynchronization.afterCommit`; nothing on rollback),
  keyed by candidate id, no Java type headers, broker failures logged instead of thrown, `max.block.ms` 5 s.
- **Redis read cache (M6.1):** `candidate` (by id) and `candidatesByElection` / `activeCandidatesByElection`, 10 min TTL,
  evicted on every write. One typed Jackson serializer per cache, so there is no class metadata in Redis.
  `LoggingCacheErrorHandler` means a Redis outage falls back to the database. The vote-time `/validate` endpoint is
  deliberately not cached, so status changes take effect immediately for voting.
- **Hardening:** `@PreAuthorize("hasRole('ADMIN')")` on all writes (on top of the URL rules); JSON 401/403;
  `@Size` limits (name/party ≤ 100, bulk ≤ 500). **Incident:** the generic 500 handler echoed exception text
  (the test showed `connection to db-host:5432 refused` reaching the client). It now logs the error and returns a fixed message.
- **Config:** Kafka/Eureka from env vars, `show-sql` off, actuator health/info/prometheus with probes. Removed the unused
  `app.jwt.expiration`, whose required `${JWT_EXPIRATION}` placeholder blocked startup without the env var.
- README fixed: test count, roles, events, config; the `VOTING_DB_URL` example doubled the DB name; dead links to deleted docs.
- Tests: **58/58** (was 41). New: `CandidateEventProducerTest` (5), `CandidateCachingTest` (5, through the real cache
  proxy), `CacheConfigTest` (3), plus security/limit tests (3) and a "500 does not leak" test.
- **Not verified:** the cache was not run against a real Redis (no Docker here). The serializers are tested directly
  and the annotations with an in-memory cache. That gap gets closed in batch 7–8.

### 2026-10-11 — Batches 1–3: user-service finished, voting and result reviewed (no commits)
- **user-service done:** the `GlobalExceptionHandler` now rethrows Spring Security exceptions (method-security
  denials were becoming 500s) and logs unexpected errors. `admin.*` is mapped from the `ADMIN_*` env vars. **26/26 tests pass.**
- **Incident found in review — Kafka outage failed committed votes:** `KafkaTemplate.send` blocks up to 60 s
  for metadata and then throws inside the after-commit listener, so the voter got a 500 although the vote was saved.
  **Fix:** `VoteEventProducer` catches send failures (sync and async) and logs them with the vote id; `max.block.ms`
  lowered to 5 s. `vote.verified` is now keyed by vote id. Regression test `VoteEventProducerTest` (red before, green after).
- Added the missing voting role tests (own vs other user's vote status, chain validation as voter). voting-service: **54/54**.
- **Lesson:** rewriting whole files dropped existing Javadoc in user-, voting- and result-service. Restored it. Edit in place
  rather than rewrite, and review `git diff` for removed comments before handing a batch over.
- voting-service and result-service READMEs updated (endpoints and roles, schema with `election_chain_heads`, hash
  formula, double-vote layers, outage behaviour, Redis keys, env vars). Removed a false claim that voting calls user-service.

### 2026-10-10 — Full review, bug-fix pass, docs (no commits)
**Review findings** (before fixes): user-service 15/15 green; candidate-service 41 tests with 1 failure + 5 errors;
result-service 14 with 1 failure; voting, gateway and eureka had no tests. Twelve bugs found (memory file `voting-system-audit-2026-10`).

**Fixed (test-first):**
- *Incident — all votes rejected:* voting/result `JwtService` parsed the JWT subject (a username) as a UUID → every token
  was rejected. **Cause:** contract drift. **Fix:** read the `userId` claim; new `JwtPrincipal(userId, username, role)`.
- *Incident — invalid candidates accepted:* candidate `/validate` returns 200 with `valid:false`; voting's Feign method returned `void`.
  **Fix:** typed `CandidateApiResponse<CandidateValidation>`, reject when not valid.
- *Idempotent retry returned 409:* the replay check now runs first and is scoped per user; replay → 200 with the original receipt.
- *Chain could fork:* added `election_chain_heads` with a pessimistic row lock. Chaining stays on by default (`VOTING_CHAIN_ENABLED`).
  Chain validation now orders by `id` and skips link checks for unchained votes.
- *Side effects before commit:* moved to `VoteCastSideEffects` (`AFTER_COMMIT`).
- *Security exceptions → 500 / leaked messages:* rethrow security exceptions, JSON 401/403 entry points, generic 500 text.
- *Result audit always empty:* wrong authority (`ADMIN`), invalid `/**/audit` pattern, JWT not forwarded, Page vs List mismatch.
  **Fix:** `hasRole('ADMIN')`, Feign `Authorization` forwarding, `PageResponse` paging (500/page), 503 on failure.
- *Tally double-count on redelivery:* atomic Lua de-dup by vote id. Names now come from all candidates, not only active ones.
- *Candidate test suite red:* producer mock was missing; the test profile now disables Eureka and Kafka admin.
- *Admin self-registration:* `role` removed from `RegisterRequest`; `AdminSeeder` from env vars; added `GET /users/{id}` and `/users/{id}/role`.
  Finished 2026-10-11 (see that entry).

**Decisions:**
- Chaining kept ON (reverses PROJECT_PLAN decision #5) because the code already chained. Made safe with a per-election lock rather than turned off.
- Resilience4j fallback placed on `@Retry` (outermost aspect) so retries happen before the fallback.
- `VOTING_DB_URL` means the server base URL in **all** services (voting used it as the full URL before).
- voting-service consumers ignore Kafka type headers (`spring.json.use.type.headers=false`) and wrap JSON in `ErrorHandlingDeserializer`.
- `@EnableFeignClients` moved to `FeignConfig` so `@WebMvcTest` slices load without Feign.

**Lessons:**
- Write the cross-service contract down (JWT claims, validation response) and test both sides against it.
- Test cross-cutting behaviour (security, resilience, transactions) through the real Spring proxy. Unit tests missed the Retry/CircuitBreaker ordering bug; the integration test caught it.

**Environment notes:** result-service is not a registered submodule; it was cloned into `result-service/` (untracked).
Docker and kubectl are not installed on the dev machine, so container and K8s work cannot be run locally yet.

### 2026-05-29 — Result Service (Milestone 5)
result-service created: Kafka `vote.cast` consumer, Redis counters, Feign enrichment, results/summary/SSE/audit endpoints. Root docs updated.

### 2026-05 — Voting Service and Kafka (Milestones 3–4)
Vote entity with hash and prev-hash, Redis double-vote and idempotency checks, DB unique constraint, Feign validation,
`vote.cast` / `vote.verified` producers, `candidate.status-changed` consumer stub, Kafka + Zookeeper in docker-compose.

### 2026-04/05 — Foundation and infrastructure (Milestones 1–2)
candidate-service (CRUD, election queries, bulk, status, soft delete, seeder) and user-service (register/login, BCrypt, JWT).
Eureka server, API gateway with JWT validation filter and Redis rate limiting.
