# Design System

_Last updated: 2026-10-10_

There is **no UI**. The system is API-only, so its "design system" is the shape of what it outputs: HTTP
responses, errors, events, receipts and logs. Any future web or mobile client should follow section 7.

## 1. API conventions
- Base path `/api/v1/<resource>` (plural nouns: `auth`, `users`, `candidates`, `votes`, `results`).
- JSON only, camelCase fields, UUIDs as strings, timestamps as ISO-8601 local date-time (`2026-10-10T12:00:00.123456`).
- Public ids are UUIDs (`id` / `voteId` / `candidateId`). Database `Long` ids never leave a service.
- Actions that aren't CRUD use verbs under the resource: `POST /votes/verify/{voteId}`, `POST /votes/chain/validate/{electionId}`.
- Pagination: Spring `?page=0&size=20&sort=field,asc`.

## 2. Response envelope
Every JSON response (success or error) uses `ApiResponse`:
```json
{ "success": true, "message": "Vote successfully cast!", "data": { ... }, "timestamp": "2026-10-10T12:00:00" }
```
- `data` is omitted or null on errors, except validation errors (below).
- Pages (voting-service) are serialised as `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`.
- Exception: the SSE endpoint `/results/election/{id}/live` streams `text/event-stream` events
  `initial-results` and `result-update`, whose data is an `ElectionResultResponse`.

## 3. Errors and status codes
| Status | When | Example message |
|---|---|---|
| 400 | validation failed (`data` = field → message) | `"Validation failed"`, `{"candidateId": "Candidate ID is required"}` |
| 400 | malformed JSON / bad UUID | `"Malformed request"` |
| 400 | candidate not valid for the election | `"Candidate is DISQUALIFIED"` |
| 401 | no / invalid / expired token, bad login | `"Authentication required"`, `"Invalid username/email or password"` |
| 403 | authenticated but not allowed | `"Access denied"` |
| 404 | resource not found | `"Vote not found with ID: …"` |
| 409 | duplicate (already voted, name taken) | `"User has already voted in this election."` |
| 429 | gateway rate limit | (empty body) |
| 503 | dependency down and no safe fallback | `"Candidate Service is unavailable and candidate status is unknown. Please retry shortly."` |
| 503 | gateway circuit breaker open, timeout (5 s) or no instance | `"voting-service is temporarily unavailable. Please retry shortly."` |
| 500 | anything unexpected | `"An unexpected error occurred."` (details only in logs) |

Status codes for voting: **201** new vote, **200** idempotent replay (`data.replay = true`).

## 4. Receipts
Vote receipt (`POST /votes`): `voteId`, `voteHash` (64 hex chars), `timestamp`, `message`, `replay`.
Full receipt (`GET /votes/{id}`): adds `candidateId`, `electionId`, `prevHash`, `status`.
A voter can prove their vote is unchanged by calling `POST /votes/verify/{voteId}`: `hashValid`, `storedHash`, `computedHash`.

## 5. Events
Kafka topic names are `<entity>.<past-tense-verb>` (`vote.cast`, `candidate.status-changed`). Payloads are flat JSON
with UUID ids and ISO timestamps. New fields may be added; consumers ignore unknown fields. Never rename or remove a field.
Full list in `ARCHITECTURE.md` §4.

## 6. Logs and metrics
- SLF4J via Lombok `@Slf4j`. Log ids, not personal data. Use `warn` for degraded paths (fallbacks, Redis misses) and
  `error` with the stack trace only for unexpected failures.
- **Format** (`logback-spring.xml`, the same in every service): readable console by default, with
  `[service,traceId,spanId]`. The `json-logs` profile (on in `prod`) prints one JSON object per line:
  `@timestamp, @version, message, logger_name, thread_name, level, level_value, service`, plus structured fields.
  The `logstash` profile also ships the same JSON over TCP to `LOGSTASH_HOST:LOGSTASH_PORT`.
- **Audit:** logger `AUDIT`, message `action=… actor=… target=… outcome=…`; with JSON logs these are separate fields.
  Actions: `USER_REGISTERED`, `LOGIN` (SUCCESS/FAILURE), `CANDIDATE_CREATED|UPDATED|STATUS_CHANGED|DELETED`,
  `VOTE_CAST` (SUCCESS, REPLAY, DUPLICATE, INVALID_CANDIDATE, CANDIDATE_SERVICE_UNAVAILABLE), `VOTE_VERIFIED`
  (VALID/TAMPERED), `CHAIN_VALIDATED` (VALID/BROKEN), `AUDIT_TRAIL_VIEWED`. Actor is `user:<id>`, `anonymous` or a
  username (registration and login). Targets are `user:`, `candidate:`, `vote:` or `election:` ids; a vote's target is
  never its candidate.
- **Metrics:** `/actuator/prometheus` in every service, tagged `application=<service>`. Business metric:
  `votes_cast_total{outcome="success|replay|duplicate|invalid_candidate|candidate_service_unavailable"}`
  (voting-service). Gateway and Resilience4j metrics come from their own Micrometer integrations.

## 7. Direction for a future UI
Not started. If one is built: show the receipt hash prominently with a copy button and a "verify my vote" action;
show live results from the SSE stream; never show who voted for whom; and follow WCAG 2.1 AA (contrast, keyboard use).
