package org.nmox.studio.editor.diagnostics;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.engine.DiagnosticsBus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The status line's problem count (3.1.0): errors and warnings across every
 * tool on the bus, hidden when there is nothing to fix.
 */
class ProblemsStatusLineTest {

    private static final String TOOL = "lsp:problems-status-line-test";

    @AfterEach
    void clear() {
        DiagnosticsBus.publish(TOOL, List.of());
    }

    private static DiagnosticsBus.Problem p(boolean error) {
        return new DiagnosticsBus.Problem(new File("x.ts"), 1, "m", error);
    }

    @Test
    @DisplayName("counts errors and warnings across batches")
    void counts() {
        int[] c = ProblemsStatusLine.count(List.of(
                List.of(p(true), p(false), p(true)), List.of(p(false)), List.of()));
        assertThat(c).containsExactly(2, 2);
    }

    @Test
    @DisplayName("says nothing when there is nothing to fix, and shows both counts otherwise")
    void text() {
        assertThat(ProblemsStatusLine.text(new int[]{0, 0})).isNull();
        assertThat(ProblemsStatusLine.text(new int[]{0, 3})).isEqualTo("✕ 0  ⚠ 3");
        assertThat(ProblemsStatusLine.text(new int[]{1, 0})).isEqualTo("✕ 1  ⚠ 0");
    }

    @Test
    @DisplayName("the chip follows the bus: visible with a count and a named tooltip, hidden when clear")
    void chipFollowsTheBus() {
        ProblemsStatusLine.Chip chip = new ProblemsStatusLine.Chip();
        assertThat(chip.getAccessibleContext().getAccessibleName()).isEqualTo("Problems");
        DiagnosticsBus.publish(TOOL, List.of(p(true), p(false), p(false)));
        chip.refresh();
        int[] all = ProblemsStatusLine.count(DiagnosticsBus.all().values());
        assertThat(all[0]).isGreaterThanOrEqualTo(1);
        assertThat(chip.isVisible()).isTrue();
        assertThat(chip.getText()).isEqualTo(ProblemsStatusLine.text(all));
        assertThat(chip.getToolTipText()).contains("Action Items");
        DiagnosticsBus.publish(TOOL, List.of());
        chip.refresh();
        int[] after = ProblemsStatusLine.count(DiagnosticsBus.all().values());
        assertThat(chip.isVisible()).isEqualTo(after[0] + after[1] > 0);
    }

    @Test
    @DisplayName("a click opens the platform's own Action Items action, by an id the cluster registers")
    void opensActionItems() {
        assertThat(ProblemsStatusLine.ACTION_CATEGORY).isEqualTo("Window");
        assertThat(ProblemsStatusLine.ACTION_ID).isEqualTo("org.netbeans.modules.tasklist.ui.TaskListAction");
    }
}
