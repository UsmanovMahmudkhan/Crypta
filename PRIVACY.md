# Privacy

Crypta is designed as an alpha-stage, security-focused scaffold for zero-trust, end-to-end encrypted communication. The intended privacy model is that clients encrypt content before sending it to the server.

## What The Server Should Store

The server may store:

- Organization, user, room, and device records.
- Device trust and attestation metadata.
- Public identity keys and prekeys.
- Key transparency records.
- Ciphertext envelopes.
- Encrypted attachment metadata.
- Delivery receipts.
- Audit log events.
- Admin actions and policy records.
- Session token hashes.

## What Should Remain Encrypted

The server should not receive or store:

- Message plaintext.
- Attachment plaintext.
- Private keys.
- Session keys.
- Group secrets.
- Local database encryption keys.
- Decrypted message metadata.

## Metadata That May Still Exist

End-to-end encryption does not hide all metadata. The server may still know:

- Which users and devices exist.
- Which users belong to rooms.
- Which device sent or received a ciphertext envelope.
- Message and attachment timing.
- Ciphertext and attachment sizes.
- Device platform, trust state, and revocation state.
- Login, session, admin, audit, and policy activity.

## Logs

Logs may contain:

- Request paths.
- Request IDs.
- Error classes.
- Health and metrics events.
- Operational timing.

Logs must not include request bodies, secrets, tokens, private keys, plaintext messages, plaintext attachments, or real vulnerability details.

## User Data Protection Assumptions

Crypta assumes clients are responsible for local encryption, decryption, and key protection. If a user device is compromised, plaintext may be exposed after decryption. If a server is compromised, encrypted content should remain protected only if client cryptography, key verification, and key storage are correctly implemented.

## Alpha-Stage Warning

Crypta has not been independently audited. Privacy guarantees are incomplete until mobile secure storage, cryptographic providers, group messaging verification, key transparency verification, deployment hardening, and operational controls are implemented and reviewed.
