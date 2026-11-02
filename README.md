# Q-BitX Android Wallet — seed preview

Native Android/Kotlin implementation based on L2b2n's MIT wallet. This Android milestone
uses the existing UI and signer; Flutter and iOS have not been implemented.

Version 1.6.1 includes a targeted seed/JNI/storage audit and the Mining/Explorer design system.
The v1 seed mapping is unchanged; an actual APK 1.6.0 → 1.6.1 update was tested on Android 15.
Seed files now carry versioned JSON metadata; plain 24-word v1 imports still work.
See SECURITY_REVIEW.md, DESIGN_SYSTEM.md and the separate delivered audit report/log archive.

## Included

- Create a new 24-word English BIP39 wallet and confirm its paper backup.
- Restore 24 words using the frozen `qbx-pqhd-v1` format (see SEED_FORMAT.md).
- Restore older raw-key backups; these wallets do not acquire a seed retrospectively.
- Android Keystore-backed encrypted preferences, disabled OS backup, secure-screen flag,
  required Android device credential on foreground entry, optional inherited app PIN.
- Legacy M and native dil1q receive addresses, QR receive screen, balance and history.
- Local signing, local transaction intent checks, exact-fee confirmation before broadcast.
- Default HTTPS RPC https://rpc.qbitx.org/ using normal certificate/hostname verification.
- Single-transaction payments, maximum fee 0.1 QBX. Smaller payments may be needed for
  wallets with many inputs. An ambiguous broadcast timeout must not be retried blindly.

## Download 1.6.9

[Download APK](https://github.com/q-bitx/Android-wallet/releases/download/v1.6.9-history-status-preview/QBitX-Android-1.6.9-history-status-preview.apk)

[Release and verification report](https://github.com/q-bitx/Android-wallet/releases/tag/v1.6.9-history-status-preview)

Repairs confirmed outgoing payments stuck as Pending while txindex is unavailable.
History uses known UTXO heights, saved block hashes and confirmed output lookup;
confirmed payments clear the stale pending amount immediately. Foreground home/history
screens refresh every 30 seconds, and history has a manual refresh button. An unavailable
status is shown explicitly rather than inferred from missing transaction data.

## Previous 1.6.8

[Download APK](https://github.com/q-bitx/Android-wallet/releases/download/v1.6.8-balance-index-preview/QBitX-Android-1.6.8-balance-index-preview.apk)

[Release and verification report](https://github.com/q-bitx/Android-wallet/releases/tag/v1.6.8-balance-index-preview)

Fixes balance refresh for recently confirmed outputs while the RPC transaction index
is unavailable or still indexing. Coinbase maturity checks use the output block hash;
ordinary transfers do not need to wait for the transaction index. Mining rewards
remain excluded until mature. See BALANCE_INDEX_1_6_8.md for tests and limitations.

## Previous 1.6.7

[Download APK](https://github.com/q-bitx/Android-wallet/releases/download/v1.6.7-receive-account-preview/QBitX-Android-1.6.7-receive-account-preview.apk)

[Release and verification report](https://github.com/q-bitx/Android-wallet/releases/tag/v1.6.7-receive-account-preview)

Receive explicitly identifies the selected account and uses the global address number.
QR codes and copied addresses are regression-tested across three actual accounts.

## Previous 1.6.6

https://github.com/q-bitx/Android-wallet/releases/tag/v1.6.6-address-list-preview

Adds a separate address list with wallet filtering and descending balance order,
wallet.dat-only import labels, and full-width wallet management buttons.

## Previous 1.6.5

https://github.com/q-bitx/Android-wallet/releases/tag/v1.6.5-asian-languages-preview

Korean replaces Ukrainian; Japanese and Thai are added. There are now 17 languages.
All interface and security/recovery strings are included. Previous Ukrainian
preferences prompt for a new choice. Language selection does not change wallet keys.

## Previous 1.6.4

https://github.com/q-bitx/Android-wallet/releases/tag/v1.6.4-multilingual-preview

- 15 interface languages, first-launch selection and saved preference; change in Settings.
- Adaptive launcher icon with safe padding for Android masks.
- All save-file actions export a real Core SQLite wallet.dat containing all app accounts.
- A fresh seed creates two distinct native witness accounts. Additional accounts reuse
  that seed; older independent seeds and imported legacy keys remain valid.
- Numbered receive addresses; imported native/legacy aliases remain selectable.
- Separate word-check fields with immediate error feedback, automatic lowercasing
  and focus progression only after a correct answer.

Read SEED_FORMAT.md before relying on seed-only recovery of additional accounts.
Full wallet.dat preserves all stored keys and derivation indices. Balances and sends
use the selected account, not an aggregate across the account list. Translations are
AI-generated and have not received independent native-speaker review.

## Previous 1.6.3

https://github.com/q-bitx/Android-wallet/releases/tag/v1.6.3-recovery-preview

Adds real unencrypted Core SQLite wallet.dat import/export, mandatory scam and
recovery questions after new-seed backup proof, and stricter balance response
validation. Import is also available in Settings. See WALLET_DAT.md for format
limits and per-key balance behavior, and RECOVERY_AUDIT.md for verification.

## Previous 1.6.2

GitHub release: https://github.com/q-bitx/Android-wallet/releases/tag/v1.6.2-ecosystem-preview

Version 1.6.2 adds the small ecosystem header caption and limits payments to eight
inputs to fit the verified public RPC 100 KiB request limit (legacy weight limits
can reduce this further). Full restored transaction history is unavailable because
the node's basic filter index is disabled. Current UTXO balance and locally recorded
payments remain available. See RPC_COMPATIBILITY.md.

## Install preview

Use the supplied APK on Android 8.0 or later with a device screen lock configured.
Create Wallet → write the 24 words offline → confirm words 4, 12, 20 → open wallet.
Import Wallet accepts 24 English words or the older colon-separated key backup.
Receive displays both address types. Send requires a recipient, amount, fee policy,
and a second confirmation showing the exact signed payment fee.

The APK is a debug-signed development preview, not a production release. Its separate
application ID `org.qbitx.wallet.preview` keeps it alongside the upstream wallet. It does
not access the upstream app's storage. Verify recovery independently before storing funds.

## Build and test

Java 17, Gradle 8.7, Android SDK 36, NDK 26.3.11579264, CMake 3.22.1.
Set JAVA_HOME and ANDROID_HOME, then run:

```
gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

The included CI builds APKs and runs unit/lint checks plus emulator-based native recovery
and encrypted-storage tests. A workflow file is not evidence that cloud CI has run.

## Security limits

See SECURITY_REVIEW.md. This is not an independent production audit or a claim of production
readiness. Device testing and live mainnet spending have not been authorized/performed.
The seed format needs external review before real funds are stored. Keep the upstream MIT
LICENSE and NOTICE.md with redistributions.
