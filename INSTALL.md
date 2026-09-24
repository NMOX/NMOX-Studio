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
sudo apt install ./nmox-studio_<version>_amd64.deb
nmox-studio        # or launch "NMOX Studio" from your app menu
```

**Any distribution**

```bash
tar -xzf NMOX-Studio-<version>-linux.tar.gz
./nmox-studio-<version>/bin/nmoxstudio
```

## From a terminal: `nmox .`

`cd myproject && nmox .` opens the folder you are in, the way `code .`
does; `nmox src/app.js` opens a file. The command returns at once and
hands later folders to the IDE that is already running.

- **macOS, Homebrew:** the cask puts `nmox` on your PATH.
- **macOS, DMG:** link the app's launcher yourself (a link, not a copy):
  `sudo mkdir -p /usr/local/bin && sudo ln -s "/Applications/NMOX Studio.app/Contents/MacOS/nmox-studio" /usr/local/bin/nmox`
- **Windows:** leave the installer's *Add "nmox" to PATH* box ticked (it
  is by default), then open a new terminal. Uninstalling removes it.
- **Linux:** the `.deb` installs `/usr/bin/nmox`. From the tarball:
  `ln -s "$PWD/nmox-studio-<version>/bin/nmox" ~/.local/bin/nmox`

## From the file manager

A folder handed over by the operating system is aimed exactly as `nmox .`
aims it.

- **macOS:** right-click a folder in Finder and choose NMOX Studio under
  **Open With**, or drop the folder on the Dock icon. A file dropped
  there opens in the editor.
- **Linux (`.deb`):** NMOX Studio appears under *Open With* for folders in
  your file manager. It is offered, never made the default.
- **Windows:** tick *Add "Open with NMOX Studio" to the right-click menu of
  folders in Explorer* when installing (unticked by default). Explorer then
  offers **Open with NMOX Studio** on a folder and inside one (Windows 11:
  under *Show more options*). Uninstalling removes it.

## Portable (all platforms)

`NMOX-Studio-<version>-portable.zip` contains the full application:
extract anywhere and run `bin/nmoxstudio` (macOS/Linux) or
`bin\nmoxstudio64.exe` (Windows).

This is the **one artifact that does not bundle a Java runtime** — it
needs Java 21+ on the machine. Every other download brings its own.

## Verifying what you downloaded

Two checks, because they answer different questions: GPG for *are these the
bytes we published* (every platform, every asset) and `spctl` for *will macOS
vouch for it*. Both are written out, **in fifteen languages**, in the user
guide's [Verifying your download](./docs/user-guide.md#verifying-your-download).

## Building installers from source

```bash
mvn clean package -DskipTests
./packaging/macos/build-dmg.sh 1.2.3        # macOS only
./packaging/linux/build-packages.sh 1.2.3   # tar.gz anywhere, .deb on Debian
iscc /DAppVersion=1.2.3 packaging\windows\nmox-studio.iss   # Windows + Inno Setup 6.3+
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
