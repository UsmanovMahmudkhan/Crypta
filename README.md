# Sovereign Comm Platform

Sovereign Comm is a Spring Boot backend and Flutter mobile scaffold for a zero-trust, end-to-end encrypted executive communication platform.

## What Is Implemented

- PostgreSQL/Flyway schema for organizations, users, devices, keys, rooms, ciphertext messages, attachments, audit events, lockdowns, WebAuthn challenges, and API sessions.
- JDBC-backed backend services replacing the former no-op scaffold.
- Bearer-token API sessions with hashed token storage.
- Bootstrap-token support for first tenant/user setup.
- Ciphertext-only message and attachment APIs with plaintext-shaped metadata rejection.
- Key bundle and append-only key transparency proof endpoints.
- OpenAPI UI through `/swagger-ui.html`.
- Kubernetes manifests under `k8s/base`.
- Flutter mobile shell under `mobile/`.

## Local Start

```bash
cp .env.example .env
docker compose up --build
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

Use `X-Bootstrap-Token` from `.env` to create the first organization and user. Rotate or remove that token before any shared environment.

## Production Notes

The backend is production-shaped but not production-certified. Before making production security claims, complete the items in `docs/security-verification.md`, especially full WebAuthn finish verification and external review of Signal/PQXDH/MLS/mobile key handling.
