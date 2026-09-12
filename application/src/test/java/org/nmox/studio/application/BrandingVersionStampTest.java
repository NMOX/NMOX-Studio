package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The version a user reads is stamped from the tag, and nothing else silently
 * ships in its place.
 *
 * <p>The Welcome footer, the About dialog, Report a Problem and the daily
 * update check all read {@code currentVersion} out of the branded startup
 * bundle. The committed value is the DEV SENTINEL {@code NMOX Studio 1.0},
 * which is load-bearing: {@code Versions.extract} reads 1.0 as "built from
 * source" and keeps a dev build out of the update check (v1.47.0). The three
 * release jobs rewrite that line from the tag before packaging.
 *
 * <p>David read {@code NMOX Studio 1.0} in a build I had made from the
 * worktree and asked why it was not 2.x. The answer was the sentinel, and it
 * was correct — but proving it meant downloading a published artifact BY HAND,
 * because nothing in the build held the two halves together. Every other
 * version claim in this project is gate-held; this one was not. So:
 *
 * <ul>
 *   <li>the committed value is exactly the sentinel, so a stray edit that
 *       hard-codes a real version (and would therefore ship stale, and would
 *       switch dev builds into the update check) fails here;
 *   <li>all three platform jobs stamp THAT file and THAT key — a rename of
 *       either breaks the build instead of quietly shipping the sentinel to
 *       users.
 * </ul>
 *
 * <p>What it cannot do is read the published asset; that stays a release-time
 * fact, checked by the update gauntlet booting a stock install.
 */
class BrandingVersionStampTest {

    private static final Path BUNDLE = Path.of("..", "branding", "src", "main", "nbm-branding",
            "core", "core.jar", "org", "netbeans", "core", "startup", "Bundle.properties");

    private static final Path WORKFLOW = Path.of("..", ".github", "workflows", "release.yml");

    /** The one value that means "built from source" (v1.47.0). */
    private static final String SENTINEL = "currentVersion=NMOX Studio 1.0";

    @Test
    @DisplayName("the committed branding version is the dev sentinel, never a real release")
    void theCommittedValueIsTheSentinel() throws IOException {
        assertThat(BUNDLE).as("the branded startup bundle").exists();
        List<String> versionLines = Files.readAllLines(BUNDLE).stream()
                .map(String::trim)
                .filter(l -> l.startsWith("currentVersion="))
                .toList();
        assertThat(versionLines)
                .as("exactly one currentVersion line, and it is the dev sentinel")
                .containsExactly(SENTINEL);
    }

    @Test
    @DisplayName("every release job stamps that file and that key")
    void everyPlatformStampsIt() throws IOException {
        assertThat(WORKFLOW).as("the release workflow").exists();
        String yaml = Files.readString(WORKFLOW).replace("\r\n", "\n");
        List<String> problems = new ArrayList<>();

        // the path each job rewrites, spelled as the workflow spells it
        String bundlePath = "branding/src/main/nbm-branding/core/core.jar/"
                + "org/netbeans/core/startup/Bundle.properties";
        List<Integer> at = new ArrayList<>();
        for (int i = yaml.indexOf(bundlePath); i >= 0; i = yaml.indexOf(bundlePath, i + 1)) {
            at.add(i);
        }
        if (at.size() != 3) {
            problems.add("the branding bundle is named in " + at.size()
                    + " places; linux, macOS and Windows each package an installer");
        }
        // ...and EACH of those places rewrites the key. Counting occurrences of
        // the key across the whole file is not enough: every job spells it
        // twice, as the pattern and as the replacement, so losing one still
        // leaves five — a mutant that renamed one survived exactly that way.
        for (int i : at) {
            String around = yaml.substring(Math.max(0, i - 400),
                    Math.min(yaml.length(), i + 400));
            // a rewrite names the key TWICE — once as the pattern it matches,
            // once as the replacement it writes. Requiring one occurrence let a
            // mutant rename the other and live; requiring both kills it.
            int spellings = around.split(java.util.regex.Pattern.quote(
                    "currentVersion=NMOX Studio "), -1).length - 1;
            if (spellings < 2) {
                problems.add("the job stamping at offset " + i + " names currentVersion "
                        + spellings + " times; a rewrite needs the pattern and the replacement, "
                        + "so this job would ship the sentinel");
            }
        }
        assertThat(problems)
                .as("a release that would ship the dev sentinel to users")
                .isEmpty();
    }
}
