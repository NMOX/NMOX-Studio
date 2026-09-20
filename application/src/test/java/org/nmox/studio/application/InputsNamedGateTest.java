package org.nmox.studio.application;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every input in the product speaks its name (v2.85.0): the walk read
 * the New Project wizard through accessibility and its name field was
 * "text field" — the census found 46 fields, combos, spinners and one
 * password field with neither {@code setAccessibleName} nor a label's
 * {@code setLabelFor} (Swing derives the name from either). The
 * TextAreasNamedGateTest sibling for the rest of the input family; a
 * JCheckBox constructed with its own text names itself and is out.
 *
 * <p>The population is derived by {@link NamedControlCensus} from the
 * CONSTRUCTION rather than the declaration. Until then this gate's pattern
 * needed the type and its {@code new} on one statement, so a field declared
 * at class level and assigned in a builder method was never asked — which is
 * how three {@link javax.swing.JPasswordField}s holding cloud API tokens sat
 * unnamed in the rack's Options panel with this gate green. Widened, it named
 * six: those three, the REFLEX spinner beside them, and the IRC message and
 * find fields. 95 → 107.
 */
class InputsNamedGateTest {

    /** Fields, combos, spinners and text-less checkboxes: the input family. */
    private static final String INPUT_TYPES =
            "JTextField|JPasswordField|JComboBox|JSpinner|JCheckBox";

    /**
     * The floor that keeps the gate from going vacuous. A derived population
     * is only as good as its derivation, and a derivation that silently
     * matches nothing passes every assertion about its offenders — so the
     * size is asserted too. Measured at 107 when the census was derived; the
     * floor sits below that so an honest deletion does not fail the build,
     * and far enough above zero that a broken pattern does.
     */
    private static final int POPULATION_FLOOR = 100;

    @Test
    @DisplayName("every text field, password field, combo, spinner and text-less checkbox is named or labelled")
    void everyInputIsNamed() throws IOException {
        List<NamedControlCensus.Site> sites = NamedControlCensus.sites(INPUT_TYPES);
        assertThat(sites)
                .as("the derived input population — a derivation that matches nothing "
                        + "would pass the offender assertion below saying nothing")
                .hasSizeGreaterThanOrEqualTo(POPULATION_FLOOR);

        List<String> offenders = sites.stream()
                .filter(s -> !namesItself(s))
                .filter(s -> !s.named())
                .map(NamedControlCensus.Site::describe)
                .toList();
        assertThat(offenders)
                .as("an input with neither an accessible name nor a labelFor label — a screen reader hears only the role")
                .isEmpty();
    }

    /** A checkbox constructed with its own text is its own label. */
    private static boolean namesItself(NamedControlCensus.Site s) {
        return "JCheckBox".equals(s.type()) && !s.args().isBlank();
    }
}
