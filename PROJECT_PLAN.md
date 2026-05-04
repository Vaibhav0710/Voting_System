# 🗳️ Blockchain-Inspired Online Voting System — Master Project Plan

> **Goal:** Build a production-grade, tamper-resistant, scalable online voting platform using microservices architecture.  
> **Author:** Vaibhav  
> **Started:** April 2026  
> **Status:** 🟡 In Progress

---

## 📌 Vision Statement

A voting platform where every vote is **hashed**, optionally **chained** (blockchain-inspired), and processed through an **event-driven microservices architecture** designed to handle **millions of concurrent users** with zero double-voting and tamper-proof audit trails.

---

## 🏗️ High-Level Architecture

```
                              ┌─────────────────────────────────┐
                              │          CLIENTS                │
                              │   (Web App / Mobile / Admin)    │
                              └──────────────┬──────────────────┘
                                             │ HTTPS
                              ┌──────────────▼──────────────────┐
                              │        API GATEWAY              │
                              │   (Spring Cloud Gateway)        │
                              │  • Route matching               │
                              │  • Rate limiting                │
                              │  • JWT validation               │
                              │  • Load balancing               │
                              └──────────────┬──────────────────┘
                                             │
                    ┌──────────┬─────────────┼─────────────┬──────────┐
                    │          │             │             │          │
           ┌────────▼──┐  ┌───▼──────┐  ┌───▼──────┐  ┌──▼───────┐  │
           │   USER    │  │CANDIDATE │  │  VOTING  │  │  RESULT  │  │
           │  SERVICE  │  │ SERVICE  │  │ SERVICE  │  │ SERVICE  │  │
           │           │  │          │  │          │  │          │  │
           │ • Auth    │  │ • CRUD   │  │ • Cast   │  │ • Tally  │  │
           │ • JWT     │  │ • Status │  │ • Hash   │  │ • Live   │  │
           │ • Roles   │  │ • Bulk   │  │ • Chain  │  │ • Audit  │  │
           └─────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  │
                 │             │             │             │          │
           ┌─────▼─────┐ ┌────▼─────┐ ┌─────▼─────┐      │          │
           │PostgreSQL │ │PostgreSQL│ │PostgreSQL │  (No DB)         │
           │user_db    │ │candidate │ │voting_db  │  Reads from      │
           │           │ │_db       │ │           │  Voting Service   │
           └───────────┘ └──────────┘ └───────────┘                  │
                                                                     │
                              ┌──────────────────────────────────────┘
                              │
                   ┌──────────▼──────────┐
                   │   EUREKA SERVER     │
                   │ (Service Discovery) │
                   │ All services        │
                   │ register here       │
                   └─────────────────────┘


          ═══════════════════ EVENT BUS ═══════════════════
          ║                 APACHE KAFKA                  ║
          ║  Topics:                                      ║
          ║  • vote.cast        → Result Service          ║
          ║  • vote.verified    → Audit / Notification    ║
          ║  • candidate.created → Voting Service cache   ║
          ║  • candidate.status-changed → Voting Service  ║
          ║  • user.registered  → Audit                   ║
          ═════════════════════════════════════════════════

          ┌───────────┐  ┌───────────┐  ┌───────────────┐
          │   REDIS   │  │ ELK Stack │  │  Prometheus   │
          │           │  │           │  │  + Grafana    │
          │ • Cache   │  │ • Logs    │  │  • Metrics    │
          │ • Idempt. │  │ • Search  │  │  • Alerts     │
          │ • Locks   │  │ • Kibana  │  │  • Dashboard  │
          └───────────┘  └───────────┘  └───────────────┘
```

---

## 🛠️ Tech Stack (Complete)

| Category | Technology | Purpose |
|----------|-----------|---------|
| **Language** | Java 17 | Primary backend language |
| **Framework** | Spring Boot 3.3.x | Microservices framework |
| **Security** | Spring Security + JWT | Authentication & authorization |
| **Database** | PostgreSQL (per service) | Persistent storage |
| **ORM** | Spring Data JPA / Hibernate | Data access layer |
| **Service Discovery** | Netflix Eureka | Service registry & discovery |
| **API Gateway** | Spring Cloud Gateway | Routing, rate limiting, auth |
| **Inter-Service (Sync)** | OpenFeign | REST-based service calls |
| **Inter-Service (Async)** | Apache Kafka | Event-driven communication |
| **Caching** | Redis | Response caching, idempotency keys, distributed locks |
| **Resilience** | Resilience4j | Circuit breaker, retry, rate limiter |
| **Logging** | ELK Stack (Elasticsearch, Logstash, Kibana) | Centralized log aggregation |
| **Monitoring** | Prometheus + Grafana | Metrics, dashboards, alerting |
| **Build** | Maven | Dependency & build management |
| **Containerization** | Docker + Docker Compose | Local development & deployment |

---

## 🧩 Service Breakdown

### Service 1: User Service ⏳ NOT STARTED

| Attribute | Detail |
|-----------|--------|
| **Responsibility** | User registration, login, JWT token generation, role management |
| **Database** | `user_service_db` (PostgreSQL) |
| **Port** | `8081` |
| **Key Entities** | `User`, `Role` |
| **Auth** | Issues JWT tokens (consumed by all other services) |
| **Status** | ⏳ Not Started |

**API Contract:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/auth/register` | Register new user |
| `POST` | `/api/v1/auth/login` | Login, returns JWT |
| `GET` | `/api/v1/auth/validate` | Validate JWT token (used by Gateway) |
| `GET` | `/api/v1/users/{id}` | Get user profile |
| `GET` | `/api/v1/users/{id}/role` | Get user role (VOTER / ADMIN) |

---

### Service 2: Candidate Service ⏳ IN PROGRESS

| Attribute | Detail |
|-----------|--------|
| **Responsibility** | Candidate registration, status management, election-scoped queries |
| **Database** | `candidateservice_db` (PostgreSQL) |
| **Port** | `8082` |
| **Key Entities** | `Candidate` (with `CandidateStatus` enum) |
| **Consumers** | Voting Service (validate), Result Service (display) |
| **Status** | ⏳ In Progress — [See detailed plan](candidate-service/IMPLEMENTATION_PLAN.md) |

**API Contract:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/candidates` | Register candidate |
| `GET` | `/api/v1/candidates/{id}` | Get by ID |
| `GET` | `/api/v1/candidates` | List all (paginated) |
| `PUT` | `/api/v1/candidates/{id}` | Update candidate |
| `DELETE` | `/api/v1/candidates/{id}` | Soft-delete |
| `GET` | `/api/v1/candidates/election/{electionId}` | Candidates for election |
| `GET` | `/api/v1/candidates/{id}/exists` | Existence check (Feign) |
| `GET` | `/api/v1/candidates/{id}/validate` | Validate for election (Feign) |
| `POST` | `/api/v1/candidates/bulk` | Bulk register |
| `GET` | `/api/v1/candidates/search` | Search/filter |
| `PATCH` | `/api/v1/candidates/{id}/status` | Change status |
| `GET` | `/api/v1/candidates/election/{electionId}/active` | Active candidates only |

**Database Schema:**
```sql
candidates (id UUID PK, name, party, election_id, status, created_at, updated_at, is_deleted)
UNIQUE(name, election_id)
```

**Kafka Events Published:** `candidate.created`, `candidate.status-changed`, `candidate.deleted`

---

### Service 3: Voting Service ⏳ NOT STARTED

> **CRITICAL SERVICE** — This is the heart of the system.

| Attribute | Detail |
|-----------|--------|
| **Responsibility** | Vote casting, hash generation, chain linking, double-vote prevention |
| **Database** | `voting_service_db` (PostgreSQL) |
| **Port** | `8083` |
| **Key Entities** | `Vote` (with hash, prevHash, chain fields) |
| **Dependencies** | User Service (validate voter), Candidate Service (validate candidate) |
| **Status** | ⏳ Not Started |

**API Contract:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/votes` | Cast a vote |
| `GET` | `/api/v1/votes/{id}` | Get vote receipt |
| `GET` | `/api/v1/votes/user/{userId}/election/{electionId}` | Check if user already voted |
| `GET` | `/api/v1/votes/election/{electionId}` | Get all votes for election (ADMIN) |
| `GET` | `/api/v1/votes/election/{electionId}/count` | Vote count by candidate |
| `POST` | `/api/v1/votes/verify/{voteId}` | Verify vote hash integrity |
| `POST` | `/api/v1/votes/chain/validate/{electionId}` | Validate entire vote chain |

**Database Schema:**
```sql
votes (
    id              UUID PK,
    user_id         UUID NOT NULL,
    candidate_id    UUID NOT NULL,
    election_id     UUID NOT NULL,
    vote_hash       VARCHAR(64) NOT NULL,     -- SHA-256 hash of vote data
    prev_hash       VARCHAR(64),              -- Previous vote's hash (chain)
    timestamp       TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(64) UNIQUE,       -- Prevent duplicate submissions
    
    CONSTRAINT uq_user_election UNIQUE (user_id, election_id)  -- ONE VOTE ONLY
)
```

**Core Logic — Double Voting Prevention (Multi-Layer):**
```
Layer 1: Redis SET check (user_id:election_id) → fastest, first line of defense
Layer 2: DB UNIQUE constraint (user_id, election_id) → absolute guarantee
Layer 3: Idempotency key in Redis → prevent duplicate network submissions
```

**Core Logic — Vote Hashing:**
```
vote_hash = SHA-256(userId + candidateId + electionId + timestamp + prevHash)
```

**Kafka Events Published:** `vote.cast`, `vote.verified`  
**Kafka Events Consumed:** `candidate.status-changed` (reject votes for disqualified candidates)

**OpenFeign Calls:**
- → **User Service:** `GET /api/v1/users/{id}/role` (verify VOTER role)
- → **Candidate Service:** `GET /api/v1/candidates/{id}/validate` (verify candidate + election)

---

### Service 4: Result Service ⏳ NOT STARTED

| Attribute | Detail |
|-----------|--------|
| **Responsibility** | Real-time vote aggregation, live results, audit reporting |
| **Database** | **NONE** — reads from Voting Service or Kafka events |
| **Port** | `8084` |
| **Key Design** | Stateless aggregation service, consumes Kafka events |
| **Dependencies** | Voting Service (vote data), Candidate Service (candidate names) |
| **Status** | ⏳ Not Started |

**API Contract:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/results/election/{electionId}` | Get election results |
| `GET` | `/api/v1/results/election/{electionId}/live` | Live results (SSE/WebSocket) |
| `GET` | `/api/v1/results/election/{electionId}/summary` | Summary with percentages |
| `GET` | `/api/v1/results/election/{electionId}/audit` | Full audit trail |

**Design Decision — No DB:**
- Consumes `vote.cast` Kafka events
- Maintains in-memory counters (backed by Redis for persistence)
- Enriches with candidate names via Feign call to Candidate Service
- Optionally can query Voting Service directly for recount

**Kafka Events Consumed:** `vote.cast` (increment counters in real-time)

---

### Service 5: API Gateway ⏳ NOT STARTED

| Attribute | Detail |
|-----------|--------|
| **Responsibility** | Single entry point, routing, rate limiting, JWT validation |
| **Database** | None |
| **Port** | `8080` (public-facing) |
| **Technology** | Spring Cloud Gateway |
| **Status** | ⏳ Not Started |

**Route Configuration:**
| Route Pattern | Service | Auth Required |
|--------------|---------|---------------|
| `/api/v1/auth/**` | User Service (8081) | ❌ No |
| `/api/v1/candidates/**` | Candidate Service (8082) | Partial (GET = public, POST/PUT/DELETE = ADMIN) |
| `/api/v1/votes/**` | Voting Service (8083) | ✅ Yes (VOTER role) |
| `/api/v1/results/**` | Result Service (8084) | ❌ No |

**Gateway Features:**
- JWT validation filter (calls User Service `/validate`)
- Rate limiting (Redis-based, per-user token bucket)
- Circuit breaker per route (Resilience4j)
- Request/response logging
- CORS handling (centralized)

---

### Service 6: Eureka Server ⏳ NOT STARTED

| Attribute | Detail |
|-----------|--------|
| **Responsibility** | Service registry and discovery |
| **Port** | `8761` |
| **Technology** | Spring Cloud Netflix Eureka Server |
| **Status** | ⏳ Not Started |

**Simple Setup:**
- `@EnableEurekaServer` on main class
- All other services register as Eureka clients
- Dashboard at `http://localhost:8761`

---

## 🔗 Service Interaction Map

```
┌──────────────────────────────────────────────────────────────────┐
│                    SYNCHRONOUS (OpenFeign)                        │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Voting Service ──► User Service                                 │
│                     (validate JWT, check VOTER role)             │
│                                                                  │
│  Voting Service ──► Candidate Service                            │
│                     (validate candidate exists + active +        │
│                      belongs to election)                        │
│                                                                  │
│  Result Service ──► Candidate Service                            │
│                     (fetch candidate names/parties for display)  │
│                                                                  │
│  Result Service ──► Voting Service                               │
│                     (fetch vote counts for recount/audit)        │
│                                                                  │
│  API Gateway    ──► User Service                                 │
│                     (JWT token validation)                       │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                   ASYNCHRONOUS (Apache Kafka)                    │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Candidate Service ──publish──► candidate.created                │
│                                 candidate.status-changed         │
│                                 candidate.deleted                │
│                                        │                         │
│                                        ▼                         │
│                                 Voting Service (consumer)        │
│                                 (update local cache,             │
│                                  reject disqualified)            │
│                                                                  │
│  Voting Service ────publish──► vote.cast                         │
│                                vote.verified                     │
│                                        │                         │
│                                        ▼                         │
│                                 Result Service (consumer)        │
│                                 (real-time tally updates)        │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 🔐 Security Architecture

```
Client (JWT in header)
    │
    ▼
API Gateway ──► User Service: /api/v1/auth/validate
    │                    │
    │               Valid? ─── No ──► 401 Unauthorized
    │                    │
    │                   Yes
    │                    │
    ▼                    ▼
Forward to target service with decoded claims (userId, role)
    │
    ▼
Target Service checks @PreAuthorize("hasRole('ADMIN')") or @PreAuthorize("hasRole('VOTER')")
```

| Endpoint Pattern | Required Role | Enforcement Point |
|-----------------|---------------|-------------------|
| `POST/PUT/DELETE /candidates/**` | `ADMIN` | Gateway filter + Controller `@PreAuthorize` |
| `POST /votes` | `VOTER` | Gateway filter + Controller `@PreAuthorize` |
| `GET /candidates/**` | None (public) | No filter |
| `GET /results/**` | None (public) | No filter |
| `POST /auth/**` | None (public) | No filter |

---

## 🗳️ Critical Flow: Casting a Vote

This is the most important flow in the system. Here's the complete data path:

```
Step 1: Client sends POST /api/v1/votes
        Body: { candidateId, electionId, idempotencyKey }
        Header: Authorization: Bearer <JWT>
                        │
Step 2: API Gateway      │
        ├─ Validate JWT via User Service
        ├─ Extract userId, role from token
        ├─ Check rate limit (Redis: token bucket)
        ├─ If VOTER role → forward to Voting Service
        └─ If not VOTER → 403 Forbidden
                        │
Step 3: Voting Service receives request
        │
        ├─ [Layer 1] Redis CHECK: "voted:{userId}:{electionId}"
        │   └─ EXISTS? → 409 "Already voted"
        │
        ├─ [Layer 2] Feign → Candidate Service: GET /{candidateId}/validate?electionId=X
        │   └─ Not found / inactive? → 400 "Invalid candidate"
        │
        ├─ [Layer 3] Redis CHECK: "idempotency:{idempotencyKey}"
        │   └─ EXISTS? → 200 (return cached response, idempotent)
        │
        ├─ [Layer 4] Build Vote entity
        │   ├─ Fetch last vote hash (prevHash) for this election
        │   ├─ Compute: hash = SHA-256(userId + candidateId + electionId + timestamp + prevHash)
        │   └─ Set idempotencyKey
        │
        ├─ [Layer 5] DB INSERT with UNIQUE(userId, electionId)
        │   └─ Constraint violation? → 409 "Already voted" (race condition safety net)
        │
        ├─ [Layer 6] Redis SET: "voted:{userId}:{electionId}" (TTL = election duration)
        │   └─ Redis SET: "idempotency:{idempotencyKey}" = response (TTL = 24h)
        │
        └─ [Layer 7] Kafka PUBLISH: "vote.cast" event
            │
            ▼
Step 4: Result Service (Kafka consumer)
        ├─ Increment in-memory counter for candidateId
        ├─ Update Redis: "results:{electionId}" hash
        └─ Push to live results (SSE/WebSocket)

Step 5: Client receives vote receipt
        { voteId, hash, timestamp, receipt }
```

---

## 🔗 Blockchain-Inspired Hashing — Design Decision

### What We Implement
| Feature | Included | Rationale |
|---------|----------|-----------|
| **Vote Hashing** | ✅ Yes | Tamper detection — if any vote data is modified, hash won't match |
| **Vote Chaining** (prevHash → currentHash) | ⚠️ Optional | Adds ordering guarantee but creates serialization bottleneck |
| **Tamper Detection API** | ✅ Yes | `POST /votes/verify/{id}` and `POST /votes/chain/validate/{electionId}` |

### Trade-off: Chaining vs No Chaining
```
WITH CHAINING:
  ✅ Any tampering breaks the chain (detectable)
  ✅ Proves vote ordering
  ❌ Votes become serialized (one at a time for prevHash)
  ❌ Massive bottleneck at scale (10M users = sequential writes)
  
WITHOUT CHAINING (independent hashes):
  ✅ Fully parallelizable writes
  ✅ Individual tamper detection still works
  ❌ Cannot prove ordering
  ❌ Attacker could delete a vote without breaking other hashes

RECOMMENDATION: Start WITHOUT chaining for performance.
Add optional per-election chaining for small elections if needed.
```

---

## 🗄️ Database Strategy

### Isolation: One DB Per Service (Strict)
```
PostgreSQL Instance
├── user_service_db        ← User Service ONLY
├── candidateservice_db   ← Candidate Service ONLY
└── voting_service_db      ← Voting Service ONLY

Result Service → No database (reads via Kafka + Feign)
```

### Why Separate Databases?
- **Independent scaling** — Voting DB will be under heavy write load; Candidate DB is mostly reads
- **Independent schema evolution** — Services can migrate their own schema without coordination
- **Fault isolation** — One DB going down doesn't take everything offline
- **True microservice boundary** — No shared tables, no accidental coupling

---

## 📦 GitHub Repository Structure (Multi-Repo)

Each service is its own GitHub repository:

```
github.com/<your-username>/
├── user-service/              ← ✅ DONE
│   ├── src/
│   ├── pom.xml
│   ├── README.md
│   ├── IMPLEMENTATION_PLAN.md
│   └── Dockerfile
│
├── candidate-service/         ← ⏳ IN PROGRESS
│   ├── src/
│   ├── pom.xml
│   ├── README.md
│   ├── IMPLEMENTATION_PLAN.md
│   └── Dockerfile
│
├── voting-service/            ← ⏳ NOT STARTED
│   ├── src/
│   ├── pom.xml
│   ├── README.md
│   ├── IMPLEMENTATION_PLAN.md
│   └── Dockerfile
│
├── result-service/            ← ⏳ NOT STARTED
│   ├── src/
│   ├── pom.xml
│   ├── README.md
│   ├── IMPLEMENTATION_PLAN.md
│   └── Dockerfile
│
├── api-gateway/               ← ⏳ NOT STARTED
│   ├── src/
│   ├── pom.xml
│   ├── README.md
│   └── Dockerfile
│
├── eureka-server/             ← ⏳ NOT STARTED
│   ├── src/
│   ├── pom.xml
│   ├── README.md
│   └── Dockerfile
│
└── voting-system-infra/       ← DevOps / Infrastructure repo
    ├── docker-compose.yml     (runs ALL services locally)
    ├── PROJECT_PLAN.md        (this file — master reference)
    ├── ai-master-prompt.md    (original prompt)
    ├── k8s/                   (Kubernetes manifests — future)
    ├── kafka/                 (topic definitions)
    └── monitoring/            (Prometheus, Grafana configs)
```

### Why Multi-Repo?
- **Independent CI/CD** — each service has its own pipeline
- **Independent versioning** — services evolve at their own pace
- **Clear ownership** — each repo is a deployable unit
- **Pull request isolation** — changes don't bleed across services

---

## 🚀 Project Milestones & Build Order

### Milestone 1: Foundation (Weeks 1–3) — 🟡 IN PROGRESS
> Get core services running with basic CRUD

| # | Task | Service | Status |
|---|------|---------|--------|
| 1.1 | Candidate Service — Core CRUD APIs (Phase 1) | Candidate Service | ⏳ In Progress |
| 1.2 | Candidate Service — Election-Scoped APIs (Phase 2) | Candidate Service | 🔜 |
| 1.3 | Candidate Service — Bulk, Search, Status (Phase 3-4) | Candidate Service | 🔜 |
| 1.4 | Candidate Service — Exception handling, mapper, config | Candidate Service | 🔜 |
| 1.5 | User Service — Entity, repository, service setup | User Service | 🔜 |
| 1.6 | User Service — Registration + Login APIs | User Service | 🔜 |
| 1.7 | User Service — JWT token generation + validation | User Service | 🔜 |
| 1.8 | User Service — Role management (ADMIN / VOTER) | User Service | 🔜 |

---

### Milestone 2: Infrastructure (Week 4)
> Service discovery, gateway, cross-service communication

| # | Task | Service | Status |
|---|------|---------|--------|
| 2.1 | Eureka Server setup | Eureka Server | 🔜 |
| 2.2 | Register all services as Eureka clients | All services | 🔜 |
| 2.3 | API Gateway — basic routing | API Gateway | 🔜 |
| 2.4 | API Gateway — JWT validation filter | API Gateway | 🔜 |
| 2.5 | API Gateway — rate limiting (Redis) | API Gateway | 🔜 |
| 2.6 | OpenFeign client setup in Candidate Service | Candidate Service | 🔜 |
| 2.7 | Verify end-to-end: Client → Gateway → Service → Response | All | 🔜 |

---

### Milestone 3: Voting Service — The Heart (Weeks 5–6)
> Most critical and complex service

| # | Task | Service | Status |
|---|------|---------|--------|
| 3.1 | Vote entity + schema (with hash fields) | Voting Service | 🔜 |
| 3.2 | Vote casting API — basic flow | Voting Service | 🔜 |
| 3.3 | OpenFeign → Candidate Service (validate candidate) | Voting Service | 🔜 |
| 3.4 | Double-vote prevention — Redis layer | Voting Service | 🔜 |
| 3.5 | Double-vote prevention — DB unique constraint | Voting Service | 🔜 |
| 3.6 | Vote hashing (SHA-256) | Voting Service | 🔜 |
| 3.7 | Idempotency key support (Redis) | Voting Service | 🔜 |
| 3.8 | Vote receipt response | Voting Service | 🔜 |
| 3.9 | Vote verification API (hash check) | Voting Service | 🔜 |
| 3.10 | Concurrency testing (simulate race conditions) | Voting Service | 🔜 |

---

### Milestone 4: Event-Driven Architecture (Week 7)
> Wire up Kafka for asynchronous communication

| # | Task | Service | Status |
|---|------|---------|--------|
| 4.1 | Kafka + Zookeeper setup (Docker) | Infrastructure | 🔜 |
| 4.2 | Candidate Service — Kafka producer (candidate events) | Candidate Service | 🔜 |
| 4.3 | Voting Service — Kafka producer (vote.cast events) | Voting Service | 🔜 |
| 4.4 | Voting Service — Kafka consumer (candidate.status-changed) | Voting Service | 🔜 |
| 4.5 | Define topic schemas / contracts | Infrastructure | 🔜 |

---

### Milestone 5: Result Service (Week 8)
> Real-time vote aggregation

| # | Task | Service | Status |
|---|------|---------|--------|
| 5.1 | Result Service — project setup (no DB) | Result Service | 🔜 |
| 5.2 | Kafka consumer for `vote.cast` events | Result Service | 🔜 |
| 5.3 | In-memory + Redis counters for tallying | Result Service | 🔜 |
| 5.4 | OpenFeign → Candidate Service (enrich results with names) | Result Service | 🔜 |
| 5.5 | Election results API | Result Service | 🔜 |
| 5.6 | Live results — SSE or WebSocket endpoint | Result Service | 🔜 |
| 5.7 | Audit trail API | Result Service | 🔜 |

---

### Milestone 6: Resilience & Caching (Week 9)
> Make the system production-tough

| # | Task | Service | Status |
|---|------|---------|--------|
| 6.1 | Redis caching — Candidate Service (candidate data) | Candidate Service | 🔜 |
| 6.2 | Redis caching — Voting Service (vote existence checks) | Voting Service | 🔜 |
| 6.3 | Resilience4j — Circuit breaker on Feign calls | All services | 🔜 |
| 6.4 | Resilience4j — Retry policies | All services | 🔜 |
| 6.5 | Resilience4j — Bulkhead (thread isolation) | Voting Service | 🔜 |
| 6.6 | Graceful degradation — what happens when Candidate Service is down? | Voting Service | 🔜 |
| 6.7 | Cache eviction strategy (Kafka-driven) | All services | 🔜 |

---

### Milestone 7: Security Hardening (Week 10)
> Lock down every endpoint

| # | Task | Service | Status |
|---|------|---------|--------|
| 7.1 | Spring Security config in Candidate Service | Candidate Service | 🔜 |
| 7.2 | Spring Security config in Voting Service | Voting Service | 🔜 |
| 7.3 | `@PreAuthorize` on all controller methods | All services | 🔜 |
| 7.4 | Gateway — CORS policy (restrict origins) | API Gateway | 🔜 |
| 7.5 | Input sanitization & SQL injection prevention audit | All services | 🔜 |
| 7.6 | Rate limiting per user (prevent brute-force voting attempts) | API Gateway | 🔜 |
| 7.7 | Audit logging — who did what, when | All services | 🔜 |

---

### Milestone 8: Observability (Week 11)
> See everything happening in the system

| # | Task | Service | Status |
|---|------|---------|--------|
| 8.1 | ELK Stack setup (Docker) | Infrastructure | 🔜 |
| 8.2 | Structured logging (JSON format) in all services | All services | 🔜 |
| 8.3 | Logstash pipeline configuration | Infrastructure | 🔜 |
| 8.4 | Prometheus metrics endpoint (`/actuator/prometheus`) | All services | 🔜 |
| 8.5 | Grafana dashboards (request rates, latency, errors) | Infrastructure | 🔜 |
| 8.6 | Distributed tracing (Spring Cloud Sleuth / Micrometer Tracing) | All services | 🔜 |
| 8.7 | Health check endpoints (`/actuator/health`) | All services | 🔜 |

---

### Milestone 9: Containerization & Deployment (Week 12)
> Run everything with one command

| # | Task | Service | Status |
|---|------|---------|--------|
| 9.1 | Dockerfile for each service | All services | 🔜 |
| 9.2 | `docker-compose.yml` — all services + infra | Infrastructure | 🔜 |
| 9.3 | Environment-based config (dev/staging/prod profiles) | All services | 🔜 |
| 9.4 | `docker-compose up` → full system running | Infrastructure | 🔜 |
| 9.5 | Optional: Kubernetes manifests | Infrastructure | 🔜 |

---

### Milestone 10: Testing & Load Testing (Week 13)
> Prove it works at scale

| # | Task | Service | Status |
|---|------|---------|--------|
| 10.1 | Unit tests for all services (80%+ coverage) | All services | 🔜 |
| 10.2 | Integration tests (Testcontainers + PostgreSQL) | All services | 🔜 |
| 10.3 | End-to-end test (register → vote → verify → results) | All services | 🔜 |
| 10.4 | Load testing with Gatling / JMeter (10K concurrent votes) | Voting Service | 🔜 |
| 10.5 | Chaos testing — kill a service, observe recovery | All services | 🔜 |
| 10.6 | Double-vote stress test (race condition verification) | Voting Service | 🔜 |

---

## 📊 Port Allocation

| Service | Port | Database |
|---------|------|----------|
| API Gateway | `8080` | None |
| User Service | `8081` | `user_service_db` |
| Candidate Service | `8082` | `candidateservice_db` |
| Voting Service | `8083` | `voting_service_db` |
| Result Service | `8084` | None (stateless) |
| Eureka Server | `8761` | None |
| Kafka | `9092` | — |
| Zookeeper | `2181` | — |
| Redis | `6379` | — |
| PostgreSQL | `5432` | — |
| Elasticsearch | `9200` | — |
| Kibana | `5601` | — |
| Prometheus | `9090` | — |
| Grafana | `3000` | — |

---

## 📝 Key Design Decisions Log

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Multi-repo vs mono-repo | **Multi-repo** | Independent CI/CD, clear ownership, mirror real-world microservices |
| 2 | Sync vs async communication | **Both** | Feign for request/response (validation), Kafka for events (fire-and-forget) |
| 3 | Double-vote prevention | **Redis + DB constraint** | Redis for speed, DB for absolute guarantee |
| 4 | Vote hashing | **SHA-256 per vote** | Industry standard, efficient, tamper-detectable |
| 5 | Vote chaining | **Optional (off by default)** | Serialization bottleneck at scale; enable for small elections |
| 6 | Result Service DB | **No database** | Pure aggregation service; source of truth is Voting Service DB |
| 7 | Delete strategy | **Soft-delete** | Audit trail preservation, data recovery |
| 8 | API versioning | `/api/v1/` prefix | Future-proof; enable breaking changes without affecting consumers |
| 9 | Gateway auth | **JWT validation at Gateway** | Single security checkpoint; services trust Gateway-forwarded headers |
| 10 | Caching layer | **Redis** | For idempotency keys, vote existence checks, rate limiting counters |

---

## ⚡ FAANG-Level Scalability Considerations

| Concern | Solution |
|---------|----------|
| **10M simultaneous votes** | Kafka decouples write (Voting) from read (Result); horizontal scaling of Voting Service instances |
| **double voting at scale** | Redis bloom filter or SET for O(1) check + DB unique constraint as safety net |
| **Candidate Service down during voting?** | Resilience4j circuit breaker + local cache (Kafka-synced) of candidate data in Voting Service |
| **Vote hash chaining bottleneck** | Disable chaining by default; use independent hashes (fully parallelizable) |
| **Result accuracy during high load** | Kafka consumer groups with exactly-once semantics; Redis atomic increments |
| **Network partition between services** | Saga pattern for distributed consistency; compensating transactions |
| **Database connection pool exhaustion** | HikariCP tuning; read replicas for Result Service queries |

---

> **Last Updated:** April 14, 2026  
> **Next Active Work:** Milestone 1 — Candidate Service Core CRUD  
> **Reference:** [ai-master-prompt.md](ai-master-prompt.md) (original vision document)
