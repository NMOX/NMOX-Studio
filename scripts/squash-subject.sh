#!/bin/bash
# The subject of a release's squash commit: the pull request's own title
# and its number, the form every release on main carries.
#
# GitHub names a squash merge after the PULL REQUEST only when the request
# holds more than one commit. With exactly one, it takes that commit's own
# message — so v3.5.8, a one-commit request whose branch commit was a
# working note, landed on main as "wip: maven-clean-plugin 3.5.0 (#842)"
# where every other release reads as its headline. A published commit
# on main is never rewritten, so the gate now states the subject itself
# instead of leaving it to a default that depends on a commit count.
#
# A pure decision, so it is a script that can be run and tested rather
# than a line inside the gate:
#   scripts/squash-subject.sh "<pr title>" <pr number>   ->  "<title> (#<n>)"
# It refuses an empty title, a title that is itself a working note, and a
# number that is not one: the gate stops before the merge rather than
# publish a subject nobody chose.
set -u
TITLE=${1-}; PR=${2-}
if [ -z "$TITLE" ] || [ -z "$PR" ]; then
  echo "usage: squash-subject.sh <pr-title> <pr-number>" >&2; exit 2
fi
case "$PR" in
  ''|*[!0-9]*) echo "squash-subject: '$PR' is not a pull request number" >&2; exit 2;;
esac
case "$TITLE" in
  wip:*|wip\ *|WIP:*|WIP\ *)
    echo "squash-subject: the pull request's title is a working note ('$TITLE'): retitle it before the gate" >&2; exit 3;;
esac
case "$TITLE" in
  *$'\n'*) echo "squash-subject: a subject is one line" >&2; exit 3;;
esac
printf '%s (#%s)\n' "$TITLE" "$PR"
