package org.nmox.studio.editor.emmet;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-proof seam law (v1.321.0) applied to Emmet: {@link EmmetTest}
 * proves the grammar diverges correctly, and THIS gate proves the call
 * sites exist — the action registered for both markup mimes, the chord
 * bound to the action's exact name, and the layer carrying the binding
 * under both mimes. A green grammar with an unwired chord is a payload
 * without a gate.
 */
class EmmetWiringGateTest {

    @Test
    @DisplayName("the action is mime-registered for HTML AND Angular templates")
    void actionRegisteredForBothMimes() throws Exception {
        String src = Files.readString(new File(
                "src/main/java/org/nmox/studio/editor/emmet/ExpandAbbreviationAction.java")
                .toPath());
        assertThat(src)
                .contains("mimeType = \"text/html\"")
                .contains("mimeType = \"text/x-ng-template\"")
                .contains("name = \"nmox-expand-abbreviation\"");
    }

    @Test
    @DisplayName("⌥⌘E dispatches the action by its exact name, in both mimes' layers")
    void chordPinned() throws Exception {
        String xml = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/emmet-keybindings.xml")
                .toPath());
        assertThat(xml)
                .as("the chord must name the action EXACTLY — a rename on"
                        + " either side silently kills the binding")
                .contains("<bind actionName=\"nmox-expand-abbreviation\" key=\"DA-E\"/>");
        String layer = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/layer.xml").toPath());
        assertThat(layer.split("emmet-keybindings\\.xml", -1).length - 1)
                .as("the binding file is registered under BOTH markup mimes"
                        + " (html + x-ng-template), each as name + url")
                .isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("the action's replacement span extends past the caret by the folded closers")
    void spanExtensionWired() throws Exception {
        String src = Files.readString(new File(
                "src/main/java/org/nmox/studio/editor/emmet/ExpandAbbreviationAction.java")
                .toPath());
        assertThat(src)
                .as("v1.332.0: without the trailingClosers offset the auto-"
                        + "closed brace SURVIVES the expansion as a stray }")
                .contains("caret + at.trailingClosers() - abbrev.length()");
    }

    @Test
    @DisplayName("the CSS branch is registered and bound on all five css-family mimes")
    void cssBranchWired() throws Exception {
        String src = Files.readString(new File(
                "src/main/java/org/nmox/studio/editor/emmet/ExpandAbbreviationAction.java")
                .toPath());
        String layer = Files.readString(new File(
                "src/main/resources/org/nmox/studio/editor/layer.xml").toPath());
        for (String mime : new String[] {
            "text/css", "text/scss", "text/less", "text/x-scss", "text/x-less"}) {
            assertThat(src)
                    .as("action registration for %s — the css-prep mimes are"
                            + " the ones REAL .scss/.less files resolve to"
                            + " (v1.230.0, twice bitten)", mime)
                    .contains("mimeType = \"" + mime + "\"");
        }
        for (String folder : new String[] {"css", "scss", "less", "x-scss", "x-less"}) {
            assertThat(layer)
                    .as("layer keybinding folder for %s", folder)
                    .contains("<folder name=\"" + folder + "\">");
        }
        // the action must actually DISPATCH to the CSS grammar — a
        // registration whose handler never branches is a chord that
        // says "No abbreviation" on every stylesheet
        assertThat(src)
                .contains("CssEmmet.abbreviationIn(")
                .contains("CssEmmet.expand(");
        // and a + chain's continuation lines must take the line's own
        // indent (v1.338.0) — without this replace, the second
        // declaration lands at column zero
        assertThat(src.substring(src.indexOf("expandCss")))
                .contains(".css().replace(\"\\n\", \"\\n\" + leading)");
    }
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("HTML routes to CSS expansion ONLY inside style regions, clipped to the region")
    void htmlStyleRegionBranch() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/editor/emmet/ExpandAbbreviationAction.java"),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        // the branch exists, keys on the EXACT mime, and consults the regions
        assertThat(src).contains("m.equals(\"text/html\")");
        int at = src.indexOf("m.equals(\"text/html\")");
        String branch = src.substring(at, src.indexOf("Emmet.AtCaret", at));
        assertThat(branch)
                .as("region-gated: css expansion only inside a style region")
                .contains("HtmlStyleRegions.find");
        assertThat(branch)
                .as("the clip law: an abbreviation must never reach past the "
                        + "region start into markup (the inline-template discipline)")
                .contains("before = doc.getText(r.start(), caret - r.start());");
        assertThat(branch).contains("expandCss(");
    }

}
