# Signing the update-center NBMs

The in-app update center (Tools ▸ Plugins) installs the product's module
NBMs. Unsigned, the Plugin Manager shows an "unsigned plugin" caution on
every install. Signing removes that caution and lets the platform verify
the NBMs came from a keystore you control.

**This is off by default.** No keystore → no signing → the historical
behavior, unchanged. It turns on the moment three repository secrets exist;
nothing else needs editing.

## What's already wired

- **Root `pom.xml`** has a `sign-nbms` profile that activates whenever
  `-Dnbm.keystore=<path>` is passed. It hands the keystore, alias, and
  password to the `nbm-maven-plugin`, which signs each module's `.nbm`.
- **`.github/workflows/release.yml`** decodes a base64 keystore secret into
  a file and passes those `-D` properties to the release build — but only
  when the secret is present.

## One-time setup

### 1. Create a keystore

A self-signed key is enough to remove the "unsigned" caution (the platform
still notes it's not chained to a public CA, which is the norm for
open-source NBMs):

```bash
keytool -genkeypair \
  -keystore nmox-signing.jks -storepass 'CHOOSE-A-PASSWORD' \
  -keypass 'CHOOSE-A-PASSWORD' \
  -alias nmox -keyalg RSA -keysize 2048 -validity 3650 \
  -dname "CN=NMOX Studio, O=NMOX, C=US"
```

Keep `nmox-signing.jks` and the password somewhere safe and **out of git**.
Losing the key just means future releases sign with a new one; leaking it
lets someone else sign NBMs as you.

### 2. Add three repository secrets

Repository → Settings → Secrets and variables → Actions → *New repository
secret*:

| Secret | Value |
|---|---|
| `NBM_SIGNING_KEYSTORE_BASE64` | `base64 -i nmox-signing.jks` (the whole file, base64-encoded) |
| `NBM_SIGNING_ALIAS` | the `-alias` you chose (`nmox` above) |
| `NBM_SIGNING_PASSWORD` | the store/key password |

On macOS/Linux: `base64 -i nmox-signing.jks | pbcopy` (or `| xclip`) to copy
the encoded keystore straight to the clipboard.

### 3. Tag a release

The next `git tag vX.Y.Z && git push --tags` build detects the secret,
signs every module NBM, and the update-center catalog it publishes carries
signed NBMs. The workflow logs a `::notice::` line saying whether signing
ran.

## Verifying a signed NBM

```bash
jarsigner -verify some-module.nbm      # -> "jar verified."
unzip -l some-module.nbm | grep META-INF   # -> <ALIAS>.SF and <ALIAS>.RSA
```

## Notes

- Only the **linux** release job builds the update-center NBMs, so that's
  the only job that signs. The macOS/Windows installer jobs are unaffected.
- The password reaches Maven as a `-D` property (the plugin's supported
  input). GitHub masks the secret in logs; on the ephemeral runner it is
  briefly visible in the process list, which is the standard trade-off for
  CI signing.
- The signing identity is yours to create and rotate — the pipeline never
  embeds a key, only reads the secret you provide.
