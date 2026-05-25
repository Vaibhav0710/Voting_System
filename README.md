# 🗳️ Blockchain-Inspired Online Voting System

> A production-grade, tamper-resistant, scalable online voting platform built with **microservices architecture**, powered by **Java + Spring Boot**, and designed to handle **millions of concurrent users**.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
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
- 🏗️ **True microservices** — independent services, databases, deployments

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

| Service | Responsibility | Port | Database |
|---------|---------------|------|----------|
| **User Service** | Auth, JWT, roles | 8081 | `user_service_db` |
| **Candidate Service** | Candidate CRUD, status, validation | 8082 | `candidateservice_db` |
| **Voting Service** | Vote casting, hashing, double-vote prevention | 8083 | `voting_service_db` |
| **Result Service** | Real-time aggregation (Kafka consumer) | 8084 | None (stateless) |
| **API Gateway** | Routing, rate limiting, JWT validation | 8080 | None |
| **Eureka Server** | Service discovery | 8761 | None |

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.x |
| Security | Spring Security + JWT |
| Databases | PostgreSQL (1 per service) |
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
| [`result-service`](https://github.com/Vaibhav0710/result-service) | Real-time vote aggregation | 🔜 Planned |
| [`voting-system-docs`](https://github.com/Vaibhav0710/Voting_System) | Main Orchestrator — architecture & infra | 📄 Active |

---

## 🚀 Quick Start (Full System)

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 14+

### Run All Services

```bash
# 1. Clone all repos
git clone https://github.com/Vaibhav0710/Voting_Eureka_Server.git
git clone https://github.com/Vaibhav0710/Voting_User_Service.git
git clone https://github.com/Vaibhav0710/Voting_Candidate_Service.git
git clone https://github.com/Vaibhav0710/Voting_Api-Gateway.git
git clone https://github.com/Vaibhav0710/Voting_Voting_Service.git

# 2. Start infrastructure (Kafka, Redis, PostgreSQL)
docker-compose up -d

# 3. Start services IN ORDER
cd Voting_Eureka_Server     && mvn spring-boot:run &
cd Voting_User_Service      && mvn spring-boot:run &
cd Voting_Candidate_Service && mvn spring-boot:run &
cd Voting_Voting_Service    && mvn spring-boot:run &
cd result-service           && mvn spring-boot:run &
cd Voting_Api-Gateway       && mvn spring-boot:run &

# 4. Verify
curl http://localhost:8761         # Eureka dashboard
curl http://localhost:8080/actuator/health  # Gateway health
```

---

## 🗳️ Core Flow: Casting a Vote

```
1. User logs in → receives JWT token
2. Client sends POST /api/v1/votes (with JWT)
3. API Gateway validates JWT → forwards to Voting Service
4. Voting Service:
   a. Redis check: has this user already voted? → 409 if yes
   b. Feign call: validate candidate exists & is active
   c. Redis check: idempotency key → return cached if duplicate
   d. Compute SHA-256 hash of vote data
   e. Save to DB (UNIQUE constraint on user+election)
   f. Set Redis flags (voted, idempotency)
   g. Publish "vote.cast" to Kafka
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
| 5 | **Result Service** — Real-time aggregation | Week 8 | 🔜 |
| 6 | **Resilience** — Caching + Circuit breakers | Week 9 | 🔜 |
| 7 | **Security** — Hardening all endpoints | Week 10 | 🔜 |
| 8 | **Observability** — ELK + Prometheus + Grafana | Week 11 | 🔜 |
| 9 | **Containerization** — Docker Compose + K8s | Week 12 | 🔜 |
| 10 | **Testing** — Load testing + Chaos testing | Week 13 | 🔜 |

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
> **Last Updated:** May 25, 2026
