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
}
