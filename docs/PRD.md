# PRD — Blockchain-Inspired Online Voting System

_Last updated: 2026-10-26_

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
- Event-driven, independently deployable services, each owning its own data (one schema per service).
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
- **Performance target (M10, defined 2026-10-26):** on the dev laptop, through the gateway, one election with the chain
  on: (a) 10,000 votes at a steady rate of 80 % of the highest rate a step test sustains, with p95 < 500 ms and 0 errors;
  (b) 10,000 votes sent at once, which may be rejected (503) but must keep integrity: no double votes, chain valid,
  tally equals stored votes. Not yet measured; plan in `docs/plans/2026-10-26-10k-load-run.md`.
- **Security:** see `SECURITY.md`. No secrets in the repo; 500s never leak internals.
- **Observability (M8):** Prometheus metrics, trace ids in logs, Zipkin tracing, JSON logs and an AUDIT log in every service (done); ELK, Prometheus, Grafana and Zipkin servers come with docker-compose (batch 7).
- **Operability (M9):** one-command `docker compose up` (Dockerfile per service, full compose with health-ordered
  start-up and an `observability` profile) and Kustomize manifests for Kubernetes. Fully run and verified on Docker Desktop and kind v1.28.0 (2026-10-24).

## 7. Success measures
- 0 double votes in concurrency stress tests (met: 50/50 unit test, 100/100 Gatling burst → 1 stored).
- Chain validation reports 0 broken links after concurrent load (met: 1000 votes checked, chainValid: true).
- All service test suites green (254 tests passed across all 6 services).
- Load test meets the M10 performance target in §6 (10k runs not yet done; 1,000-vote run met p95 < 500 ms at that size only: p95 = 163 ms, p99 = 282 ms at 1,000 votes).

## 8. Scope and roadmap
| Milestone | Content | Status |
|---|---|---|
| M1–M5 | Candidate, user, Eureka, gateway, voting, Kafka, result services | done |
| Fix pass | 12 review bugs (JWT parsing, candidate validation, admin self-registration, idempotency, chain race, audit, …) | done |
| M6 | Resilience4j + caching | done & verified (voting, result, candidate, gateway) |
| M7 | Security hardening | done & verified (gateway spoofing, path matching, roles, CORS; limits and @PreAuthorize; AUDIT log in all services) |
| M8 | Observability (Prometheus, ELK, Zipkin, Grafana) | done & verified in Docker (Prometheus scrapes all services, Grafana dashboard, Zipkin distributed traces, Kibana JSON logs) |
| M9 | Dockerfiles, full compose, profiles, K8s | done & verified on Docker Desktop (16 containers healthy) and kind v1.28.0 (12 pods Running, e2e 14/14) |
| M10 | Coverage, Testcontainers, e2e, Gatling, chaos | done except the 10k load run (254 tests, PostgreSQL 16 Testcontainers IT, e2e 14/14, chaos 8/8, Gatling 1,000 votes p95=163ms) |

## 9. Open questions
- Should elections become a first-class entity (open/close times, eligible voters)?
- Should the ballot be unlinkable from the voter (e.g. store a voter commitment instead of `user_id`)?
- Should the gateway validate JWTs locally instead of calling user-service on every request?
