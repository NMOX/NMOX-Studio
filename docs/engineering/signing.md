# How NMOX Studio signs things

Four independent signing systems. **Each answers a different question, and
none substitutes for another** — that is the whole point of this document,
because the sibling documents each describe one system and it is easy to
believe you have read about signing when you have read about a quarter of it.

| System | Answers | Covers | Certificate |
|---|---|---|---|
| [GPG checksums](#1-gpg-signed-checksums) | *are these the bytes we published?* | **all 21 assets**, every platform | ours, self-managed |
| [NBM signing](#2-nbm-signing) | *did this update come from us?* | the 11 module NBMs | ours, self-signed **by decision** |
| [Apple Developer ID](#3-apple-developer-id--notarization) | *will macOS let this run?* | `.app` + `.dmg` | Apple, since v3.0.0 |
| [Windows Authenticode](#4-windows-authenticode) | *will SmartScreen calm down?* | launchers + installer | **not purchased** |

Sibling documents, which this one deliberately does not repeat:
[release-signing.md](./release-signing.md) (the installer lanes, the
one-sitting checklist, and what the first real signing run actually cost) and
[nbm-signing.md](./nbm-signing.md) (the update-center mechanics).

---

## The design law: every lane is inert without its secret

No flags, no branches to forget, no "release mode". Each lane reads a
repository secret and **does nothing at all** when it is unset, so the same
`build-dmg.sh` produces an ad-hoc bundle on a laptop and a notarized one in
CI. `ReleaseSigningLanesGateTest` holds that property: each step gated on its
secret, and the unsigned path byte-identical to what it has always been.

The cost is stated plainly because it was paid in full: **a lane that cannot
run without its secret cannot be tested without its secret.** Five releases'
worth of real defects sat in reviewed, gated, never-executed code until a
certificate existed. See the *"What it really took"* section of
[release-signing.md](./release-signing.md).

---

## 1. GPG-signed checksums

**The only mechanism that covers Linux**, and the only one that covers every
artifact at once.

Each release builds one `SHA256SUMS` manifest over all 21 published assets and
detach-signs it with the project GPG key. Verifying one signature transitively
verifies everything — DMG, `-setup.exe`, `.deb`, `.tar.gz`, the portable zip,
the SBOM, all 11 NBMs, and the update catalog.

```bash
curl -sL https://raw.githubusercontent.com/NMOX/NMOX-Studio/main/KEYS | gpg --import
gpg --verify SHA256SUMS.asc SHA256SUMS
sha256sum -c SHA256SUMS --ignore-missing
```

Basenames only, so `sha256sum -c` works from a download directory. The public
key and its fingerprint live in [`KEYS`](../../KEYS) at the repo root.

**Linux needs nothing further.** There is no Gatekeeper or SmartScreen
equivalent; signed checksums are the convention. Adding more verification would
mean hosting an apt repository or shipping through Flathub — distribution
changes, not certificates.

## 2. NBM signing

The in-app updater ships 11 module NBMs per release, each `jarsigner`-signed
with a 4096-bit RSA keystore (root pom profile `sign-nbms`, activated by the
`nbm.keystore` property).

On its own that makes the Plugin Installer stop and ask the user to verify an
unknown certificate. So `ui/…/update/NmoxTrustedCerts.java` ships the
certificate's **public half** inside the product as a
`KeyStoreProvider` at `TrustLevel.TRUST` — the same mechanism Apache NetBeans
uses for its own certificate. `Utilities.verifyCertificates` then finds the
signer in a trusted store and the update reads TRUSTED with no prompt.

**Self-signed on purpose, and it stays that way.** This is our own update
channel, pinned to one exact certificate rather than delegated to a CA. A
commercial certificate would cost money and change nothing a user can observe —
the IDE is already deciding whether to trust *us*, not whether to trust a chain.
Trust is for that exact certificate, never a CA delegation.

`scripts/nbm-trust-probe.sh <tag>` repeats the platform's own verdict headlessly
for any release.

## 3. Apple Developer ID + notarization

Live since **v2.188.4**, shipped in **v3.0.0**. Signed as
`Developer ID Application: David Liedle (GVEU23Q6RB)`, notarized by Apple, with
the ticket stapled to both the app and the DMG.

### Order of operations, and why each step is where it is

1. **Sign the natives inside jars.** `codesign` signs *files*; a jar is a zip,
   so ten Mach-O libraries across five jars are invisible to the bundle scan.
   This runs first because rewriting a jar changes bytes the bundle seal covers.
2. **Sign every Mach-O, deepest first**, with `--options runtime --timestamp
   --entitlements`. Not `codesign --deep`, which Apple deprecated for signing:
   it reuses the top-level entitlements for nested code and skips shapes it does
   not recognise, and the notary service rejects one unsigned nested binary.
3. **Seal the bundle** last.
4. **`chmod -R a-w` the clusters** — *after* signing, never before, because
   `codesign` writes the signature **into** each binary and cannot sign a
   read-only one.
5. **Re-verify**, so the mode change is proven not to have broken the seal
   rather than assumed.
6. **`ditto` to a zip → notarize → staple the app.** `notarytool` takes an
   archive, never a bundle, and `ditto`'s zip is the one Apple documents.
7. **Build the DMG → sign → notarize → staple that too.**

### Two details that are not obvious

**The entitlements land on `jre/bin/java`, not only on the bundle.** The
bundle's main executable is a `/bin/sh` launcher, so the process that actually
JITs is a separate exec'd binary with its own entitlement evaluation. An
entitlement on the bundle alone would never reach it.

**Both the app and the DMG are stapled.** The DMG's ticket covers the download;
only the app's *own* ticket lets a first launch succeed offline once the user
has dragged it out.

### Why the clusters ship read-only

A signed bundle and an in-place updater cannot both be right. `/Applications/NMOX
Studio.app` is owned by whoever dragged it there, so its clusters are writable
by default and the updater would install **inside the bundle**, breaking the
seal the notarization ticket vouches for.

Read-only clusters push every install — module update *and* new third-party
plugin — into the userdir instead. Measured both directions on a real
2.187.0 → 2.187.1 update:

| cluster | files written inside the bundle | `codesign --verify --deep --strict` |
|---|---|---|
| writable | 1,068 | **exit 1** — *a sealed resource is missing or invalid* |
| read-only | **0** | **exit 0** |

The read-only run put 955 jars in the userdir and booted at 2.187.1 while the
bundle's own jars stayed at 2.187.0. Installing a third-party plugin was
measured separately and behaves the same: three files in the userdir, the
bundle byte-identical to the pristine DMG, signature still valid.

No platform change was needed. `Utilities.canWriteInCluster` gates on plain
`File.canWrite()`, and `InstallManager.checkTargetCluster` warns and falls
through to the userdir rather than throwing — it only raises `WRITE_PERMISSION`
when a caller forces a global install. The shadow path was always there.

### Verifying a release yourself

```bash
codesign --verify --deep --strict "/Applications/NMOX Studio.app"
xcrun stapler validate "/Applications/NMOX Studio.app"
spctl --assess --type execute -vv "/Applications/NMOX Studio.app"
```

The third should say `source=Notarized Developer ID`.

## 4. Windows Authenticode

Written, gated, and **not purchased** — ledger 86b.

`packaging/windows/authenticode-sign.ps1` signs the launcher executables before
Inno Setup packages them and the installer after it is built, then verifies each
against the Authenticode policy. It accepts Azure **Artifact Signing** (renamed
from Trusted Signing) or a `.pfx`.

Before buying, note what Microsoft's current docs require of the individual
path: Public Trust for **individuals is US/Canada only**, the Azure billing
account must be Account Type **Individual** with legal name and address matching
a government-issued ID, the *Artifact Signing Identity Verifier* role is needed,
and `Microsoft.CodeSigning` must be registered on the subscription.

Temper the expectation either way: **Authenticode buys less than notarization
did.** SmartScreen softens as download reputation accrues rather than flipping a
hard refusal on day one.

---

## Where the code is

| Piece | File |
|---|---|
| GPG checksums | `.github/workflows/release.yml` — *Checksums + GPG signature* |
| NBM signing profile | root `pom.xml` — profile `sign-nbms` |
| In-product TRUST certificate | `ui/src/main/java/org/nmox/studio/ui/update/NmoxTrustedCerts.java` |
| macOS signing, notarization, stapling | `packaging/macos/build-dmg.sh` |
| Hardened-runtime entitlements | `packaging/macos/entitlements.plist` |
| macOS certificate import | `.github/workflows/release.yml` — *Import Developer ID certificate* |
| Windows signing | `packaging/windows/authenticode-sign.ps1` |
| The gates | `application/src/test/java/org/nmox/studio/application/ReleaseSigningLanesGateTest.java` |
