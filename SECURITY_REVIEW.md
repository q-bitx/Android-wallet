# Implementation security review — 1 November 2026

This is an internal source review and automated test record, not an independent security
audit, penetration test, or production certification. Consult the delivered verification
report for actual run results; CI configuration and test code alone do not prove execution.

## Corrections

- Removed inherited trust-all TLS and hostname verifier; disallowed HTTP and embedded RPC credentials.
- Disabled cleartext in Android network security configuration; stopped redirects.
- Normal RPC calls validate IDs and bound responses to 8 MB. The inherited raw-transaction
  construction request is separately checked by a local transaction policy before signing.
- Added generated BIP39 entropy, checksum validation, domain-separated versioned key expansion,
  deterministic native API, encrypted seed storage, paper-backup confirmation and recovery tests.
- Added device credential foreground gate and FLAG_SECURE. Keys/seed remain in Keystore-backed
  encrypted preferences; OS backup remains disabled.
- Added exact local input/output/change/fee validation, own-input checks, bounded canonical parser,
  full-address/exact-fee approval, single-payment execution guard, and single-transaction limit.
- Failed coinbase maturity lookups now reject spending instead of assuming non-coinbase status.
- Preserve existing raw-key imports; never fabricate seed recovery for old random keys.

## Remaining release blockers and limitations

- No independent cryptographic review of the new application-specific derivation. Only one
  account/key is implemented. This is not a full PQ-HD hierarchy or BIP32-compatible wallet.
- Host crypto tests do not prove JNI/device behavior. Instrumentation tests must run on real
  Android devices/emulators, covering backup/restore, process death, screen lock, backgrounding,
  screenshots, biometrics/PIN, database durability, accessibility and repeated sends.
- Preferences use the inherited AndroidX security-crypto prerelease dependency; migrate/review
  its storage lifecycle and authentication binding before release. The device credential gates
  UI access; it is not a claim that every key operation is hardware-backed or per-use authorized.
- Managed strings and native stack temporaries cannot all be reliably erased. Existing export
  UI can intentionally copy/save plaintext backup material. Clipboard/file threat model remains.
- Inherited optional app PIN uses unsalted SHA256 and has no robust durable rate limiting.
  Device credentials are mandatory in this preview, but this does not repair that inherited PIN design.
- RPC is trusted for chain/UTXO/history data. No SPV/proof validation, authenticated genesis
  binding or independent prevout proof check is implemented. A malicious RPC may falsify balance
  or UTXO values; local intent checking alone is not complete protection against dishonest data.
- History discovery relies on a nonstandard scanblocks RPC and inherited incremental scanning;
  native/legacy history completeness, reorgs, mempool accounting and request budgets need further work.
- A network timeout during broadcast is ambiguous. Pending-input persistence is inherited and
  occurs after a successful response, not before submission. Query status before any retry.
- Debug signing, no production release key, no reproducible-build attestation, no hardware-backed
  key attestation, rooted-device guarantees, dependency vulnerability audit or store compliance review.
- Android Gradle plugin 8.5.1 warns that SDK 36 is newer than its tested SDK range. Updating the
  build toolchain and testing Android 15/16 edge-to-edge behavior remain release work.

No production Core, pool, daemon settings, services or network rules were modified. Any local
regtest process uses a new datadir and disabled peer connectivity. No mainnet broadcast is part
of the test procedure.
