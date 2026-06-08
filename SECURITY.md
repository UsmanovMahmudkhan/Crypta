# Security Policy

Crypta is an alpha-stage, security-focused scaffold for zero-trust, end-to-end encrypted communication. It has not been independently audited and must not be treated as production-ready security software.

## Supported Versions

| Version | Supported |
| ------- | --------- |
| `0.1.0-alpha` | Security reports accepted |
| Earlier versions | Not supported |

## Reporting Vulnerabilities

Do not report vulnerabilities through public GitHub issues, discussions, pull requests, screenshots, or social media.

Please report suspected vulnerabilities privately by contacting the repository maintainer. If GitHub private vulnerability reporting is enabled for this repository, use that channel first. Otherwise, use the maintainer contact listed in the repository profile or open a non-sensitive issue asking for a private security contact without disclosing technical details.

## What To Include

Include as much of the following as possible:

- Affected commit, branch, or release.
- Vulnerable component or endpoint.
- Reproduction steps or proof-of-concept details.
- Expected and actual impact.
- Whether secrets, private keys, tokens, credentials, or real user data may be exposed.
- Logs or screenshots with sensitive information redacted.
- Any suggested remediation.

## Private Disclosure Expectations

Keep vulnerability details private until maintainers have confirmed the issue, prepared a fix or mitigation, and coordinated disclosure timing with you. Do not test against systems you do not own or have explicit permission to assess.

## Security Expectations

Crypta currently provides a prototype foundation for ciphertext envelope handling, Yubico-backed WebAuthn/passkey ceremony verification, onboarding-only bootstrap access, device trust records, prekey storage, key transparency records with verifier-produced checkpoint metadata, audit logging, encrypted attachment metadata grants, and organization-scoped governance/policy logic.

The project does not yet provide a complete, audited secure messenger. Cryptographic changes, authentication changes, storage changes, logging changes, and deployment changes must be reviewed with extra care and documented clearly.

## Known Alpha-Stage Limits

- WebAuthn finish endpoints use persisted Yubico ceremony options and verified credentials, but still require external implementation review, browser/client ceremony testing, and an attestation trust policy before production claims.
- The Go security verifier is a hardening boundary for production-style verification work, not an independent security audit.
- Mobile secure-key storage is interface-level scaffolding, not complete native iOS/Android implementation.
- Signal-style, PQXDH-style, and MLS-inspired concepts are represented as architecture and provider boundaries, not independently verified cryptographic implementations.
- Server, deployment, and operational controls require hardening before production use.
