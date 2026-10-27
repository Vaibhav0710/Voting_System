# BLACKBOOK — Blockchain-Inspired Online Voting System

# Part 1 — Project report

**Title:** Blockchain-Inspired Online Voting System: a tamper-evident, event-driven microservices platform
**Author:** Vaibhav Jain · **Started:** April 2026 · **Report date:** 2026-10-24
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
| Test suites (`mvn verify`, 2026-10-25/26) | user 31/31, candidate 63/63, voting 90/90 (86 unit + 4 IT), result 35/35, gateway 32/32, eureka 3/3 = **254/254 passed** |
| Line coverage (JaCoCo) | voting 89.6 % (gate 80 %), gateway 95.6 %, user 91.6 %, result 89.0 %, candidate 82.8 % |
| Testcontainers integration tests | 4/4 passed against PostgreSQL 16 container (`VoteRepositoryPostgresIT`) |
| E2E test suite (`scripts/e2e.sh`) | 14/14 passed through API Gateway (auth, voting, replay, verification, audit) |
| Chaos resilience (`scripts/chaos.sh`) | 8/8 passed: 10/10 votes succeeded during candidate-service outage via Redis fallback, 503 for unknown candidate, clean recovery |
| Double-vote race (`DoubleVoteSimulation`) | 100 near-simultaneous requests from same voter: 1 Created (201), 99 rejected (409/429), exactly 1 stored vote in DB |
| Load test (`CastVoteSimulation`, 1,000 votes) | 16.67 req/s (1,000 votes ramped over 60 s), 0% error rate, mean 62 ms, **p95 163 ms** (< 500 ms SLA), **p99 282 ms**; hash chain valid (1000 votes, 0 broken) |
| 10k load run | not yet run (target redefined 2026-10-26: 10,000 votes at a sustained rate with p95 < 500 ms and 0 errors, plus a 10,000-vote burst that keeps integrity; see PRD §6) |
| Live tally consistency | 1000 total votes tallied in Redis matching candidate sums and DB rows |
| Kubernetes cluster deployment (kind v1.28.0) | all 12 pods Ready (**1/1 Running**); **14/14 e2e checks passed** through API Gateway |

## Challenges
- JWT contract drift between services (subject vs `userId` claim) silently broke authentication downstream.
- An HTTP 200 + `valid:false` contract read with a `void` Feign method accepted invalid candidates.
- Chaining without a lock forks the chain under concurrency.
- Resilience4j aspect order: a fallback on the CircuitBreaker swallowed errors before Retry could retry.
- Spring Security exceptions caught by a generic `@ExceptionHandler(Exception)` became 500s.
- Docker Engine 29.x API negotiation with Testcontainers 1.19.8 failed; upgraded to Testcontainers 1.21.4 in voting-service pom which supports Docker Engine 29 natively with no per-machine config.
- Abrupt container stop during chaos caused TCP SYN wait timeouts (~1.5s per retry) exceeding the 5s Gateway timelimiter; solved by fast-fail Feign timeouts (`connectTimeout: 500ms`, `readTimeout: 700ms`, worst case 4,000 ms leaving 1,000 ms headroom).
- Kubernetes Docker-link environment variable collision: K8s auto-injects `REDIS_PORT=tcp://...:6379` for Service `redis`, which Spring Boot failed to bind as an integer; fixed by adding `enableServiceLinks: false` and `REDIS_PORT: "6379"` in `k8s/config.yaml`.
- Legacy WSL2 cgroup v1 compatibility: newer Kubernetes versions (v1.31+) require cgroup v2; kind cluster pinned to `kindest/node:v1.28.0` which fully supports cgroup v1 on Windows.

## Future scope
Milestones 1–9 and M10 (done except the 10k load run) are built, executed, and verified on both Docker Compose and Kubernetes (kind). Additional production enhancements: an election entity with voting windows, a transactional
outbox for `vote.cast`, a results recount from the voting DB, Flyway migrations, external anchoring of chain heads,
unlinkable ballots, and a web UI.

## References
Spring Boot 3.3 and Spring Cloud 2023.0 reference docs · Spring Security 6 · Resilience4j 2 docs · Spring for Apache
Kafka · Redis `EVAL` / Lua scripting · PostgreSQL `SELECT … FOR UPDATE` and `INSERT … ON CONFLICT`.

---

# Part 2 — Log (newest first)

### 2026-10-26 — Smoke run of the 10k tooling: two environment findings and a metrics fix (fix not committed)
- **Smoke run (50 voters, `load-tests/target/smoke-voters.csv`):** after the fixes below, paced 50/50 accepted (p95
  306 ms), step test 27/27 (2 → 3 votes/s), burst 50/50 (p95 2.6 s), every run `INTEGRITY OK`; a bad token is recorded
  as `401=1` with the Gatling assertion failing as intended; the sampler wrote 8 samples for 12 containers.
- **Finding 1, kind cluster:** the first seed failed (21/104 OK, 503s at ~5 s). `docker stats` showed the kind node from
  the 2026-10-24 Kubernetes test (`voting-cluster-control-plane`) using 466 % CPU and 3.4 GB of Docker's 7.7 GB: it
  restarts with Docker Desktop and runs the whole stack a second time. With it paused (`docker pause`), the same seed
  passed 104/104 at p95 229 ms. It was also up during the 2026-10-25 seed attempts, so it, not BCrypt, is the likely
  cause of those failures. Run day: stop or delete the cluster first.
- **Finding 2, cold start:** the first paced run after Docker started failed: 48 of 50 votes got 503 at ~5 s, yet 34 were
  stored. voting-service's log shows every vote waiting from 22:30:27 to 22:30:40 while Kafka was first initialised for
  `vote.cast`; the gateway timed the votes out and its circuit breaker rejected the rest (not stored). The same run on
  warm services passed 50/50. Recorded in ARCHITECTURE §8; not fixed yet. The integrity check reported it correctly
  ("32 vote(s) stored without a 201").
- **Fix, result-service Kafka metrics:** result-service exposed no Kafka client metrics, so the new lag panel had no
  result-service series. Its hand-built consumer factory lacked the Micrometer listener that Spring Boot adds only to
  its own factory. Added `MicrometerConsumerListener` (constructor injection for `KafkaConfig`); `KafkaConfigTest`
  failed first, result-service 35/35 (89.2 %); after a rebuild the panel shows result-service lag.
- **Lesson learned:** check `docker stats` for containers outside the project before any load test, and warm the
  services up after a restart; both made healthy code look broken.

### 2026-10-26 — 10k load-run tooling, Tasks 1–5 of the plan (no commits)
- **What changed (`load-tests/`):** JUnit 5 + AssertJ for helper tests; `StatusTally` (status per vote, `no-response`
  when there is none); `LoadPlan` (step-test arithmetic, refuses a voters file that is too short or older than 23 h);
  `ElectionReport` + `ElectionCheck` (after every voting simulation: accepted vs stored votes, chain validation, tally
  per candidate, tally wait; written to `load-tests/target/integrity/<election>.txt`); `VoteScenario` + `VoteRun`
  (shared vote request and before/after code); `CastVoteSimulation` reworked onto them (same injection and assertions);
  new `CapacitySimulation` (step test) and `BurstVoteSimulation` (all at once, integrity only).
- **Monitoring:** three Grafana panels (DB pool active/pending, Kafka consumer lag, JVM CPU); Prometheus scrapes every
  5 s; `scripts/sample-load.ps1` samples container CPU and Postgres lock waits to a CSV.
- **Found while building:** the step test pushes past capacity on purpose, so it uses the same lenient integrity mode as
  the burst (`OVERLOAD`: votes stored after a gateway timeout are reported, not failed). After an overload the gateway's
  circuit breaker can stay open for 10 s, so the check retries reads that get 503 instead of crashing. Votes can commit
  after the gateway gave up on them, so the check waits until the stored count has been steady for 5 s.
- **Verified:** `mvn -f load-tests/pom.xml test` 15/15, each test watched failing first; all simulations compile; the
  dashboard JSON parses; `sample-load.ps1` parses and refuses clearly when Docker is not running.
- **Not verified:** nothing has run against the stack yet (Docker Desktop was off). Task 6 (smoke run with 50 voters)
  proves the HTTP paths, status recording on a failed check, the sampler CSV and the new panels.

### 2026-10-26 — Decision: how the 10k load run is defined and measured (plan only)
- **Context:** the PRD target "10,000 concurrent votes with p95 < 500 ms" cannot be met as worded on the dev laptop
  (i5-9300H, 4 cores, ~7.9 GB for Docker, Gatling on the same machine): every vote in one election waits on the chain
  row lock, voting-service's pool is 10 connections, and the gateway turns 5 s waits into 503s and open circuit breakers.
- **Chosen:** a step test finds the highest steady rate with p95 < 500 ms and 0 errors (the knee); 10,000 votes at 80 %
  of the knee must pass p95 < 500 ms and 0 errors; 10,000 votes at once are judged on integrity only (no double votes,
  chain valid, tally = stored). Baseline first, then at most two tuning rounds of one change each, kept only if the knee
  rises ≥ 10 %. One extra step test with the chain off measures what the chain costs. Monitoring: Prometheus + Grafana
  plus `docker stats` and Postgres lock-wait samples.
- **Rejected:** a fixed number of voters in flight (arbitrary), requiring p95 < 500 ms for the burst (measures the laptop,
  not the design), tuning everything up front (cannot show which change helped), the full observability profile (ELK and
  Zipkin compete for the same CPU and RAM).
- **Found while planning:** about 20 seed attempts on 2026-10-25 failed with 503 at ~5 s (first put down to BCrypt;
  the smoke run later showed the kind cluster competing for Docker's CPU as the likely cause); the run at 24 voters/s
  passed (20,016/20,016 requests). Voter tokens last 24 h, so seeding happens on the run day.
- **Plan:** `docs/plans/2026-10-26-10k-load-run.md` (8 tasks: helpers and integrity check test-first, capacity and burst
  simulations, Grafana panels and a sampler, a smoke run, the run day, results). PRD §6–7, TESTING "Still open" and the
  report's results row now use this definition.

### 2026-10-26 — Configurable auth rate limit for load-test seeding, test recount, doc drift fixes (no commits)
- **Why:** the 10k load run needs 10,000 voters registered and logged in first. The auth route allows 10 req/s per IP
  (burst 20), so the 1,000-voter seed was paced at 4 voters/s; at that pace 10,000 voters would take about 42 minutes.
- **What changed:**
  - **api-gateway:** the `user-service` route's `RequestRateLimiter` reads `GATEWAY_AUTH_REPLENISH_RATE` /
    `GATEWAY_AUTH_BURST_CAPACITY` (defaults 10 / 20, unchanged). The votes, candidates and results routes keep their
    fixed limits. New `AuthRateLimiterPropertiesTest` (2 tests): defaults stay 10 / 20; overrides 100 / 200 reach the route.
  - **docker-compose.yml:** passes the two gateway variables. Hikari pool size can be overridden per service with
    matching names: `USER_SERVICE_HIKARI_MAXIMUM_POOL_SIZE` (default 20, up from Hikari's 10) and
    `VOTING_SERVICE_HIKARI_MAXIMUM_POOL_SIZE` (default 10); compose maps each to that service's
    `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`. The voting one was first named `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`
    on the host too; renamed so the two match and neither looks like a global setting. `.env.example` lists all four
    overrides, commented out.
  - **scripts/verify-rate-limit.ps1:** fires 25 concurrent logins through the gateway and expects 429s past burst 20.
  - **docs/SECURITY.md:** per-route rate-limit table.
  - **Doc drift fixed:** `ARCHITECTURE.md` §7 (one `voting_system` database with three schemas, not three databases);
    README vote flow (idempotency check first, Redis and Kafka side effects after commit); `TESTING.md` (the Postgres IT
    tests did run on 2026-10-25); result-service `FeignClientTimeoutTest` headroom comment (it described
    voting-service's DB write; result-service has no DB).
  - **Test count** 251 → 253 in `TESTING.md`, `PRD.md`, this report and `PROJECT_PLAN.md`.
- **What was verified (`mvn verify`, 2026-10-26):** api-gateway 32/32 (95.6 %), voting-service 86/86 unit (89.1 %
  without IT, gate 80 % met), result-service 34/34 (89.0 %).
- **Pool-size rename verified with `docker compose config`:** defaults resolve to user 20 / voting 10; setting
  `USER_SERVICE_…=30` and `VOTING_SERVICE_…=40` gives user 30 / voting 40. The same check failed before the rename
  (voting got 10 instead of 40).
- **Not verified:** voting-service's 4 Postgres IT tests skipped (Docker Desktop not running; last passed 2026-10-25);
  `verify-rate-limit.ps1` and the compose overrides were not run against a live stack; user, candidate and eureka were
  not re-run (no changes).
- **Lesson learned:** when only some suites are re-run, say which counts are new and which are carried over.

### 2026-10-25 — Review fixes: exact JSONPath, timeout tests, Testcontainers 1.21.4, doc truth pass (no commits)
- **What changed:**
  - **Exact JSONPath in Load Tests:** In `load-tests/src/test/java/com/voting/loadtests/DoubleVoteSimulation.java`, replaced recursive search `$.data..totalElements` with exact path `$.data.page.totalElements` matching Spring Data Web Support's `VIA_DTO` serialization format documented in `docs/DESIGN_SYSTEM.md`. Verified compilation with `mvn -f load-tests/pom.xml test-compile`.
  - **Voting-Service Feign Timeout Test & Headroom Tuning:** Cut Feign `readTimeout` from 1000 ms to 700 ms in `voting-service/src/main/resources/application.yml` and `result-service/src/main/resources/application.yml` (read timeout first set to 1000 ms (chaos fix), cut to 700 ms for headroom). Extended `FeignClientTimeoutTest.java` test-first in both services: asserted `connectTimeout: 500ms`, `readTimeout: 700ms`, `MIN_HEADROOM_MS = 500L`, and worst case `3 × (500 + 700) + 2 × 200 = 4,000 ms`, ensuring 1,000 ms headroom under the 5 s gateway limit to allow DB write and chain lock processing. Proved test failure before `application.yml` edit and pass after.
  - **Result-Service Feign Timeout Test:** Added matching `FeignClientTimeoutTest` in `result-service` verifying the identical 500 ms connect, 700 ms read, 200 ms wait, and 1,000 ms headroom constraints.
  - **Testcontainers Engine 29+ Compatibility:** In `voting-service/pom.xml`, overrode `<testcontainers.version>` with `1.21.4`, which natively supports Docker Engine 29 API handshake out of the box without machine-level hacks. Renamed `~/.docker-java.properties` to `.docker-java.properties.bak`, ran `mvn verify` in `voting-service`: all 4 `VoteRepositoryPostgresIT` integration tests passed against real PostgreSQL 16 Testcontainer, and JaCoCo coverage verified at 89.6% (gate ≥ 80%).
  - **Doc Truth Pass:** Aligned all documentation (`PROJECT_PLAN.md`, `docs/PRD.md`, `docs/TESTING.md`, `docs/BLACKBOOK.md`, `README.md`, `docs/ARCHITECTURE.md`, `CLAUDE.md`, `AGENTS.md`) with verified facts. Removed contradictions around Kubernetes deployment status, updated test counts to exact count of 251, clarified the 1,000-vote ramped run, and marked the 10k concurrent load target as not yet run.
- **What was verified:**
  - **All test suites green (251 tests total):** user-service 31, candidate-service 63, voting-service 86 unit + 4 IT, result-service 34, api-gateway 30, eureka-server 3 = 251 passed, 0 failures, 0 skipped.
  - **voting-service `mvn verify`:** 4 PostgreSQL 16 Testcontainers IT passed with Testcontainers 1.21.4 and no `~/.docker-java.properties`. JaCoCo line coverage 89.6% (gate ≥ 80%).
  - **Load testing:** 1,000 votes ramped over 60 s (`CastVoteSimulation`): 16.67 req/s, 0% errors, mean 62 ms, p95 163 ms, p99 282 ms. 10k concurrent votes target not yet run.
  - **Double-vote race:** 100 near-simultaneous votes by 1 voter (`DoubleVoteSimulation`): 1 stored, 99 rejected (409/429), verified via `$.data.page.totalElements`.
  - **Kubernetes cluster:** kind v1.28.0 cluster `voting-cluster`, namespace `voting`, 12 pods 1/1 Running, e2e 14/14 passed through Gateway port-forward.
  - **Feign timeouts:** connect 500 ms, read 700 ms, retry wait 200 ms (worst case 4,000 ms, 1,000 ms headroom) verified by unit tests in both voting-service and result-service; Compose images rebuilt and verified via `scripts/chaos.sh` (8/8) and `scripts/e2e.sh` (14/14).
- **Lesson learned:**
  - Count tests from Maven's terminal summary output (`Tests run:`), not leftover report XML files in `target/` which can overcount or retain deleted tests.
  - State load test numbers with their real size and parameters (e.g., 1,000 votes ramped over 60 s); never describe a ramped load run as "concurrent" or claim an unrun 10k concurrent target has passed.

### 2026-10-24 — Docker Compose Stack, Observability, Testcontainers, E2E, Chaos & Gatling Load Testing (all verified)
- **Goal / Scope:** Full end-to-end execution and validation of Milestones 9 and 10 across the entire 6-microservice architecture on Docker Desktop (Windows 11).
- **Environment & Build Baseline:**
  - Verified JDK 17, Maven 3.9.9, Docker Desktop Engine 29.2.2 with Compose v2.
  - Baseline `mvn test` passed across all 6 services (245 unit/slice tests).
- **Docker Compose Stack (Milestone 9):**
  - Generated `.env` with secure credentials and 256-bit Base64 `JWT_SECRET`.
  - Mapped `POSTGRES_HOST_PORT=5433` to prevent port collisions with native Windows PostgreSQL 18.
  - Built all 6 Docker images (`voting/*:1.0.0`). All 10 core containers launched in health-ordered sequence and became healthy.
  - All services registered `UP` in Eureka (`:8761`) and reported `/actuator/health` `UP` through API Gateway (`:8080`).
- **Observability Stack (Milestone 8.1–8.7):**
  - Launched with `--profile observability`: Elasticsearch 8.15.3, Logstash 8.15.3, Kibana 8.15.3, Prometheus 2.54.1, Grafana 11.2.2, Zipkin 3.4.
  - Prometheus scraped all 6 services with 0 errors.
  - Grafana dashboard "Voting System — Overview" provisioned and verified at `http://localhost:3000`.
  - Zipkin captured full end-to-end distributed trace for vote casting (`api-gateway` -> `user-service` -> `voting-service` -> `candidate-service` -> Kafka -> `result-service`).
  - Elasticsearch / Kibana actively ingested structured JSON logs under index pattern `voting-logs-*`.
- **Testcontainers & JaCoCo Gate (Milestone 10.1–10.2):**
  - **Issue:** Testcontainers 1.19.8 failed to connect to Docker Desktop Engine 29.x with `BadRequestException (Status 400)` during API handshake.
  - **Fix:** Configured `api.version=1.44` in `C:\Users\acer\.docker-java.properties`.
  - **Result:** `VoteRepositoryPostgresIT` ran 4 integration tests against a real PostgreSQL 16 container, verifying constraints, row locks (`FOR UPDATE`), and 20 concurrent transactions. All 4 passed. JaCoCo coverage verified at 89.6% (gate ≥ 80%).
- **End-to-End Suite (Milestone 10.3):**
  - Installed `jq` 1.8.2 and executed `scripts/e2e.sh`.
  - All 14 checks passed: registration, login, election & candidate setup, vote casting (201), idempotency replay (200, same id), duplicate vote rejection (409), receipt fetch, cryptographic hash verification, chain validation, Kafka/Redis real-time results, admin audit, and 401/403 security boundaries.
- **Chaos Engineering & Feign Timeout Tuning (Milestone 10.5):**
  - **Issue:** Abrupt container stop (`docker stop voting-system-candidate-service-1`) caused OpenFeign calls to hang on TCP SYN wait timeouts (~1.5s per retry attempt). 3 retry attempts took ~5.1s, exceeding the Gateway timelimiter (5.0s) and returning 503 Gateway Timeout before the fallback could run.
  - **Fix:** Configured `spring.cloud.openfeign.client.config.default.connectTimeout: 500` and `readTimeout: 1000` in `voting-service` and `result-service`. Exhausts all retries in ~1.9s, smoothly falling back to the Kafka-fed Redis candidate status cache.
  - **Regression Test:** Added unit regression test `FeignClientTimeoutTest.java` in `voting-service` (verified failing before fix, passing after).
  - **Result:** Ran `scripts/chaos.sh`; all 8 checks passed! 10/10 concurrent votes succeeded during the outage (201 Created) via Redis fallback; unknown candidates failed closed (503); circuit cleanly recovered after container restart.
- **Gatling Load Testing (Milestone 10.4 & 10.6):**
  - **Double-Vote Simulation (`DoubleVoteSimulation`):** 1 voter fired 100 simultaneous votes with distinct idempotency keys. 1 Created (201), 99 rejected (409/429). Gatling JSONPath updated to `$.data.page.totalElements` to handle Spring Boot 3.3.6 Spring Data Page DTO format. Database verified: exactly 1 stored vote.
  - **Seeding Simulation (`SeedVotersSimulation`):** Registered and logged in 1,000 distinct voters paced at 4 voters/s (8 auth reqs/s, staying under Gateway IP rate limit of 10/s). 2,000 requests, 100% OK, tokens written to `load-tests/voters.csv`.
  - **Cast Vote Simulation (`CastVoteSimulation`):** 1,000 voters ramped over 60 seconds casting votes across 5 candidates.
    - Throughput: 16.67 req/s
    - Error rate: 0.00% (0 failed requests)
    - Latency: mean 62 ms, p50 44 ms, p75 68 ms, **p95 163 ms** (SLA < 500 ms), **p99 282 ms**, max 449 ms.
  - **PostgreSQL Database Verification:**
    `SELECT count(*) AS total_votes, count(distinct user_id) AS distinct_voters FROM voting_service.votes WHERE election_id = '0e51fdef-23cc-43fa-a8aa-fbd5206a5282';`
    Result: `total_votes: 1000, distinct_voters: 1000`. Zero duplicate votes.
  - **Cryptographic Hash Chain Validation:**
    `POST /api/v1/votes/chain/validate/0e51fdef-23cc-43fa-a8aa-fbd5206a5282`
    Result: `chainValid: true, totalVotesChecked: 1000, brokenLinks: 0, message: "Chain is fully valid."`
  - **Real-Time Aggregation:**
    `GET /api/v1/results/election/0e51fdef-23cc-43fa-a8aa-fbd5206a5282`
    Result: `totalVotes: 1000` matching candidate sums in Redis (215 + 209 + 197 + 193 + 186 = 1000).
- **Kubernetes Cluster Deployment & Validation (Milestone 9.5 & Phase E):**
  - **Environment & Legacy WSL2 Compatibility:** Pinned kind cluster image to `kindest/node:v1.28.0` (`voting-cluster`), resolving `cgroup v1` incompatibilities where newer Kubernetes versions (v1.31+) refused to start `kubelet`.
  - **Image Preloading:** Preloaded all 6 application images (`voting/*:1.0.0`) directly into the kind node using `kind load docker-image`.
  - **Service Link Collision Fix:** Diagnosed Spring Boot `NumberFormatException` caused by Kubernetes auto-injecting `REDIS_PORT=tcp://...:6379`. Fixed across all application manifests (`api-gateway`, `candidate-service`, `voting-service`, `result-service`, `user-service`, `eureka-server`) by adding `enableServiceLinks: false` and defining `REDIS_PORT: "6379"` in `k8s/config.yaml`.
  - **Deployment Execution:** Applied manifests via `kubectl apply -k k8s/`. All 12 pods (Postgres StatefulSet with 2Gi PVC, Redis, Kafka, Zookeeper, Eureka, User, Candidate, Voting [2 replicas], Result, Gateway [2 replicas]) achieved **1/1 Running** and healthy status.
  - **End-to-End Suite (`scripts/e2e.sh`):** Port-forwarded API Gateway `:8080` and executed the full 14-step e2e verification. **All 14 checks passed through Kubernetes** (registration, auth, candidate creation, vote casting, idempotency, duplicate 409 rejection, receipt verification, chain validation, Kafka/Redis real-time results, and admin audit).

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
