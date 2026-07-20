package org.nmox.studio.ui.actions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source gate: "Discard experiment" is an irreversible tree delete
 * ({@code Experiments.discard(dir)} stops anything running there and
 * removes the directory), so its confirmation dialog must default the
 * Enter/Space button to No — never to the destructive Yes. The default
 * button is set only by a {@code NotifyDescriptor} constructor's
 * {@code initialValue} argument; the {@code Confirmation(...)} shortcut
 * hard-codes {@code initialValue = OK_OPTION}, so a future edit reverting
 * to it (or dropping the {@code NO_OPTION} initial value) would re-arm the
 * destructive default and fails here by name. Mirrors the infra
 * DialogSafetyTest (ledger 53, v1.98.0).
 */
class ManageExperimentsSafetyTest {

    private static String discardBlock() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/ui/actions/ManageExperimentsAction.java"),
                StandardCharsets.UTF_8);
        int m = src.indexOf("discard.addActionListener(");
        assertThat(m).as("the discard listener exists").isPositive();
        // up to the confirmation-answer check that follows the dialog build
        int end = src.indexOf("!= NotifyDescriptor.YES_OPTION", m);
        assertThat(end).as("the discard confirm answer-check exists").isPositive();
        return src.substring(m, end);
    }

    @Test
    @DisplayName("The Discard confirmation defaults to No via the full ctor's initialValue")
    void discardDefaultsToSafe() throws Exception {
        String block = discardBlock();
        assertThat(block)
                .as("No is the default button — the full ctor with a NO_OPTION initialValue arg")
                .contains("NotifyDescriptor.YES_OPTION, NotifyDescriptor.NO_OPTION},")
                .contains("NotifyDescriptor.NO_OPTION);");
        assertThat(block)
                .as("the Confirmation shortcut hard-codes initialValue = OK_OPTION — must not be used")
                .doesNotContain("new NotifyDescriptor.Confirmation(");
    }
}
