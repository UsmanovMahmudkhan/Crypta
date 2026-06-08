# Security Verification Notes

This repository contains alpha-stage controls around persistence, sessions, audit chaining, ciphertext-only storage, bootstrap access, and Kubernetes deployment scaffolding. It is not yet externally cryptography-audited.

## Implemented Controls

- API sessions use random bearer tokens stored only as SHA-256 hashes.
- WebAuthn ceremonies persist short-lived Yubico request options, consume challenges once, and store only verified credentials for login.
- Bootstrap authentication is limited to onboarding; sensitive application, admin, governance, key, room, message, attachment, and device-revocation routes require bearer sessions.
- The backend rejects metadata keys shaped like plaintext fields, including nested map/list/array structures.
- Audit events are hash chained per organization.
- Key transparency entries are append-only hash-chain records.
- CQL governance execution is organization-scoped, table/column allowlisted, parameterized, length-limited, and result-capped.
- Actuator health probes are configured for deployment readiness and liveness.

## Required Before Production Claims

- Complete external WebAuthn/passkey implementation review, including attestation trust policy and browser/client ceremony testing.
- Replace provider contracts for Signal/PQXDH/MLS with audited mobile/native bindings.
- Verify attachment key wrapping with external cryptographic review.
- Add a real object-storage signer for upload/download URLs.
- Run OWASP ASVS 5.0 review, dependency scanning, container scanning, and penetration testing.
- Review Kubernetes NetworkPolicy against the actual cluster CNI and database/network topology.
