package org.nmox.studio.ui.whatsnew;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The bundled release notes ARE the repo's CHANGELOG — byte-for-byte, with a parseable head. */
class BundledChangelogTest {

    @Test
    @DisplayName("The ui jar carries CHANGELOG.md verbatim and its head entry parses")
    void bundledIsTheRepoChangelog() throws Exception {
        try (InputStream in = ReleaseNotes.class.getResourceAsStream("CHANGELOG.md")) {
            assertThat(in).as("CHANGELOG.md bundled beside ReleaseNotes (ui/pom.xml bundle-changelog)").isNotNull();
            String bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String repo = Files.readString(Path.of("..", "CHANGELOG.md"));
            assertThat(bundled).isEqualTo(repo);
            ReleaseNotes.Entry head = ReleaseNotes.head(ReleaseNotes.parse(bundled));
            assertThat(head).isNotNull();
            assertThat(head.version()).matches("\\d+\\.\\d+\\.\\d+");
            assertThat(head.body()).isNotBlank();
        }
    }
}
