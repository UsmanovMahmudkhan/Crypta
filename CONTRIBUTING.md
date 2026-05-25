# Contributing

Crypta is an alpha-stage, security-focused scaffold. Contributions should be careful, realistic, and explicit about security tradeoffs.

## Local Setup

Requirements:

- Java 21
- Maven
- Docker and Docker Compose
- Flutter SDK, for mobile scaffold work

Backend setup:

```bash
cp .env.example .env
docker compose up --build
```

The backend runs on `http://localhost:8080` by default. Health checks are available at `/actuator/health`.

For a direct Maven run, start PostgreSQL with Docker Compose or another local database, export the variables listed in `.env.example`, then run:

```bash
mvn spring-boot:run
```

Mobile scaffold setup:

```bash
cd mobile
flutter pub get
flutter test
```

## Branch Naming

Use short, descriptive branch names:

- `feature/<topic>`
- `fix/<topic>`
- `docs/<topic>`
- `security/<topic>`
- `chore/<topic>`

## Commit Messages

Use concise, imperative commit messages:

- `docs: add threat model`
- `fix: reject invalid ciphertext metadata`
- `security: document key transparency assumptions`

Keep unrelated changes in separate commits when possible.

## Pull Requests

Pull requests should include:

- A clear summary of the change.
- The reason the change is needed.
- Tests performed.
- Documentation updated, or an explanation for why no docs changed.
- Security and privacy impact.
- Migration or deployment notes, if applicable.

Cryptographic and security-sensitive changes must explain the design, assumptions, threat model impact, downgrade risks, failure behavior, and test evidence.

## Code Style

- Follow the existing Spring Boot package structure and Java style.
- Keep controller logic thin and use service interfaces for behavior.
- Keep mobile cryptographic operations behind provider interfaces or native boundaries.
- Avoid logging request bodies, ciphertext metadata that may contain sensitive context, secrets, tokens, private keys, or credentials.
- Prefer clear validation and fail-closed behavior for security-sensitive code paths.

## Test Expectations

Run relevant tests before opening a PR:

```bash
mvn test
```

For mobile changes:

```bash
cd mobile
flutter test
```

Add or update tests when changing authentication, authorization, plaintext rejection, message handling, device trust, key infrastructure, audit logging, CQL parsing, Smalltalk governance logic, deployment behavior, or API contracts.

## Documentation Expectations

Update documentation when a change affects:

- API behavior or authentication requirements.
- Deployment or environment variables.
- Threat model assumptions.
- Security limitations or guarantees.
- Mobile/native boundary expectations.
- Governance, audit, or policy behavior.

## Security-Specific Rules

Never commit plaintext secrets, private keys, tokens, credentials, real user data, production logs, or unredacted vulnerability details.

Use example values only, and label them clearly. Rotate any secret that may have been exposed.

Do not introduce new cryptographic primitives or protocols without a clear design note and external-review plan. Prefer audited libraries and narrow integration boundaries.

Do not claim Crypta is production-ready, fully secure, military-grade, unbreakable, or independently audited unless that has actually happened and is documented.
