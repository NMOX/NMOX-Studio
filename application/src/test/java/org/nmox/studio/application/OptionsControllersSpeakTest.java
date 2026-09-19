package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An Options panel tells the dialog when it changes, or Apply never lights up.
 *
 * <p>Half of the platform's contract was implemented in all three of this
 * product's panels and the other half was missing. Each one has a correct
 * {@code isChanged()}; not one of them ever fired {@code PROP_CHANGED}, and a
 * grep for {@code firePropertyChange} across all 910 product files returned
 * only accessibility notifications on rack widgets. Read from the shipped
 * bytecode, {@code OptionsDisplayerImpl} listens for that property on the
 * controller and calls back to decide whether Apply is enabled — so with no
 * fire, Apply stayed grey however many settings you touched and the only way
 * out of the dialog was OK.
 *
 * <p>Nothing could have caught it from inside a module: each panel looked
 * complete on its own, and the missing half is a conversation with the host.
 * The population is derived from the sources — every class implementing
 * {@code OptionsPanelController} — so a fourth panel is held to the same rule
 * the day it is written.
 */
class OptionsControllersSpeakTest {

    private static final List<Path> MODULES = List.of(
            Path.of("../core"), Path.of("../editor"), Path.of("../tools"),
            Path.of("../rack"), Path.of("../project"), Path.of("../ui"),
            Path.of("../infra"), Path.of("../apiclient"), Path.of("../dbstudio"),
            Path.of("../web3"));

    /** Every product class that implements the platform's Options contract. */
    private static List<Path> controllers() throws IOException {
        List<Path> found = new ArrayList<>();
        for (Path module : MODULES) {
            Path src = module.resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> s = Files.walk(src)) {
                for (Path p : s.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String body = Files.readString(p, StandardCharsets.UTF_8);
                    if (body.contains("extends OptionsPanelController")
                            || body.contains("extends org.netbeans.spi.options.OptionsPanelController")) {
                        found.add(p);
                    }
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("the product really has Options panels, so the law below is not vacuously green")
    void thePopulationIsFound() throws IOException {
        assertThat(controllers())
                .as("no OptionsPanelController found — the match stopped working, "
                        + "and a gate over an empty population proves nothing")
                .hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("every Options panel fires PROP_CHANGED — an isChanged() nobody asks is half a contract")
    void everyControllerFiresWhenItChanges() throws IOException {
        List<String> silent = new ArrayList<>();
        for (Path p : controllers()) {
            String body = GateSources.stripComments(
                    Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"));
            boolean fires = body.contains("firePropertyChange")
                    && body.contains("PROP_CHANGED");
            if (!fires) {
                silent.add(p.getFileName().toString());
            }
        }
        assertThat(silent)
                .as("these implement isChanged() and never tell the dialog, so Apply stays grey")
                .isEmpty();
    }

    @Test
    @DisplayName("and a panel's controls are wired to the fire — a private changed() nobody calls is the same defect one layer in")
    void theFireIsReachedFromAControl() throws IOException {
        List<String> unwired = new ArrayList<>();
        for (Path p : controllers()) {
            String body = GateSources.stripComments(
                    Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"));
            boolean listener = body.contains("addItemListener")
                    || body.contains("addActionListener")
                    || body.contains("addChangeListener")
                    || body.contains("addDocumentListener");
            if (!listener) {
                unwired.add(p.getFileName().toString());
            }
        }
        assertThat(unwired)
                .as("no control in these panels notifies anything, so the fire can never happen")
                .isEmpty();
    }
}
