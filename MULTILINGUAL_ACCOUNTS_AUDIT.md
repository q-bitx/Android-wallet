# Android 1.6.4 verification — 2026-11-02 (Buenos Aires)

## Delivered behavior

1. Fifteen bundled languages, first-launch choice before wallet unlock, persisted
   preference and language change in Settings. All 183 keys exist in every
   language with matching formatting parameters. Indonesian uses Android's `in`
   resource alias; Arabic and Urdu RTL configuration is checked. Seed words stay
   English. Translation assistance is machine/AI, not independent native-speaker review.
2. Adaptive launcher icon has safe padding. Actual Android rendering was checked
   for nonempty foreground and unclipped ink bounds; see launcher-preview.png.
3. Every save-file button exports real unencrypted Core SQLite wallet.dat containing
   all PQ wallets/accounts in the app, rather than a renamed .qbx text backup.
4. Receive addresses are numbered and identified as witness/legacy. New seed accounts
   show witness only; previous/imported key aliases remain visible. The receive list
   includes every imported account; the dashboard shows the selected account's
   addresses first and provides access to the complete list.
5. A fresh seed creates two distinct native witness accounts. Further accounts reuse
   that seed, with versioned derivation indices and an unchanged v1 root index 0.
   Previous independent seeds are preserved. Seed recovery restores the first two
   indices; re-add further accounts in order. wallet.dat retains all stored indices
   and unrelated imported keys. See SEED_FORMAT.md and WALLET_DAT.md.
6. New-account generation/import work runs off the UI thread. Superseded balance
   refreshes cannot apply data from a previous account; balance is per selected key.
7. Three separate seed-check boxes normalize case, show immediate invalid-input
   feedback and unlock/focus the next box only after a correct answer. Both security
   questions still gate initial access. Decimal input also supports localized digits
   and decimal separators without rounding money below a satoshi.

## Verification results

- Final local build and Android lint passed: 0 errors, 58 warnings.
- JVM: 39 passed, one optional external regtest fixture skipped, zero failures.
- Android 15 x86_64 emulator: all 27 selected functional, JNI, recovery and UI tests
  passed. After reviewing Russian copy, 8 UI/locale checks passed again; the final
  APK has byte-identical DEX and native libraries to the full-suite-tested APK.
- 131 indexed derivations (including 0, 1, 255, 1000, 10000) matched independent
  Python PBKDF2/HMAC + native C public/secret-key hashes. Actual Android JNI signed
  and verified each and rejected altered messages. Index 0 retains the v1 vector.
- Existing 1000-seed full-key recovery corpus and 100 durable encrypted create/delete/
  restore cycles passed. Duplicate imports, corrupt metadata, failed commits,
  uncertain-broadcast reservations, PIN throttling and malformed JNI sizes passed.
- New accounts share a seed without repeating onboarding. Full multi-account
  wallet.dat and seed + sequential account re-add preserve every complete key pair.
  Previous single-key v1 storage still signs correctly and keeps legacy visible.
- Core regtest recognized both PQ keys in the actual Android export. Only the copy's
  network application ID was changed for isolated regtest; key bytes were untouched.
  Core added two keys; Android recovered all four with exact full-key hash matches.
- A separate descriptor Core wallet generated two PQ keys; both imported losslessly.
- Read-only mainnet RPC check: main chain height 244226, both new witness addresses
  valid, combined four-alias UTXO scan successful. No valid transaction broadcast.

## Limits

This is a debug-signed preview. No production service configuration changed and
no real funds were sent. Physical ARM hardware, a real funded user wallet and an
independent production security audit were not established. Berkeley DB, encrypted
wallets, ECDSA key migration and full restored history remain unsupported. Stored
PQ keys are capped at 1000 to keep every stored account exportable. A seed does not
recover unrelated imported keys; preserve the complete wallet.dat for them.

The unencrypted export is sensitive. Temporary SQLite processing occurs in private
app cache with cleanup on normal completion; crashes can leave a temporary file.
Secure memory/flash deletion and hardware-backed Keystore protection are not proven
on all devices. See SECURITY_REVIEW.md for the prior audit's remaining limitations.
Raw node diagnostics can remain untranslated; independent linguistic review is pending.

## APK

`QBitX-Android-1.6.4-multilingual-preview.apk`, versionCode 30, 23853570 bytes.
SHA-256: `8fd4198e03de6c1f5e9645664d769864408eb79bf5e6632f22c64891798f2586`. Local preview signing certificate is unchanged from 1.6.3.
