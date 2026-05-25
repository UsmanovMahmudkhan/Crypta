# Deployment

Crypta is alpha-stage and not production-ready. These notes describe local and experimental deployment paths only.

## Local Docker Compose Setup

Copy the example environment file and replace default secrets before using any shared environment:

```bash
cp .env.example .env
docker compose up --build
```

Services:

- `db`: PostgreSQL 16 exposed locally on port `5433`.
- `app`: Spring Boot backend exposed on port `8080`.

Check readiness:

```bash
curl http://localhost:8080/actuator/health
```

## Environment Variables

| Variable | Purpose |
| -------- | ------- |
| `POSTGRES_DB` | Local Compose database name. |
| `POSTGRES_USER` | Local Compose database user. |
| `POSTGRES_PASSWORD` | Local Compose database password. |
| `DATABASE_URL` | JDBC URL used by Spring Boot. |
| `DATABASE_USERNAME` | Database username used by Spring Boot. |
| `DATABASE_PASSWORD` | Database password used by Spring Boot. |
| `WEBAUTHN_RP_ID` | WebAuthn relying party ID. |
| `WEBAUTHN_RP_NAME` | WebAuthn relying party display name. |
| `WEBAUTHN_ALLOWED_ORIGINS` | Allowed WebAuthn origins. |
| `BOOTSTRAP_TOKEN` | Temporary bootstrap token for initial setup. |
| `PORT` | Optional backend port; defaults to `8080`. |

Do not use example values outside local development.

## PostgreSQL Setup

Flyway migrations run on application startup. For direct Maven runs, provide a reachable PostgreSQL database and the `DATABASE_*` variables:

```bash
mvn spring-boot:run
```

Backups, encryption at rest, connection pooling, least-privilege database users, and migration rollback plans are required before production use.

## Backend Deployment Notes

The Dockerfile builds the application with Maven and runs it on a Java 21 runtime image as a non-root user.

Before deploying beyond local development:

- Use TLS at the ingress or load balancer.
- Use non-default, rotated secrets.
- Restrict actuator exposure.
- Disable or rotate bootstrap credentials after initial setup.
- Configure structured logging without request bodies.
- Run dependency and container image scans.
- Confirm Flyway migrations against staging data.

## Mobile Build Notes

The `mobile` directory is a Flutter scaffold:

```bash
cd mobile
flutter pub get
flutter test
```

Native secure-key storage, device attestation, local encrypted storage, push notification behavior, and release signing remain TODO. Do not treat the current mobile scaffold as a complete secure client.

## Kubernetes Deployment Notes

Kubernetes base manifests exist under `k8s/base`.

Render locally with:

```bash
kustomize build k8s/base
```

The included `secret.example.yaml` is an example only. Replace it with External Secrets, Sealed Secrets, cloud secret manager integration, or another managed secret workflow.

The base manifests include:

- Namespace.
- ConfigMap.
- Secret example.
- Deployment.
- Service.
- Ingress.
- HPA.
- NetworkPolicy.

These manifests require environment-specific review for ingress class, TLS issuer, database network path, image registry, resource limits, pod security, NetworkPolicy behavior, monitoring, and backup strategy.

## Security Warnings For Production

Do not deploy Crypta to production until:

- WebAuthn/passkey finish verification is complete.
- Native mobile secure-key storage is complete.
- Real direct and group cryptographic providers are integrated and reviewed.
- Key transparency verification is implemented client-side.
- Object storage signing is implemented.
- Authorization and governance execution have been audited.
- Load testing and failure testing have been performed.
- Operational runbooks and incident response procedures exist.

## Secret Management Recommendations

- Store secrets in a dedicated secret manager.
- Rotate bootstrap tokens immediately after setup.
- Use short-lived credentials where possible.
- Never commit real secrets, private keys, tokens, credentials, or user data.
- Keep `.env` local and untracked.
- Redact secrets from logs, support bundles, screenshots, and issue reports.
