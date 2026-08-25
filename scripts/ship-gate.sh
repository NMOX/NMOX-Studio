#!/bin/bash
# The hardened ship gate (v2.26.0 semantics, committed to the repo in
# v2.37.8 after a reboot wiped the scratchpad copy): prove the PR's
# checks, merge, then prove docs/main-green/tag against the MERGE SHA
# from the PR record — no worktree access, no version literals to rot
# (the docs-landed check derives from TAG). Run detached:
#   nohup scripts/ship-gate.sh <pr> <vX.Y.Z> > gate.log 2>&1 &
set -u
cd /Users/david/vcs/git/github/nmox/NMOX-Studio
PR=${1:?usage: ship-gate.sh <pr-number> <vX.Y.Z>}; TAG=${2:?usage: ship-gate.sh <pr-number> <vX.Y.Z>}
PUSH='git -c url.git@github.com:.insteadOf=ssh-bypass: push ssh-bypass:NMOX/NMOX-Studio.git'

for i in $(seq 1 60); do
  STATE=$(gh pr checks $PR 2>/dev/null | /usr/bin/awk -F'	' '{print $2}' | sort -u | tr '\n' ' ')
  echo "checks: $STATE"
  case "$STATE" in *fail*) echo "CHECKS-FAILED: $STATE"; exit 1;; esac
  [[ "$STATE" == *pass* && "$STATE" != *pending* ]] && break
  sleep 30
done
gh pr merge $PR --squash 2>&1 | /usr/bin/tail -1
# fetch only — NEVER checkout: a gate that switches the working tree
# to main while a unit is mid-flight silently reroutes the developer's
# commits onto local main (bit hard on 2026-08-25, PR 583 shipped
# near-empty). Every proof below reads objects via `git show <sha>:`,
# which needs the fetch, not a checkout.
git fetch -q origin main
echo "merged"
SHA=$(gh pr view $PR --json mergeCommit --jq '.mergeCommit.oid')
echo "merge sha: $SHA"
git show "$SHA:CHANGELOG.md" | grep -Fq "## [${TAG#v}] - " && echo "docs landed" || { echo "DOCS-MISSING"; exit 1; }
for i in $(seq 1 90); do
  R=$(gh run list --branch main --commit "$SHA" --json status,conclusion --jq '.[0] | .status+":"+(.conclusion // "")' 2>/dev/null)
  echo "main: $R::$SHA"
  [[ "$R" == completed:success ]] && break
  [[ "$R" == completed:* ]] && { echo "MAIN-RED: $R"; exit 1; }
  sleep 60
done
[[ "$R" == completed:success ]] || { echo "MAIN-TIMEOUT"; exit 1; }
echo "main-green"
git tag -a "$TAG" -m "nmox-studio ${TAG#v}" "$SHA"
eval "$PUSH $TAG" 2>&1 | /usr/bin/tail -1
echo "tagged $TAG"
for i in $(seq 1 90); do
  N=$(gh release view "$TAG" --json assets --jq '.assets | length' 2>/dev/null || echo 0)
  echo "assets: $N"
  [[ "$N" == 19 ]] && { echo "RELEASE-COMPLETE: 19 assets"; exit 0; }
  sleep 60
done
echo "ASSETS-TIMEOUT"; exit 1
