# Official release signing — the one action

**Ledger 86 is a decision, not a task.** Apple Developer ID and Windows
Authenticode are a purchase and a legal identity; no principle about code
quality settles whether to spend someone else's money or enrol under their
name. This document exists so the decision can be made as a yes or a no,
with the price and the work both visible.

**The work is done.** The release lane signs and notarizes on macOS and
Authenticode-signs on Windows the moment the secrets exist — no code
change, no PR, no edit to this repository beyond adding secrets and (for
macOS) the cask cleanup named at the bottom.

**None of it has ever run.** There are no accounts, so both lanes are
written from Apple's and Microsoft's documentation, reviewed, and gated so
they cannot execute by accident. The first release carrying the secrets is
their first real test. [What can and cannot be verified](#what-is-unproven)
is spelled out below — read that before you set the secrets.

Sibling document: [nbm-signing.md](./nbm-signing.md) — the update-center
NBM signing that has been ON since v2.42.0, and the in-product TRUST
certificate (v2.43.0). This document is only about the *installers*.

---

## The cost, once a year

| | Price | Who must do it |
|---|---|---|
| Apple Developer Program | **$99 / year** | David personally — Apple verifies a legal identity and enrolment cannot be delegated |
| Azure Trusted Signing | **~$120 / year** (≈$9.99 / month) | Requires an Azure subscription and an identity validation that takes days |
| **Total** | **≈$219 / year** | |

Nothing else costs money. The GPG key, the self-signed NBM keystore and
the TRUST certificate already ship and stay as they are.

---

## What each one buys

**Apple Developer ID + notarization (~$99/yr)** — the DMG and the app
inside it carry a signature Apple can attribute, and a notarization ticket
stapled to both. Users stop seeing Gatekeeper's refusal on first launch,
and **the Homebrew cask stops clearing the quarantine attribute** — the
one place the product currently asks for trust it would rather not need.

**Windows Authenticode (~$120/yr)** — the launcher executables and the
installer carry a publisher signature. SmartScreen's "unknown publisher"
warning softens as reputation accrues; it does not vanish on day one.

**Either can be bought alone.** The lanes are independent: setting only the
macOS secrets signs and notarizes macOS while Windows ships unsigned
exactly as today, and the other way round.

---

## macOS — the checklist

1. **Enrol** at <https://developer.apple.com/programs/> ($99/yr, needs an
   Apple ID with two-factor and a legal identity check).
2. **Create a "Developer ID Application" certificate.** In Xcode:
   *Settings ▸ Accounts ▸ Manage Certificates ▸ + ▸ Developer ID
   Application*. Without Xcode, generate a CSR in *Keychain Access ▸
   Certificate Assistant* and upload it at
   <https://developer.apple.com/account/resources/certificates>.
   Not "Apple Development" and not "Mac App Distribution" — only a
   *Developer ID Application* certificate can notarize for distribution
   outside the App Store, and the release lane refuses any other kind by
   name.
3. **Export it as a .p12** from Keychain Access (right-click the
   certificate ▸ Export, choose a password). The export must include the
   private key — select the certificate, not just the key.
4. **Create an App Store Connect API key** for notarization:
   <https://appstoreconnect.apple.com/access/integrations/api> ▸ *Keys* ▸
   **+**, role *Developer*. Download the `.p8` **once** (it cannot be
   downloaded again) and note the **Key ID** and the **Issuer ID** shown
   above the table.
5. **Set four repository secrets** (*Settings ▸ Secrets and variables ▸
   Actions ▸ New repository secret*):

   | Secret | Value |
   |---|---|
   | `MACOS_SIGNING_CERTIFICATE_BASE64` | `base64 -i DeveloperID.p12 \| pbcopy` |
   | `MACOS_SIGNING_CERTIFICATE_PASSWORD` | the password you chose at export |
   | `MACOS_NOTARY_KEY_BASE64` | `base64 -i AuthKey_XXXXXXXX.p8 \| pbcopy` |
   | `MACOS_NOTARY_KEY_ID` | the Key ID (e.g. `2X9R4HXF34`) |
   | `MACOS_NOTARY_ISSUER_ID` | the Issuer ID (a UUID) |

   The signing identity is **not** a secret: the lane reads it out of the
   certificate itself, so it cannot go stale.

   *Alternative to steps 4–5's last three rows:* an app-specific password
   from <https://appleid.apple.com> works instead of an API key — set
   `MACOS_NOTARY_APPLE_ID`, `MACOS_NOTARY_PASSWORD` and
   `MACOS_NOTARY_TEAM_ID` (the ten-character Team ID from your membership
   page) and leave the three key rows unset. The API key is preferred: it
   is scoped to notarization and revocable on its own.

6. **Tag a release.** The lane signs every Mach-O in the bundle under the
   hardened runtime, notarizes the app, staples it, builds the DMG, signs
   and notarizes that, and staples it too. Expect the job to take
   **10–45 minutes longer** than today — Apple's notary queue decides.
7. **Do the cask cleanup** described at the bottom of this file. Until you
   do, installs keep clearing a quarantine attribute that is no longer
   there — harmless, but it makes the caveats a lie.

---

## Windows — the checklist

The ledger names **Azure Trusted Signing**, and that is the right route:
since the CA/Browser Forum's June 2023 rules, a publicly trusted
code-signing private key must live on certified hardware or in a cloud
HSM, so a downloadable `.pfx` from a public CA is no longer something you
can buy. Trusted Signing keeps the key in Azure and plugs into `signtool`.

1. **Create an Azure subscription** if there is none, then a **Trusted
   Signing account** (<https://portal.azure.com> ▸ *Trusted Signing
   accounts*). Choose the Basic tier (~$9.99/month).
2. **Complete identity validation.** Microsoft verifies the legal entity;
   this takes days, not minutes, and is the long pole.
3. **Create a certificate profile** (*Public Trust*) inside the account.
   Note the account name, the profile name, and the account's **endpoint**
   URI (e.g. `https://eus.codesigning.azure.net`).
4. **Create a service principal** with the *Trusted Signing Certificate
   Profile Signer* role on the account, and note its client id, tenant id
   and client secret.
5. **Set six repository secrets:**

   | Secret | Value |
   |---|---|
   | `AZURE_TRUSTED_SIGNING_ENDPOINT` | the account endpoint URI |
   | `AZURE_TRUSTED_SIGNING_ACCOUNT` | the Trusted Signing account name |
   | `AZURE_TRUSTED_SIGNING_PROFILE` | the certificate profile name |
   | `AZURE_CLIENT_ID` | the service principal's application id |
   | `AZURE_TENANT_ID` | the directory (tenant) id |
   | `AZURE_CLIENT_SECRET` | the service principal secret |

6. **Tag a release.** The lane signs the launcher executables before Inno
   Setup packages them and the installer after it is built, then verifies
   each against the Authenticode policy.

### The .pfx alternative

`packaging/windows/authenticode-sign.ps1` also accepts
`WINDOWS_SIGNING_PFX_BASE64` (+ `WINDOWS_SIGNING_PFX_PASSWORD`) and signs
with `signtool /f`. That path is kept for three real cases — an existing
pre-2023 certificate, an internal CA, or a self-signed certificate used to
prove the pipeline end to end — and it is the half that could be *written*
without an Azure account to test against. **A self-signed .pfx removes no
warning for users**; it only demonstrates that the lane signs.

---

## What is unproven

Written and reviewed, never executed. Specifically:

- **No signature has been produced**, on either platform. The gate
  (`ReleaseSigningLanesGateTest`) proves the steps exist, that each is
  gated on its secret, and that the unsigned path is byte-identical — it
  cannot prove that Apple accepts the bundle or that signtool accepts the
  dlib.
- **The workflow parses** (checked with a YAML parse; `actionlint` was not
  available on the machine that wrote this) and every packaging shell
  script passes `bash -n`.
- **Notarization may reject the first bundle.** The likeliest cause is a
  native library the scan finds unsigned — the bundle's own Mach-O files
  are all signed explicitly, but a `.dylib` or `.jnilib` carried *inside a
  jar* is not reachable by `codesign`. If that happens, the submission log
  names the file: `xcrun notarytool log <submission-id>`. The fix is to
  sign that library inside its jar (unzip, sign, rezip) in
  `build-dmg.sh` — a known, bounded piece of work, not a redesign.
- **The stapled ticket travelling with `cp -R`** into the DMG staging
  directory is assumed, not measured. If `stapler validate` on the app
  inside a mounted DMG ever fails, copy with `ditto` instead.
- **Timing.** Apple's notary queue is usually minutes and occasionally
  much longer; the lane waits up to 45 minutes and then fails the release
  rather than publishing an unnotarized DMG under a notarized tag.

---

## The day the macOS secrets are set: what comes out

Notarization removes the reason the Homebrew cask clears the quarantine
attribute. **Both homes must change together** — the checked-in
`Casks/nmox-studio.rb` and the heredoc in `.github/workflows/release.yml`
that regenerates it — because `CaskGeneratorParityTest` holds them
byte-identical (v2.149.0).

Delete from **both**:

1. The `postflight_steps do … end` block and the four comment lines above
   it beginning "The app is ad-hoc signed, not notarized".
2. The first `caveats` paragraph ("Heads up: … pinned by the sha256
   above.") and the second ("Installing from the DMG by hand instead? …
   `xattr -dr com.apple.quarantine` …"). Keep the in-app updater
   paragraph.

Nothing else: `depends_on`, `app` and `zap` are unaffected.

The same day, these documents stop being true and need re-wording, with a
translators' pass on the guides:

- `INSTALL.md` — the "ad-hoc signed but not notarized" first-launch note.
- `README.md` — the macOS note.
- `docs/user-guide.md` **and all fourteen translations** — two sentences
  each: the `brew trust` paragraph and the "macOS, first launch" callout.

---

## Where the code is

| Piece | File |
|---|---|
| macOS signing, notarization, stapling | `packaging/macos/build-dmg.sh` |
| Hardened-runtime entitlements | `packaging/macos/entitlements.plist` |
| macOS certificate import | `.github/workflows/release.yml` — *Import Developer ID certificate (optional)* |
| Windows signing | `packaging/windows/authenticode-sign.ps1` |
| Windows credentials | `.github/workflows/release.yml` — the `windows` job's `env:` block |
| The gate | `application/src/test/java/org/nmox/studio/application/ReleaseSigningLanesGateTest.java` |
