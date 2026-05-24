# Deployment Runbook

## Local Development

1. Copy `.env.example` to `.env` and change every secret value.
2. Run `docker compose up --build`.
3. Check `http://localhost:8080/actuator/health`.
4. Bootstrap the first organization/user with `X-Bootstrap-Token`.

Docker Compose is for local development only. Shared staging and production environments should use Kubernetes with externally managed PostgreSQL, TLS, and secret management.

## Kubernetes

1. Build and publish the image.
2. Replace `k8s/base/secret.example.yaml` with an ExternalSecret, SealedSecret, or provider-managed Secret.
3. Set `WEBAUTHN_RP_ID` and `WEBAUTHN_ALLOWED_ORIGINS` to the final HTTPS host.
4. Render manifests with `kustomize build k8s/base`.
5. Apply to staging, wait for `/actuator/health/readiness`, then run smoke tests.

## Release Gates

- `mvn test` passes.
- Flyway migrations apply cleanly against a staging copy of PostgreSQL.
- Container image has an SBOM and vulnerability scan result.
- Kubernetes manifests render successfully.
- `/actuator/health/readiness` and `/v3/api-docs` respond in staging.
- Bootstrap token is rotated or disabled after initial tenant setup.

## Rollback

Roll back the Deployment image tag first. Database migrations in this project must be forward-compatible; destructive migrations require a separate backup, restore rehearsal, and manual approval.
