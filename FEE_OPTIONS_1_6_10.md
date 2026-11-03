# Android 1.6.10 — equal fee estimates and consistent applied rates

Date: 2026-11-02. Preview, versionCode 36.

The public node returned 0.00005 QBX/kvB (5 sat/vB) for confirmation targets 2, 6 and 25. All three old Low/Normal/High buttons therefore selected the same rate. Estimates are not required to differ; increasing a displayed price solely to create different labels would not represent those estimates.

Changes:
- Group equal applicable fee rates into one choice, labeled Recommended, with an explanation. Distinct rates remain separate; partially equal estimates are grouped as well.
- Preserve policy aliases when grouping: selecting an earlier Low/High alias still uses the displayed grouped rate. Equal estimates are not raised to invent price differences.
- Display and confirmation text use the same integral sat/vB rate that WalletViewModel passes to SendPlanner. Fractional estimates round upward consistently instead of being displayed as fractional values and silently truncated during planning.
- Convert RPC QBX/kvB units using decimal arithmetic before conversion to Double. This prevents floating-point overshoot at exact integer rates, e.g. 1 sat/vB accidentally rounding up to 2.
- Invalid/non-finite/non-positive/excessive estimates retain the existing policy fallback rates. Existing maximum absolute transaction fee checks and final exact-fee approval remain unchanged.
- All 17 locales include the new explanation and Recommended label (188 strings per locale).
- Key derivation, storage, wallet.dat format, signing and transaction construction are unchanged. Ten key-related files are compared byte-for-byte against 1.6.9 in verification/1.6.10/unchanged-key-sources.json.

Verification:
- Public read-only RPC evidence: verification/1.6.10/rpc-fees.json. All three estimates were 5 sat/vB; relay/mempool minimum was 1 sat/vB at the time of the check. A relay minimum is not a guarantee of confirmation time.
- JVM: 58 passed, 1 pre-existing optional external-fixture test skipped, no failures/errors. Includes equal/partial/distinct options, unit conversion, fractional rates, invalid data and planner fee differences.
- Android 15 x86_64: 12 selected tests passed, none skipped. Includes grouped and distinct fee UI, confirmation text/callback policy, seed safety/send confirmation/import/receive regressions, three history statuses, and no-txindex balance decoding.
- The opt-in live Android fee test fetched all three estimates through the actual NodeRpcClient and verified that they produce one 5 sat/vB choice. It is skipped in ordinary CI without explicit live parameters.
- Local build succeeded; lint: 0 errors, 58 warnings. APK signature verified and matches 1.6.9. Test data was cleared and the emulator stopped afterward.

APK: QBitX-Android-1.6.10-fee-options-preview.apk, 23679382 bytes.
SHA-256: b32af47cc503aad901525a0dd4ced97e4b1a5db8d685ea13b10d2340aefd1645.

This remains a debug-signed preview, not independent production security certification. Fee estimates are not confirmation-time guarantees. Existing node-trust, history-discovery, wallet.dat and physical-device audit limitations remain. No real funds were sent and no production Core/pool/network configuration was changed.
