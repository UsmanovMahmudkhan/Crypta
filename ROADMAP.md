# Roadmap

Crypta is currently an alpha-stage, security-focused scaffold. This roadmap is intentionally conservative.

## Current Status

The repository contains a Spring Boot backend scaffold, PostgreSQL migrations, a Flutter mobile client structure, Docker Compose, Kubernetes base manifests, and governance/policy prototypes. It is suitable for architecture exploration and controlled development, not production deployment.

## Implemented Foundation

- Backend API controllers for organizations, users, devices, WebAuthn challenge scaffolding, sessions, keys, messages, attachments, rooms, admin actions, emergency lockdowns, and governance evaluation.
- PostgreSQL schema managed by Flyway.
- Bearer-token session storage using token hashes.
- Bootstrap token path for initial setup.
- Ciphertext envelope storage for direct and group message records.
- Plaintext metadata guard for selected request metadata.
- Public key and prekey storage concepts, including PQ prekey records.
- Key transparency entry records using hash-chain style data.
- Tamper-evident audit log concepts.
- Flutter mobile structure with provider interfaces for crypto and native security boundaries.
- Docker Compose local environment and Kubernetes base manifests.
- CQL parser and Smalltalk governance rule evaluation prototypes.

## Short-Term Goals

- Replace WebAuthn finish placeholders with complete passkey verification.
- Add endpoint-level integration tests for every controller.
- Tighten authorization checks for room membership, device trust, admin actions, and governance routes.
- Expand plaintext rejection tests for messages, attachments, policy metadata, logs, and error handling.
- Document complete bootstrap and tenant setup workflow.
- Add API examples that use fake identifiers and fake ciphertext only.

## Mid-Term Goals

- Implement native iOS secure-key storage using Keychain/Secure Enclave boundaries.
- Implement native Android secure-key storage using Keystore/StrongBox boundaries.
- Integrate audited libraries or reviewed bindings for direct-message cryptography.
- Implement real encrypted group messaging verification around MLS-style group state.
- Add object storage signing for encrypted attachment upload and download.
- Add rate limiting, abuse controls, and operational monitoring.
- Add structured audit export adapters with redaction and backpressure behavior.
- Validate Kubernetes NetworkPolicy, ingress, TLS, resource limits, and secret injection in a real cluster.

## Long-Term Goals

- Complete formal threat model validation with external reviewers.
- Complete independent cryptographic audit and remediation.
- Complete mobile security review for key lifecycle, local storage, attestation, and screen protections.
- Add reproducible release processes, SBOM publication, signed artifacts, and provenance.
- Add load testing for message ingestion, inbox polling, key bundle fetches, audit export, and attachment metadata flows.
- Add operational security documentation for incident response, key rotation, secret rotation, admin recovery, backups, and disaster recovery.

## Not Yet Implemented

- Independent cryptographic audit.
- Complete WebAuthn/passkey finish verification.
- Full native mobile secure-key storage.
- Real encrypted group messaging verification.
- Production object storage integration for encrypted attachments.
- Production MDM integration.
- Production SIEM integration.
- Load testing.
- Operational security runbooks.
- Formal threat model validation.
- Secure deployment hardening for a specific cloud or Kubernetes environment.
- Complete metadata minimization strategy.
- Verified key transparency consistency proofs with external monitoring.

## Production Readiness Requirements

Before production use, Crypta needs:

- Independent cryptographic audit of protocol choices, bindings, key lifecycle, downgrade handling, and group messaging.
- Mobile security audit covering native key storage, biometric gating, device attestation, local encrypted storage, and compromise recovery.
- Full authentication and authorization review.
- Penetration testing and dependency/container scanning.
- Load and reliability testing under realistic messaging, attachment, and audit workloads.
- Deployment hardening for TLS, ingress, secrets, database access, network policies, logging, backups, monitoring, and incident response.
- Operational security documentation and maintainer runbooks.
- Explicit release criteria and rollback plans.
