# Public RPC verification — 2026-11-01

Endpoint: https://rpc.qbitx.org/ with normal TLS verification. No production configuration changed.

- getblockchaininfo: main chain, synced, empty warnings at height 244140.
- scantxoutset: both legacy M and native dil1q descriptors accepted, success=true.
- estimatesmartfee: returned 0.00005 QBX/kB.
- getmempoolinfo, getrawmempool, getrawtransaction(verbose), gettxout: successful.
- Observed a real public PQ witness transaction: size 5387 bytes, vsize 1434.
- sendrawtransaction with invalid 00 and 40000 zero bytes: JSON decode error -22,
  confirming endpoint and an 80068-byte JSON transport without broadcasting a valid transaction.
- A read-only getblockcount request padded beyond 110000 bytes returned HTTP 413.
  Public proxy limit is 102400 bytes. Planner now caps inputs at 8 and the common
  RPC path rejects oversized requests before transmission.
- Upstream numeric scanblocks(from,to) is incompatible with current Core API.
  A bounded, one-block standard scanblocks(start,descriptors,height,height)
  returned "Index is not enabled for filtertype basic". App no longer invokes
  the incompatible API or advances a history cursor falsely. Full restored history
  is unavailable; current UTXOs and local transaction records remain usable.

No valid mainnet transaction was broadcast. Mainnet payment acceptance, real ARM
hardware and production readiness are not established by these checks. Local
signing must remain on-device; pqsendtoaddress/sendmany use node-held wallet keys
and are deliberately not substituted for noncustodial sendrawtransaction.

Earlier seed/signature, Android emulator and regtest evidence is described in
SECURITY_REVIEW.md; it is not an independent production certification.
