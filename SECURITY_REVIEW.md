# Targeted security review — 1 November 2026

Internal review by Codex of seed derivation/recovery, encrypted storage, JNI boundaries,
transaction approval/broadcast persistence and the Android interface. This is not an
independent audit, NIST certification or proof of production readiness. The delivered
AUDIT_REPORT.md and raw logs distinguish executed tests from untested release conditions.

## Findings addressed in 1.6.1

| Severity | Finding | Change |
|---|---|---|
| High | Seed restoration wrote raw keys before seed/version in a second edit; failed writes could acknowledge an incomplete recovery record | One checked synchronous commit stores keys, seed, version, wallet list and active ID; errors do not return a created wallet |
| High | Inputs were reserved only after a successful broadcast response; an ambiguous timeout could allow a retry against the same inputs | Persist reservations and an uncertain-broadcast marker before networking; history refresh cannot clear uncertain reservations |
| Medium | A duplicate imported seed could create two local wallets with separate pending-spend state for one actual key | Deduplicate by public key and reuse the existing wallet ID |
| Medium | Stored seed/version were not checked against both stored keys before signing | Re-derive the full pair on first use in a manager; reject unsupported metadata/mismatches, allow explicit valid-seed repair |
| Medium | Seed file export omitted the recovery format/version | Versioned qbx-seed-backup JSON; reject unknown versions; keep plain 24-word v1 and legacy raw-key import |
| Medium | Native boundary did not consistently handle nulls, invalid sizes, allocation failure or wipe secret copies on every exit | Bounded checked JNI API; local secret copies and volatile erasure; maximum message 1 MiB |
| Medium | Optional app PIN used unsalted SHA256 and no durable attempt limit | Random salt, PBKDF2-HMAC-SHA256/120000, constant-time checks, persisted 5-attempt/30-second lockout; old hashes migrate on successful verification |
| Low | BIP39 dictionary had only a word-count check | Check 2048 unique sorted entries and a frozen canonical SHA256 |
| Low | Seed text input could enable keyboard autocorrection | Disable autocorrect and use password keyboard input variation; the keyboard/OS must still be trusted |

Existing protections remain: Android device credential foreground gate, FLAG_SECURE,
disabled Android OS backup, Keystore-backed encrypted preferences, normal HTTPS certificate
and hostname verification, no HTTP/redirects/embedded credentials, local transaction intent
checking, exact-fee confirmation, single-transaction payments and a 0.1 QBX maximum fee.

## Cryptographic evidence and its limits

The inherited functions are named Dilithium3, but their sizes/algorithm correspond to
ML-DSA-65. NIST ACVP known answers check exact key generation and pure-mode signature
generation/verification, including contexts. Prehash modes and other parameter sets are
outside the wallet's API and outside this audit. These tests are not a module certificate.

The qbx-pqhd-v1 mapping is unchanged from 1.6.0. Tests compare complete public/private key
bytes and both M/dil1q addresses. Randomized signatures are expected to differ; restored
keys must verify them. "Lossless" here means identical key material and addresses for the
same valid mnemonic and frozen recovery format, not recovery of missing or incorrect words.
BIP39 has an 8-bit checksum for 24 words: some changed valid words can form a different
valid mnemonic. The app rejects bad checksums; it cannot infer the originally intended seed.

## Remaining release conditions

- qbx-pqhd-v1 is application-specific, one account/key, not BIP32 or a complete PQ-HD tree.
  It needs independent design review. Bitcoin wallets derive different keys from these words.
- Real ARM Android hardware, Android 8/16, OS upgrades, biometric enrollment changes,
  Keystore invalidation, host power failure and sustained lifecycle/concurrency behavior
  need additional testing. Emulator tests do not establish hardware-backed guarantees.
- The inherited AndroidX security-crypto alpha dependency and its lifecycle need a reviewed
  migration. UI device authentication is not hardware authentication for every signing call.
- Managed strings and all internal polynomial temporaries are not reliably erased. Rooted/
  debugged or compromised devices/keyboards are outside any claim of key confidentiality.
  The user can intentionally copy/save a plaintext backup; the device clipboard and destination
  file must be treated as sensitive. This APK is debug-signed and debuggable.
- RPC supplies trusted chain/UTXO/history data. There is no SPV/prevout proof verification,
  authenticated genesis binding or complete native/legacy reorg/history assurance.
- Uncertain broadcasts remain conservatively locked. Safe reconciliation/unlocking UI is
  not implemented; explicit rejection or a network timeout can temporarily immobilize inputs.
  The retained raw outbox is not a complete multi-transaction reconciliation engine.
- PIN throttling assumes the OS clock/storage are not controlled by an attacker; device
  credentials remain mandatory. This is not a defense against arbitrary code execution.
- Four public dependency/version pairs were queried against OSV. An empty response is not
  a complete transitive-dependency audit or a guarantee that no vulnerability exists.
- No independent side-channel analysis, mobile penetration test, release signing ceremony,
  reproducible-build attestation, store review or cloud CI run has been completed.
- AGP 8.5.1 warns about SDK 36 being newer than its tested range. Lint warnings and full
  accessibility/localization coverage require release triage; successful builds are not certification.

No production Core, pool, network configuration or real-funds transaction is changed by this work.
All transaction acceptance tests use a fresh, peer-isolated localhost regtest and public test seeds.
