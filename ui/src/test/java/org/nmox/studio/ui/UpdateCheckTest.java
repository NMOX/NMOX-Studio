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
}
