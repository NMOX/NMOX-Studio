#!/bin/bash
#
# Run the assembled NMOX Studio from this checkout, building it first if it
# has never been built.
#
# It keeps its settings in ./userdir and its caches in ./userdir-cache (both
# ignored by git), never in your installed copy's. Delete both for a first
# launch. The launcher needs Java 21+ to run; JAVA_HOME is handed to it when
# set. The in-app update check is off: this build's module versions are the
# tree's dev numbers, and the update center would offer to "update" it to the
# latest release.
#
# After changing code, rebuild FROM THE ROOT (./build.sh, or
# `mvn -o install -DskipTests`) before running again - resuming the reactor at
# the application module assembles whatever module jars are in ~/.m2.

set -e
cd "$(dirname "$0")"
ROOT=$(pwd)

if [ ! -x application/target/nmoxstudio/bin/nmoxstudio ]; then
    echo "NMOX Studio is not built yet - building it first."
    ./build.sh
fi

ARGS=(--userdir "$ROOT/userdir" --cachedir "$ROOT/userdir-cache"
      -J-Dplugin.manager.check.updates=false)
[ -n "$JAVA_HOME" ] && ARGS+=(--jdkhome "$JAVA_HOME")
# Name the process for the macOS menu bar - a macOS-only JVM flag (fatal on
# Linux), hence the guard.
[ "$(uname)" = "Darwin" ] && ARGS+=(-J-Xdock:name="NMOX Studio")

exec application/target/nmoxstudio/bin/nmoxstudio "${ARGS[@]}" "$@"
