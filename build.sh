#!/bin/bash
#
# Build NMOX Studio from a clean tree.
#
#   ./build.sh            the assembled app, no tests (mvn clean install -DskipTests)
#   ./build.sh --verify   the whole gate a PR must clear: tests, SpotBugs,
#                         find-sec-bugs and the JaCoCo floors (mvn clean verify)
#
# `install` rather than `package`, so every module lands in ~/.m2 and a later
# one-module rebuild (`mvn -o install -DskipTests -pl editor`) finds its
# siblings. The inner loop is written down in CONTRIBUTING.md.

set -e
cd "$(dirname "$0")"

case "$1" in
    ""|--verify) ;;
    *) echo "usage: ./build.sh [--verify]" >&2; exit 2 ;;
esac

if ! command -v mvn >/dev/null 2>&1; then
    echo "ERROR: Maven is not on PATH. Install Maven 3.6.3+ (brew install maven, or https://maven.apache.org/download.cgi)." >&2
    exit 1
fi

# Ask Maven which JVM it will build with - JAVA_HOME if set, else the java on
# PATH - rather than guessing from `java -version`, which can be a different
# JVM entirely. The root pom's enforcer holds the same rule; this only says it
# before the reactor starts.
MVN_JAVA=$(mvn -v 2>/dev/null | sed -n 's/^Java version: \([0-9][0-9]*\).*/\1/p')
if [ -z "$MVN_JAVA" ]; then
    echo "ERROR: could not read the Java version from 'mvn -v'." >&2
    exit 1
fi
if [ "$MVN_JAVA" -lt 25 ]; then
    echo "ERROR: Maven is using Java $MVN_JAVA; NMOX Studio builds with JDK 25 or newer." >&2
    echo "  macOS:    brew install openjdk@25" >&2
    echo "            export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home" >&2
    echo "  anywhere: Temurin 25 (https://adoptium.net) or Zulu 25 (https://www.azul.com/downloads/)" >&2
    echo "(The bytecode still targets 21; only the build JDK moved. On an older JDK the ui" >&2
    echo " module fails as hundreds of misleading 'cannot find symbol: Bundle' lines.)" >&2
    exit 1
fi

echo "Building NMOX Studio with $(mvn -v 2>/dev/null | head -1) on Java $MVN_JAVA"

if [ "$1" = "--verify" ]; then
    mvn clean verify || exit $?
else
    mvn clean install -DskipTests || exit $?
fi

if [ ! -x application/target/nmoxstudio/bin/nmoxstudio ]; then
    echo "ERROR: the build finished but application/target/nmoxstudio/bin/nmoxstudio is missing." >&2
    exit 1
fi
echo
echo "Built: application/target/nmoxstudio/"
echo "Run it: ./run.sh"
