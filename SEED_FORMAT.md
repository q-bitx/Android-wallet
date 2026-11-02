# Q-BitX seed recovery format v1

Status: application-specific preview; not a standardized BIP32 or interoperable PQ-HD scheme.
Never change this mapping for wallets already created with this version.

1. Generate 32 bytes with Android/JVM SecureRandom.
2. BIP39 English: append SHA256 entropy checksum (8 bits); map 264 bits to 24 words.
3. Validate word count, membership and checksum before restoration. Normalize NFKD, whitespace and English letter case.
4. Standard BIP39 PBKDF2-HMAC-SHA512, 2048 iterations, 64 bytes, salt `mnemonic`, empty passphrase.
5. HMAC-SHA512 with the BIP39 binary seed as key, and exact ASCII data:
   `Q-BitX/PQ-HD/v1/mainnet/account/0/receive/0/Dilithium3`.
6. Take the first 32 bytes, feed them to `pqcrystals_dilithium3_ref_seed_keypair`.
   The inherited key expansion appends K=6 and L=5, then SHAKE256 expands the input.
7. HASH160(public key) produces the existing mainnet legacy version-50 address and the
   existing native PQ `dil` witness address. Private/public key sizes: 4032/1952 bytes.

## Indexed accounts since 1.6.4

The existing receive index 0 is unchanged, including its complete expanded PQ key.
For index `i` (0 through 10000), use the same BIP39 binary seed and HMAC procedure
with exact ASCII data `Q-BitX/PQ-HD/v1/mainnet/account/0/receive/{i}/Dilithium3`,
where `{i}` is an unsigned decimal integer with no leading zeros. This extension
is identified as `qbx-pqhd-v1-indexed`; it is application-specific, not BIP32.
No BIP39 passphrase or public-child derivation is implemented.

A fresh installation creates indices 0 and 1 with native witness receive addresses.
Additional accounts derive the next index from the selected account's existing seed.
Earlier independent seeds and random imported keys are not merged or replaced.
Plain 24-word recovery restores indices 0 and 1; re-add further accounts in order
as with an account-based wallet. There is no automatic chain discovery of arbitrary
indices. Preserve wallet.dat to retain all stored keys, indices and independent seeds.
Change returns to the selected key's own native witness address. Balances and sends
are per selected account. Underlying M aliases remain mathematically valid; new
accounts hide them, while imported/previous wallets display both address forms.
Imported legacy raw-key backups have no recoverable seed. Existing random keys must never
be represented as recoverable by an unrelated mnemonic.

Since 1.6.1, a seed-file backup is JSON with `format: "qbx-seed-backup"`, numeric `version: 1`,
`derivation: "qbx-pqhd-v1"` and the `mnemonic` string. This changes the backup container,
not key derivation. Plain 24-word imports continue to select v1. Unsupported container or
derivation versions fail closed. The canonical English dictionary SHA256 is
`2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda`.

Record the 24 words AND the format identifier `qbx-pqhd-v1`. An ordinary Bitcoin wallet
will derive different keys. Backups of old raw-key wallets remain in their original format.

Public test vector (never fund): 23 times `abandon`, then `art`.
Derived 32-byte key seed:
`1fe2b102d15cc52b830313a238abf486a0f6c1135c328431b1e434acf9bafed5`.
SHA256 of expanded public key:
`596877d62cafea0318202c507ed2ba3cffc55cca3b098cce410e278fcfc3d32c`.


Root metadata uses envelope version 1, derivation `qbx-pqhd-v1`, optional `index: 0`.
Child metadata uses envelope version 2, derivation `qbx-pqhd-v1-indexed` and its
numeric `index`. Both include the same mnemonic. Every envelope is validated
against the complete key pair before storage. Index metadata is required in
wallet.dat; older mobile versions refuse unsupported child metadata rather than
silently deriving a different key. Use 1.6.4 or a compatible later version for
indexed-account recovery. Core reads standard PQ key records and ignores mobile
metadata. Its private keys do not depend on understanding this derivation.

The Android tests compare 131 indices, including 0, 1, 255, 1000 and 10000, against
an independent Python HMAC/PBKDF2 plus native C oracle. See the public test-only
`indexed-account-oracle.json` fixture; never fund its known seed.
