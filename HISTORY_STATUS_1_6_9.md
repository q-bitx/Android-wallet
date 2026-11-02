# Android 1.6.9 — confirmed payments stuck as Pending

Date: 2026-11-02. Preview, versionCode 35.

The balance scanner used explicit block lookups, but history still used an unqualified getrawtransaction query. With txindex unavailable, confirmed outgoing records stayed at zero confirmations. The pending amount/fee label was also calculated before history reconciliation and could remain stale.

Changes:
- UTXO heights are carried into history lookups. Confirmed records save an optional blockHash for later refreshes, including after their outputs have been spent.
- For older pending records without metadata, a bounded lookup of the recipient/change outputs (vout 0/1) can recover the confirmed block from gettxout plus its tip header. The transaction is then fetched with an explicit block hash.
- Returned transaction ID, block hash, positive confirmations and active-chain membership must match. Missing outputs or failed queries never imply confirmation.
- If no verifiable status is available, history shows “Status unavailable” instead of treating an old zero as an indefinitely verified Pending status. Unknown outgoing records keep input reservations locked.
- Confirmation counts are revalidated rather than permanently cached at six confirmations. Saved metadata is compatible with older history JSON (missing blockHash remains null).
- Pending outgoing amount and fee are recomputed in the same state update that applies confirmed history. Record amount, fee and local send timestamp are preserved.
- Home/history refresh every 30 seconds only while the activity is resumed, authenticated and unlocked, after seed confirmation. Refresh is suppressed during wallet loading or payment work. History also has a manual refresh button.
- Cancellation is propagated; history updates retain wallet and refresh-generation guards.
- Keys, seed derivation, wallet.dat format, signing and transaction construction are unchanged. KeyManager changes only the TxRecord metadata declaration, not its key-management implementation.

Verification:
- JVM: 53 passed, 1 pre-existing optional external-fixture test skipped, zero failures/errors.
- Android 15 x86_64: 9 deterministic/regression tests passed, none skipped. Covers all three history statuses, balance without txindex, address-list behavior and actual account/QR/clipboard switching.
- Two additional opt-in live Android tests passed. A disposable emulator wallet was given an old zero-confirmation local record for a public confirmed payment; the actual WalletViewModel repaired its status, cleared pending delta and saved the block hash. After force-stopping the app, a separate process loaded the saved record and verified it again using the stored block hash.
- Live parameters and personal transaction data are omitted from this repository. The live tests require explicit runtime parameters and are skipped in ordinary CI.
- Final build succeeded; lint: 0 errors, 58 warnings. All 17 locale resource sets match (186 strings each).
- Key-related source checks are in verification/1.6.9/key-source-checks.json. APK signing certificate matches 1.6.8.

APK: QBitX-Android-1.6.9-history-status-preview.apk, 23915037 bytes.
SHA-256: 1b7b2064ce94b36af49422ef41704339e42bb725b2d0c5b4c666bb821ffe84c0.

Limitations: old fully spent transactions with no saved block metadata may remain unverifiable until the node index is available. The two-output recovery path applies to this client's recipient/change transaction format, not arbitrary historical multi-recipient transactions. Full chain-history discovery remains limited by the node's index capabilities. This is a debug-signed preview, not an independent production security certification. No real funds are sent by these checks and production Core/pool/network configuration is not changed.
