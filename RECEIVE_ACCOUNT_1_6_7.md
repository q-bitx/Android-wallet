# Android 1.6.7 — receive account identity and routing

Date: 2026-11-02. Preview, versionCode 33.

The receive screen previously numbered its filtered address list from 1, even when a different account was selected. Version 1.6.6 already scoped the ordinary Receive action to the selected account; older versions started from the first address of the global list. This release makes the account identity explicit instead of implying that only account 1 can receive.

- Both receive routes share WalletReceiveScreen: the ordinary action resolves the active account; a row opened from All addresses resolves the owner of that specific address.
- Account name is displayed above the QR code. Address numbers use the same global address list as the dashboard instead of restarting at 1 for each account.
- Changing the owner resets receive selection. The copied indicator resets with the address. Empty addresses cannot be copied.
- README download links now point to 1.6.7 instead of the outdated 1.6.5 download.
- No key derivation, storage, signing, network client or ViewModel behavior changed. The 12 security source files are compared against 1.6.6 in verification/1.6.7/unchanged-security-sources.json.

Verification:
- Final build succeeded; Android lint: 0 errors, 58 warnings.
- JVM: 40 passed, 1 optional external-fixture test skipped, zero failures/errors.
- Android 15 x86_64: 10 targeted tests passed, none skipped. A real WalletViewModel creates three unfunded accounts and uses the actual dashboard selector in order 1 → 2 → 3 → 1. For every selection, tests compare the owner and address text, decode the rendered QR bitmap, and read back the system clipboard. A separate test opens account 2 from the list while account 1 is active and verifies the same outputs.
- The production-state test requires empty disposable app data. It never broadcasts transactions. Other tests cover the existing address list, management buttons, seed safety gate, import and send confirmation.
- All 17 locale resource key sets match.

Final APK: QBitX-Android-1.6.7-receive-account-preview.apk, 23912374 bytes.
SHA-256: 77d89da97e2816a86f033b4a2f0106ad4c2c1b21ec394f70b9ca8867c3c8c8b9.
The final APK passed all 10 Android tests again, with no skips. Its signing certificate matches 1.6.6, allowing installation over that preview. Test wallet data was cleared and the emulator stopped afterward.

This remains a debug-signed preview, not an independent production security certification. No real funds were sent, and no production Core/pool/network configuration was changed. Full restored history, encrypted/Berkeley DB import and other earlier limitations remain unchanged.
