# Flutter mobile scaffold

This directory is a contract-first mobile scaffold, not a complete Flutter app.

Required production boundaries:

- Flutter owns presentation, application orchestration, domain models, networking, encrypted local database adapters, and mission room UX.
- Native iOS modules own Secure Enclave / Keychain access, biometric-gated key unwrap, device attestation, jailbreak signals, and screenshot prevention where supported.
- Native Android modules own Keystore / StrongBox access, biometric-gated key unwrap, hardware key attestation, root signals, and `FLAG_SECURE` screenshot prevention.
- Signal Protocol, PQXDH, MLS, and cryptographic primitives must be provided through audited libraries or externally reviewed native bindings. The Dart interfaces are dependency boundaries, not permission to hand-roll crypto.
