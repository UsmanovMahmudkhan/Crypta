# Governance

Crypta governance is intentionally conservative because the project deals with security-sensitive architecture.

## Maintainer Responsibilities

Maintainers are responsible for:

- Reviewing code, documentation, tests, and security impact.
- Keeping project claims accurate and avoiding exaggerated security language.
- Triage of issues and pull requests.
- Coordinating vulnerability reports privately.
- Approving releases and changelog entries.
- Protecting secrets, credentials, and maintainer access.

## Security Decision Process

Security-sensitive decisions should be documented in pull requests or design notes. Maintainers should consider:

- Threat model impact.
- Failure modes.
- Downgrade risks.
- Metadata exposure.
- Logging impact.
- Operational requirements.
- Test evidence.
- Need for external review.

When uncertainty is high, choose the simpler design, fail closed, and mark the area as TODO until reviewed.

## Cryptographic Change Review

Cryptographic changes require extra scrutiny. A PR must explain:

- Which protocol, library, or provider boundary is affected.
- What assumptions are made.
- How keys are generated, stored, rotated, revoked, and destroyed.
- How downgrade and replay risks are handled.
- How clients verify key transparency or group state.
- What tests were performed.
- Whether external cryptographic review is required.

Do not introduce homegrown cryptographic primitives.

## Release Approval Process

Before a release:

- Tests must pass.
- Documentation and changelog must be updated.
- Known security limitations must be listed.
- Migration and deployment notes must be reviewed.
- Dependencies and container images should be scanned.
- Maintainers must agree that release notes do not overstate maturity.

Alpha releases may include incomplete features, but they must clearly label incomplete security behavior.

## Issue And PR Triage

Maintainers should label issues by type and risk where possible:

- `bug`
- `documentation`
- `enhancement`
- `security`
- `privacy`
- `deployment`
- `needs-triage`

Public issues must not contain vulnerability details, secrets, private keys, tokens, credentials, or real user data.

## Responsible Disclosure Handling

For private vulnerability reports:

1. Acknowledge receipt when possible.
2. Confirm scope and affected versions.
3. Reproduce privately.
4. Prepare a fix or mitigation.
5. Coordinate disclosure timing.
6. Credit reporters if they want credit.
7. Publish a security advisory or release note when appropriate.
