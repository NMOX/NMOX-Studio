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

# a fail is terminal only when it belongs to the PR's CURRENT head —
# a force-push moments before this gate leaves the rollup showing the
# OLD head's failure until the new run registers (bit twice, v2.36.2
# and v2.39.1: both "failures" were stale reads). The grace: on fail,
# re-read after 20s and only die when the fail persists with nothing
# pending behind it.
FAILS=0
for i in $(seq 1 60); do
  STATE=$(gh pr checks $PR 2>/dev/null | /usr/bin/awk -F'	' '{print $2}' | sort -u | tr '\n' ' ')
  echo "checks: $STATE"
  case "$STATE" in
    *fail*)
      FAILS=$((FAILS+1))
      if [ "$FAILS" -ge 2 ] && [[ "$STATE" != *pending* ]]; then
        echo "CHECKS-FAILED: $STATE"; exit 1
      fi
      sleep 20; continue;;
  esac
  FAILS=0
  [[ "$STATE" == *pass* && "$STATE" != *pending* ]] && break
  sleep 30
done
gh pr merge $PR --squash 2>&1 | /usr/bin/tail -1
# fetch only — NEVER checkout: a gate that switches the working tree
# to main while a unit is mid-flight silently reroutes the developer's
# commits onto local main (bit hard on 2026-08-25, PR 583 shipped
# near-empty). Every proof below reads objects via `git show <sha>:`,
# which needs the fetch, not a checkout.
echo "merged"
SHA=$(gh pr view $PR --json mergeCommit --jq '.mergeCommit.oid')
echo "merge sha: $SHA"
# fetch with retry (v2.38.9): GitHub can take a few seconds to
# materialize a squash commit — `git show` losing that race read as a
# false DOCS-MISSING on 2026-08-26. Three tries, five seconds apart.
for i in 1 2 3; do
  git fetch -q origin main && git cat-file -e "$SHA" 2>/dev/null && break
  sleep 5
done
git cat-file -e "$SHA" || { echo "MERGE-SHA-UNFETCHABLE"; exit 1; }
git show "$SHA:CHANGELOG.md" | grep -Fq "## [${TAG#v}] - " && echo "docs landed" || { echo "DOCS-MISSING"; exit 1; }
# tree-identity fast path (v2.38.9, David: "these waits slow us
# down"): a squash of a GREEN pr head into an unmoved main produces a
# commit with the IDENTICAL TREE the three PR lanes just verified —
# same tree, same verdict, so waiting for main CI to re-verify it
# re-proves a proven thing at five minutes a release. Tag now; main
# CI still runs and its verdict lands in the log for the record.
PRHEAD=$(gh pr view $PR --json headRefOid --jq '.headRefOid')
if [ -n "$PRHEAD" ]    && [ "$(git rev-parse "$SHA^{tree}")" = "$(git rev-parse "$PRHEAD^{tree}" 2>/dev/null)" ]; then
  echo "tree-identical to the green PR head — tagging without the main-CI wait"
else
  echo "tree differs from PR head (main moved) — waiting for main CI"
  RERAN=0
  for i in $(seq 1 90); do
    R=$(gh run list --branch main --commit "$SHA" --json status,conclusion,databaseId --jq '.[0] | .status+":"+(.conclusion // "")+":"+(.databaseId|tostring)' 2>/dev/null)
    V=${R%:*}
    echo "main: $V::$SHA"
    [[ "$V" == completed:success ]] && break
    # a startup_failure is the workflow failing to LAUNCH, not red
    # tests (seen 2026-08-26, runner-side): one automatic rerun
    # before treating it as terminal
    if [[ "$V" == completed:startup_failure && "$RERAN" == 0 ]]; then
      RERAN=1
      gh run rerun "${R##*:}" 2>&1 | tail -1
      sleep 30
      continue
    fi
    [[ "$V" == completed:* ]] && { echo "MAIN-RED: $V"; exit 1; }
    sleep 60
  done
  [[ "$V" == completed:success ]] || { echo "MAIN-TIMEOUT"; exit 1; }
  echo "main-green"
fi
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
