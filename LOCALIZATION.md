# Interface languages

English, Russian, Simplified Chinese, German, Spanish, Hindi, French, Portuguese,
Arabic, Turkish, Vietnamese, Indonesian, Korean, Japanese, Thai, Urdu and Filipino.

The chooser appears before wallet unlock on first launch. Its selection is stored
in a separate interface-preferences file, applied via a configuration context and
can be changed in Settings. Seed words remain the canonical English BIP39 dictionary.
Arabic and Urdu use RTL layout; the Q-BitX brand keeps its LTR ordering.

All 17 resource sets contain the same 183 keys with matching format placeholders.
wallet.dat, QBX, key/address identifiers and parameter tokens are protected. Android
requires the legacy `values-in` folder for Indonesian; the user-facing BCP47 code
remains `id`. Decimal input supports ASCII, Arabic-Indic, Persian and Devanagari
digits plus decimal comma/Arabic decimal separator, without rounding sub-satoshi
amounts. Thousands separators and scientific notation are not accepted.

Translation provenance: machine/AI assistance; no independent native-speaker audit.
Raw node diagnostic text may remain in the language returned by Core. The language
set covers major crypto-use audiences rather than claiming to map one language per
ranked country. Selection context: https://www.chainalysis.com/blog/2026-global-crypto-adoption-index/
Android locale context: https://developer.android.com/guide/topics/resources/app-languages

Since 1.6.5, Korean replaces Ukrainian; Japanese and Thai are added. A previously
saved Ukrainian choice triggers the chooser again rather than silently selecting
a language the user may not understand. Thai is included for the active Thai
crypto audience (2025 Chainalysis top-20 context: https://www.chainalysis.com/blog/2025-global-crypto-adoption-index/).
