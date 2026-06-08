# Threat Model

Crypta is an alpha-stage, security-focused scaffold. This threat model documents intended security direction and known gaps. It is not a substitute for independent review.

## Security Goals

- Keep message and attachment plaintext off the backend.
- Keep private keys, group secrets, and local encryption keys on client devices.
- Store and route ciphertext envelopes only.
- Support device trust decisions and revocation.
- Make public key replacement detectable through key transparency.
- Preserve tamper-evident audit records for security-relevant actions.
- Fail closed on incomplete authentication, unsupported cryptographic flows, and invalid ciphertext metadata.

## Non-Goals

- Guarantee security against compromised endpoints.
- Hide all metadata from the server.
- Provide plaintext recovery after all trusted devices are lost.
- Claim military-grade, unbreakable, production-ready, or independently audited security.
- Implement cryptographic primitives from scratch.
- Silently decrypt content for legal hold or administrators.

## Assets Protected

- Message plaintext.
- Attachment plaintext.
- Private identity keys.
- Session keys and group secrets.
- Device signing keys.
- Passkey credentials.
- Bearer session tokens.
- Key transparency history.
- Audit log integrity.
- Organization, room, membership, and policy metadata.

## Trusted Components

Trusted components are expected to be minimized and reviewed:

- User-controlled client devices before compromise.
- Native secure-key storage once implemented and audited.
- Audited cryptographic libraries or bindings once selected.
- Backend authorization logic for metadata and routing decisions.
- PostgreSQL integrity for stored metadata, subject to server compromise assumptions below.
- Maintainer release and secret-management process.

## Untrusted Components

- Public networks.
- Push notification providers.
- Object storage contents and operators.
- Database administrators with broad access.
- Cloud infrastructure operators.
- Logs and monitoring systems unless specifically hardened.
- Public issue trackers for vulnerability details.
- Any client device after malware, root, jailbreak, or physical compromise.

## Threat Actors

- Network attackers.
- Malicious insiders.
- Compromised administrators.
- Cloud or database operators with excessive access.
- Attackers stealing bearer tokens or bootstrap tokens.
- Attackers registering unauthorized devices.
- Attackers attempting public-key substitution.
- Attackers harvesting ciphertext for future cryptanalysis.
- Malware on mobile or desktop endpoints.
- Users leaking data through screenshots, forwarding, or external capture.

## Attack Surfaces

- REST API endpoints.
- Bootstrap token flow.
- Bearer session validation.
- WebAuthn/passkey registration and login flow.
- Device registration and attestation records.
- Key upload and key bundle fetch endpoints.
- Message and attachment metadata validation.
- Inbox retrieval and delivery receipts.
- Room membership and policy changes.
- Governance CQL and Smalltalk execution.
- Admin actions and emergency lockdown endpoints.
- PostgreSQL migrations and queries.
- Docker and Kubernetes deployment configuration.
- Logs, metrics, audit exports, and SIEM connectors.

## Metadata Risks

Even with end-to-end encryption, the server may see:

- Organization identifiers.
- User identifiers.
- Device identifiers.
- Room identifiers.
- Sender and recipient relationships.
- Message timing.
- Ciphertext size.
- Attachment object keys and sizes.
- Delivery receipt timing.
- Login and session activity.
- Device platform and trust state.
- Admin and policy activity.

Metadata minimization, padding, batching, sealed sender-style behavior, and traffic analysis defenses are not yet implemented.

## Device Compromise Risks

If a device is compromised, attackers may access plaintext after decryption, capture screen contents, steal active sessions, misuse unlocked keys, or impersonate the user until revocation. Native secure storage, biometric gating, device attestation, local database encryption, and compromise recovery are required future work.

## Server Compromise Assumptions

The design assumes the server should not be able to decrypt message or attachment content because it should not possess plaintext or private keys. A compromised server may still:

- Withhold, replay, reorder, or delete ciphertext envelopes.
- Serve malicious or stale public key bundles.
- Modify metadata and policy responses unless clients verify them.
- Expose metadata and audit logs.
- Attempt downgrade attacks.
- Abuse bootstrap, admin, or governance paths.

Key transparency, client verification, signed client state, audit monitoring, and independent review are required to reduce these risks.

## Key Compromise Scenarios

- Device private key compromise: revoke the device, rotate identity/prekeys, invalidate sessions, and notify affected rooms.
- Bearer token compromise: revoke sessions and investigate audit logs.
- Bootstrap token compromise: rotate immediately, audit tenant/user/device creation, and remove unauthorized records.
- Public key substitution: clients should detect unexpected key transparency changes once verification is complete.
- Group epoch compromise: rotate group state, remove compromised devices, and re-establish trust with verified members.

## What Crypta Currently Protects Against

The current scaffold provides partial protection against:

- Accidental backend storage of obvious plaintext metadata fields in selected paths.
- Plain bearer-token storage by hashing session tokens at rest.
- Unauthenticated access to most API routes.
- Basic device revocation at the backend session layer.
- Some audit-log tampering through hash-chain style records.
- Some public key history tampering through key transparency record structure.
- Challenge replay and origin-forgery attempts in passkey ceremonies through a Yubico-backed WebAuthn verification boundary.

## What Crypta Does Not Yet Protect Against

Crypta does not yet provide:

- Independent cryptographic assurance.
- External WebAuthn/passkey implementation review and attestation trust policy.
- Complete native secure-key storage.
- Complete Signal Protocol, PQXDH, Double Ratchet, or MLS implementation.
- Verified encrypted group messaging.
- Complete key transparency consistency proofs and external monitoring.
- Strong metadata privacy.
- Production-grade rate limiting, abuse detection, or anomaly detection.
- Complete object storage signing.
- Production MDM or SIEM integrations.
- Formal operational security runbooks.
- Deployment hardening for a specific production environment.

## Required Future Audits

- Cryptographic design and implementation audit.
- Mobile secure storage and native module audit.
- WebAuthn/passkey implementation audit.
- Authorization and access-control audit.
- Governance rule execution sandbox review.
- Deployment and Kubernetes hardening review.
- Logging, audit, and SIEM privacy review.
- Penetration testing and dependency/container scanning.
