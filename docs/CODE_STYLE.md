# Code Style

_Last updated: 2026-10-10. Conventions as used in the six services; follow the surrounding code._

## Java (17)
**Formatting**
- 4-space indent, braces on the same line, ~120-character lines. Some older test classes use 8-space indent; leave them, but use 4 in new code.
- Explicit imports in new code (no `*` wildcards); static imports allowed in tests.
- One top-level class per file. Small value types are `record`s (`JwtPrincipal`, `CandidateEvent`, `PageResponse`).

**Naming**
- Packages: `com.voting.<service>.{config, controller, dto, exception, mapper, model, model.enums, repository, security, service, client, event}`.
- Classes: `XController`, `XService` / `XServiceImpl` (interface + impl where present), `XRepository`, `XMapper`,
  `XRequest` / `XResponse` / `XDTO` for DTOs, `XException` for domain errors, `XClient` for Feign.
- Methods are verbs (`castVote`, `ensureCandidateValid`, `recordVote`). Booleans start with `is`/`has` (`hasVoted`, `isAdmin`).
- Constants are `UPPER_SNAKE_CASE`; Redis key prefixes come from config, not literals in many places.

**Spring patterns to use**
- Constructor injection via Lombok `@RequiredArgsConstructor` (explicit constructor when `@Value`s are needed). No field `@Autowired` in main code.
- Typed settings as `@ConfigurationProperties` records (e.g. `VotingProperties`).
- Controllers are thin: validate with `@Valid`, authorise with `@PreAuthorize`, delegate to a service, wrap the result in `ApiResponse`.
- Services own transactions (`@Transactional`, `readOnly = true` for reads). Keep methods small and give private helpers clear names.
- Entities never leave the service layer; map with a `XMapper` component.
- Side effects that must follow a commit use `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- Remote calls go through one component per dependency (`CandidateValidator`, `CandidateDirectory`) carrying the
  Resilience4j annotations. Put the fallback on the outermost `@Retry`.
- `@EnableFeignClients` lives in a `FeignConfig` class, not on the application class (keeps `@WebMvcTest` slices light).

**Patterns to avoid**
- Catching `Exception` broadly, except in the global handler or for a deliberate degrade (then log a warning).
- Echoing `ex.getMessage()` to clients from the generic 500 handler.
- `hasAuthority('ADMIN')`: roles carry the `ROLE_` prefix, so use `hasRole('ADMIN')`.
- Business logic in controllers; `RuntimeException("…")` instead of a named exception; magic strings repeated across classes.
- Dead code, commented-out code, TODO stubs and placeholder tests.

**Error handling**
- Throw a named domain exception (`DuplicateVoteException`, `InvalidCandidateException`, …) and map it to a status
  in the service's `GlobalExceptionHandler`. Security exceptions are rethrown so Spring Security answers 401/403.
- Degrade on non-critical infrastructure (Redis reads → treat as a miss; candidate names → "Unknown Candidate"), never on integrity checks.

**Logging**
- `@Slf4j`; placeholders (`log.info("… {}", id)`), never string concatenation.
- `info` for business events, `warn` for degraded paths, `error` + exception for unexpected failures.
- Never log secrets, tokens, passwords or a voter's candidate choice.
- Security-relevant events go through the service's `AuditLogger` (`AUDIT` logger), never plain `log.info`:
  `record(action, actor, target, outcome)` or `recordForCurrentUser(action, target, outcome)`. Actions are `UPPER_SNAKE_CASE`.

**Comments**
- Javadoc on every class (its responsibility), and on public methods when the *why* isn't obvious. No narration of what the code plainly does.

**Dependencies**
- Versions come from the Spring Boot parent and the Spring Cloud BOM. Pin extra libraries in `<properties>`
  (`resilience4j.version` 2.2.0, JJWT 0.12.6, springdoc 2.5.0). Add nothing without a clear need.

## YAML (application config)
- Environment-specific values as `${ENV_VAR:default}`. Secrets get **no default**.
- Test config in `src/test/resources/application-test.yml`, activated with `@ActiveProfiles("test")`. Never name it
  `application.yml`: that replaces the real config on the test classpath.

## Commits (when the owner commits)
Conventional Commits: `feat: …`, `fix: …`, `docs: …`, `test: …`, `chore: …`, imperative mood, one service per commit (each is its own repo).
