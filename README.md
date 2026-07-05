# Core Banking Service

A lightweight core banking app built with Spring Boot, MyBatis, PostgreSQL, and RabbitMQ.

Reference used: [Fintech Engineering Handbook](https://w.pitula.me/fintech-engineering-handbook/) by Voytek Pitula.

## Features

- Multi-currency accounts with balances
- Transactions (IN/OUT) with validation and balance updates
- Transactional outbox relay to RabbitMQ for account updates

## Tech Stack

- Java 25
- Spring Boot
- MyBatis
- Gradle
- PostgreSQL
- RabbitMQ
- JUnit, Testcontainers, JaCoCo

## Run with Docker

```bash
docker compose up --build
```

Services:

- API: http://localhost:8080
- PostgreSQL: localhost:5432
- RabbitMQ AMQP: localhost:5672
- RabbitMQ UI: http://localhost:15672 (myuser / secret)

Stop:

```bash
docker compose down
```

## Run locally

Requirements: Docker, JDK 25.

Start dependencies:

```bash
docker compose up -d postgres rabbitmq
```

Run app:

```bash
./gradlew bootRun
```

## API quick start

Sample requests are in `endpoints.http`.

Base path: `/api/v1/accounts`

Endpoints:

- `POST /api/v1/accounts`
- `GET /api/v1/accounts/{accountId}`
- `POST /api/v1/accounts/{accountId}/transactions`
- `GET /api/v1/accounts/{accountId}/transactions`

## Tests and coverage

```bash
./gradlew check
```

JaCoCo enforces minimum coverage.

## Design choices

- Layered architecture: API → Application → Domain → Infrastructure
- Joda Money for currency-safe arithmetic and JSON string representation to avoid floating-point issues
- MyBatis for explicit SQL control
- Transactional outbox pattern for reliable RabbitMQ publishing
- Liquibase for schema versioning

## Throughput estimate

Install [k6](https://k6.io/) for load testing.

```sh
# 0. Go to the benchmark directory
cd benchmark

# 1. Start the stack with limits
docker compose up --build -d

# 2. Verify limits are applied
docker stats --no-stream

# 3. Run k6
k6 run loadtest.js
```

Measured on a local Docker stack with resource-constrained services:

| Service    | CPU    | Memory |
| ---------- | ------ | ------ |
| API        | 1 core | 1 GB   |
| PostgreSQL | 1 core | 1 GB   |
| RabbitMQ   | 1 core | 512 MB |

> On AMD Ryzen 7 PRO 8840U (16) @ 5.13 GHz

k6 load test with 50 VUs (Virtual Users) over 2 minutes:

- **~1,667 requests/sec** sustained
- **p(95) latency: 81 ms**
- **0% failures**

The script simulates 50 concurrent users creating transactions on 10 pre-created accounts for about 2 minutes, validating that the API stays fast (<500 ms at p95) and reliable (<1% errors).

Each iteration creates an account and posts a transaction, so the workload is mixed. For pure transaction creation on a hot account/currency path, throughput may differ.

Actual results depend on hardware, DB tuning, RabbitMQ durability, connection pool size, and request mix. Run your own load test (e.g., k6, wrk) against `POST /api/v1/accounts/{accountId}/transactions` for precise numbers.

## Horizontal scaling

- Run stateless API replicas behind a load balancer
- Tune connection pooling against shared PostgreSQL
- Ensure idempotency and retries for publishers/consumers
- Outbox relay already uses a claim/lock strategy for multi-node safety
- Add observability (metrics, traces, structured logs) and autoscaling
- Plan DB scaling: indexing, read replicas, partitioning/sharding, monitoring

## AI usage

Cross-reference and domain analysis using https://w.pitula.me/fintech-engineering-handbook/ as a reference.

AI assisted with boilerplate, test scaffolding, validation/error handling ideas, verification and documentation refinement. All output was reviewed and adapted.
