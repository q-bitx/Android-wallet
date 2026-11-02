# Q-BitX ecosystem alignment

References inspected on 1 November 2026:
https://mining.qbitx.org/ and https://explorer.qbitx.org/.
The local mining-web/src/styles.css and explorer-fix/src/index.css confirm their shared tokens.

| Role | Token |
|---|---|
| Background | #0B0E11 |
| Card | #111619 |
| Raised card | #161D1B |
| Accent panel | #14291D |
| Mint action/accent | #00FF88 |
| Action foreground | #06160C |
| Body text | #E7EFEC |
| Secondary text | #96AA9C |
| Fine border | #25302F |

Use the same hexagonal Q-BitX symbol, mint X, compact WALLET wordmark, neutral sans-serif
headings and monospace addresses/labels. Thin card outlines and restrained green surfaces
replace the purple/blue palette. Buttons use dark text on mint; primary card corners are 8 dp.
Adaptive launcher icons use the ecosystem symbol and dark background.

The seed screen uses numbered word cells, responsive columns for large text/small screens,
an offline-backup instruction, a mandatory three-word confirmation, and the visible recovery
format. No seed-copy button is added to onboarding. Seed instructions and address-switch
labels are translated into all four existing locales. Native PQ is the default receive address;
legacy M remains available without changing the wallet's keys.

Device UI tests render the dashboard, seed, receive QR and payment confirmation using public
test data. Saved screenshots are captures of actual Compose rendering on the Android emulator;
they are not mockups or images of a funded account. See the audit artifact for visual evidence.
