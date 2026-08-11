package org.nmox.studio.editor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The editor-side half of the ⌘P → Go to File fix (v1.216.0).
 *
 * <p>The platform's base editor keybindings bind D-P to tooltip-show,
 * and a keystroke the focused editor consumes never reaches the global
 * Shortcuts dispatcher — so v1.212.0's rebind worked everywhere EXCEPT
 * inside an editor, the one place a developer actually is when reaching
 * for Go to File. The v1.212.0 mask targeted a Keymaps file that exists
 * in no cluster; the only tree that can release the key is
 * Editors/Keybindings, and that release is what this test pins.
 */
class GoToFileKeyUnbindTest {

    private static final Path LAYER =
            Path.of("src/main/resources/org/nmox/studio/editor/layer.xml");
    private static final Path BINDINGS =
            Path.of("src/main/resources/org/nmox/studio/editor/nmox-keybindings.xml");

    @Test
    @DisplayName("the keybindings file removes the editor's D-P claim, key-only")
    void removesTheDPClaim() throws Exception {
        String xml = Files.readString(BINDINGS, StandardCharsets.UTF_8);
        // the REMOVE entry is key-only per the 1.1 DTD; naming an action
        // there would be a different (and wrong) statement. The file also
        // carries POSITIVE binds now (Angular-top arc) — those must name
        // their action, so the key-only rule is pinned per-line
        assertThat(xml).contains("<bind key=\"D-P\" remove=\"true\"/>");
        assertThat(xml).doesNotContain("actionName=\"\" ");
        for (String line : xml.lines().toList()) {
            if (line.contains("remove=\"true\"")) {
                assertThat(line).as("remove entries stay key-only")
                        .doesNotContain("actionName");
            }
        }
        assertThat(xml).contains("Editor KeyBindings settings 1.1");
    }

    @Test
    @DisplayName("the global chords carry the Angular-top actions (kits without them ignore the chord)")
    void globalChordsBound() throws Exception {
        String xml = Files.readString(BINDINGS, StandardCharsets.UTF_8);
        assertThat(xml)
                .as("⌥⌘E must reach kits whose mime-scoped Keybindings never load"
                        + " (the TypeScript pane, proven live)")
                .contains("<bind actionName=\"nmox-expand-abbreviation\" key=\"DA-E\"/>");
        assertThat(xml)
                .contains("<bind actionName=\"nmox-ng-goto-component\" key=\"DA-B\"/>");
    }

    @Test
    @DisplayName("registered in the tree the editor actually reads, after the platform file")
    void registeredInTheEditorsTree() throws Exception {
        String layer = Files.readString(LAYER, StandardCharsets.UTF_8);
        int keybindings = layer.indexOf("<folder name=\"Keybindings\">");
        assertThat(keybindings).as("Editors/Keybindings registration").isGreaterThan(0);
        String region = layer.substring(keybindings,
                layer.indexOf("</folder>", layer.indexOf("nmox-keybindings.xml")));
        assertThat(region).contains("<folder name=\"NetBeans\">");
        assertThat(region).contains("<folder name=\"Defaults\">");
        assertThat(region).contains("nmox-keybindings.xml");
        // the remove must be processed AFTER the platform's bind
        assertThat(region).contains("intvalue=\"9999\"");
    }
}
