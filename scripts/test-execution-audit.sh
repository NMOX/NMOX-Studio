#!/bin/sh
# Test-execution audit: every module that declares tests must actually run
# them. Guards the unbound-surefire class of bug.
#
# Why this exists: the application module's surefire was never bound to a
# lifecycle phase, so its test sources compiled but never executed — its
# LauncherJavaSelectionTest and ApplicationTest were dead weight, and that
# was discovered incidentally during PR #70, not by CI. A module can lose
# its test execution silently (wrong packaging, a botched plugin config, a
# skip flag) and every remaining test still passes, so "BUILD SUCCESS"
# proves nothing about the module that went dark.
#
# This runs AFTER `mvn verify`: for each module containing *Test.java
# sources, it asserts the module produced surefire reports that ran at
# least one test. A module with test sources but zero executed tests fails
# the audit.
#
# Usage: scripts/test-execution-audit.sh
# Exit:  0 every test-bearing module executed tests; 1 otherwise.
set -eu

# Modules are the immediate child dirs with a pom.xml. Keeping this
# derived (not a hardcoded list) means a new module is covered the day it
# is added, without editing this script.
FAILED=0
CHECKED=0

for pom in */pom.xml; do
    module="${pom%/pom.xml}"
    testdir="$module/src/test"

    # Only modules that actually declare tests are in scope. "Declares
    # tests" is matched against surefire's OWN default include patterns
    # (*Test, Test*, *Tests, *TestCase) — not just the repo's current
    # *Test.java convention — so a future FooTests.java that surefire
    # would run but a narrower glob would miss can't create a blind spot
    # where an unbound surefire goes unnoticed.
    [ -d "$testdir" ] || continue
    testcount="$(find "$testdir" \( -name '*Test.java' -o -name 'Test*.java' \
        -o -name '*Tests.java' -o -name '*TestCase.java' \) 2>/dev/null \
        | wc -l | tr -d ' ')"
    [ "$testcount" -gt 0 ] || continue

    CHECKED=$((CHECKED + 1))
    reports="$module/target/surefire-reports"

    if [ ! -d "$reports" ]; then
        echo "AUDIT FAIL: $module has $testcount test source(s) but produced" >&2
        echo "            no surefire-reports directory — its tests never ran." >&2
        echo "            (unbound surefire? wrong packaging? check the module pom.)" >&2
        FAILED=1
        continue
    fi

    # Freshness guard against a stale report faking a green: if this build
    # recompiled the tests but did not run them, the compiled test classes
    # are newer than every report. When target/test-classes exists, at
    # least one TEST-*.xml must be newer than it. In CI this never triggers
    # (the runner checks out fresh, so reports are always written after the
    # compile in the same run); it closes the local "audit without a
    # rebuild after breaking test execution" hole.
    testclasses="$module/target/test-classes"
    if [ -d "$testclasses" ] \
        && [ -z "$(find "$reports" -name 'TEST-*.xml' -newer "$testclasses" 2>/dev/null)" ]; then
        echo "AUDIT FAIL: $module — surefire reports are older than its compiled" >&2
        echo "            test classes: the reports are stale, tests did not run" >&2
        echo "            in this build. Re-run after 'mvn verify' (or 'mvn clean')." >&2
        FAILED=1
        continue
    fi

    # Sum tests="N" across every TEST-*.xml the run produced. Reading the
    # XML (not the .txt summary) is robust to locale and to the summary
    # line format changing between surefire versions.
    ran="$(grep -ho 'tests="[0-9]*"' "$reports"/TEST-*.xml 2>/dev/null \
        | grep -o '[0-9]*' \
        | awk '{ s += $1 } END { print s + 0 }')"
    # awk always prints a number, but guard the arithmetic test anyway so a
    # surprise non-numeric can never abort the loop under set -e.
    case "$ran" in ''|*[!0-9]*) ran=0 ;; esac

    if [ "$ran" -eq 0 ]; then
        echo "AUDIT FAIL: $module has $testcount test source(s) but its" >&2
        echo "            surefire reports show 0 tests executed." >&2
        FAILED=1
        continue
    fi

    echo "audit: $module — $testcount test source(s), $ran test(s) executed"
done

if [ "$CHECKED" -eq 0 ]; then
    # A repo-shape change (no module has test sources) should not silently
    # pass a check whose entire job is to find zero-execution modules.
    echo "AUDIT FAIL: found no modules with *Test.java sources — did the module" >&2
    echo "            layout change, or is this being run from the wrong dir?" >&2
    exit 1
fi

if [ "$FAILED" -ne 0 ]; then
    echo "test-execution audit FAILED — a module with tests ran none of them." >&2
    exit 1
fi

echo "test-execution audit OK — all $CHECKED test-bearing module(s) executed their tests."
exit 0
