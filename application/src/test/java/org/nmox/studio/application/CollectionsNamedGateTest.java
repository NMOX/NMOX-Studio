package org.nmox.studio.application;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every table, list and tree in the product speaks its name (v2.85.0,
 * the third leg of the accessibility sweep after text areas and
 * inputs): the census found 26 — the device shelf, the wizard's
 * template list, the Docker tables, SONAR's ports, BLACKBOX's timeline,
 * API Studio's collections and tables, DB Studio's tree and grid,
 * Contract Studio's artifacts and reports — that a screen reader could
 * only call "table". A name or a labelFor anywhere in the file counts.
 *
 * <p>The population is derived by {@link NamedControlCensus} from the
 * CONSTRUCTION rather than the declaration. Widened, it named four trees and
 * lists a reader meets constantly: the Navigator's own outline, the NPM
 * Explorer's scripts, and IRC's channel tree and nick list. 31 → 37.
 *
 * <p>A factory ({@code return new JList<>(model)}) is skipped because the
 * caller holds what it builds and names it there — the rule this gate stated
 * in prose before the census could act on it. Two exist: {@code Popups}'
 * popup-target list and the learning-space picker's width-tracking list.
 */
class CollectionsNamedGateTest {

    /**
     * The floor that keeps the gate from going vacuous — see
     * {@code InputsNamedGateTest}. Measured at 37 when the census was derived.
     */
    private static final int POPULATION_FLOOR = 34;

    @Test
    @DisplayName("every JTable, JList and JTree constructed in the product is named or labelled")
    void everyCollectionIsNamed() throws IOException {
        List<NamedControlCensus.Site> sites = NamedControlCensus.sites("JTable|JList|JTree");
        assertThat(sites)
                .as("the derived table/list/tree population — a derivation that matches nothing "
                        + "would pass the offender assertion below saying nothing")
                .hasSizeGreaterThanOrEqualTo(POPULATION_FLOOR);

        List<String> offenders = sites.stream()
                .filter(s -> !s.named())
                .map(NamedControlCensus.Site::describe)
                .toList();
        assertThat(offenders)
                .as("a table, list or tree without an accessible name — a screen reader hears only the role")
                .isEmpty();
    }
}
