# Security

_Last updated: 2026-10-10_

## 1. Secrets
| Secret | Where it lives | Used by |
|---|---|---|
| `JWT_SECRET` | env var, Base64, ≥ 256 bits (`openssl rand -base64 32`) | user (signs), candidate, voting, result (verify) |
| `VOTING_DB_USER` / `VOTING_DB_PASSWORD` | env vars | user, candidate, voting |
| `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | env vars | user-service bootstrap admin |

- `.gitignore` excludes `.env`, `*.env.local`, `application-local.yml`.
- Test YAML files hold **test-only** JWT secrets. Never reuse them anywhere real.
- Never log: passwords, tokens, `Authorization` headers, secrets, or the candidate a voter chose.

## 2. Authentication
- user-service issues **HS256 JWTs**: subject = username, `userId` (UUID), `role`, `iat`, `exp` (`JWT_EXPIRATION` ms; 24 h in examples).
- Passwords: BCrypt (cost 10). Login failures always return the same message ("Invalid username/email or password").
- The gateway calls `GET /api/v1/auth/validate` for secured routes, and **every service also verifies the token itself**
  (signature + expiry) with the shared secret, so a service reached directly is still protected.
- A missing or invalid token → **401** JSON. An authenticated caller without permission → **403** JSON.

## 3. Authorization
| Endpoint | Rule |
|---|---|
| `POST /api/v1/auth/register`, `/login` | public. Registration always creates `ROLE_VOTER` |
| `GET /api/v1/users/{id}` | ADMIN or the user themself |
| `GET /api/v1/users/{id}/role` | any authenticated user |
| `GET /api/v1/candidates/**` | public |
| `POST/PUT/PATCH/DELETE /api/v1/candidates/**` | `ROLE_ADMIN` |
| `POST /api/v1/votes` | `ROLE_VOTER` |
| `GET /api/v1/votes/{id}`, `POST /votes/verify/{id}` | owner of the vote or ADMIN |
| `GET /api/v1/votes/user/{userId}/election/{electionId}` | that user or ADMIN |
| `GET /api/v1/votes/election/{id}`, `POST /votes/chain/validate/{id}` | `ROLE_ADMIN` |
| `GET /api/v1/votes/election/{id}/count` | public |
| `GET /api/v1/results/election/{id}`, `/summary`, `/live` | public |
| `GET /api/v1/results/election/{id}/audit` | `ROLE_ADMIN` (the caller's JWT is forwarded to voting-service) |

**Admins** exist only through `AdminSeeder` (env vars at startup). There is no API to grant ADMIN.

## 4. Vote integrity
- One vote per user per election: DB unique `(user_id, election_id)`; Redis flag as a fast path; per-user idempotency keys.
- Tamper evidence: SHA-256 per vote plus a per-election hash chain (row-locked, so it cannot fork).
  `verify` recomputes one vote's hash; `chain/validate` walks the whole election.
- Votes for disqualified, withdrawn, deleted, unknown or other-election candidates are rejected. When candidate-service is down,
  only candidates cached as ACTIVE in that election are accepted; otherwise 503.
- Limit: the hash is not keyed and the chain lives in the same DB, so someone with DB write access could recompute it.
  Anchoring chain heads externally is future scope.

## 5. Sensitive data
- `users.email` and password hashes (never returned; `UserResponseDTO` has no password).
- `votes` links `user_id` to `candidate_id`: the ballot is **not secret** from DB admins. Receipts are only shown to the owner or an ADMIN.
- Audit logs must record who and what (vote id, election), never the candidate choice.

## 6. Network exposure
| Port | Service | Should be public? |
|---|---|---|
| 8080 | api-gateway | yes (the only public port) |
| 8081–8084 | services | no, internal network only |
| 8761 | Eureka dashboard | no |
| 5432 / 6379 / 9092 / 2181 | Postgres / Redis / Kafka / Zookeeper | no |

Actuator: only `health`, `info`, `prometheus` are exposed (unauthenticated), so keep them internal. Swagger UI is enabled
(planned: off in the `prod` profile). Kafka and Redis run without auth or TLS: local and dev only.

## 7. Threats and mitigations
| Threat | Mitigation | Status |
|---|---|---|
| Double voting via races/retries | DB unique + idempotency + Redis flag; 50-thread test | done |
| Self-granting admin | role removed from registration; env bootstrap | done |
| Forged/expired JWT | signature + expiry checked in every service | done |
| Malformed subject crashed auth (all votes 403) | `userId` claim used | fixed 2026-10-10 |
| Information leak in 500s | generic message, details only in logs | done in voting/result; user already OK |
| Kafka redelivery inflating results | Lua de-dup by vote id | done |
| Poison Kafka messages | `ErrorHandlingDeserializer` | done (voting, result) |
| Client spoofing `loggedInUser`/`role` headers to dodge rate limits | strip inbound headers at the gateway | **planned (M7)** |
| Gateway open-path match uses `contains` | prefix matching | **planned (M7)** |
| Brute-force login | gateway rate limit (10/s, burst 20 on auth routes) | partial |
| CORS | gateway `globalcors` with allowed origins from env | **planned (M7)** |

## 8. Hard rules
- Never commit secrets or `.env` files. Never put real secrets in test configs.
- Never add a role field to public registration.
- Never trust gateway-added headers inside a service; read identity from the verified JWT.
- Never echo exception messages to clients.
- Never log a voter's candidate choice.

## 9. Leaked secret procedure
1. Generate a new `JWT_SECRET` and set it in **every** service together. All existing tokens become invalid, which is intended.
2. Rotate DB passwords (`ALTER ROLE … PASSWORD …`) and update the env vars.
3. If committed: remove from history (`git filter-repo`) in the affected service repo and force-push (owner approval needed).
4. Add a BLACKBOOK log entry.
