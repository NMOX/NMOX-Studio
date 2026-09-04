#!/bin/bash
# The third half of the pipeline, in-repo since v2.68.2 (ship-branch.sh opens
# the PR, ship-gate.sh proves and tags, this one lands the release on the
# machine): wait for the release's 21 assets, fast-forward the MAIN checkout,
# wait for the Homebrew tap to carry the version, reinstall the cask, and
# delete the merged unit branch. Every wait has a leash and says which one
# expired; nothing here checks out a branch in a worktree it does not own.
# Usage: scripts/post-ship.sh <vX.Y.Z> [merged-branch]
set -u
TAG=${1:?usage: post-ship.sh <vX.Y.Z> [merged-branch]}
BRANCH=${2:-}
VER=${TAG#v}
cd "$(git rev-parse --show-toplevel)" || exit 2
git rev-parse --abbrev-ref HEAD | grep -qx main || { echo "NOT-ON-MAIN: run from the main checkout"; exit 2; }
for i in $(seq 1 60); do n=$(gh release view "$TAG" --repo NMOX/NMOX-Studio --json assets -q '.assets|length' 2>/dev/null); [ "${n:-0}" -ge 21 ] && break; sleep 30; done
[ "${n:-0}" -ge 21 ] || { echo "ASSETS-INCOMPLETE: ${n:-0} of 21 after 30 min"; exit 1; }
echo "assets: $n"
git pull -q --ff-only origin main || { echo PULL-FAILED; exit 1; }
echo "main: $(git log --oneline -1)"
TAP=$(brew --repository)/Library/Taps/nmox/homebrew-nmox-studio
for i in $(seq 1 60); do git -C "$TAP" pull -q 2>/dev/null; grep -q "version \"$VER\"" "$TAP/Casks/nmox-studio.rb" 2>/dev/null && break; sleep 30; done
grep -q "version \"$VER\"" "$TAP/Casks/nmox-studio.rb" || { echo "TAP-NOT-AT-$VER after 30 min"; exit 1; }
brew reinstall --cask nmox/nmox-studio/nmox-studio 2>&1 | tail -1 || { echo CASK-FAILED; exit 1; }
echo "cask: $(brew list --cask --versions nmox-studio)"
if [ -n "$BRANCH" ]; then git branch -D "$BRANCH" 2>&1 | tail -1; fi
echo "POST-SHIP-DONE $TAG"
