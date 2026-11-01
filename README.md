# Patchsentry Ledger

An optional Java 21 / Spring Boot REST service for storing and retrieving Patchsentry QA reports. Postgres holds the relational core, MongoDB holds variable-shape AI evidence, and Redis provides a rate limiter shared by every replica. It ships with Docker and Kubernetes manifests. Its CI exercises a disposable local **kind** cluster; this project has not deployed a production Java Ledger service.

## Start here for reviewers

To reproduce Patchsentry's defect-detection workflow, begin with the [engine reviewer quick-start](https://github.com/Vaidehi2510/QA-testbot/blob/main/docs/reviewer-quickstart.md). That path runs deterministic fixture tests without an account, model API key or Java deployment. The [public dashboard demo](https://patchsentry.vercel.app/demo) illustrates the report interface with sample data; it is not evidence of tests against a customer product.

Use this repository to inspect the optional storage service and reproduce its Java tests or local ingestion smoke test below. [CI runs](https://github.com/Vaidehi2510/Patchsentry-Ledger-Java/actions/workflows/ci.yml) record execution results for the selected source revision.

| Repository | Role |
| --- | --- |
| [QA-testbot](https://github.com/Vaidehi2510/QA-testbot) | Executes configured checks and optional AI-assisted reviews while keeping product source read-only. |
| [patchsentry-web](https://github.com/Vaidehi2510/patchsentry-web) | Website, project controls, scheduling and reports. Its hosted assessment receipt chain remains authoritative for the website's release decisions. |
| **Patchsentry-Ledger-Java** (this repository) | Optional company-owned report service and additional assessment archive. Storing a report does not prove its tests ran or authorize a release. |

The website's [Java archive adapter](https://github.com/Vaidehi2510/patchsentry-web/blob/main/src/lib/server/ledger-bridge.ts) is implemented. Activating it requires an explicitly deployed HTTPS service, an operator-approved origin, a project-scoped token and owner transfer consent. Java Ledger is not required for the existing website or the no-key reviewer demo.

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

## Reproduce the Java service locally

For the Java test suite, use **JDK 21**, **Maven 3.9+** and a running Docker engine accessible to Testcontainers. The first run downloads Maven dependencies and the public Postgres 16, MongoDB 7 and Redis 7 images. No model-provider or cloud account is required.

```bash
git clone https://github.com/Vaidehi2510/Patchsentry-Ledger-Java.git
cd Patchsentry-Ledger-Java
mvn -B verify
```

`verify` runs the framework-free core tests, mocked service/filter tests and real Testcontainers datastore integration tests. Inspect Maven's reported failures and skipped tests, plus each module's `target/surefire-reports`; a successful build with deliberately skipped tests is not the complete verification path. Use the linked CI run and its source revision when citing observed test counts or outcomes.

To inspect the running API, use Docker Compose v2. The checked-in local Compose configuration exposes Postgres on 5432, MongoDB on 27017, Redis on 6379 and Ledger on 8080, so those ports must be available. The smoke script additionally requires Bash, `curl`, `jq`, `sha256sum` and `sha1sum` in `PATH`.

```bash
docker compose up --build -d
curl -fsS http://localhost:8080/actuator/health/readiness
```

Wait until readiness returns `UP`, then run the synthetic ingestion check. The token below matches the checked-in local development Compose configuration.

```bash
LEDGER_ADMIN_TOKEN=local-dev-admin-token bash scripts/smoke.sh
```

Expected final output resembles `Smoke test passed: run <record-prefix> scored 100`. The script creates a synthetic project and scoped token, uploads a synthetic report and queries its stored score. It verifies that ingestion/authentication/query path; it does not execute a product test suite or demonstrate product-wide QA coverage. Stop the local services when finished:

```bash
docker compose down
```

## Tests

| Suite | What it covers |
|---|---|
| `ledger-core` (JUnit 5) | Scoring rules and precedence, validation limits, token generation and hashing |
| `RunIngestServiceTest` (Mockito) | Create vs. update, revision binding, cross-repo rejection, Mongo failure rolls back |
| `RedisRateLimiterTest` (Mockito) | Window keys, TTL on first hit, limit edge, fail-open |
| `RunnerAuthFilterTest` (Mockito) | Malformed, unknown, valid, and rate-limited tokens |
| `LedgerIntegrationTest` (Testcontainers) | Real Postgres, MongoDB, and Redis: idempotency, conflicts, auth, revocation, rate limit, readiness and scoped assessment archives |

## Reproduce the local Kubernetes rehearsal

This optional path additionally needs `kind`, `kubectl` and `openssl`. It creates a disposable local cluster, not a hosted production service.

```bash
docker build -t patchsentry-ledger:local .
kind create cluster --name ledger
kind load docker-image patchsentry-ledger:local --name ledger
kubectl apply -f deploy/k8s/namespace.yaml
kubectl -n patchsentry create secret generic ledger-secrets \
  --from-literal=admin-token="$(openssl rand -hex 24)" --from-literal=database-password="$(openssl rand -hex 24)"
kubectl apply -k deploy/k8s
```

The manifests configure 2 replicas, startup/liveness/readiness probes, rolling updates with `maxUnavailable: 0` and `maxSurge: 1`, an HPA (2–6 pods at 70% CPU) and a PodDisruptionBudget. These are configuration settings, not a measured availability or capacity guarantee. The service runs as a non-root user with a read-only root filesystem. The JVM sizes its heap from the pod's memory limit (`-XX:MaxRAMPercentage=75`). `datastores.yaml` is for development and CI only; a company deployment needs separately operated Postgres, MongoDB and Redis plus its own backup, retention and security controls.

CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs `mvn verify`, builds the image, creates a disposable kind cluster, deploys and runs `scripts/smoke.sh` through the Service. It does not deploy a Java service behind the public Patchsentry website. Remove your local rehearsal cluster when finished:

```bash
kind delete cluster --name ledger
```

## Next steps

* A transactional outbox for the MongoDB write, instead of relying on retries.
* OpenAPI docs, Spring Security with OAuth2 for the admin API, and Micrometer metrics.
* Terraform for EKS with RDS, DocumentDB, and ElastiCache.

## Portal-native assessment archive

The website's **Pilot & Ledger** view now has its own encrypted assessment receipt chain. This service can hold an additional company-owned copy after it is deployed and explicitly connected. It does not replace the execution engine or authorize releases merely because a report was stored.

Scoped runner tokens now support `GET /api/runner/project` and `PUT`/`GET /api/runner/assessments/{recordId}/{payloadDigest}`. The digest is SHA-256 of the exact request body bytes. Completed portal-native PRs, builds and releases are retained without converting builds into fake PRs. Exact retries are idempotent; another payload digest creates a new version, and an existing record cannot change source/subject identity. Database triggers reject updates to archived snapshots. An administrator who controls the database remains outside this integrity boundary.

Issue only a project-scoped `psl_` runner token to the website. Never configure its global admin token there. The website operator must approve the exact HTTPS origin using `LEDGER_ALLOWED_ORIGINS`, and the product owner must explicitly approve report transfer. Each copy has its own retention and access policy. The archive acknowledges storage of bytes, not independent execution, correctness or build provenance. The legacy PR-scoring endpoints remain unchanged.

The scoped `GET /api/runner/assessments/{recordId}/{payloadDigest}/payload` endpoint exports the exact stored JSON bytes after verifying SHA-256. It uses private/no-store headers and never exposes another project’s archive. Website retention does not implicitly restore copies from the separate archive.

## Contribute

Documentation, reproducibility, API examples, accessibility feedback and tests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for setup and review expectations, the [code of conduct](CODE_OF_CONDUCT.md) for participation, and [SECURITY.md](SECURITY.md) for private vulnerability reporting. Use public issues for non-sensitive reproducibility problems; keep credentials and customer reports out of examples.

## License

Patchsentry Ledger is available under the [MIT License](LICENSE).
