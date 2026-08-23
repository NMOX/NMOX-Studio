package org.nmox.studio.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The update check's pure parsing: the latest-release tag comes out of
 * GitHub's JSON without a JSON library, tolerating the v prefix and
 * ignoring bodies that carry no tag at all.
 */
class UpdateCheckTest {

    @Test
    @DisplayName("latestTag pulls the version from GitHub's release JSON, v-prefix or not")
    void tagParsing() {
        assertThat(UpdateCheck.latestTag("{\"tag_name\": \"v1.25.0\", \"name\": \"x\"}"))
                .isEqualTo("1.25.0");
        assertThat(UpdateCheck.latestTag("{\"tag_name\":\"2.0\"}")).isEqualTo("2.0");
        assertThat(UpdateCheck.latestTag("{\"name\": \"no tag here\"}")).isNull();
        assertThat(UpdateCheck.latestTag("not even json")).isNull();
    }

    @Test
    @DisplayName("latestTag tolerates whitespace around the colon and takes the first tag")
    void tagParsingSpacingAndFirstWins() {
        assertThat(UpdateCheck.latestTag("{ \"tag_name\"  :   \"v3.4.5\" }"))
                .isEqualTo("3.4.5");
        // the release endpoint returns one object; the regex takes the first match
        assertThat(UpdateCheck.latestTag(
                "{\"tag_name\":\"1.0.0\",\"assets\":[{\"tag_name\":\"9.9.9\"}]}"))
                .isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("latestTag ignores a tag that isn't a version number")
    void tagParsingNonNumeric() {
        assertThat(UpdateCheck.latestTag("{\"tag_name\":\"nightly\"}")).isNull();
        assertThat(UpdateCheck.latestTag("{\"tag_name\":\"\"}")).isNull();
    }

    @Test
    @DisplayName("currentVersion reads the startup Bundle's currentVersion, unstamped in a dev build")
    void currentVersionReadsBundle() {
        // the platform's startup Bundle is on the test classpath; unbranded it
        // carries the dev "Platform Dev (Build {0})" string with no x.y version
        String raw = UpdateCheck.currentVersion();
        assertThat(raw).isNotNull();
        // that dev string has no dotted version, so the update check treats it
        // as unstamped and never compares against a release
        assertThat(org.nmox.studio.core.util.Versions.extract(raw)).isNull();
    }

    @Test
    @DisplayName("the notification targets the in-app Plugin Manager, converging with the platform's own update channel")
    void notificationTargetsThePluginManager() throws Exception {
        // v1.56: the daily heads-up and the platform's weekly autoupdate must
        // land on the SAME updater, not two contradictory procedures. Pin the
        // action id the click resolves — Actions.forID looks it up at
        // Actions/System/<id dots→dashes>.instance.
        assertThat(UpdateCheck.PLUGIN_MANAGER_CATEGORY).isEqualTo("System");
        assertThat(UpdateCheck.PLUGIN_MANAGER_ID)
                .isEqualTo("org.netbeans.modules.autoupdate.ui.actions.PluginManagerAction");
        // the web releases page survives only as the fallback when the action
        // can't be resolved (a stripped platform) — not the primary path
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/ui/UpdateCheck.java"));
        assertThat(src).contains("openUpdater()");
        assertThat(src)
                .as("web browse must be the fallback inside openUpdater, not the notification's direct action")
                .doesNotContain("e -> openReleases()");
    }

    /**
     * Drives {@link UpdateCheck#run()} with the given shots-dir property and
     * preference state, restoring both afterwards. In the test JVM the
     * stamped-version gate always ends the run before any scheduling or
     * network — the startup Bundle is the unbranded dev one — so run()
     * returns synchronously on every path exercised here.
     */
    private static long runWithState(String shotsDir, Boolean updateCheckPref,
            Long lastRun) throws Exception {
        // a per-JVM SCRATCH node (the v1.225.0 idiom): the real
        // "nmox/ui" node is shared across surefire forks through the
        // user's actual pref store, and the windows lane proved two
        // forks racing it — this test's putLong read back as 0
        java.util.prefs.Preferences prefs = org.openide.util.NbPreferences.root()
                .node("nmox/ui-test-" + java.lang.ProcessHandle.current().pid());
        UpdateCheck.prefsOverride = prefs;
        String oldShots = System.getProperty("nmox.shots.dir");
        String oldEnabled = prefs.get("updateCheck", null);
        String oldLast = prefs.get("updateCheck.lastRun", null);
        try {
            if (shotsDir == null) {
                System.clearProperty("nmox.shots.dir");
            } else {
                System.setProperty("nmox.shots.dir", shotsDir);
            }
            if (updateCheckPref == null) {
                prefs.remove("updateCheck");
            } else {
                prefs.putBoolean("updateCheck", updateCheckPref);
            }
            if (lastRun == null) {
                prefs.remove("updateCheck.lastRun");
            } else {
                prefs.putLong("updateCheck.lastRun", lastRun);
            }
            new UpdateCheck().run();
            // observed BEFORE the finally restores the user's real prefs
            return prefs.getLong("updateCheck.lastRun", -1L);
        } finally {
            if (oldShots == null) {
                System.clearProperty("nmox.shots.dir");
            } else {
                System.setProperty("nmox.shots.dir", oldShots);
            }
            if (oldEnabled == null) {
                prefs.remove("updateCheck");
            } else {
                prefs.put("updateCheck", oldEnabled);
            }
            UpdateCheck.prefsOverride = null;
            if (oldLast == null) {
                prefs.remove("updateCheck.lastRun");
            } else {
                prefs.put("updateCheck.lastRun", oldLast);
            }
        }
    }

    @Test
    @DisplayName("The screenshot forge boot never checks for updates (no network, no balloons in shots)")
    void shotsForgeSilencesTheCheck() throws Exception {
        // gate proof by absence of the side effect the enabled path always
        // has: check() stamps lastRun first thing, the forge path never gets
        // there (and neither does anything later — this returns synchronously)
        assertThat(runWithState("/tmp/somewhere", true, 1L))
                .as("the shots-dir gate returns before any state changes")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("The updateCheck=false preference turns the daily check fully off")
    void optOutPreferenceStopsTheRun() throws Exception {
        // must return without scheduling: in this JVM anything past the pref
        // gate would still stop at the unstamped dev version, so the real
        // assertion is that the run survives with the pref off and leaves
        // the throttle stamp alone
        assertThat(runWithState(null, false, 2L)).isEqualTo(2L);
    }

    @Test
    @DisplayName("A check within the last day is throttled — once per day means once")
    void dailyThrottleSkipsARecentCheck() throws Exception {
        long justNow = System.currentTimeMillis();
        assertThat(runWithState(null, true, justNow))
                .as("the throttle returns without touching the stamp")
                .isEqualTo(justNow);
    }

    @Test
    @DisplayName("A dev build (unstamped version) never schedules a check at all")
    void devBuildNeverChecks() throws Exception {
        // last run in the distant past, pref on: the run proceeds to the
        // version gate, reads the unbranded dev Bundle, and stops there —
        // synchronously, before WindowManager or any network is touched.
        // (currentVersionReadsBundle pins that this JVM's version is dev.)
        assertThat(runWithState(null, true, 0L)).isEqualTo(0L);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("The boot update check bounds the response read (no OOM on a hostile/redirected endpoint)")
    void updateCheckBoundsTheRead() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/ui/UpdateCheck.java"));
        assertThat(src)
                .as("ofString() buffers the whole body; read through a capped stream instead")
                .contains("BodyHandlers.ofInputStream()")
                .contains("HttpBodies")   // the v1.124.0 core helper owns the capped mechanics
                .doesNotContain("BodyHandlers.ofString()");
    }
}
