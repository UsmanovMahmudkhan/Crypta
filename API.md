# API Overview

This is a simplified manual overview of the REST API implemented by the Spring Boot controllers. Swagger/OpenAPI support is present through `springdoc-openapi`; when the backend is running, generated docs should be available under `/v3/api-docs` and Swagger UI under `/swagger-ui`.

Authentication notes:

- `/actuator/health`, `/v3/api-docs/**`, and Swagger UI are public.
- WebAuthn login start/finish is public, but only verified credentials can issue sessions.
- WebAuthn registration start/finish requires `X-Bootstrap-Token`, same-user bearer auth, or bearer admin auth.
- `POST /api/v1/organizations` requires `X-Bootstrap-Token` or bearer `PLATFORM_OPERATOR`.
- `POST /api/v1/users`, `POST /api/v1/devices`, and `POST /api/v1/bootstrap/sessions` support the bootstrap onboarding flow.
- `/api/v1/admin/**` and `/api/v1/governance/**` require bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`; bootstrap is not accepted.
- Other API routes require a bearer session token unless otherwise noted.

## Organizations

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/organizations` | Create an organization tenant. | `X-Bootstrap-Token` or bearer `PLATFORM_OPERATOR`. | `name`, `jurisdiction`, optional `externalTenantId`. | `id`, `createdAt`. | Intended for controlled onboarding/platform use. |

## Users

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/users` | Register a user in an organization. | `X-Bootstrap-Token`, bearer `PLATFORM_OPERATOR`, or bearer `ORG_ADMIN`. | `organizationId`, `email`, `displayName`, optional `roles`. | `id`, `createdAt`. | Bearer actors are restricted to their organization unless platform-scoped. |

## Devices

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/devices` | Register a device and attestation record. | `X-Bootstrap-Token` or bearer token. | `userId`, `platform`, `deviceName`, `attestationFormat`, `attestationObjectBase64`, `deviceSigningPublicKeyBase64`. | `id`, `createdAt`. | Bootstrap may register onboarding devices; bearer actors are same-user or admin scoped. |
| `DELETE` | `/api/v1/devices/{deviceId}` | Revoke a device. | Bearer token. | Path `deviceId`. | Empty response. | Revokes device trust state and API sessions for that device. |

## WebAuthn / Passkeys

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/webauthn/registration/options/{userId}` | Start passkey registration. | `X-Bootstrap-Token`, same-user bearer, or bearer admin. | Path `userId`. | Challenge and public key credential options. | Persists Yubico creation options with a short-lived challenge. |
| `POST` | `/api/v1/webauthn/registration/finish` | Finish passkey registration. | `X-Bootstrap-Token`, same-user bearer, or bearer admin. | `userId`, optional `deviceId`, `credentialJson` containing the browser credential response. | Session response. | Uses Yubico ceremony verification and stores only `VERIFIED` credentials for login. |
| `POST` | `/api/v1/webauthn/login/options/{userId}` | Start passkey login. | Public. | Path `userId`. | Challenge and public key credential options. | Requires an existing `VERIFIED` credential; legacy demo credentials are excluded. |
| `POST` | `/api/v1/webauthn/login/finish` | Finish passkey login. | Public. | `userId`, optional `deviceId`, `credentialJson` containing the browser assertion response. | Session response. | Validates challenge, origin, ceremony type, replay state, and registered credential membership. |

## Bootstrap Sessions

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/bootstrap/sessions` | Issue a bearer session for a bootstrapped user/device. | `X-Bootstrap-Token`. | `userId`, `deviceId`. | `userId`, `deviceId`, `token`, `expiresAt`. | Bootstrap is onboarding-only and does not grant admin/governance access. Rotate after setup. |

## Keys

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/keys/identity` | Upload an identity public key. | Bearer token. | `deviceId`, `algorithm`, `publicKeyBase64`, `signatureBase64`. | Empty response. | Appends a key transparency event. |
| `POST` | `/api/v1/keys/signed-prekeys` | Upload a signed prekey. | Bearer token. | `deviceId`, `keyId`, `algorithm`, `publicKeyBase64`, optional `signatureBase64`. | Empty response. | Protocol validation is not complete. |
| `POST` | `/api/v1/keys/one-time-prekeys` | Upload a one-time prekey. | Bearer token. | `deviceId`, `keyId`, `algorithm`, `publicKeyBase64`, optional `signatureBase64`. | Empty response. | Intended for Signal-style setup concepts. |
| `POST` | `/api/v1/keys/pq-prekeys` | Upload a PQ prekey. | Bearer token. | `deviceId`, `keyId`, `algorithm`, `publicKeyBase64`, optional `signatureBase64`. | Empty response. | PQXDH-style concept only; audit required. |
| `GET` | `/api/v1/keys/bundle/{userId}` | Fetch active public key bundle. | Bearer token. | Path `userId`. | Identity keys, atomically claimed one-time/PQ prekeys, and transparency checkpoint summary. | Clients must verify key changes and block PQ downgrades. |
| `GET` | `/api/v1/keys/transparency/{userId}` | Fetch key transparency proof records. | Bearer token. | Path `userId`. | Latest log index, signed tree head, consistency proof, checkpoint ID, proof version, and entry proofs. | Proofs come from the Go verifier when configured, with deterministic local fallback for development. |

## Messages

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/messages/direct` | Store a direct ciphertext envelope. | Bearer token. | `organizationId`, sender IDs, recipient IDs, `messageKind`, `ciphertextBase64`, `ciphertextSha256`, `cryptoMetadata`. | `id`, `createdAt`. | Sender must match authenticated session; metadata is checked for obvious plaintext fields. |
| `POST` | `/api/v1/messages/groups` | Store a group ciphertext envelope. | Bearer token. | `organizationId`, `roomId`, sender IDs, `messageKind`, `ciphertextBase64`, `ciphertextSha256`, `cryptoMetadata`. | `id`, `createdAt`. | Requires `messageKind` of `MLS_GROUP`; real MLS verification is not complete. |
| `GET` | `/api/v1/messages/inbox` | Fetch ciphertext envelopes for a device. | Bearer token. | Query `deviceId`, optional `after`, optional `limit`. | List of ciphertext envelope records. | Requested device must match authenticated session. |
| `POST` | `/api/v1/messages/receipts` | Record a delivery receipt. | Bearer token. | `messageId`, `deviceId`, `receiptType`. | Empty response. | Receipt metadata may reveal delivery timing. |

## Mission Rooms

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/mission-rooms` | Create a room. | Bearer token. | `organizationId`, `name`, `classification`, `policy`. | `id`, `createdAt`. | Creator becomes owner; policy requires review. |
| `POST` | `/api/v1/mission-rooms/policy` | Add a room policy version. | Bearer token. | `roomId`, `policy`, `reason`. | Empty response. | Policy metadata is checked for obvious plaintext fields. |
| `POST` | `/api/v1/mission-rooms/members/invite` | Invite or reactivate a member. | Bearer token. | `roomId`, `userId`, optional `allowedDeviceIds`, `reason`. | Empty response. | MLS commit verification is not complete. |
| `POST` | `/api/v1/mission-rooms/members/remove` | Remove a member. | Bearer token. | `roomId`, `userId`, optional `allowedDeviceIds`, `reason`. | Empty response. | Clients still need verified group state transitions. |

## Attachments

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/attachments` | Create encrypted attachment metadata. | Bearer token. | `roomId`, `objectKey`, `ciphertextSha256`, `ciphertextBytes`, `cryptoMetadata`. | `id`, `createdAt`. | Attachment plaintext and content keys must stay client-side. |
| `GET` | `/api/v1/attachments/{attachmentId}/download` | Fetch encrypted attachment download metadata. | Bearer token. | Path `attachmentId`. | Object key, hash, size, crypto metadata, signed grant token, grant signature, expiry, and object-storage download mode. | Grant is metadata-only and never exposes plaintext. |

## Admin

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/admin/actions` | Record a signed admin action envelope. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | Raw signed action envelope JSON string. | Empty response. | Envelope validation and dual control need hardening. |
| `POST` | `/api/v1/admin/audit/export` | Request audit export. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | `organizationId`, `sinkType`, optional `from`, optional `to`. | Empty response. | SIEM sink is configured out of band. |
| `GET` | `/api/v1/admin/security/verifier` | Check verifier health. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | None. | Verifier status and mode. | Production readiness fails if verifier configuration is missing. |
| `GET` | `/api/v1/admin/security/transparency-monitor/{organizationId}` | Check key transparency monitor status. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | Path `organizationId`. | Entry count, latest log index, latest entry time, verifier mode. | Operational visibility endpoint. |
| `POST` | `/api/v1/admin/security/audit/verify/{organizationId}` | Ask verifier to validate audit-chain checkpoint state. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | Path `organizationId`. | Verification result and checkpoint ID. | Deeper audit replay remains a future verifier enhancement. |
| `POST` | `/api/v1/admin/emergency-lockdowns` | Start org or room lockdown. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | `organizationId`, optional `roomId`, `reason`, `scope`. | Empty response. | Blocks selected activity by org/room. |
| `POST` | `/api/v1/admin/emergency-lockdowns/{lockdownId}/end` | End a lockdown. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | Optional body with `reason`. | Empty response. | Requires careful audit review. |

## Governance

| Method | Path | Purpose | Auth | Request | Response | Security notes |
| ------ | ---- | ------- | ---- | ------- | -------- | -------------- |
| `POST` | `/api/v1/governance/cql/parse` | Parse a CQL query. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | Raw query string. | Parsed query object. | Bootstrap is rejected; parser is experimental. |
| `POST` | `/api/v1/governance/cql/execute` | Execute a CQL query. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | Raw query string. | List of allowlisted result rows. | Execution is organization-scoped, table/column allowlisted, parameterized, length-limited, and result-capped. |
| `POST` | `/api/v1/governance/smalltalk/evaluate` | Evaluate a Smalltalk policy script. | Bearer `PLATFORM_OPERATOR` or `ORG_ADMIN`. | `script`, `context`. | Evaluation result. | Disabled by default and in production unless explicitly enabled; applies script length and recursive plaintext-shaped input/output checks. |
