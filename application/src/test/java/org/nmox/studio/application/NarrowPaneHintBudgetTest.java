package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A hint that lives in a narrow pane has a budget, in every language
 * (v2.123.0).
 *
 * <p>Three walks in a row found the same defect wearing different clothes:
 * a message longer than the thing drawing it. The Workbench middle-elided
 * its tooling descriptions into gibberish (v2.119.0), the learning-space
 * picker clipped its blurbs mid-word and grew a sideways scrollbar
 * (v2.120.0), and Contract Studio"s empty-state hint — a row in a ~320px
 * tree — read "No artifacts found — Compile (forge b".
 *
 * <p>Where the text can wrap, wrapping wins and nothing is cut. Where it
 * CANNOT — a tree row is a row — the only honest answer is to write a
 * shorter sentence, and the only way that survives twelve translations is
 * to fail the build when one does not fit. The budget is a character count
 * rather than a measured width because the gate has no toolkit: it is
 * deliberately generous, and it is a ceiling on prose length, not a
 * promise about pixels.
 */
class NarrowPaneHintBudgetTest {

    /** Key → the budget its pane can show, with why that pane is narrow. */
    private static final Map<String, Integer> BUDGETS = Map.of(
            // a node in the contract tree, which shares the studio with the
            // Interact pane and is the narrower half
            "Web3StudioTopComponent_noArtifacts", 68,
            "Web3StudioTopComponent_noDeployments", 68);

    private static final List<Path> BUNDLES = List.of(
            Path.of("..", "web3", "src", "main", "resources", "org", "nmox", "studio", "web3", "ui"));

    @Test
    @DisplayName("every narrow-pane hint fits its budget in every language the product speaks")
    void hintsFitTheirPane() throws IOException {
        List<String> over = new ArrayList<>();
        int measured = 0;
        for (Path dir : BUNDLES) {
            assertThat(dir).as("the bundle directory this gate reads").isDirectory();
            try (var files = Files.list(dir)) {
                for (Path f : files.filter(p -> p.getFileName().toString().startsWith("Bundle"))
                        .filter(p -> p.toString().endsWith(".properties")).toList()) {
                    String lang = f.getFileName().toString()
                            .replace("Bundle", "").replace(".properties", "").replace("_", "");
                    for (String line : Files.readString(f, StandardCharsets.UTF_8).split("\n")) {
                        int eq = line.indexOf('=');
                        if (eq < 0) {
                            continue;
                        }
                        String key = line.substring(0, eq);
                        Integer budget = BUDGETS.get(key);
                        if (budget == null) {
                            continue;
                        }
                        measured++;
                        String value = line.substring(eq + 1).strip();
                        if (value.length() > budget) {
                            over.add((lang.isEmpty() ? "en" : lang) + " " + key + ": "
                                    + value.length() + " > " + budget + " — " + value);
                        }
                    }
                }
            }
        }
        assertThat(measured)
                .as("the gate should find these hints in the translated bundles, "
                        + "or it is guarding keys nobody ships")
                .isGreaterThan(12);
        assertThat(over)
                .as("a hint wider than its pane is cut where the reader cannot get it back; "
                        + "write a shorter sentence, and put any command on the button that runs it")
                .isEmpty();
    }
}
