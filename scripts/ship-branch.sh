#!/bin/bash
# The ship half of the pipeline, in-repo since v2.68.1 (the gate half is
# scripts/ship-gate.sh): rebase a unit branch onto origin/main from the
# fork sha CAPTURED AT FORK TIME, push, open the PR, print PR=<n> last.
# The laws it carries, each paid for on 2026-09-02/03:
#   - the fork is a PARAMETER, never "the parent branch's current tip" — a
#     pipelined parent is rebased by its own ship, so its tip stops being an
#     ancestor and `rebase --onto` replays the whole lineage (PR #645);
#   - two pipelined branches inserting CHANGELOG/CLAUDE.md lines at the same
#     anchor collide at rebase; the resolver keeps both, ours above, the
#     newest headline wins — and refuses any NON-docs conflict;
#   - conflict-marker checks are LINE-ANCHORED (this changelog's prose quotes
#     the marker strings; a substring grep refused clean trees twice);
#   - the PR title is a quoted ARGUMENT, never a literal spliced into a script
#     (an apostrophe in "What's New" produced no PR and no error);
#   - every exit code is checked explicitly (a `| tail` masked a failed rebase).
# Usage (from the unit's worktree, on the unit's branch):
#   scripts/ship-branch.sh <branch> <fork-sha> "<pr title>" <pr-body-file>
set -u
set -o pipefail  # a `cmd | tail` must fail when cmd fails — the exit the header promises to check (v2.69.1 review)
BRANCH=${1:?usage: ship-branch.sh <branch> <fork-sha> "<pr title>" <pr-body-file>}
FORK=${2:?usage: ship-branch.sh <branch> <fork-sha> "<pr title>" <pr-body-file>}
TITLE=${3:?usage: ship-branch.sh <branch> <fork-sha> "<pr title>" <pr-body-file>}
BODY=${4:?usage: ship-branch.sh <branch> <fork-sha> "<pr title>" <pr-body-file>}
[ -f "$BODY" ] || { echo "NO-BODY-FILE: $BODY"; exit 2; }
cd "$(git rev-parse --show-toplevel)" || exit 2
git rev-parse --abbrev-ref HEAD | grep -qx "$BRANCH" || { echo "WRONG-BRANCH: on $(git rev-parse --abbrev-ref HEAD), expected $BRANCH"; exit 2; }
[ -z "$(git status --short)" ] || { echo "DIRTY-TREE: commit first"; exit 2; }
git fetch origin -q || { echo FETCH-FAILED; exit 1; }
git merge-base --is-ancestor "$FORK" "$BRANCH" || { echo "FORK-NOT-ANCESTOR: $FORK is not in $BRANCH's history"; exit 1; }

resolve_docs() {
python3 - "$@" <<'PY'
import re, sys
def newest(vs): return max(vs, key=lambda v: [int(x) for x in v.split('.')])
for path in sys.argv[1:]:
    s = open(path).read()
    if '<<<<<<<' not in s:
        continue
    # rebase: the first side is upstream (main's newest), the second is ours — keep both, ours above
    s = re.sub(r'<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n', lambda m: m.group(2) + m.group(1), s, flags=re.S)
    if path.endswith('CLAUDE.md'):
        heads = re.findall(r'\*\*Status\*\*: shipping \(v([0-9.]+)\), ', s)
        if len(heads) > 1:
            keep = newest(heads); seen = [False]
            def one(m):
                if seen[0]:
                    return ''
                seen[0] = True
                return m.group(1) + keep + m.group(3)
            s = re.sub(r'(\*\*Status\*\*: shipping \(v)([0-9.]+)(\), [^\n]*\n)', one, s)
    open(path, 'w').write(s)
    print('resolved:', path)
PY
}

if ! git rebase --onto origin/main "$FORK" "$BRANCH" > /tmp/ship-branch-rebase.log 2>&1; then
  U=$(git diff --name-only --diff-filter=U | tr '\n' ' ')
  echo "conflicts: $U"
  for f in $U; do case "$f" in CHANGELOG.md|CLAUDE.md) ;; *) git rebase --abort; echo "REBASE-FAILED-NONDOCS: $f"; exit 1;; esac; done
  resolve_docs $U || { git rebase --abort; echo RESOLVER-FAILED; exit 1; }
  git add $U
  GIT_EDITOR=true git rebase --continue > /tmp/ship-branch-rebase2.log 2>&1 || { git rebase --abort; echo REBASE-FAILED; exit 1; }
  echo REBASED-WITH-DOCS-RESOLUTION
else
  echo "REBASED: $(tail -1 /tmp/ship-branch-rebase.log)"
fi
grep -qE '^(<<<<<<< |>>>>>>> )' CHANGELOG.md CLAUDE.md && { echo REAL-MARKERS; exit 1; }
git log --oneline -3 | cat
git -c url."git@github.com:".insteadOf="ssh-bypass:" push ssh-bypass:NMOX/NMOX-Studio.git "$BRANCH" 2>&1 | tail -1 || { echo PUSH-FAILED; exit 1; }
URL=$(gh pr create --head "$BRANCH" --title "$TITLE" --body-file "$BODY" 2>&1 | tail -1)
echo "$URL"
PR=$(echo "$URL" | grep -oE 'pull/[0-9]+' | tail -1 | cut -d/ -f2)
[ -n "$PR" ] || { echo NO-PR; exit 1; }
echo "PR=$PR"
