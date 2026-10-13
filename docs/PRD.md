# PRD — Blockchain-Inspired Online Voting System

_Last updated: 2026-10-10_

## 1. Problem
Online elections must guarantee three things at once: each eligible voter votes **at most once**, nobody can
**silently change** a recorded vote, and results are **available quickly**, even under heavy, bursty load.
Simple CRUD voting apps fail on double-voting races, offer no tamper evidence and recount from the database on every view.

## 2. Users
| User | Needs |
|---|---|
| **Voter** (`ROLE_VOTER`) | Register, log in, see candidates, cast one vote per election, get a receipt, check their vote was recorded unchanged |
| **Election admin** (`ROLE_ADMIN`) | Register and manage candidates, change candidate status, view all votes, validate the hash chain, see the audit trail |
| **Public / observers** | See live and final results without logging in |
| **Owner (Vaibhav)** | A production-style Java microservices project to demonstrate backend skills |

## 3. Goals and non-goals
**Goals**
- Strict one-vote-per-user-per-election, safe under concurrent requests and client retries.
- Tamper evidence: per-vote SHA-256 hash, optional per-election hash chain, verification APIs.
- Event-driven, independently deployable services with their own databases.
- Real-time results without querying the voting database for every view.
- Keep accepting votes when a dependency (Candidate Service, Redis) has a short outage, where that is safe.

**Non-goals (for now)**
- Voter identity proofing (KYC) or electoral-roll eligibility checks.
- Secret ballot with cryptographic unlinkability: the vote row stores `user_id` and `candidate_id`.
- A front-end UI. The system is API-only.
- An election entity with start and end times: elections are identified only by a UUID on candidates and votes.

## 4. Use cases
1. Voter registers (always as VOTER) and logs in to get a JWT.
2. Anyone lists candidates of an election (all, or only active ones).
3. Admin creates candidates (one or in bulk), edits them, changes status (ACTIVE / DISQUALIFIED / WITHDRAWN) or soft-deletes them.
4. Voter casts a vote with an idempotency key and gets a receipt (vote id, hash, timestamp). A retry with the same key returns the same receipt.
5. Voter fetches their receipt or verifies their vote's hash. Admin can do this for any vote.
6. Admin validates an election's whole hash chain and reads the audit trail.
7. Anyone reads results, a summary with percentages and winner, or subscribes to live results (SSE).

## 5. Functional requirements
| # | Requirement | Status |
|---|---|---|
| F1 | Register with username, email, password; public registration always creates `ROLE_VOTER` | built |
| F2 | Login returns an HS256 JWT (subject = username, `userId`, `role`) | built |
| F3 | Admin account bootstrapped from `ADMIN_USERNAME`/`ADMIN_EMAIL`/`ADMIN_PASSWORD` | built |
| F4 | `GET /users/{id}` (self or admin), `GET /users/{id}/role` | built |
| F5 | Candidate CRUD, bulk create, status change, soft delete; unique (name, election) | built |
| F6 | Candidate validation endpoint: exists, same election, ACTIVE | built |
| F7 | Cast vote: reject duplicates (409), invalid candidates (400); idempotent replay (200) | built |
| F8 | SHA-256 hash per vote; per-election chain when `voting.chain.enabled=true` (default) | built |
| F9 | Verify one vote (owner or admin); validate an election chain (admin) | built |
| F10 | Vote counts per candidate (public) | built |
| F11 | `vote.cast` / `vote.verified` / `candidate.*` Kafka events | built |
| F12 | Results, summary, live SSE (public); audit trail (admin) | built |
| F13 | Gateway: routing, JWT validation, spoof-proof per-user rate limiting, edge role checks, circuit breakers, CORS | built |
| F14 | Candidate search endpoint (`/candidates/search`) from the original plan | not built |

## 6. Non-functional requirements
- **Integrity:** the DB unique constraints `(user_id, election_id)` and `idempotency_key` are the final guarantee; Redis is only a fast path.
- **Concurrency:** 50 simultaneous votes by one voter must store exactly one vote, and concurrent votes must form one unbroken chain (tested).
- **Availability:** retry, circuit breaker and bulkhead on Candidate Service calls; a cached status lets voting continue for known ACTIVE candidates.
- **Performance target (M10):** 10,000 concurrent votes with p95 < 500 ms through the gateway (not yet measured).
- **Security:** see `SECURITY.md`. No secrets in the repo; 500s never leak internals.
- **Observability (M8):** Prometheus metrics, trace ids in logs, Zipkin tracing, JSON logs and an AUDIT log in every service (done); ELK, Prometheus, Grafana and Zipkin servers come with docker-compose (batch 7).
- **Operability (M9):** one-command `docker compose up` (Dockerfile per service, full compose with health-ordered
  start-up and an `observability` profile) and Kustomize manifests for Kubernetes. Written and checked offline; not yet run
  (no Docker or kubectl on the dev machine).

## 7. Success measures
- 0 double votes in the concurrency stress test (met: 50/50 concurrent attempts → 1 stored).
- Chain validation reports 0 broken links after concurrent load (met in the H2 integration test).
- All service test suites green (user 26/26, candidate 58/58, voting 54/54, result 28/28 as of 2026-10-11).
- Load test meets the p95 target (pending M10).

## 8. Scope and roadmap
| Milestone | Content | Status |
|---|---|---|
| M1–M5 | Candidate, user, Eureka, gateway, voting, Kafka, result services | done |
| Fix pass | 12 review bugs (JWT parsing, candidate validation, admin self-registration, idempotency, chain race, audit, …) | done except user-service tests |
| M6 | Resilience4j + caching | done (voting, result, candidate, gateway) |
| M7 | Security hardening | done (gateway spoofing, path matching, roles, CORS; limits and @PreAuthorize; AUDIT log in all services) |
| M8 | Observability (Prometheus, ELK, Zipkin, Grafana) | done in code (metrics, tracing, JSON logs, audit, probes; servers, scrape config and a Grafana dashboard in compose); not yet run end to end |
| M9 | Dockerfiles, full compose, profiles, K8s | written and validated offline (images build path, YAML, references); not yet run |
| M10 | Coverage, Testcontainers, e2e, Gatling, chaos | partly done (concurrency tests) |

## 9. Open questions
- Should elections become a first-class entity (open/close times, eligible voters)?
- Should the ballot be unlinkable from the voter (e.g. store a voter commitment instead of `user_id`)?
- Should the gateway validate JWTs locally instead of calling user-service on every request?
