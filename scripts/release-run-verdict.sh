#!/bin/bash
# What the ship gate should do while it waits for a tag's release assets
# (v2.174.0). Pure: every input is an argument, the only output is one
# word, and no network or repo is touched — so the decision the gate acts
# on can be proven by running THIS, rather than by reading the gate.
#
# Why it exists: the asset count alone cannot tell a slow upload from a
# dead one. GitHub's own release step fails often enough to matter
# (v2.172.0 once, v2.173.0 four times: "Headers Timeout Error", "Error
# creating asset temp dir", "Error saving asset"), and v2.173.0's gate sat
# out its whole 90-minute timer on a run that had already finished failing
# — leaving a 16-asset draft and the homebrew job skipped behind it.
#
# Usage: release-run-verdict.sh <assets> <status:conclusion:id> <reran> <rerun-seen>
# Echoes one of:
#   complete  the release is whole; stop waiting, happily
#   rerun     the run finished red and has not been re-run yet
#   fail      it finished red AGAIN after its one re-run; stop waiting, by name
#   wait      anything else — still running, still uploading, or the re-run
#             has not been seen running yet (that last case is the rollup's
#             own grace one surface over: right after `gh run rerun` the run
#             still reads `completed` for a poll or two, and a stale read
#             must never abort a healthy release)
set -u
ASSETS=${1:?usage: release-run-verdict.sh <assets> <status:conclusion:id> <reran> <rerun-seen>}
RUN=${2-}
RERAN=${3:-0}
SEEN=${4:-0}

[ "$ASSETS" = 21 ] && { echo complete; exit 0; }

case "$RUN" in
  completed:success:*) echo wait;;
  completed:*)
    if [ "$RERAN" = 0 ]; then echo rerun
    elif [ "$SEEN" = 1 ]; then echo fail
    else echo wait
    fi;;
  *) echo wait;;
esac
