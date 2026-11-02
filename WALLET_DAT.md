# Core wallet.dat support (Android 1.6.3 preview)

Import: Import Wallet -> Import from file, on the initial screen or in Settings.
Existing wallets do not need to be deleted. Imports append or reuse matching keys.

Export: Settings -> Export Core wallet.dat -> acknowledge the unencrypted backup
warning -> select a file destination. Export contains the active wallet only.

## Supported data

- Real, unencrypted Q-BitX Core SQLite wallet.dat, schema 0, mainnet application ID
  0x51424954 (QBIT). File limit: 32 MiB; 100000 records; 1 to 1000 PQ key pairs.
- Standard Core `dilpk` and `dilsk` records. Both legacy-wallet and descriptor-wallet
  checksum forms are accepted, followed by an actual signature/key-pair check.
- Every PQ key becomes a separate selectable mobile wallet; the displayed balance
  belongs to that selected key's M and dil1q addresses, rather than the aggregate
  of all keys in the desktop Core wallet.
- All keys are verified before one atomic encrypted-preferences commit. Duplicates
  reuse existing wallet IDs. Failed validation does not replace the current wallet.
- Mobile-created files also store a versioned qbx-pqhd-v1 seed envelope in an
  optional `qbxseed` record keyed by the same key ID. Core ignores this metadata;
  standard PQ key records remain readable by Core. A desktop-generated random
  key does not acquire a recoverable 24-word seed retrospectively.

## Explicit limits

Encrypted wallets, old Berkeley DB files and watch-only PQ keys are refused.
ECDSA keys, desktop labels, transaction history and Core settings are not imported.
Keep the original Core backup; this is a PQ-key import, not full database migration.
The exported file is not password encrypted. Anyone who obtains it can spend its
funds. File processing stays on the phone; wallet.dat, private keys and seed are
never uploaded to RPC. Temporary SQLite files reside in app-private cache and are
deleted in finally blocks; a process crash during processing may leave a temporary
file until app data is cleared. Secure deletion from flash is not guaranteed.

## Security onboarding

New wallet: 24 words -> check words 4, 12, 20 -> scam question -> recovery question
-> wallet access. Each answer is followed by an explanation. A wrong answer on
 either question offers Repeat and restarts both questions. Final confirmation is
persisted only after the second correct answer; restarting earlier returns to
pending seed onboarding. Losing one backup does not destroy access if another
valid seed/key backup or accessible wallet remains; the explanation states that
loss of all recovery copies makes recovery impossible.
