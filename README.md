# 🗳️ Blockchain-Inspired Online Voting System

> A production-grade, tamper-resistant, scalable online voting platform built with **microservices architecture**, powered by **Java + Spring Boot**, and designed to handle **millions of concurrent users**.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.x-red.svg)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 🎯 What This Project Does

An election voting platform where:
- 🔒 **Every vote is hashed** (SHA-256) for tamper detection
- ⛓️ **Optional vote chaining** (blockchain-inspired) for ordering proof
- 🚫 **Zero double-voting** — enforced at 3 layers (Redis → DB → Idempotency)
- 📡 **Real-time results** via Kafka event streaming
- 🏗️ **True microservices** — independent services, data (one schema each), deployments

---

## 🏗️ Architecture Overview

```
┌──────────────┐
│   Clients    │
└──────┬───────┘
       │
┌──────▼───────┐     ┌──────────────┐
│  API Gateway │─────│ Eureka Server│
│  (Port 8080) │     │ (Port 8761)  │
└──────┬───────┘     └──────────────┘
       │
  ┌────┼────────────┬──────────────┐
  │    │            │              │
  ▼    ▼            ▼              ▼
┌────┐┌─────────┐ ┌──────┐ ┌──────────┐
│User││Candidate│ │Voting│ │  Result  │
│Svc ││ Service │ │ Svc  │ │  Service │
│8081││  8082   │ │ 8083 │ │   8084   │
└──┬─┘└───┬─────┘ └──┬───┘ └────┬─────┘
   │      │          │          │
   ▼      ▼          ▼          │
┌────┐ ┌─────┐  ┌────────┐  (No DB)
│ DB │ │ DB  │  │  DB    │
└────┘ └─────┘  └────────┘

        ═══ Kafka Event Bus ═══
```

| Service | Responsibility | Port | Database.schema |
|---------|---------------|------|----------|
| **User Service** | Auth, JWT, roles | 8081 | `voting_system.user_service` |
| **Candidate Service** | Candidate CRUD, status, validation | 8082 | `voting_system.candidate_service` |
| **Voting Service** | Vote casting, hashing, double-vote prevention | 8083 | `voting_system.voting_service` |
| **Result Service** | Real-time aggregation (Kafka consumer) | 8084 | None (stateless) |
| **API Gateway** | Routing, rate limiting, JWT validation | 8080 | None |
| **Eureka Server** | Service discovery | 8761 | None |

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.6 |
| Security | Spring Security + JWT |
| Databases | PostgreSQL (one `voting_system` database, 1 schema per service) |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Sync Communication | OpenFeign |
| Async Communication | Apache Kafka |
| Caching & Locking | Redis |
| Resilience | Resilience4j (circuit breaker, retry) |
| Logging | ELK Stack (Elasticsearch, Logstash, Kibana) |
| Monitoring | Prometheus + Grafana |
| Build | Maven |
| Containerization | Docker + Docker Compose |

---

## 📦 Repository Structure (Multi-Repo)

Each service has its own GitHub repository for independent CI/CD:

| Repository | Description | Status |
|-----------|-------------|--------|
| [`user-service`](https://github.com/Vaibhav0710/Voting_User_Service) | Authentication, JWT, roles | ✅ Complete |
| [`candidate-service`](https://github.com/Vaibhav0710/Voting_Candidate_Service) | Candidate lifecycle management | ✅ Complete |
| [`api-gateway`](https://github.com/Vaibhav0710/Voting_Api-Gateway) | Routing, rate limiting, auth filter | ✅ Complete |
| [`eureka-server`](https://github.com/Vaibhav0710/Voting_Eureka_Server) | Service registry & discovery | ✅ Complete |
| [`voting-service`](https://github.com/Vaibhav0710/Voting_Voting_Service) | Vote casting, hashing, integrity | ✅ Complete |
| [`result-service`](https://github.com/Vaibhav0710/Voting_Result_Service) | Real-time vote aggregation | ✅ Complete |
| [`voting-system-docs`](https://github.com/Vaibhav0710/Voting_System) | Main Orchestrator — architecture & infra | 📄 Active |

---

## 🚀 Quick Start (Full System)

### Prerequisites
- Docker with Docker Compose v2 (builds every service; no local Java needed)
- For development without Docker: Java 17+, Maven 3.9, and your own Postgres 16, Redis 7 and Kafka

### Run the whole system

```bash
# 1. Clone with all six services (each is its own repo, checked out as a submodule)
git clone --recurse-submodules https://github.com/Vaibhav0710/Voting_System.git
cd Voting_System

# 2. Configure secrets (.env is git-ignored); fill in JWT_SECRET, passwords
cp .env.example .env

# 3. Build and start Postgres, Redis, Kafka, Eureka and the five services (start order is handled by health checks)
docker compose up -d --build

# Optional: also start ELK, Prometheus, Grafana and Zipkin
#   set SPRING_PROFILES_ACTIVE=json-logs,logstash and TRACING_SAMPLING=1.0 in .env, then:
docker compose --profile observability up -d --build

# 4. Verify
curl http://localhost:8080/api/v1/candidates   # through the gateway (the only public port)
open http://localhost:8761                     # Eureka dashboard (bound to 127.0.0.1)
```

| UI (observability profile) | URL |
|---|---|
| Grafana (dashboard "Voting System — Overview") | http://localhost:3000 (admin / `GRAFANA_ADMIN_PASSWORD`) |
| Prometheus | http://localhost:9090 |
| Kibana (index `voting-logs-*`) | http://localhost:5601 |
| Zipkin | http://localhost:9411 |

### Kubernetes

```bash
cp k8s/secrets.env.example k8s/secrets.env    # fill in; git-ignored
kubectl apply -k k8s/                          # namespace "voting"; the ingress exposes only /api
```
The manifests use the images compose builds (`voting/<service>:1.0.0`): load them into the cluster
(`kind load docker-image …`, `minikube image load …`) or push them to a registry.

---

## 🗳️ Core Flow: Casting a Vote

```
1. User logs in → receives JWT token
2. Client sends POST /api/v1/votes (with JWT)
3. API Gateway validates JWT → forwards to Voting Service
4. Voting Service (one DB transaction):
   a. Idempotency key seen before? → return the original receipt (200)
   b. Redis check: has this user already voted? → 409 if yes
   c. Feign call: validate candidate exists & is active (cached status if Candidate Service is down)
   d. Lock the election's chain head, compute the SHA-256 hash chained to the previous vote
   e. Save to DB (UNIQUE constraint on user+election → 409)
   f. After commit: set Redis flags (voted, idempotency), publish "vote.cast" to Kafka
5. Result Service consumes Kafka event → updates live tally
6. Client receives vote receipt with hash
```

---

## 📋 Project Milestones

| # | Milestone | Timeline | Status |
|---|-----------|----------|--------|
| 1 | **Foundation** — Candidate + User Services | Weeks 1–3 | ✅ Complete |
| 2 | **Infrastructure** — Eureka + Gateway + Feign | Week 4 | ✅ Complete |
| 3 | **Voting Service** — Core vote casting | Weeks 5–6 | ✅ Complete |
| 4 | **Event-Driven** — Kafka integration | Week 7 | ✅ Complete |
| 5 | **Result Service** — Real-time aggregation | Week 8 | ✅ Complete |
| 6 | **Resilience** — Caching + Circuit breakers | Week 9 | ✅ Complete |
| 7 | **Security** — Hardening all endpoints | Week 10 | ✅ Complete |
| 8 | **Observability** — ELK + Prometheus + Grafana | Week 11 | ✅ Complete & Verified |
| 9 | **Containerization** — Docker Compose + K8s | Week 12 | ✅ Complete & Verified |
| 10 | **Testing** — Load testing + Chaos testing | Week 13 | 🟡 Done except 10k load run |

> 📖 **Detailed breakdown:** [PROJECT_PLAN.md](PROJECT_PLAN.md)

---

## 🔐 Security Model

- **JWT-based authentication** issued by User Service
- **Centralized validation** at API Gateway
- **Role-based access control** — `ADMIN` (manage candidates) vs `VOTER` (cast votes)
- **Rate limiting** per user via Redis token bucket
- **Input validation** via Jakarta Bean Validation on every endpoint
- **Idempotency keys** to prevent duplicate submissions

---

## 📝 Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Multi-repo | ✅ | Independent CI/CD, clear service boundaries |
| Sync + Async | OpenFeign + Kafka | Feign for validations, Kafka for events |
| Double-vote prevention | Redis + DB constraint | Speed + absolute guarantee |
| Vote integrity | SHA-256 per vote | Tamper detection without serialization bottleneck |
| Result Service DB | None | Pure aggregation from Kafka; Voting DB is source of truth |

---

## 📄 Documentation

| Document | Description |
|----------|-------------|
| [PROJECT_PLAN.md](PROJECT_PLAN.md) | Master implementation plan with all milestones |
| [ai-master-prompt.md](ai-master-prompt.md) | Original project vision & AI prompt |
| Per-service `README.md` | Setup, API docs, schema for each service |
| Per-service `IMPLEMENTATION_PLAN.md` | Step-by-step build checklist |

---

## 📝 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

> **Maintainer:** Vaibhav  
> **Last Updated:** October 24, 2026
