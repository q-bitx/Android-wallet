# Android 1.6.6 — address list and management layout

Date: 2026-11-02. Preview, versionCode 32.

- Home “All addresses” has a dedicated screen, separate from “Receive QBX”.
- The screen starts with the selected wallet. A wallet selector can show any wallet or all wallets.
- Every row identifies its owner, numbered address and witness/legacy type. Tapping a row opens the exact address QR/copy screen.
- Explicit read-only RPC scans obtain each displayed address balance separately. Sort is descending spendable balance (immature coinbase excluded), stable for ties; unknown balances are last and shown as an em dash, never a fabricated zero.
- Scans are serial, wait for an existing normal refresh, and cancel when leaving the list. Refresh clears old values. Large imported wallets may take time to scan; partial results appear progressively. This is not a single-height atomic snapshot.
- The ordinary Receive action now limits its chooser to the selected wallet.
- File-import label is “wallet.dat” in all 17 locales. Old mobile backup parsing remains for backward compatibility.
- Rename/remove/create actions use full-width buttons with a minimum height, allowing long translations to wrap.
- No derivation, key storage, local signing or transaction-broadcast code changed. Prior crypto and Core evidence remains in MULTILINGUAL_ACCOUNTS_AUDIT.md.

Verification:
- Final build succeeded; Android lint: 0 errors, 58 warnings.
- JVM: 40 passed, 1 optional external fixture skipped, 0 failures/errors.
- Android 15 x86_64: 11 targeted tests passed, none skipped. Includes selected/all wallet filtering, actual row ordering by balance, correct receive target, unknown balance, management buttons, import label, seed safety gates, send confirmation, receive UI and all 17 locale resources.
- All 17 locale string key sets match (185 each); no .qbx in file-import labels.
- Cryptography, storage and network source files are unchanged from 1.6.5; see verification/1.6.6/unchanged-security-sources.json. The ViewModel now adds read-only address scanning.
- APK signature verified; certificate matches 1.6.5, so installation over the previous preview is supported.

APK: QBitX-Android-1.6.6-address-list-preview.apk, 23658490 bytes.
SHA-256: c89639ff24505137fc7bed4e0c254e277e6fdd0544ebf287bb78728ce3fd3d68.

Limitations from earlier previews still apply: debug signing, no independent production certification, no encrypted/Berkeley DB Core import, and no guaranteed full restored history. No real funds are sent by these checks and production Core/pool/network configuration is not changed.
