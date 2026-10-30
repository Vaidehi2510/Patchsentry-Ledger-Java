# Patchsentry Ledger

A Java 21 / Spring Boot REST service that ingests, scores, and serves QA run reports from [Patchsentry](https://patchsentry.vercel.app) runners. Postgres holds the relational core, MongoDB holds variable-shape AI evidence, and Redis provides a rate limiter shared by every replica. It ships with Docker, Kubernetes manifests, and a CI pipeline that deploys to a real cluster.

## Architecture

```
Runner ──PUT /api/runner/runs/{recordId}──▶ RunnerAuthFilter ─▶ RunnerController ─▶ RunIngestService
          (Bearer psl_… token)              │ hash lookup (Postgres)                 │ validate (core)
                                            │ rate limit (Redis INCR, per token)     │ score (Strategy)
                                            ▼                                        ├─▶ Postgres: qa_runs, check_results (JPA, @Version)
Admin ──/api/admin/*──▶ AdminAuthFilter ─▶ AdminController ─▶ ProjectService         └─▶ MongoDB: run_evidence (findings, limitations, AI usage)
                         (constant-time)                     RunQueryService ── joins both stores for run detail
```

| Module | Contents |
|---|---|
| `ledger-core` | Framework-free domain: `RunReport`, `RunReportValidator`, `ScoringPolicy` strategies, `RunnerTokens`. No Spring, so it's fast to test and reuse. |
| `ledger-service` | Spring Boot app: Web MVC, Data JPA + Flyway, Data MongoDB, Data Redis, Actuator. |

## Design decisions

* **Idempotent uploads.** `PUT` keyed by a 64-hex record ID: `201` on the first upload, `200` on re-uploads. The checks are replaced, not appended. A record ID stays bound to its project, PR number, and commit SHA; moving it returns `409`.
* **Two stores, one failure mode.** Evidence is written to MongoDB inside the Postgres transaction, before commit. If MongoDB fails, Postgres rolls back. If the commit fails after that, the evidence document is orphaned but harmless: it's keyed by record ID and overwritten when the runner retries.
* **Safe under concurrency.** `@Version` gives optimistic locking on runs, and unique indexes catch duplicate projects and tokens. Both races map to `409` with a retry hint.
* **Distributed rate limit.** A fixed-window `INCR` + `EXPIRE` counter in Redis counts correctly no matter which pod serves the request. It fails open when Redis is down, because losing QA results is worse than briefly losing the limit.
* **Credentials.** Runner tokens carry 256 bits of entropy and only their SHA-256 hash is stored. The admin token is compared in constant time. The admin API is disabled until `LEDGER_ADMIN_TOKEN` is set.
* **Truthful scoring.** The default `required-checks` policy floors the score (99.9% never shows as 100) and treats "nothing verified" as `BLOCKED`, never `PASSED`. Set `LEDGER_SCORING_POLICY=all-checks` to count optional checks too.
* **Patterns.** Strategy (`ScoringPolicy`), Template Method (`AbstractScoringPolicy`), Factory (`ScoringPolicies`), Repository (Spring Data), and Adapter (`RateLimiter` → Redis).

## API

| Method and path | Auth | Purpose |
|---|---|---|
| `POST /api/admin/projects` | admin | Create a project for `owner/repo` |
| `POST /api/admin/projects/{id}/tokens` | admin | Issue a runner token. It's shown once. |
| `DELETE /api/admin/tokens/{tokenId}` | admin | Revoke a token |
| `GET /api/admin/projects/{id}/runs?pr=&limit=` | admin | Latest runs, newest first |
| `GET /api/admin/projects/{id}/runs/{recordId}` | admin | Checks (Postgres) plus evidence (MongoDB) |
| `PUT /api/runner/runs/{recordId}` | runner | Upload or update a run report |
| `GET /actuator/health/readiness` | none | Readiness, including db, mongo, and redis |

Errors use RFC 9457 `application/problem+json`. A validation failure lists every problem in `errors`.

## Run it

Requirements: JDK 21, Maven 3.9+, and Docker.

```bash
mvn verify                      # unit tests + Testcontainers integration tests
docker compose up --build       # Postgres, MongoDB, Redis, and the service on :8080
LEDGER_ADMIN_TOKEN=local-dev-admin-token bash scripts/smoke.sh
```

## Tests

| Suite | What it covers |
|---|---|
| `ledger-core` (18, JUnit 5) | Scoring rules and precedence, validation limits, token generation and hashing |
| `RunIngestServiceTest` (7, Mockito) | Create vs. update, revision binding, cross-repo rejection, Mongo failure rolls back |
| `RedisRateLimiterTest` (3, Mockito) | Window keys, TTL on first hit, limit edge, fail-open |
| `RunnerAuthFilterTest` (4, Mockito) | Malformed, unknown, valid, and rate-limited tokens |
| `LedgerIntegrationTest` (7, Testcontainers) | Real Postgres, MongoDB, and Redis: idempotency, conflicts, auth, revocation, rate limit, readiness |

## Deploy to Kubernetes

```bash
docker build -t patchsentry-ledger:local .
kind create cluster --name ledger && kind load docker-image patchsentry-ledger:local --name ledger
kubectl apply -f deploy/k8s/namespace.yaml
kubectl -n patchsentry create secret generic ledger-secrets \
  --from-literal=admin-token="$(openssl rand -hex 24)" --from-literal=database-password="$(openssl rand -hex 24)"
kubectl apply -k deploy/k8s
```

The service runs 2 replicas with startup, liveness, and readiness probes, a zero-downtime rolling update, an HPA (2–6 pods at 70% CPU), and a PodDisruptionBudget. It runs as a non-root user with a read-only root filesystem. The JVM sizes its heap from the pod's memory limit (`-XX:MaxRAMPercentage=75`). `datastores.yaml` is for development and CI only. In production, point the ConfigMap at managed Postgres, MongoDB, and Redis.

CI (`.github/workflows/ci.yml`) runs `mvn verify`, builds the image, creates a kind cluster, deploys, and runs `scripts/smoke.sh` through the Service.

## Next steps

* A transactional outbox for the MongoDB write, instead of relying on retries.
* OpenAPI docs, Spring Security with OAuth2 for the admin API, and Micrometer metrics.
* Terraform for EKS with RDS, DocumentDB, and ElastiCache.

## Portal-native assessment archive

The website's **Pilot & Ledger** view now has its own encrypted assessment receipt chain. This service can hold an additional company-owned copy after it is deployed and explicitly connected. It does not replace the execution engine or authorize releases merely because a report was stored.

Scoped runner tokens now support `GET /api/runner/project` and `PUT`/`GET /api/runner/assessments/{recordId}/{payloadDigest}`. The digest is SHA-256 of the exact request body bytes. Completed portal-native PRs, builds and releases are retained without converting builds into fake PRs. Exact retries are idempotent; another payload digest creates a new version, and an existing record cannot change source/subject identity. Database triggers reject updates to archived snapshots. An administrator who controls the database remains outside this integrity boundary.

Issue only a project-scoped `psl_` runner token to the website. Never configure its global admin token there. The website operator must approve the exact HTTPS origin using `LEDGER_ALLOWED_ORIGINS`, and the product owner must explicitly approve report transfer. Each copy has its own retention and access policy. The archive acknowledges storage of bytes, not independent execution, correctness or build provenance. The legacy PR-scoring endpoints remain unchanged.
