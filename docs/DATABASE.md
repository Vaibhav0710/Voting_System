# Database

_Last updated: 2026-10-10_

## 1. Engines and connection
- **PostgreSQL** (README targets 16; any 14+ works), one database per service on one server. Services never read
  another service's database.
- **Redis 7** for fast paths, caches, tallies and gateway rate limiting (section 5).
- **H2** (in-memory) in tests only. voting-service runs H2 in `MODE=PostgreSQL` so the native `ON CONFLICT` SQL is exercised.

Connection comes only from env vars (no credentials in the repo):

| Var | Meaning |
|---|---|
| `VOTING_DB_URL` | Server base URL, e.g. `jdbc:postgresql://localhost:5432`. Each service appends its DB name |
| `VOTING_DB_USER` / `VOTING_DB_PASSWORD` | DB credentials |

| Service | Database | Default schema |
|---|---|---|
| user-service | `userservice_db` | public |
| candidate-service | `candidateservice_db` | public |
| voting-service | `voting_service_db` | public |
| result-service, api-gateway, eureka | none | — |

Create the three databases once (`CREATE DATABASE userservice_db;` and so on). Hibernate creates the tables.

## 2. Tables

### `users` (userservice_db)
| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | generated (`GenerationType.UUID`); this is the JWT `userId` |
| `username` | varchar(50) | unique, not null |
| `email` | varchar(100) | unique, not null |
| `password` | varchar(255) | BCrypt hash, never returned by the API |
| `role` | varchar(20) | `ROLE_VOTER` or `ROLE_ADMIN` |
| `created_at`, `updated_at` | timestamp | Hibernate-managed |

### `candidates` (candidateservice_db)
| Column | Type | Notes |
|---|---|---|
| `id` | bigint identity PK | internal only |
| `external_id` | UUID | unique, the public candidate id |
| `name` | varchar(255) | not null |
| `party` | varchar(255) | optional |
| `election_id` | UUID | not null; elections have no table of their own |
| `status` | varchar | `ACTIVE`, `DISQUALIFIED`, `WITHDRAWN` (default ACTIVE) |
| `is_deleted` | boolean | soft delete, default false |
| `created_at`, `updated_at` | timestamp | |

"One name per election" is enforced in the service (`existsByNameAndElectionIdAndIsDeletedFalse`), **not** by a DB
constraint. The plan's `UNIQUE(name, election_id)` was never added, because soft-deleted rows would block re-use.

### `votes` (voting_service_db)
| Column | Type | Notes |
|---|---|---|
| `id` | bigint identity PK | internal; insertion order = chain order |
| `external_id` | UUID | unique, the public vote id / receipt id |
| `user_id` | UUID | not null, immutable |
| `candidate_id` | UUID | not null, immutable |
| `election_id` | UUID | not null, immutable |
| `vote_hash` | varchar(64) | SHA-256 hex, immutable |
| `prev_hash` | varchar(64) | previous vote's hash in the election (`GENESIS` for the first); null when chaining is off |
| `idempotency_key` | varchar(64) | client-supplied, immutable |
| `timestamp` | timestamp | microsecond precision; its epoch seconds are part of the hash |
| `status` | varchar(20) | `CAST`, `VERIFIED`, `DISPUTED` |
| `created_at` | timestamp | |

Constraints: **`uq_user_election UNIQUE (user_id, election_id)`** is the one-vote guarantee, and
**`uq_idempotency_key UNIQUE (idempotency_key)`**.

### `election_chain_heads` (voting_service_db), added 2026-10-10
| Column | Type | Notes |
|---|---|---|
| `election_id` | UUID PK | |
| `last_hash` | varchar(64) | hash of the newest vote in the chain |

Locked with `SELECT … FOR UPDATE` while a chained vote is cast. A missing row is created with
`INSERT … ON CONFLICT DO NOTHING`, seeded from the election's newest existing vote (so older chains continue) or `GENESIS`.

## 3. Relations
No foreign keys cross services. `votes.candidate_id` refers to `candidates.external_id` and `votes.user_id` to
`users.id` by convention only; they are checked through APIs at vote time. `election_id` is a shared UUID with no owner table.

## 4. Migrations
- Hibernate `ddl-auto: update` (voting-service: `${JPA_DDL_AUTO:update}`). There is no Flyway/Liquibase yet.
- voting-service `DatabaseMigrationRunner` backfills `votes.status = 'CAST'` where null, from when the column was added.
- Planned (M9): a `prod` profile with `ddl-auto: validate`. Adopting Flyway (`src/main/resources/db/migration/V<n>__<desc>.sql`) is recommended before any production use.

## 5. Redis keys
| Key | Type | Owner | TTL | Purpose |
|---|---|---|---|---|
| `vot:voted:{userId}:{electionId}` | string `"1"` | voting | 24 h | fast "already voted" check |
| `vot:idempotency:{userId}:{key}` | string (vote id) | voting | 24 h | idempotent replay |
| `vot:candidate:{candidateId}` | hash `{electionId, status}` | voting | none | candidate status cache for the fallback (`DELETED` after delete) |
| `res:votes:{electionId}` | hash candidateId → count | result | none | tally |
| `res:total:{electionId}` | string counter | result | none | total votes |
| `res:seen:{electionId}` | set of vote ids | result | none | redelivery de-duplication |
| `candidate::{candidateId}` | JSON (typed) | candidate | 10 min | cached `GET /candidates/{id}` |
| `candidatesByElection::{electionId}`, `activeCandidatesByElection::{electionId}` | JSON list | candidate | 10 min | cached election lists; evicted on every candidate write |
| `request_rate_limiter.{key}.*` | gateway | short | token bucket state |

Redis is never the source of truth. Losing it loses fast paths and live tallies, but no votes.
Planned: a recount endpoint to rebuild `res:*` from voting-service `/votes/election/{id}/count`.

## 6. Seed data
candidate-service `DataSeeder` inserts 10 demo candidates into an **empty** `candidates` table:
election `11111111-1111-1111-1111-111111111111` (5 candidates, one WITHDRAWN, one DISQUALIFIED) and
`22222222-2222-2222-2222-222222222222` (5 candidates, one WITHDRAWN). The admin user comes from `AdminSeeder` (env vars).

## 7. Backup / restore
Not automated yet. Use `pg_dump -Fc <db>` per database and `pg_restore -d <db>`. The `votes` table is the one that matters,
because results can be rebuilt from it.
