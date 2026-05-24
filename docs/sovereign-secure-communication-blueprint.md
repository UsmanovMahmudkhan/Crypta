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

