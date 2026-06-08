# Internal Demo Runbook

Crypta is still an alpha scaffold. This runbook gives maintainers one repeatable internal demo path that uses fake ciphertext and explicit demo-only crypto.

## 1. Start the backend stack

```bash
cp .env.example .env
docker compose up --build
```

The backend should expose readiness at:

```bash
curl http://localhost:8080/actuator/health/readiness
```

## 2. Provision demo identities

Use `X-Bootstrap-Token` from `.env` to create one organization, two users, and one device for each user. Then issue a bearer token with:

```bash
POST /api/v1/bootstrap/sessions
```

The bootstrap token is onboarding-only. The existing integration test in `BackendFlowIntegrationTest` is the executable reference for this flow: it uses bootstrap for provisioning and bootstrap-session issuance, then uses bearer sessions for direct ciphertext envelopes, inbox reads, receipts, rooms, attachment metadata, MDM sync, and audit export.

## 3. Run the mobile demo

Offline demo mode requires no backend and is the safest first check:

```bash
cd mobile
flutter pub get
flutter test
flutter run
```

Backend-connected mode is enabled with `--dart-define` values:

```bash
flutter run \
  --dart-define=CRYPTA_BACKEND_URL=http://localhost:8080 \
  --dart-define=CRYPTA_BOOTSTRAP_TOKEN="$BOOTSTRAP_TOKEN" \
  --dart-define=CRYPTA_ORGANIZATION_ID=<org-id> \
  --dart-define=CRYPTA_USER_ID=<sender-user-id> \
  --dart-define=CRYPTA_DEVICE_ID=<sender-device-id> \
  --dart-define=CRYPTA_RECIPIENT_USER_ID=<recipient-user-id> \
  --dart-define=CRYPTA_RECIPIENT_DEVICE_ID=<recipient-device-id> \
  --dart-define=CRYPTA_ROOM_ID=<room-id>
```

## 4. Acceptance checks

- The app starts in an explicit offline or connected state.
- Verify device, passkey, lockdown, attach, and send controls all produce visible outcomes.
- Lockdown disables message sending.
- Send creates a deterministic demo ciphertext envelope and never claims audited production crypto.
- Backend validation rejects bad message kind, bad hash shape, bad receipt type, and unsafe attachment metadata.

## Local reliability commands

```bash
make backend-test
make mobile-test
make verifier-test
make compose-smoke
```

`backend-test` uses Testcontainers for the full integration flow when Docker is available. Without Docker, that integration test is skipped by design.
