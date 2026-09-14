# Tutorial: Studio ng Kontrata (Web3)

<!-- languages -->
[English](contract-studio.md) · [Español](contract-studio.es.md) · [Français](contract-studio.fr.md) · [Deutsch](contract-studio.de.md) · [Русский](contract-studio.ru.md) · [Українська](contract-studio.uk.md) · [Polski](contract-studio.pl.md) · [Português (Brasil)](contract-studio.pt.md) · [Bahasa Indonesia](contract-studio.id.md) · **Filipino** · [Tiếng Việt](contract-studio.vi.md) · [简体中文](contract-studio.zh.md) · [हिन्दी](contract-studio.hi.md) · [עברית](contract-studio.he.md) · [العربية](contract-studio.ar.md)
<!-- /languages -->

Ang Studio ng Kontrata ay buong workbench para sa smart contract: puno ng
mga artifact ng Foundry/Hardhat, pakikipag-ugnayang ginagabayan ng ABI na
may nababasang mga return at revert, buhay na tagamasid ng block at event,
at pane ng pangangasiwa ng gas at laki — na may mahigpit na tuntunin:
**hindi kailanman dumadampi sa IDE ang anumang private key**.

Ito ang maikling paglilibot. Para sa buong halimbawa — pagsulat ng
kontratang escrow, pagsubok dito, at pagpapatakbo nito sa lokal na chain —
tingnan ang [making-a-smart-contract.md](../making-a-smart-contract.md).

![Tumatakbo ang ANVIL sa rack at kusang nakakonekta rito ang Studio ng Kontrata — chain 31337, ang kontrata sa puno ng artifact kasama ang gamit nito sa laking EIP-170](../images/contract-studio.png)

## Buksan ito

`⌥⌘6`, o ang tab na **Studio ng Kontrata**. Kakailanganin mo ang Foundry
(`anvil`, `forge`) na nakainstall; suriin gamit ang
`Kasangkapan ▸ Doktor ng Environment…`.

## Mga hakbang

1. **Magsimula ng lokal na chain.** Sa rack, ilagay ang **ANVIL** at
   pindutin ang GO — nagpapatakbo ito ng lokal na EVM devnet na may mga
   account na hindi naka-lock at may pondo na. Kusang kumokonekta rito ang
   Studio ng Kontrata.

2. **Buuin ang mga artifact.** Sa isang Foundry project, patakbuhin ang
   `forge build` (ang device na **FORGE**, o ang Buuin ng IDE). Napupuno ng
   iyong mga compiled na kontrata ang puno ng artifact ng Studio ng
   Kontrata.

3. **I-deploy at makipag-ugnayan.** Pumili ng kontrata, pindutin ang
   **I-deploy** (gumagamit ito ng account ng anvil na hindi naka-lock —
   walang pagtitipa ng key), saka gamitin ang pane na **Interact**: `CALL`
   ang isang view function at tingnan ang nababasang return; `SEND` ng
   transaksiyon at pagmasdan ang receipt. Isinasalin sa nababasang teksto
   ang mga revert at custom error.

4. **Masdan ang chain.** Sinusuri ng pane na **Watch** ang mga bagong
   block bawat ilang segundo at binabasa ang mga event log ayon sa iyong
   mga ABI. Ipinapakita ng pane na **Oversight** ang talaan ng gas, ang
   mga hatol sa laking EIP-170, at ang aklat ng mga address ng deployment.

## Ang iyong natutunan

- Dumadaan ang mga padala sa mga **account na hindi naka-lock** ng devnet
  — walang hawak na key ang IDE at walang code para pumirma.
- Nasa keychain lamang ang mga lihim na RPC URL at hindi kailanman
  isinusulat sa file.
- Nagtatanong ng kumpirmasyon bago ang anumang padala sa endpoint na
  **hindi loopback**, kaya hindi aksidenteng maibrodkast sa tunay na chain.

## Susunod

- Ang buong gabay ng escrow:
  [making-a-smart-contract.md](../making-a-smart-contract.md).
- Nasa rack ang GOVERNOR (bantay ng gas) at ang preset na Web3 Bench.
