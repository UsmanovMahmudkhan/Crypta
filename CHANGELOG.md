# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project aims to follow semantic versioning once releases stabilize.

## [0.1.0-alpha] - Unreleased

### Added

- Initial Spring Boot backend scaffold for a zero-trust, end-to-end encryption platform.
- PostgreSQL schema and Flyway migrations for organizations, users, devices, sessions, key records, ciphertext envelopes, encrypted attachments, audit events, and governance records.
- WebAuthn/passkey challenge scaffolding and bootstrap session flow.
- Signal-style identity key, signed prekey, one-time prekey, and PQ prekey storage concepts.
- Key transparency record structure using append-only hash-chain style entries.
- Direct and group ciphertext envelope ingestion endpoints.
- Flutter mobile client scaffold with crypto provider and native security module boundaries.
- Docker Compose setup for local backend and PostgreSQL development.
- Kubernetes base manifests for deployment experimentation.
- CQL and Smalltalk-based governance/policy logic prototypes.

### Changed

- Documentation now frames Crypta as an alpha-stage, security-focused scaffold rather than a stable or audited secure messaging product.

### Security

- Added explicit warnings that the project is not independently audited and is not production-ready.
- Documented required future work for WebAuthn verification, native secure-key storage, encrypted group messaging verification, deployment hardening, threat model validation, and cryptographic review.

### Documentation

- Added repository governance, contribution, support, privacy, architecture, deployment, API, security, threat model, roadmap, changelog, issue template, and pull request template documentation.
