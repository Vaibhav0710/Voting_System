# Testing

_Last updated: 2026-10-10_

## 1. Stack
JUnit 5, Mockito, AssertJ / Hamcrest, Spring Boot Test (`@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`),
spring-security-test, MockMvc, H2 in-memory DB. Planned: Testcontainers (Postgres), JaCoCo coverage, Gatling load tests.

## 2. Running tests
```bash
cd voting-service && mvn test                              # one service
cd voting-service && mvn test -Dtest=VoteServiceTest       # one class
cd voting-service && mvn test -Dtest=VoteServiceTest#*Chain*   # matching methods
```
No Kafka, Redis, Postgres or Eureka is needed: tests use H2, mock Redis-backed beans, set
`spring.kafka.listener.auto-startup=false` / `spring.kafka.admin.auto-create=false` and `eureka.client.enabled=false`.
There is no single root build yet. The root `pom.xml` lists `result-service`, which a fresh clone lacks, so test each service on its own.

## 3. Current results (2026-10-11, `mvn test`)
| Service | Tests | Result |
|---|---|---|
| user-service | 26 | 26 pass (was 15; added admin seeder, users API, role-ignored and 401 tests) |
| candidate-service | 41 | 41 pass (was 1 failure + 5 errors) |
| voting-service | 54 | 54 pass (had no tests before) |
| result-service | 28 | 28 pass (was 1 failure) |
| api-gateway | 0 | none yet (planned M7) |
| eureka-server | 0 | config only |

## 4. Layout and kinds of test
```
<service>/src/test/java/com/voting/<service>/
  controller/   @WebMvcTest + real SecurityConfig + real JWTs (TestTokens) → status codes and role rules
  service/      Mockito unit tests (@Nested per method), plus @SpringBootTest integration tests
  security/     JwtService tests (valid, expired, wrong key, missing claim)
  support/      TestTokens: builds User-Service-shaped JWTs with the test secret
<service>/src/test/resources/application-test.yml
```
Notable tests:
- `VoteConcurrencyIntegrationTest`: 50 threads, same voter → exactly 1 stored; 50 voters → chain valid.
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

## 9. Not yet covered (planned, M10)
- Testcontainers Postgres tests (`@Testcontainers(disabledWithoutDocker = true)`; Docker is not installed on the dev machine).
- End-to-end script through the gateway (register → vote → replay → verify → results → audit).
- Gatling load test (10k concurrent votes, p95 < 500 ms) and a double-vote stress run.
- Chaos test: stop candidate-service mid-load, check fallback and recovery.
- JaCoCo with an 80 % line-coverage gate on voting-service.
- api-gateway filter tests.
