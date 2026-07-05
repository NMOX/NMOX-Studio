package org.nmox.studio.ui.actions;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.projectstudio.ClassicKit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Classic Kit dialog's decision surface: which checkbox says what,
 * which delivery mode greys what out, and how the outcome report reads.
 * The plan/validation logic itself lives (and is tested) in ClassicKit;
 * these helpers are what the dialog adds on top.
 */
class ClassicKitActionTest {

    @Test
    @DisplayName("Backbone's checkbox announces the Underscore it drags in")
    void backboneLabelCarriesUnderscore() {
        ClassicKit.Lib backbone = ClassicKit.libraries().stream()
                .filter(lib -> "backbone".equals(lib.id())).findFirst().orElseThrow();

        assertThat(ClassicKitAction.libraryLabel(backbone))
                .contains("Backbone 1.6.0")
                .contains("Underscore 1.13.7")
                .contains("wired first");
    }

    @Test
    @DisplayName("Everyone else's checkbox is just name and version")
    void plainLabelsForTheRest() {
        for (ClassicKit.Lib lib : ClassicKit.libraries()) {
            if (!"backbone".equals(lib.id())) {
                assertThat(ClassicKitAction.libraryLabel(lib)).isEqualTo(lib.label());
            }
        }
    }

    @Test
    @DisplayName("npm mode disables exactly the npm-incapable library (Prototype)")
    void npmModeDisablesPrototypeOnly() {
        for (ClassicKit.Lib lib : ClassicKit.libraries()) {
            assertThat(ClassicKitAction.enabledFor(lib, false))
                    .as(lib.id() + " enabled under Vendored").isTrue();
            assertThat(ClassicKitAction.enabledFor(lib, true))
                    .as(lib.id() + " under npm").isEqualTo(lib.npmCapable());
        }
    }

    @Test
    @DisplayName("Every generator the kit offers has an honest checkbox label")
    void generatorLabelsCoverTheKit() {
        var labels = ClassicKitAction.generatorLabels();

        assertThat(labels.keySet())
                .containsExactlyElementsOf(ClassicKit.generatorIds());
        assertThat(labels.get("webpack")).contains("entry auto-detected");
        assertThat(labels.get("bower")).contains("name from folder");
    }

    @Test
    @DisplayName("The report marks changes with a check and leaves-alone with a dash")
    void reportRendersHonestly() {
        String report = ClassicKitAction.renderReport(List.of(
                new ClassicKit.Outcome("vendor/jquery-3.7.1.min.js", "written", true),
                new ClassicKit.Outcome("index.html", "already wired", false),
                new ClassicKit.Outcome("webpack.config.js.suggested",
                        "existing webpack.config.js kept — suggestion written alongside", true)));

        assertThat(report)
                .contains("✓ vendor/jquery-3.7.1.min.js\n")
                .contains("– index.html  (already wired)")
                .contains("✓ webpack.config.js.suggested  (existing webpack.config.js kept");
        // "written" needs no echo — the check mark says it
        assertThat(report).doesNotContain("(written)");
    }
}
