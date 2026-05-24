# Security Verification Notes

This repository now enforces production-shaped controls around persistence, sessions, audit chaining, ciphertext-only storage, bootstrap access, and Kubernetes deployment. It is not yet externally cryptography-audited.

## Implemented Controls

- API sessions use random bearer tokens stored only as SHA-256 hashes.
- WebAuthn ceremonies persist short-lived challenges and consume them once to block challenge replay.
- Sensitive routes require bearer authentication or a configured bootstrap token.
- The backend rejects metadata keys shaped like plaintext fields.
- Audit events are hash chained per organization.
- Key transparency entries are append-only hash-chain records.
- Actuator health probes are configured for deployment readiness and liveness.

## Required Before Production Claims

- Wire Yubico WebAuthn finish verification end to end, including client data, authenticator data, origin, RP ID, challenge, signature, and signature counter checks.
- Replace provider contracts for Signal/PQXDH/MLS with audited mobile/native bindings.
- Verify attachment key wrapping with external cryptographic review.
- Add a real object-storage signer for upload/download URLs.
- Run OWASP ASVS 5.0 review, dependency scanning, container scanning, and penetration testing.
- Review Kubernetes NetworkPolicy against the actual cluster CNI and database/network topology.
