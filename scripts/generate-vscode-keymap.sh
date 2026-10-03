#!/usr/bin/env bash
# Regenerates the VS Code keymap profile from scripts/vscode-keymap/chords.txt
# and the default (NetBeans) profile of the assembled cluster.
#
# A keymap profile inherits nothing from another profile (the editor storage
# reads Editors/Keybindings/<profile> alone; NbKeymap reads Shortcuts and
# Keymaps/<profile> alone), so the VS Code profile is generated rather than
# written: the generator is VsCodeKeymapProfile in the application module's
# tests, and VsCodeKeymapProfileGateTest fails the build when the committed
# files are not what it produces. Run this after editing chords.txt, after a
# chord is added to the default profile, or after a platform upgrade; then
# review and commit what it changed:
#
#   ui/src/main/resources/org/nmox/studio/ui/layer.xml   (the two marked regions)
#   ui/src/main/resources/org/nmox/studio/ui/keymap/*.xml
#   scripts/vscode-keymap/displaced.txt
#
# It builds the cluster with `verify` into the working tree's own target
# directories (nothing is installed into ~/.m2). Needs JDK 25 as JAVA_HOME.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
cd "$ROOT"

if ! mvn -o clean verify -pl application -am \
        -Dtest='VsCodeKeymapProfileGateTest' -Dsurefire.failIfNoSpecifiedTests=false \
        -Djacoco.skip=true -Dspotbugs.skip=true -Dnmox.vscode.keymap.write=true; then
    echo "generate-vscode-keymap: the build or the generator failed; nothing above is to be committed" >&2
    exit 1
fi
git status --short -- ui/src/main/resources/org/nmox/studio/ui scripts/vscode-keymap
