# Installing NMOX Studio

NMOX Studio runs on macOS, Windows and Linux. Every platform needs a
**JDK 17 or newer** on the machine (set `JAVA_HOME`, have `java` on the
PATH, or pass `--jdkhome /path/to/jdk` at launch). For the rack devices
you will also want **Node.js + npm** and **git** — NMOX Studio finds
them in the standard install locations (Homebrew, nvm, volta, fnm,
asdf) even when launched from a desktop icon.

Download artifacts from the
[releases page](https://github.com/NMOX/NMOX-Studio/releases).

## macOS

1. Open `NMOX-Studio-<version>-macos.dmg` and drag **NMOX Studio** to
   Applications.
2. The build is ad-hoc signed but not notarized, so the **first** launch
   needs one of:
   - right-click the app → **Open** → Open, or
   - `xattr -dr com.apple.quarantine "/Applications/NMOX Studio.app"`

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

- **"Cannot find java"** — install a JDK 17+ (e.g. Temurin) and set
  `JAVA_HOME`, or launch with `--jdkhome /path/to/jdk`.
- **Devices say "launch failed: Cannot run program npm"** — install
  Node.js; NMOX Studio searches Homebrew, nvm, volta, fnm and asdf
  locations automatically.
- **Settings location** — user data lives under
  `~/Library/Application Support/nmoxstudio` (macOS),
  `%LOCALAPPDATA%\nmoxstudio` (Windows), `~/.nmoxstudio` (Linux).
