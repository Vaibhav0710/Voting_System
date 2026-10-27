# Testing

_Last updated: 2026-10-26_

## 1. Stack
JUnit 5, Mockito, AssertJ / Hamcrest, Spring Boot Test (`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`),
spring-security-test, MockMvc, H2 in-memory DB, JaCoCo 0.8.12 (coverage), Testcontainers (PostgreSQL 16, voting-service),
Gatling 3.11 Java DSL (`load-tests/`), bash + curl + jq scripts (`scripts/`).

## 2. Running tests
```bash
cd voting-service && mvn test                              # one service
cd voting-service && mvn test -Dtest=VoteServiceTest       # one class
cd voting-service && mvn test -Dtest=VoteServiceTest#*Chain*   # matching methods
cd voting-service && mvn verify                            # + *IT tests, coverage report and 80 % gate
mvn -f load-tests/pom.xml test                             # load-test helper unit tests (no stack needed)
```
`mvn verify` writes a coverage report to `target/site/jacoco/index.html` in every service. Only voting-service enforces
a minimum (80 % line coverage, `jacoco.line.minimum` in its pom); the others report. `*IT` classes run in `verify` through
failsafe and skip themselves when Docker is not available (`@Testcontainers(disabledWithoutDocker = true)`).
No Kafka, Redis, Postgres or Eureka is needed: tests use H2, mock Redis-backed beans, set
`spring.kafka.listener.auto-startup=false` / `spring.kafka.admin.auto-create=false` and `eureka.client.enabled=false`.
There is no single root build yet. The root `pom.xml` lists `result-service`, which a fresh clone lacks, so test each service on its own.

## 3. Current results (`mvn verify`, 254 tests total)
| Service | Tests | Line coverage | Notes |
|---|---|---|---|
| user-service | 31 pass (2026-10-25) | 91.6 % | admin seeder, users API, role ignored, 401s, audit, metrics, 404 |
| candidate-service | 63 pass (2026-10-25) | 82.8 % | candidate CRUD, validation, Kafka events, soft delete, search |
| voting-service | 86 unit pass (2026-10-26) + 4 IT pass (2026-10-25) | 89.6 % with IT, 89.1 % unit only (gate 80 %) | 4 PostgreSQL 16 Testcontainers IT; FeignClientTimeoutTest regression test; JaCoCo gate met |
| result-service | 35 pass (2026-10-26) | 89.2 % | idempotent consumer, SSE, summary, audit, Redis tallies, FeignClientTimeoutTest regression test, Kafka consumer metrics |
| api-gateway | 32 pass (2026-10-26) | 95.6 % | filter, open-path rules, roles, token validation (MockWebServer), rate-limit key, auth rate-limit env overrides, CORS, fallback |
| eureka-server | 3 pass (2026-10-25) | 33.3 % (1 of 3 lines) | registry API, metrics, readiness; the only class is `main` |
| load-tests (helpers) | 15 pass (2026-10-26, `mvn test`) | not measured | status tally, voters-file guards, integrity report, 503 retry; the simulations themselves need the stack |

All 254 service tests pass (the 15 load-test helper tests are counted separately). On 2026-10-26 the three changed services were re-run; voting's 4 IT tests skipped because Docker
was not running (they last passed on 2026-10-25), and the unchanged services were not re-run. Both voting-service and result-service include FeignClientTimeoutTest asserting connect 500 ms and read 700 ms (worst case 3 x (500 + 700) + 2 x 200 = 4,000 ms, leaving 1,000 ms headroom under the 5 s gateway limit). Testcontainers 1.21.4 supports Docker Engine 29 with no per-machine config (4 PostgreSQL 16 IT tests passed).

## 4. Layout and kinds of test
```
<service>/src/test/java/com/voting/<service>/
  controller/   @WebMvcTest + real SecurityConfig + real JWTs (TestTokens) → status codes and role rules
  service/      Mockito unit tests (@Nested per method), plus @SpringBootTest integration tests
  repository/   *IT: Testcontainers PostgreSQL (voting-service)
  security/     JwtService tests (valid, expired, wrong key, missing claim)
  support/      TestTokens: builds User-Service-shaped JWTs with the test secret; ConcurrentTasks: start N tasks at once
<service>/src/test/resources/application-test.yml
```
Notable tests:
- `VoteConcurrencyIntegrationTest`: 50 threads, same voter → exactly 1 stored; 50 voters → chain valid.
- `VoteRepositoryPostgresIT`: the same guarantees on real PostgreSQL 16 (constraint `uq_user_election`, the chain-head
  `FOR UPDATE` lock blocking a second transaction, 20 concurrent votes). Passed against a PostgreSQL 16 container on
  2026-10-25; skipped when Docker is not running.
- `VoteRedisStoreTest` / `CandidateStatusCacheTest`: key format, TTLs, and every Redis failure read as a cache miss.
- `CandidateValidatorResilienceIntegrationTest`: a 503 is retried 3× through the real Resilience4j proxy, then the cache fallback runs.
- `ResultServiceContextTest`: full context wiring + retry/fallback on `CandidateDirectory`.
- `VoteHasherTest.matchesFormulaUsedForExistingVotes`: pins the hash formula so stored votes stay verifiable.

## 5. Test data and environment
- H2 per test context. voting uses `MODE=PostgreSQL;LOCK_TIMEOUT=20000` so the row lock and `ON CONFLICT` behave like Postgres.
- Test-only JWT secrets live in `application-test.yml` / `TestTokens`. They are not real secrets.
- Fixed UUIDs in tests where readability helps (`1111…`, `aaaa…`).

## 6. Faking external services
| Dependency | How |
|---|---|
| Redis | `@MockBean VoteRedisStore` / `CandidateStatusCache` / `StringRedisTemplate`; RedisAutoConfiguration excluded in voting tests |
| Kafka | `@MockBean` producers (`VoteEventProducer`, `CandidateEventProducer`); listeners not started |
| Feign clients | `@MockBean CandidateClient` / `VotingClient`, throwing `FeignException` for outage cases |
| Eureka | disabled in the test profile |

## 7. What must always be tested
- One-vote guarantee under concurrency, and idempotent replay.
- Candidate validity (inactive / other election / unknown → rejected).
- Hash formula stability and chain validation (intact, tampered, unchained).
- Every endpoint's role rule: anonymous 401, wrong role 403, right role 2xx.
- JWT parsing: valid, expired, wrong key, missing claims.
- 500 responses do not leak exception text.
- Kafka consumers: malformed events ignored, redeliveries not double-counted.
- Any bug fix: a regression test that fails without the fix.

## 8. Rules
- Tests never touch real accounts, real databases or real secrets.
- No placeholder tests (an empty `contextPlaceholder()` test was removed from result-service).
- Prefer a real Spring proxy test for cross-cutting behaviour (security, resilience, transactions).

## 9. Deployment files (compose, K8s)
Executed and verified on Docker Desktop & Kind (2026-10-24):
- `docker compose up -d --build`: builds all 6 images (`voting/*:1.0.0`), launches 10 core containers in dependency order (`postgres`, `redis`, `zookeeper`, `kafka`, `eureka-server`, `user-service`, `candidate-service`, `voting-service`, `result-service`, `api-gateway`). All services UP in Eureka and `/actuator/health` UP through the gateway.
- `docker compose --profile observability up -d`: launches Elasticsearch 8.15.3, Logstash 8.15.3, Kibana 8.15.3, Prometheus 2.54.1, Grafana 11.2.2, and Zipkin 3.4. Prometheus scrapes all 6 services with 0 errors; Grafana dashboard provisioned; Kibana ingests structured JSON logs; Zipkin captures distributed traces end-to-end.
- **Kubernetes (kind v1.28.0 cluster `voting-cluster`):**
  - Generated `k8s/secrets.env` from `secrets.env.example` with local secrets (git-ignored).
  - Loaded all 6 application images (`voting/*:1.0.0`) into the kind cluster.
  - Added `enableServiceLinks: false` on application pods and `REDIS_PORT: "6379"` in `k8s/config.yaml` to prevent Kubernetes Docker-link environment variable injection (`REDIS_PORT=tcp://...`) from crashing Spring Boot's integer port binder.
  - Deployed stack: `kubectl apply -k k8s/`. All 12 pods (Eureka, User, Candidate, Voting, Result, Gateway, Postgres StatefulSet with 2Gi PVC, Redis, Kafka, Zookeeper) reached **1/1 Running** and healthy.
  - Port-forwarded Gateway and executed `scripts/e2e.sh`: **all 14 checks passed through Kubernetes**!

## 10. Stack-level checks (verified 2026-10-24 on Docker)
All checks run through the gateway (`http://localhost:8080`).

| Check | Command | Result & Evidence |
|---|---|---|
| End-to-end | `scripts/e2e.sh` | **14/14 checks passed**: register → login → candidates → vote (201) → replay (200, same id) → second vote (409) → receipt → verify → chain valid → results via Kafka → admin audit; role boundaries 401/403 enforced |
| Seed voters | `USERS=1000 mvn -f load-tests/pom.xml gatling:test -Dgatling.simulationClass=com.voting.loadtests.SeedVotersSimulation` | **2000 requests, 100% OK, 0 errors**: 1000 voters registered and logged in paced at 4 voters/s (8 auth req/s, under gateway IP limit 10/s); tokens saved to `load-tests/voters.csv` (git-ignored, valid 24h) |
| Load (1,000 votes) | `USERS=1000 RAMP_SECONDS=60 mvn -f load-tests/pom.xml gatling:test -Dgatling.simulationClass=com.voting.loadtests.CastVoteSimulation` | **1000 votes, 100% OK (0% errors)**: throughput 16.67 req/s, mean 62 ms, p50 44 ms, p75 68 ms, **p95 163 ms** (< 500 ms SLA limit), **p99 282 ms**, max 449 ms; 100% requests < 800 ms (1,000 votes ramped over 60 s; 10k concurrent target not yet run) |
| Double vote | `mvn -f load-tests/pom.xml gatling:test -Dgatling.simulationClass=com.voting.loadtests.DoubleVoteSimulation` | **100 near-simultaneous votes from 1 voter**: exactly 1 Created (201), 99 rejected (409/429), DB query confirmed exactly 1 stored vote |
| Chaos | `scripts/chaos.sh` | **8/8 checks passed**: candidate-service stopped; 10/10 concurrent votes succeeded via Redis fallback cache; unknown candidate returned 503; after restart circuit closed and Feign recovered |
| DB Integrity | PostgreSQL `voting_service.votes` | **1000 votes, 1000 distinct voters**, 0 duplicate votes (`uq_user_election`) |
| Hash Chain | `POST /api/v1/votes/chain/validate/{id}` | **1000 votes checked, chainValid: true, 0 broken links** |
| Real-time Tally | `GET /api/v1/results/election/{id}` | **1000 total votes tallies in Redis** matching candidate sums exactly |

Notes:
- **Fast-fail Feign timeouts:** To ensure clean fallback to Redis during candidate-service outages, `voting-service` and `result-service` set `connectTimeout: 500ms` and `readTimeout: 700ms`, completing 3 retries in ~1.9s and worst case in 4,000 ms (< 5.0s gateway timeout with 1,000 ms headroom).
- **Testcontainers Engine 29+ Handshake:** Testcontainers 1.21.4 supports Docker Engine 29 with no per-machine config.
- **Spring Boot 3.3 Page serialization:** Gatling JSONPath uses `$.data.page.totalElements` to handle the Spring Data Page DTO structure.

### Still open
- **10k load run:** not yet run. The tooling (`CapacitySimulation`, `BurstVoteSimulation`, an integrity check after
  every voting simulation, `scripts/sample-load.ps1`, three new Grafana panels) passed a 50-voter smoke run on
  2026-10-26: paced 50/50 (p95 306 ms), step test 27/27, burst 50/50 (p95 2.6 s), all `INTEGRITY OK`; a bad token is
  recorded as `401=1`. Before any measured run: stop the kind cluster (it shares Docker's CPU and RAM) and send one
  warm-up run after any service restart (see ARCHITECTURE §8, cold start).
  Target and run book in `docs/plans/2026-10-26-10k-load-run.md`: a step test finds the
  sustainable rate, 10,000 votes at 80 % of it must meet p95 < 500 ms with 0 errors, and a 10,000-vote burst must keep
  integrity (see PRD §6).
