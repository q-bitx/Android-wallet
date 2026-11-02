# Android 1.6.8 — balance refresh without txindex

Date: 2026-11-02. Preview, versionCode 34.

Root cause: recent confirmed UTXOs required a getrawtransaction lookup to distinguish ordinary transfers from immature mining rewards. The old lookup omitted the block hash, so an absent or still-building transaction index aborted the balance scan. The UI then retained its last balance, which could be the initial zero.

Fix:
- Pass the UTXO output height to the maturity lookup.
- Resolve getblockhash(height), then call getrawtransaction(txid, true, blockhash). Core can read the known block without txindex.
- Cache block hashes per scan. Cache maturity results by transaction and output height.
- Verify returned transaction ID, block hash, positive confirmations, active-chain membership and input structure. Missing data, reorgs or inconsistent replies still fail closed.
- Preserve the 100-confirmation maturity boundary. Ordinary transfers remain available; immature coinbase rewards remain excluded.
- No production Core/pool/network configuration, key derivation, storage or signing code changed. Ten key-related Kotlin files are byte-compared against 1.6.7 in verification/1.6.8/unchanged-key-sources.json.

Verification:
- Reproduced with a fresh, disconnected Core regtest node, txindex=0, 102 locally generated blocks and a PQ transfer of 1.123 test QBX. The old unqualified lookup returns RPC -5; the explicit-block lookup succeeds and reports an ordinary transfer. A fresh coinbase remains identifiable.
- The captured public regtest RPC fixture is included in JVM and Android tests. Reproduction helper: tools/core_no_txindex_fixture.py. It requires an explicit Core binary/output path, creates fresh regtest data and never reads production wallet configuration.
- JVM: 45 passed, 1 pre-existing optional external-fixture test skipped, zero failures/errors. Covers exact amount, maturity boundary, cache behavior, missing data, transaction/block mismatch and reorg rejection.
- Android 15 x86_64: all 6 targeted offline/regression tests passed. Includes the Core fixture, actual account switching/QR/clipboard and address-list behavior.
- One additional opt-in Android test passed against the live public HTTPS RPC using the actual NodeRpcClient balance scan. Public receipt parameters are supplied at runtime and deliberately omitted from this repository. This test is skipped in ordinary CI unless explicit parameters are supplied; it never broadcasts.
- Local build and final test build succeeded; lint: 0 errors, 58 warnings.
- APK certificate verified, unchanged from 1.6.7. Test data was cleared and the emulator stopped after verification.

APK: QBitX-Android-1.6.8-balance-index-preview.apk, 23658494 bytes.
SHA-256: ab39b97ec0d510793ef21cad1a1acf90b41547f903ddefd0f1866303b370cbdb.

Limitations: this is a debug-signed preview, not an independent production security certification. Full transaction-history restoration still depends on node index capabilities; this patch fixes UTXO balance/maturity scanning. Unconfirmed incoming transactions are not part of scantxoutset. The client still trusts the configured node and is not an SPV verifier. Earlier wallet.dat and physical-device audit limitations remain. No real funds were sent by these checks.
