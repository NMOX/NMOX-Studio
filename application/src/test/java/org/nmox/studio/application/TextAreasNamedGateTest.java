package org.nmox.studio.application;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every text area in the product carries an accessible name (v2.85.0,
 * the Task Board walk read the Standup through accessibility and heard
 * "text entry area" — the role, not the thing). The v2.37.2 sweep named
 * the dialogs' label-less inputs; the report areas (Standup, Sprint),
 * the DevTools details panes, the Image Kit report and the Docker
 * previews had none. A text area is an input or a document; either way
 * assistive technology needs to know which one.
 *
 * <p>The population is derived by {@link NamedControlCensus} from the
 * CONSTRUCTION rather than the declaration, so a field declared at class
 * level and assigned in a constructor is asked like any other. Widened, it
 * named two — the REPL's session screen and PHOSPHOR's scrollback, where the
 * SCROLL PANE around the area carried a name and the area a reader actually
 * lands in carried none. 20 → 28.
 */
class TextAreasNamedGateTest {

    /**
     * The floor that keeps the gate from going vacuous — see
     * {@code InputsNamedGateTest}. Measured at 28 when the census was derived.
     */
    private static final int POPULATION_FLOOR = 26;

    @Test
    @DisplayName("every JTextArea constructed in the product gets an accessible name somewhere in its file")
    void everyTextAreaIsNamed() throws IOException {
        List<NamedControlCensus.Site> sites = NamedControlCensus.sites("JTextArea");
        assertThat(sites)
                .as("the derived text-area population — a derivation that matches nothing "
                        + "would pass the offender assertion below saying nothing")
                .hasSizeGreaterThanOrEqualTo(POPULATION_FLOOR);

        List<String> offenders = sites.stream()
                .filter(s -> !s.named())
                .map(NamedControlCensus.Site::describe)
                .toList();
        assertThat(offenders)
                .as("a JTextArea without an accessible name — a screen reader hears only the role")
                .isEmpty();
    }
}
