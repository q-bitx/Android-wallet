# Android 1.6.5 language verification — 2026-11-02

Korean replaces Ukrainian; Japanese and Thai are added. Total: 17 languages,
183 interface strings per language. The three new sets include security questions,
seed recovery, wallet.dat warnings, amounts and fees. English BIP39 words are unchanged.
A stored Ukrainian preference triggers language selection again. Wallet data is not reset.

- All resource key sets and formatting placeholders match.
- Local build succeeded; Android lint: 0 errors, 58 warnings.
- JVM: 39 passed, one optional external fixture skipped, zero failures. Thai numeric
  input is included in the existing localized decimal-input test.
- Android 15 x86_64 emulator: all 5 targeted tests passed, none skipped. They cover
  security strings across all 17 locales, formatting, saved preferences, retired
  Ukrainian preference handling, first-launch selection, and actual scrolling/tapping
  of Korean, Japanese and Thai choices.
- Source equality against 1.6.4 verified for 12 cryptography, key-storage
  and RPC source files. No derivation, key or transaction code changed. Earlier
  crypto/Core evidence remains in MULTILINGUAL_ACCOUNTS_AUDIT.md.

APK: `QBitX-Android-1.6.5-asian-languages-preview.apk`, versionCode 31, 23638854 bytes.
SHA-256: `ab9ed0c4819746e437ceb0b6243d06148d4f6e947fe28227d4ff528c8c7fa818`.

This is a debug-signed preview. Translation is AI-assisted; independent native-speaker
review is pending. This language change is not a new production security certification.
No real funds were sent and no production Core/pool/network configuration was changed.
