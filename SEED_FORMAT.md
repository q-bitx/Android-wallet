# Q-BitX seed recovery format v1

Status: application-specific preview; not a standardized BIP32 or interoperable PQ-HD scheme.
Never change this mapping for wallets already created with this version.

1. Generate 32 bytes with Android/JVM SecureRandom.
2. BIP39 English: append SHA256 entropy checksum (8 bits); map 264 bits to 24 words.
3. Validate word count, membership and checksum before restoration. Normalize NFKD and whitespace.
4. Standard BIP39 PBKDF2-HMAC-SHA512, 2048 iterations, 64 bytes, salt `mnemonic`, empty passphrase.
5. HMAC-SHA512 with the BIP39 binary seed as key, and exact ASCII data:
   `Q-BitX/PQ-HD/v1/mainnet/account/0/receive/0/Dilithium3`.
6. Take the first 32 bytes, feed them to `pqcrystals_dilithium3_ref_seed_keypair`.
   The inherited key expansion appends K=6 and L=5, then SHAKE256 expands the input.
7. HASH160(public key) produces the existing mainnet legacy version-50 address and the
   existing native PQ `dil` witness address. Private/public key sizes: 4032/1952 bytes.

Only account 0, receive key 0 is implemented. Change returns to this same key's native PQ
address. This is a frozen, domain-separated deterministic account, not a complete HD tree.
No BIP39 passphrase, additional account, path selector or seed-based discovery is exposed.
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
