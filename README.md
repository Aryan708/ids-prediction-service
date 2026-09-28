# Network Intrusion Detection API

This project turns my [CICIDS2017 Random Forest intrusion-detection model](https://github.com/Aryan708/Binary-Network-Intrusion-Detection-using-Machine-Learning) into a production-style service. A **Spring Boot (Java 17)** REST API accepts network flows, gets predictions from a **Python model service**, caches repeat results in **Redis**, and stores every prediction in **MySQL**. The whole stack runs with **Docker Compose** and is tested in **GitHub Actions**.

```
                 ┌───────────────────────── api-service (Spring Boot, :8080) ─────────────────────────┐
 client ──JSON──▶│ Controller → validation → PredictionService → ScoringService ──cache miss──▶ RestClient ├──▶ ml-service (FastAPI, :8000)
                 │                               │                    │                              │      Random Forest (joblib)
                 │                               ▼                    ▼                              │
                 │                         MySQL (Flyway)       Redis cache                           │
                 └─────────────────────────────────────────────────────────────────────────────────────┘
```

## Why it's split into two services

- The model was trained with scikit-learn, so Python serves it with no conversion step.
- Java handles everything around the model: the API contract, validation, persistence, caching, error handling and docs. This is the usual way ML gets integrated into enterprise backends.
- Each service can be scaled, deployed and updated on its own. Retraining the model doesn't require redeploying the API.

## Features

| Area | What's implemented |
|---|---|
| REST API | `POST /api/v1/predictions`, `POST /api/v1/predictions/batch` (≤500), `GET /api/v1/predictions/{id}`, paged `GET /api/v1/predictions?label=ATTACK`, `GET /api/v1/predictions/stats`, `GET /api/v1/model` |
| Validation | Bean Validation on requests, plus a check that every flow has exactly the model's 78 features with finite values. Batch errors are reported per flow (`flows[3]: missing ...`) |
| Errors | RFC 7807 `ProblemDetail` responses: 400 for bad input, 404 when a prediction isn't found, 503 when the model service is down (with connect/read timeouts) |
| Database | MySQL 8, schema versioned with **Flyway**, Spring Data JPA, indexed for filtering by label and time |
| Caching | **Redis**: the model's output is cached per unique flow (SHA-256 of the features plus model version, 1h TTL). A batch sends only its cache misses to the model, in a single call. Stats are cached for 30s. If Redis goes down, predictions still work without the cache |
| API docs | **Swagger UI / OpenAPI** at `/swagger-ui.html` |
| Tests | **JUnit 5 + Mockito** unit tests, `@WebMvcTest` controller tests, `MockRestServiceServer` HTTP-client tests, a `@SpringBootTest` integration test (H2 + Flyway), and **pytest** for the model service |
| DevOps | Multi-stage Dockerfiles running as a non-root user, Docker Compose with health checks, and a GitHub Actions CI pipeline |
| Ops | Spring Boot Actuator `/actuator/health` and `/actuator/metrics` |

## Run it

### 1. Train the model

Use the same CICIDS2017 CSVs as the original notebook:

```bash
cd ml-service
python -m venv .venv && .venv\Scripts\activate        # Windows  (Linux/Mac: source .venv/bin/activate)
pip install -r requirements.txt
python train_model.py --data-dir "C:\Users\agsri\Downloads\Aimlassignment"
```

This repeats the notebook's steps: strip column names, label BENIGN as Normal and everything else as Attack, drop inf/NaN rows, take a stratified 20% subset, then an 80/20 split and `RandomForestClassifier(random_state=42)`. The model and its metrics are saved to `models/model.joblib`.

> To try the stack without the dataset, run `python train_model.py --synthetic`. This trains a **demo model on fake data**, and it reports `model_version: demo-synthetic` so it can't be mistaken for the real model.

### 2. Start everything

```bash
docker compose up --build
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### 3. Try it

```bash
curl -X POST localhost:8080/api/v1/predictions -H "Content-Type: application/json" -d @examples/flow.json
curl -X POST localhost:8080/api/v1/predictions/batch -H "Content-Type: application/json" -d @examples/batch.json
curl "localhost:8080/api/v1/predictions?label=ATTACK&page=0&size=10"
curl localhost:8080/api/v1/predictions/stats
```

Send the same flow twice. The second response has `"cacheHit": true` and a lower `latencyMs`.

Example response:

```json
{
  "id": 1,
  "label": "NORMAL",
  "attackProbability": 0.02,
  "modelVersion": "rf-cicids2017-20260928",
  "sourceIp": "192.168.10.5",
  "destinationPort": 443,
  "cacheHit": false,
  "latencyMs": 18,
  "createdAt": "2026-09-28T10:15:30.123456Z"
}
```

## Run the tests

```bash
cd api-service && mvn verify           # needs JDK 17+ and Maven
cd ml-service  && pip install -r requirements-dev.txt && python -m pytest
```

## Project layout

```
api-service/                       Spring Boot API
  src/main/java/com/aryan/ids/
    web/        PredictionController, GlobalExceptionHandler
    service/    PredictionService, ScoringService (cache + batching), FeatureValidator, FeatureHasher, ModelMetadataService
    client/     MlServiceClient (RestClient + timeouts), wire DTOs
    domain/     Prediction entity, Label
    repository/ PredictionRepository (Spring Data JPA)
    config/     Redis cache, HTTP client, OpenAPI
  src/main/resources/db/migration/  Flyway SQL
ml-service/                        FastAPI model server + training script + pytest
examples/                          Sample request bodies
docker-compose.yml                 MySQL + Redis + both services
.github/workflows/ci.yml           Build and test both services, build Docker images
```

## Next steps

- Kubernetes manifests (Deployment, Service, ConfigMap, probes on `/actuator/health`) and a deployment to a local `kind` cluster, then AWS EKS with RDS MySQL and ElastiCache Redis
- A small React dashboard showing live attack rate and recent alerts
- Publish ATTACK predictions to RabbitMQ for downstream alerting
