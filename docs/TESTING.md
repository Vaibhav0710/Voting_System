# Testing

_Last updated: 2026-10-14_

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
```
`mvn verify` writes a coverage report to `target/site/jacoco/index.html` in every service. Only voting-service enforces
a minimum (80 % line coverage, `jacoco.line.minimum` in its pom); the others report. `*IT` classes run in `verify` through
failsafe and skip themselves when Docker is not available (`@Testcontainers(disabledWithoutDocker = true)`).
No Kafka, Redis, Postgres or Eureka is needed: tests use H2, mock Redis-backed beans, set
`spring.kafka.listener.auto-startup=false` / `spring.kafka.admin.auto-create=false` and `eureka.client.enabled=false`.
There is no single root build yet. The root `pom.xml` lists `result-service`, which a fresh clone lacks, so test each service on its own.

## 3. Current results (2026-10-14, `mvn verify`, 245 tests total)
| Service | Tests | Line coverage | Notes |
|---|---|---|---|
| user-service | 31 pass | 91.6 % | admin seeder, users API, role ignored, 401s, audit, metrics, 404 |
| candidate-service | 63 pass | 82.8 % | |
| voting-service | 85 pass + 4 IT skipped | 89.6 % (gate 80 %) | was 66 / 79.2 %: Redis store and status-cache tests added; Postgres IT skipped (no Docker) |
| result-service | 33 pass | 89.0 % | |
| api-gateway | 30 pass | 95.6 % | filter, open-path rules, roles, token validation (MockWebServer), rate-limit key, CORS, fallback |
| eureka-server | 3 pass | 33.3 % (1 of 3 lines) | registry API, metrics, readiness; the only class is `main` |

`GatewayObservabilityTest` once timed out (5 s WebTestClient read) when all six builds ran in parallel; it passes when run
alone. Run the services one at a time when measuring.

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
  `FOR UPDATE` lock blocking a second transaction, 20 concurrent votes). Its test bodies were checked once on H2; the
  container run itself has not happened yet (no Docker).
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
Docker and kubectl are not installed on the dev machine, so the containers and manifests have **not been run**. What was
checked offline (2026-10-14):
- `mvn -B -q clean package -DskipTests` in each service produces exactly one jar (what the Dockerfile copies).
- A throwaway validator (SnakeYAML + Jackson, kept outside the repo) parsed every YAML/JSON file under `docker-compose.yml`,
  `k8s/` and `observability/` (30 documents) and checked: `depends_on` targets exist, every built service has a Dockerfile,
  every `${VAR:?}` is in `.env.example`, mounted paths exist, kustomization resources exist, and every `secretKeyRef` key
  is in `k8s/secrets.env.example`. A negative run (a key and a file removed) reported both problems.

To run it for real once Docker is available: `docker compose config -q`, `docker compose up -d --build`, wait for
`docker compose ps` to show every service healthy, then the checks in section 10. For K8s: `kubectl kustomize k8s/` and
`kubectl apply -k k8s/` on kind or minikube.

## 10. Stack-level checks (written 2026-10-14, not yet run: they need Docker)
All go through the gateway. Admin credentials come from the environment or `.env` (`ADMIN_USERNAME`, `ADMIN_PASSWORD`).
Scripts need bash, curl and jq (jq is not installed on the dev machine yet).

| Check | Command | Passes when |
|---|---|---|
| End-to-end | `scripts/e2e.sh` | register → login → candidates → vote (201) → replay (200, same id) → second vote (409) → receipt → verify → chain valid → results via Kafka → admin audit; plus 401/403 role checks |
| Seed voters | `USERS=1000 mvn -f load-tests/pom.xml gatling:test -Dgatling.simulationClass=com.voting.loadtests.SeedVotersSimulation` | all voters registered; tokens in `load-tests/voters.csv` (git-ignored, valid 24 h) |
| Load | `... -Dgatling.simulationClass=com.voting.loadtests.CastVoteSimulation` (`USERS`, `RAMP_SECONDS`) | every vote 201, p95 < 500 ms; new election each run |
| Double vote | `... -Dgatling.simulationClass=com.voting.loadtests.DoubleVoteSimulation` | 100 simultaneous votes by one voter: exactly one 201, rest 409/429, one stored vote |
| Chaos | `scripts/chaos.sh` (`VOTERS`) | candidate-service stopped: cached ACTIVE candidate votes 201, unknown candidate 503, gateway 503 JSON; after restart the circuit closes |

Notes:
- Seeding is paced (`SEED_RATE`, default 4 voters/s) because the gateway allows 10 auth requests/s per IP.
- The gateway rate-limits votes per user (burst 10), so in the double-vote run most requests get 429 and only about ten
  reach voting-service at once. The service-level race is covered by the 50-thread tests above.
- Verified offline: `bash -n scripts/*.sh`; `mvn -f load-tests/pom.xml test-compile`; each simulation starts and fails
  with a clear message when credentials or the voter file are missing; a failed seed leaves `voters.csv` untouched.

Still open: run all of the above on Docker and record the numbers (BLACKBOOK results table), and a Kubernetes run.
