# Q-BitX Android Wallet — seed preview

Native Android/Kotlin implementation based on L2b2n's MIT wallet. This Android milestone
uses the existing UI and signer; Flutter and iOS have not been implemented.

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
