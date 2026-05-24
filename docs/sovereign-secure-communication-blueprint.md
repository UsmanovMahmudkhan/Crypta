# Sovereign Executive Secure Communication Platform Blueprint

Verified reference anchors checked on 2026-05-24:

- [IETF RFC 9420: Messaging Layer Security](https://www.ietf.org/rfc/rfc9420)
- [Signal PQXDH specification](https://signal.org/docs/specifications/pqxdh/)
- [W3C WebAuthn Level 3](https://www.w3.org/TR/webauthn-3/)
- [Spring Security passkeys/WebAuthn](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html)
- [Apple Secure Enclave key protection](https://developer.apple.com/documentation/Security/protecting-keys-with-the-secure-enclave)
- [Android hardware-backed Keystore](https://source.android.google.cn/docs/security/features/keystore?hl=en)
- [Sigstore Rekor transparency log overview](https://docs.sigstore.dev/logging/overview/)

## 1. Executive Summary

This platform is a zero-trust, end-to-end encrypted communication system for high-risk organizations. It is not a normal chat app. The backend is a delivery, policy, identity, audit, and encrypted storage plane. It must not receive plaintext messages, plaintext attachments, private keys, group secrets, or decrypted message metadata except where a specific enterprise policy explicitly accepts the privacy/security tradeoff.

Direct messages use a Signal-style provider boundary with PQXDH-capable session setup and Double Ratchet message protection. Mission rooms use MLS for group state, commits, epoch changes, and membership-driven key rotation. The server stores public keys, signed prekeys, one-time prekeys, PQ prekeys, ciphertext envelopes, encrypted attachment objects, delivery receipts, policy records, and audit events.

The hardest parts are cryptographic correctness, device compromise recovery, key transparency, usable verification UX, MLS lifecycle correctness, and enterprise compliance features that do not destroy E2EE. These areas require external cryptographic and mobile security review before production.

## 2. Threat Model

Assumptions:

- Networks are monitored and actively attacked.
- Backend, PostgreSQL, Redis, and object storage can leak.
- Administrators and insiders can be malicious.
- Push notification providers are untrusted.
- User devices can be stolen, phished, jailbroken, rooted, or malware-infected.
- Attackers may attempt public-key substitution and harvest encrypted traffic for later cryptanalysis.
- Screenshots, photos of screens, compromised endpoints, and human forwarding cannot be fully prevented.

Primary attacker goals:

- Read message or attachment plaintext.
- Replace identity/prekeys to man-in-the-middle conversations.
- Add unauthorized devices to sensitive rooms.
- Abuse admin console or support paths.
- Infer sensitive activity from metadata.
- Prevent key rotation or force downgrade away from PQXDH/MLS.

## 3. Security Goals

- E2EE by default for direct and mission room messages.
- No plaintext messages or attachments on backend.
- No private, symmetric, MLS epoch, or group keys on backend.
- Forward secrecy and post-compromise security.
- PQ-aware direct key agreement using PQXDH where supported.
- MLS for group messaging with membership-change key rotation.
- Key transparency for public key replacement detection.
- Verified devices only for sensitive rooms.
- Hardware-backed key protection on iOS and Android.
- Minimal metadata, generic push notifications, immutable audit trails.
- Zero-trust authorization, least privilege, secure defaults, defense in depth.

## 4. Non-Goals

- Do not claim the product is unhackable, military-grade, or immune to endpoint compromise.
- Do not implement Signal Protocol, PQXDH, MLS, AEADs, KDFs, signatures, or Merkle-tree security from scratch in product code.
- Do not support plaintext recovery for users who lose all verified devices unless enterprise escrow is explicitly chosen with clear loss of E2EE guarantees.
- Do not promise prevention of external leaks, photography of screens, malware capture, or all screenshots on every OS.
- Do not make legal hold silently decrypt content. Legal hold can preserve ciphertext and metadata; plaintext legal hold requires a separate, explicit enterprise key escrow model and is not recommended for v1.

## 5. Full System Architecture

Core components:

- Flutter mobile clients with native security modules.
- Spring Boot backend modular monolith initially, separable into services later.
- PostgreSQL for durable identity, policy, key metadata, ciphertext envelopes, audit, and delivery receipts.
- Redis for ephemeral fanout, queues, WebSocket presence hints, and rate limiting; never as a plaintext cache.
- WebSocket over TLS 1.3 for realtime encrypted envelope delivery.
- Object storage for encrypted attachment blobs only.
- MDM connectors for device posture.
- SIEM export pipeline for security/audit events.
- Append-only key transparency log with client-verifiable proofs.

Trust boundaries:

- Client cryptographic boundary: all encryption/decryption and private key handling.
- Backend policy boundary: authorization, room membership, device trust, rate limiting, storage, delivery.
- Admin boundary: signed, audited, least-privileged, optionally dual-control.
- External boundary: FCM/APNs, MDM, SIEM, object storage, identity providers.

## 6. Backend Architecture

Recommended initial shape: modular Spring Boot monolith with strict package boundaries and service interfaces. Split into separately deployable services only after APIs and audit semantics stabilize.

Required services:

- `AuthService`: WebAuthn registration/login ceremony, session issuance, reauth for sensitive operations.
- `OrganizationService`: tenants, jurisdiction policy, enterprise settings.
- `UserService`: users, lifecycle, roles.
- `DeviceTrustService`: device registration, attestation verification, verified/revoked state.
- `KeyService`: identity public key upload, key bundle fetch, key state.
- `PreKeyService`: signed prekeys, one-time prekeys, PQ prekeys.
- `KeyTransparencyService`: append key events, expose inclusion/consistency proofs.
- `DirectMessageService`: accept and persist direct ciphertext envelopes only.
- `GroupMessageService`: accept and persist MLS ciphertext envelopes only.
- `MLSGroupService`: room-to-MLS group lifecycle, membership state, commit envelope delivery.
- `RoomPolicyService`: mission room policy validation and versioning.
- `WebSocketGateway`: authenticated device channel, no plaintext parsing.
- `DeliveryService`: offline queue and delivery receipt orchestration.
- `NotificationService`: generic push only.
- `AttachmentService`: encrypted attachment object lifecycle.
- `AuditService`: append-only metadata/admin/security audit.
- `AdminGovernanceService`: admin approvals, dual control, signed actions.
- `MDMIntegrationService`: MDM posture sync.
- `SIEMExportService`: normalized event export.
- `EmergencyLockdownService`: global/org/room/device lockdown controls.

Backend invariants:

- Reject any field named or shaped like `message_body`, `plaintext`, `file_plaintext`, or `decrypted_metadata`.
- Enforce room/device policy before accepting ciphertext.
- Never log request bodies for key, message, or attachment routes.
- Use separate authorization checks for user, device, admin, and service actor.
- Use tamper-evident audit hashes for admin and policy actions.

## 7. Mobile Architecture

Flutter layers:

- Presentation: mission room UI, verification UI, safety numbers, emergency lock UI, admin UX where allowed.
- Application: workflows, state machines, retry policy, sync orchestration.
- Domain: users, devices, key bundles, rooms, room policies, message envelopes.
- Infrastructure: REST API, WebSocket client, object upload/download, MDM state adapter.
- Crypto bridge: `DirectCryptoProvider`, `GroupCryptoProvider`, `KeyTransparencyVerifier`.
- Secure storage: `SecureStorageProvider` backed by native modules.
- WebSocket layer: device-authenticated realtime encrypted envelope stream.
- Local encrypted database: SQLCipher/Drift or native encrypted storage, storing ciphertext and local indexes only.
- Notification layer: generic push handling and local unlock flow.

Native module boundaries:

- iOS: Secure Enclave/Keychain wrapper, biometric unlock, DeviceCheck/App Attest where applicable, jailbreak signals, secure screen controls where available.
- Android: Keystore/StrongBox wrapper, BiometricPrompt, hardware key attestation, Play Integrity/enterprise attestation where applicable, root signals, `FLAG_SECURE`.

Flutter is not suitable for low-level key generation, hardware attestation verification, biometric-gated key unwrap, jailbreak/root detection, or reliable secure screen controls. These require native implementations and security review.

## 8. Cryptographic Architecture

Direct messaging:

- Use Signal Protocol style sessions with PQXDH-capable initial key agreement and Double Ratchet message protection.
- Store identity public keys, signed prekeys, one-time prekeys, and PQ prekeys on the server.
- Use associated data binding: sender device, recipient device, org, conversation id, key versions, and protocol version.
- Clients must fail closed on downgrade from PQXDH where a recipient device advertises PQ support.

Group messaging:

- Use MLS per RFC 9420 for mission rooms.
- Treat room membership changes as MLS commits with epoch transitions.
- Server stores MLS public group metadata and opaque MLS messages only; clients verify group state.

Attachments:

- Client generates random content encryption key.
- Client encrypts file locally with AEAD using room/message associated data.
- Content key is wrapped per recipient/session/group using E2EE mechanisms.
- Server stores ciphertext blob and crypto metadata only.

Required provider interfaces:

- `DirectCryptoProvider`
- `GroupCryptoProvider`
- `KeyTransparencyVerifier`
- `SecureStorageProvider`
- `DeviceAttestationProvider`

External audit required:

- Library choice and binding code.
- PQXDH downgrade handling.
- MLS epoch handling.
- Attachment key wrapping.
- Local database encryption and key lifecycle.

## 9. Database Schema

The initial PostgreSQL schema is implemented in [V1__initial_secure_comm_schema.sql](/Users/mahmudkhonusmonov/Documents/java/src/main/resources/db/migration/V1__initial_secure_comm_schema.sql).

Tables included:

- `organizations`
- `users`
- `user_roles`
- `devices`
- `device_attestations`
- `webauthn_credentials`
- `identity_public_keys`
- `signed_prekeys`
- `one_time_prekeys`
- `pq_prekeys`
- `key_transparency_entries`
- `rooms`
- `room_members`
- `room_policies`
- `encrypted_messages`
- `encrypted_attachments`
- `message_delivery_receipts`
- `audit_events`
- `admin_actions`
- `emergency_lockdowns`
- `mdm_devices`
- `siem_exports`

Schema rules:

- No plaintext `message_body` column.
- No plaintext attachment column.
- `encrypted_messages.ciphertext` and `encrypted_attachments.object_key` reference ciphertext only.
- Crypto metadata is separate JSONB and must contain no plaintext user content.
- Retention is represented with `expires_at`, `retention_expires_at`, and policy versions.
- Soft delete is limited to message/attachment lifecycle where retention policy allows it; audit and key transparency entries are append-only.

## 10. API Design

All APIs require TLS 1.3, request IDs, device-bound sessions after login, rate limiting, structured audit, and body logging disabled for sensitive routes.

### Organization Setup

`POST /api/v1/organizations`

- Request: `name`, `jurisdiction`, `externalTenantId`.
- Response: `id`, `createdAt`.
- Authentication: bootstrap owner or platform operator.
- Authorization: tenant creation permission.
- Security notes: creates first audit root; no default weak policy.
- Failure cases: duplicate tenant, invalid jurisdiction, unauthorized bootstrap actor.

### User Registration

`POST /api/v1/users`

- Request: `organizationId`, `email`, `displayName`, `roles`.
- Response: `id`, `createdAt`.
- Authentication: admin WebAuthn session.
- Authorization: org admin with user-management permission.
- Security notes: invite only; no password fallback.
- Failure cases: duplicate email, invalid role, org locked, admin reauth required.

### Device Registration

`POST /api/v1/devices`

- Request: `userId`, `platform`, `deviceName`, `attestationFormat`, `attestationObjectBase64`, `deviceSigningPublicKeyBase64`.
- Response: `id`, `createdAt`.
- Authentication: user WebAuthn ceremony plus existing device approval for high-risk orgs.
- Authorization: user can add own device subject to org policy.
- Security notes: starts as pending until attestation and verification pass.
- Failure cases: attestation invalid, MDM noncompliant, device limit reached, org lockdown.

### WebAuthn Registration

`POST /api/v1/webauthn/registration/options/{userId}`

- Request: path `userId`.
- Response: challenge and public key credential options.
- Authentication: invite token or existing authenticated session.
- Authorization: user registration scope.
- Security notes: short challenge TTL; bind to origin/RP ID.
- Failure cases: expired invite, invalid origin, user disabled.

`POST /api/v1/webauthn/registration/finish`

- Request: `userId`, `credentialJson`.
- Response: empty success.
- Authentication: challenge-bound ceremony.
- Authorization: same user or approved admin-assisted flow.
- Security notes: store credential public key and attestation metadata.
- Failure cases: challenge mismatch, replay, unsupported authenticator policy.

### WebAuthn Login

`POST /api/v1/webauthn/login/options/{userId}`

- Request: path `userId`.
- Response: challenge and allowed credentials.
- Authentication: none before ceremony.
- Authorization: login allowed for active user.
- Security notes: generic errors to reduce enumeration.
- Failure cases: user disabled, too many attempts, org lockdown.

`POST /api/v1/webauthn/login/finish`

- Request: `userId`, `credentialJson`.
- Response: device-bound session token and refresh metadata in production.
- Authentication: WebAuthn assertion.
- Authorization: active user, compliant device.
- Security notes: require step-up for admin and sensitive room access.
- Failure cases: signature invalid, cloned credential counter anomaly, device revoked.

### Key Uploads

`POST /api/v1/keys/identity`

- Request: `deviceId`, `algorithm`, `publicKeyBase64`, `signatureBase64`.
- Response: empty success.
- Authentication: device-bound session.
- Authorization: device can upload only its own key.
- Security notes: append key transparency entry; reject unsigned replacement.
- Failure cases: stale device, invalid signature, downgrade algorithm, log append failure.

`POST /api/v1/keys/signed-prekeys`

- Request: `deviceId`, `keyId`, `algorithm`, `publicKeyBase64`, `signatureBase64`.
- Response: empty success.
- Authentication: device-bound session.
- Authorization: own device only.
- Security notes: signed by identity key; expire and rotate.
- Failure cases: signature invalid, too many active prekeys.

`POST /api/v1/keys/one-time-prekeys`

- Request: `deviceId`, `keyId`, `algorithm`, `publicKeyBase64`.
- Response: empty success.
- Authentication: device-bound session.
- Authorization: own device only.
- Security notes: claim atomically once.
- Failure cases: duplicate key id, quota exceeded.

`POST /api/v1/keys/pq-prekeys`

- Request: `deviceId`, `keyId`, `algorithm`, `publicKeyBase64`, `signatureBase64`.
- Response: empty success.
- Authentication: device-bound session.
- Authorization: own device only.
- Security notes: distinguish one-time PQ prekeys and last-resort signed PQ prekey.
- Failure cases: unsupported PQ algorithm, invalid signature, quota exceeded.

### Key Bundle Fetch

`GET /api/v1/keys/bundle/{userId}`

- Request: path `userId`.
- Response: identity keys and available prekeys per verified device.
- Authentication: active device session.
- Authorization: sender has permission to contact recipient.
- Security notes: include transparency proof reference; atomically claim one-time prekeys.
- Failure cases: no verified recipient device, policy blocks contact, prekey exhaustion.

### Key Transparency Proof Fetch

`GET /api/v1/key-transparency/users/{userId}/proof`

- Request: path `userId`, optional tree head.
- Response: inclusion proof, consistency proof, signed tree head.
- Authentication: active session.
- Authorization: same org or allowed contact graph.
- Security notes: client verifies proof before trusting key changes.
- Failure cases: proof unavailable, tree head mismatch, org isolation violation.

### Room Creation

`POST /api/v1/mission-rooms`

- Request: `organizationId`, `name`, `classification`, `policy`.
- Response: `id`, `createdAt`.
- Authentication: WebAuthn step-up for creator.
- Authorization: mission-room create permission.
- Security notes: initial MLS group created client-side; server stores policy and opaque MLS identifiers.
- Failure cases: invalid classification, policy weaker than org baseline, lockdown active.

### Room Policy Update

`POST /api/v1/mission-rooms/policy`

- Request: `roomId`, `policy`, `reason`.
- Response: empty success.
- Authentication: admin step-up.
- Authorization: room policy admin; dual approval for high classification.
- Security notes: creates new policy version and room event; may trigger MLS commit.
- Failure cases: weaker-than-baseline policy, missing approval, active legal/lockdown conflict.

### Member Invite

`POST /api/v1/mission-rooms/members/invite`

- Request: `roomId`, `userId`, `allowedDeviceIds`, `reason`.
- Response: empty success.
- Authentication: room admin step-up.
- Authorization: invite permission and device policy.
- Security notes: invited client receives MLS welcome encrypted to verified device.
- Failure cases: unverified device, classification mismatch, MDM noncompliant.

### Member Removal

`POST /api/v1/mission-rooms/members/remove`

- Request: `roomId`, `userId`, `reason`.
- Response: empty success.
- Authentication: room admin step-up.
- Authorization: remove permission.
- Security notes: must trigger MLS commit and epoch change; removed devices stop receiving new messages.
- Failure cases: missing commit, target is last owner, lockdown constraints.

### Encrypted Attachment Upload

`POST /api/v1/attachments`

- Request: `roomId`, `objectKey`, `ciphertextSha256`, `ciphertextBytes`, `cryptoMetadata`.
- Response: `id`, `createdAt`, production presigned upload URL.
- Authentication: active verified device.
- Authorization: room attachment policy.
- Security notes: server validates size/hash only; no content scanning unless client-side or explicit enterprise gateway.
- Failure cases: attachment disabled, size limit, hash mismatch, storage unavailable.

### Encrypted Attachment Download

`GET /api/v1/attachments/{attachmentId}/download`

- Request: path `attachmentId`.
- Response: presigned download URL and crypto metadata.
- Authentication: active verified device.
- Authorization: room membership and policy.
- Security notes: object remains ciphertext; client verifies hash and decrypts.
- Failure cases: expired retention, revoked member, lockdown active, object missing.

### Admin Actions

`POST /api/v1/admin/actions`

- Request: signed admin action envelope.
- Response: action id and approval state in production.
- Authentication: admin WebAuthn step-up.
- Authorization: least-privilege admin role.
- Security notes: signed action, dual-control for destructive operations, tamper-evident audit.
- Failure cases: invalid signature, missing approval, policy forbids action.

### Audit Export

`POST /api/v1/admin/audit/export`

- Request: `organizationId`, `sinkType`, `from`, `to`.
- Response: export job id in production.
- Authentication: compliance admin WebAuthn step-up.
- Authorization: audit export permission.
- Security notes: exports metadata/admin/security events, never message plaintext.
- Failure cases: sink unavailable, invalid range, legal restriction.

### Emergency Lockdown

`POST /api/v1/admin/emergency-lockdowns`

- Request: `organizationId`, `roomId`, `reason`, `scope`.
- Response: lockdown id in production.
- Authentication: emergency admin step-up, optional break-glass dual approval.
- Authorization: emergency permission.
- Security notes: stops new sessions, room sends, attachment access, or device activity according to scope.
- Failure cases: invalid scope, approval missing, actor not break-glass eligible.

## 11. WebSocket Event Design

All WebSocket connections are authenticated as a specific device. Payloads are envelopes with `type`, `eventId`, `serverTime`, `recipientDeviceId`, and type-specific body. Server must not decrypt ciphertext fields.

### `encrypted_direct_message`

- Request/event fields: `messageId`, `senderUserId`, `senderDeviceId`, `recipientUserId`, `recipientDeviceId`, `ciphertext`, `cryptoMetadata`.
- Response fields: ack `messageId`, `acceptedAt`.
- Authentication: active device WebSocket session.
- Authorization: sender may contact recipient device.
- Security notes: ciphertext only; metadata minimized.
- Failure cases: recipient blocked, device revoked, malformed envelope.

### `encrypted_group_message`

- Fields: `messageId`, `roomId`, `mlsGroupId`, `epoch`, `senderDeviceId`, `ciphertext`, `cryptoMetadata`.
- Response: ack `messageId`, `acceptedAt`.
- Authentication: active verified device.
- Authorization: active room member and allowed device.
- Security notes: enforce policy before persistence.
- Failure cases: stale epoch, removed member, lockdown active.

### `delivery_receipt`

- Fields: `messageId`, `deviceId`, `receiptType`.
- Response: ack.
- Authentication: recipient device.
- Authorization: receipt for own delivery only.
- Security notes: no read receipt by default for high-sensitivity rooms unless policy allows.
- Failure cases: unknown message, receipt disabled.

### `typing_signal_privacy_safe`

- Fields: `roomId` or `recipientDeviceId`, `coarseState`, `ttlMillis`.
- Response: none or ack.
- Authentication: active device.
- Authorization: same room/contact.
- Security notes: optional, short TTL, disabled for high classification by default.
- Failure cases: policy disabled, rate limit.

### `device_revoked`

- Fields: `deviceId`, `userId`, `revokedAt`, `reasonCode`.
- Response: client ack.
- Authentication: server-to-device event.
- Authorization: target user/org devices.
- Security notes: clients wipe local secrets for revoked device; peers distrust keys.
- Failure cases: offline device receives on next sync.

### `key_changed`

- Fields: `userId`, `deviceId`, `keyVersion`, `transparencyProofRef`.
- Response: client proof verification result in production telemetry.
- Authentication: server-to-device event.
- Authorization: contacts/rooms affected.
- Security notes: clients must verify transparency before trusting.
- Failure cases: proof invalid triggers safety warning and send block.

### `room_policy_changed`

- Fields: `roomId`, `policyVersion`, `changedBy`, `effectiveAt`.
- Response: ack and optional MLS commit status.
- Authentication: server-to-device.
- Authorization: room members.
- Security notes: policy changes may require client-side state transition.
- Failure cases: client policy cache stale.

### `emergency_lockdown_started`

- Fields: `lockdownId`, `scope`, `roomId`, `startedAt`, `reasonCode`.
- Response: ack and local enforcement status.
- Authentication: server-to-device.
- Authorization: affected devices.
- Security notes: disable sends/downloads and require reauth.
- Failure cases: offline clients enforce at sync.

### `emergency_lockdown_ended`

- Fields: `lockdownId`, `endedAt`, `policyVersion`.
- Response: ack.
- Authentication: server-to-device.
- Authorization: affected devices.
- Security notes: require fresh policy sync before resuming.
- Failure cases: stale client remains locked until sync.

## 12. Key Transparency Design

Model:

- Append-only log of identity key and device trust changes.
- Each entry is canonicalized, hashed, indexed, and included in a Merkle tree.
- Server signs tree heads; independent monitors verify consistency.
- Clients verify inclusion and consistency proofs before accepting new keys.

Entry contents:

- `organizationId`, `subjectUserId`, `subjectDeviceId`, `entryType`, key hash, previous key hash, device state, timestamp, issuer signature.

Mitigations:

- Key substitution: client detects unexpected key changes or missing log inclusion.
- Split-view attack: require gossip/monitoring, checkpoint pinning, and SIEM alerting on inconsistent tree heads.
- Malicious admin: admin can request key actions but cannot forge device signatures or hide append-only evidence if monitors are effective.

Hard truth: key transparency is only valuable if clients actually verify proofs and independent monitors watch the log.

## 13. Secure File Storage Design

Upload flow:

1. Client encrypts file locally with random content key.
2. Client computes ciphertext hash.
3. Client wraps content key for recipients or MLS group.
4. Backend issues presigned upload target.
5. Object storage receives ciphertext only.
6. Backend stores object key, ciphertext hash, size, retention, crypto metadata.

Download flow:

1. Client requests attachment access.
2. Backend checks room membership, device trust, retention, lockdown.
3. Backend returns presigned URL and crypto metadata.
4. Client downloads ciphertext, verifies hash, unwraps content key, decrypts locally.

Do not use server-side DLP/content scanning unless the organization explicitly accepts client-side scanning or a controlled decryption gateway that breaks pure E2EE.

## 14. MDM Integration Design

Supported posture signals:

- Device enrollment state.
- OS version and patch level.
- Screen lock and biometric policy.
- Jailbreak/root risk signal.
- App version minimum.
- Hardware-backed key availability.
- Remote wipe eligibility.

Providers:

- Microsoft Intune, Jamf, VMware Workspace ONE, MobileIron/Ivanti, Android Enterprise, Apple Business Manager integrations.

Enforcement:

- MDM state informs `devices.mdm_compliant`.
- Sensitive rooms require verified device plus compliant MDM state.
- Noncompliant devices lose room access and attachment download until remediated.

## 15. SIEM Integration Design

Export normalized events:

- Admin action created/approved/executed.
- Device registered/verified/revoked.
- Key uploaded/changed/proof failure.
- Room created/policy changed/member changed.
- Emergency lockdown started/ended.
- Authentication success/failure/risk.
- MDM posture changes.

Never export:

- Plaintext message content.
- Plaintext attachment content.
- Private keys, content keys, group keys.
- Full ciphertext unless explicitly configured for forensic retention.

Sinks:

- Splunk HEC, Elastic, Microsoft Sentinel, Chronicle, generic syslog/CEF/JSON over mTLS.

## 16. Emergency Lockdown Design

Scopes:

- Organization-wide: block new sends, key uploads, attachment downloads, admin changes except break-glass.
- Room-level: freeze membership, block sends/downloads, require MLS state resync after unlock.
- User/device-level: revoke sessions, require re-verification, optionally remote wipe.

Actions:

- Emit WebSocket `emergency_lockdown_started`.
- Revoke or suspend active sessions based on scope.
- Stop delivery queue release for affected scope.
- Preserve audit and ciphertext retention according to policy.
- Export high-priority SIEM event.

Ending lockdown requires explicit signed admin action and fresh client policy sync.

## 17. Deployment Architecture

Production baseline:

- Kubernetes or hardened VM deployment with separate namespaces/security groups.
- TLS 1.3 everywhere; mTLS for internal service calls and SIEM/MDM connectors.
- PostgreSQL with encryption at rest, PITR, strict network access, row-level tenancy checks in app.
- Redis with TLS, auth, no persistence for sensitive queues unless encrypted envelopes only.
- Object storage with private buckets, presigned URLs, object lock where required.
- HSM/KMS for server signing keys used by transparency log and audit hash signing; never for message keys.
- WAF and DDoS protection at the edge.
- Centralized secrets manager with rotation.
- Immutable infrastructure and signed containers.

Operational controls:

- Separate production/admin networks.
- Just-in-time admin access.
- Break-glass accounts with hardware security keys.
- Mandatory audit export and alerting.
- Disaster recovery tested with encrypted backup restore.

## 18. Security Risks and Mitigations

Risk: endpoint compromise can expose plaintext after decryption.

- Mitigation: hardware-backed keys, biometric unlock, local DB encryption, MDM posture, jailbreak/root signals, short session TTLs, remote wipe.
- Cannot guarantee: malware-free endpoints or prevention of screen photography.

Risk: malicious key substitution.

- Mitigation: device signatures, key transparency, safety numbers, independent monitors, send-block on proof failure.

Risk: push metadata leakage.

- Mitigation: generic pushes only, no sender/room/message preview, fetch after unlock.

Risk: admin abuse.

- Mitigation: least privilege, dual control, signed admin actions, tamper-evident audit, SIEM alerting.

Risk: MLS implementation mistakes.

- Mitigation: audited MLS library, conformance tests, external cryptographic review, epoch-state fuzzing.

Risk: legal hold pressure weakens E2EE.

- Mitigation: v1 legal hold preserves ciphertext and audit metadata only; any plaintext escrow is a separately branded enterprise mode with explicit warnings.

Dangerous mistakes:

- Logging plaintext or request bodies.
- Implementing crypto primitives manually.
- Allowing unverified devices into sensitive rooms.
- Reusing attachment keys.
- Treating jailbreak/root detection as a guarantee.
- Trusting server-provided key bundles without transparency verification.
- Sending notification previews.

## 19. Implementation Roadmap

Phase 0: Threat model, architecture, library selection, security review plan.

- Deliver threat model, data classification, crypto library shortlist, mobile native feasibility spike, audit plan.

Phase 1: Authentication, organization, users, devices, WebAuthn, basic admin.

- Implement org/user lifecycle, WebAuthn, device registration, attestation storage, admin audit.

Phase 2: Direct E2EE messaging with Signal-style provider interface.

- Integrate audited provider or native bridge; key bundles, prekeys, ciphertext APIs, no plaintext backend.

Phase 3: WebSocket delivery, offline queue, Redis events, encrypted local storage.

- Device WebSocket sessions, offline envelopes, delivery receipts, local encrypted DB.

Phase 4: Key transparency log and device verification.

- Merkle log, signed tree heads, client proof verification, safety number UX.

Phase 5: Encrypted attachments and object storage.

- Client encryption, wrapped keys, presigned upload/download, retention.

Phase 6: Mission rooms and policy engine.

- Classification, verified-device controls, screenshot best-effort, forwarding restrictions, auto-delete, legal hold metadata mode.

Phase 7: MLS group encryption.

- MLS provider integration, group lifecycle, welcome/commit handling, membership change key rotation.

Phase 8: MDM, SIEM, emergency lockdown.

- Device posture enforcement, SIEM exports, lockdown workflows, remote wipe hooks.

Phase 9: Hardening, penetration testing, cryptographic audit, compliance preparation.

- External audits, mobile reverse engineering review, fuzzing, load tests, incident runbooks, compliance mapping.

## 20. Repository Structure

Initial scaffold:

```text
.
├── docs/
│   └── sovereign-secure-communication-blueprint.md
├── mobile/
│   ├── README.md
│   └── lib/src/
│       ├── domain/crypto_provider_contracts.dart
│       └── native/native_security_module.dart
