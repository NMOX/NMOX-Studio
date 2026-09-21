# Installing NMOX Studio

NMOX Studio runs on macOS, Windows and Linux. **The installers bundle
their own Java runtime — you do not need a JDK installed.** That covers
the `.dmg`, the `-setup.exe`, the `.deb` and the `.tar.gz`.

The one exception is `-portable.zip`, which is deliberately
bring-your-own-Java and needs **Java 21 or newer** (set `JAVA_HOME`, have
`java` on the PATH, or pass `--jdkhome /path/to/jdk` at launch).

For the rack devices you will also want **Node.js + npm** and **git** — NMOX Studio finds
them in the standard install locations (Homebrew, nvm, volta, fnm,
asdf) even when launched from a desktop icon.

Download artifacts from the
[releases page](https://github.com/NMOX/NMOX-Studio/releases).

## macOS

1. Open `NMOX-Studio-<version>-macos.dmg` and drag **NMOX Studio** to
   Applications.
2. Double-click it. The app is signed with an Apple Developer ID and
   notarized by Apple, with the ticket stapled to both the app and the
   DMG, so it opens normally — no right-click, no `xattr`, and no
   network needed for the check.

## Windows

Run `NMOX-Studio-<version>-windows-setup.exe` and follow the wizard
(per-user install needs no admin rights). A start-menu entry and
optional desktop icon point at the 64-bit launcher.

Prefer no installer? The portable zip works on Windows too: extract and
run `bin\nmoxstudio64.exe`.

## Linux

**Debian/Ubuntu**

```bash
sudo apt install ./nmox-studio_<version>_all.deb
nmox-studio        # or launch "NMOX Studio" from your app menu
```

**Any distribution**

```bash
tar -xzf NMOX-Studio-<version>-linux.tar.gz
./nmox-studio-<version>/bin/nmoxstudio
```

## Portable (all platforms)

`NMOX-Studio-<version>-portable.zip` contains the full application:
extract anywhere and run `bin/nmoxstudio` (macOS/Linux) or
`bin\nmoxstudio64.exe` (Windows).

This is the **one artifact that does not bundle a Java runtime** — it
needs Java 21+ on the machine. Every other download brings its own.

## Verifying what you downloaded

Optional, and it takes about twenty seconds. Two independent checks, because
they answer different questions.

**Are these the bytes we published?** — works on every platform and covers
every asset, the `.deb` and `.tar.gz` included:

```bash
curl -sL https://raw.githubusercontent.com/NMOX/NMOX-Studio/main/KEYS | gpg --import
gpg --verify SHA256SUMS.asc SHA256SUMS
sha256sum -c SHA256SUMS --ignore-missing
```

`SHA256SUMS` and `SHA256SUMS.asc` are release assets. Verifying that one
signature transitively verifies everything listed in the manifest, so you do
not need a separate check per file. The key's fingerprint is in
[`KEYS`](./KEYS).

**Will macOS vouch for it?** — a different question, answered by Apple rather
than by us:

```bash
spctl --assess --type execute -vv "/Applications/NMOX Studio.app"
```

You want `source=Notarized Developer ID`. If you would rather see the whole
chain:

```bash
codesign --verify --deep --strict "/Applications/NMOX Studio.app"   # silent = valid
xcrun stapler validate "/Applications/NMOX Studio.app"              # ticket travels offline
```

The signature reads **Developer ID Application: David Liedle (GVEU23Q6RB)**.

A note on what stays true afterwards: the in-app updater (**Tools ▸ Plugins**)
installs into your user directory rather than into the app bundle, so updating
never invalidates that signature. You can re-run `codesign --verify` after an
update and get the same answer.

Windows installers are not signed yet — SmartScreen may warn on first run. The
GPG check above is the way to verify a Windows download.

## Building installers from source

```bash
mvn clean package -DskipTests
./packaging/macos/build-dmg.sh 1.2.3        # macOS only
./packaging/linux/build-packages.sh 1.2.3   # tar.gz anywhere, .deb on Debian
iscc /DAppVersion=1.2.3 packaging\windows\nmox-studio.iss   # Windows + Inno Setup 6
```

Releases are produced automatically by `.github/workflows/release.yml`
when a `v*` tag is pushed; all five artifacts land on the GitHub
release.

## Troubleshooting

- **"Cannot find java"** — you are on the portable zip, which ships no
  runtime. Install **Java 21+** (e.g. Temurin) and set `JAVA_HOME`, or
  launch with `--jdkhome /path/to/jdk`. The installers bundle a runtime
  and never hit this.
- **"Cannot run on older versions of Java than Java 21"** — same cause,
  but a JDK older than 21 is on the PATH. Point `--jdkhome` at a newer
  one rather than changing your system default.
- **Devices say "launch failed: Cannot run program npm"** — install
  Node.js; NMOX Studio searches Homebrew, nvm, volta, fnm and asdf
  locations automatically.
- **Settings location** — user data lives under
  `~/Library/Application Support/nmoxstudio` (macOS),
  `%LOCALAPPDATA%\nmoxstudio` (Windows), `~/.nmoxstudio` (Linux).
