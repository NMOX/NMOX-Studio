package org.nmox.studio.rack.projectstudio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The beginner-persona grants (v1.264.0), pinned. The 2026-08-04
 * first-run walk found a brand-new user's FIRST Run dying with node's
 * raw errno dump because the Vanilla Web template pinned
 * {@code http-server -p 8080} and something else owned 8080 — while
 * http-server WITHOUT a -p scans upward from 8080 and prints the port
 * it actually bound (measured live: 8080 busy -> "Available on:
 * http://127.0.0.1:8081"). And of 88 learning spaces, not one existed
 * for someone who has never written HTML.
 */
class BeginnerPathTest {

    @Test
    @DisplayName("the Vanilla Web template never pins a port — first Run survives a busy 8080")
    void vanillaWebDoesNotPinAPort() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/ProjectTemplates.java"),
                StandardCharsets.UTF_8);
        assertThat(src)
                .as("http-server scans from 8080 when no -p is given; pinning "
                        + "one turns a busy port into a beginner-facing crash")
                .doesNotContain("http-server -p");
    }

    @Test
    @DisplayName("the catalog opens with a space for someone who has never written HTML")
    void firstWebPageSpaceExistsAndLeads() throws Exception {
        String json = Files.readString(Path.of(
                "src/main/resources/org/nmox/studio/rack/projectstudio/learn-catalog.json"),
                StandardCharsets.UTF_8);
        assertThat(json).contains("\"first-web-page\"");
        assertThat(json).contains("Your First Web Page");
        assertThat(json).as("the first space sends the learner to the ⇄ chip SOLDER lights since v2.69.15, not only to a copied address")
                .contains("Click the **⇄ serving** chip on the status line");
        // FIRST in the catalog: the picker keeps catalog order, and the
        // one reader the other 88 spaces skip should not have to scroll
        int first = json.indexOf("\"slug\"");
        assertThat(json.indexOf("first-web-page"))
                .as("the beginner space is the first entry")
                .isLessThan(json.indexOf("\"slug\"", first + 1));
        // its server command must not pin a port either
        assertThat(json).doesNotContain("http-server\", \"-p");
    }
}
