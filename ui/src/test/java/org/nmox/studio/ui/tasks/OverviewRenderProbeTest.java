package org.nmox.studio.ui.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The overview face BUILDS — a headless render probe (v2.4.0). The
 * first live walk found the panel dying mid-{@code show()}: the painted
 * FlowStrip extended bare {@code JComponent}, whose
 * {@code getAccessibleContext()} is null, so the NPE aborted the build
 * after the FLOW label and the walk saw half a dashboard. Unit tests on
 * {@link BoardStats} could never catch that — the crash lives in Swing
 * construction — so this probe builds the REAL panel from a real board
 * and asserts the whole component tree exists, both faces of the
 * empty-history and rich-history states.
 */
class OverviewRenderProbeTest {

    private static int deepCount(java.awt.Container c) {
        int n = 0;
        for (java.awt.Component k : c.getComponents()) {
            n++;
            if (k instanceof java.awt.Container inner) {
                n += deepCount(inner);
            }
        }
        return n;
    }

    @Test
    @DisplayName("show() builds the whole face — tiles, columns, flow, attention")
    void overviewBuildsWhole() throws Exception {
        TaskBoard board = TaskBoard.starter();
        TaskBoard.Card w = board.addCard(0, "waiting card", "");
        TaskBoard.Card c = board.addCard(1, "moving card", "");
        board.moveCard(c.id(), 2, 0); // stamps done → the flow strip has history
        // the editorial panels (v2.5.0) build too: register, legend, retro
        board.block(w.id(), "alice", "needs the cert");
        board.setLabel(w.id(), "auth");
        board.setRetro("went well: everything");
        // the TIME section renders too: one closed session + one running
        board.clockIn(w.id(), System.currentTimeMillis() - 7_200_000L);
        board.clockOut(w.id(), System.currentTimeMillis() - 3_600_000L);
        board.clockIn(c.id(), System.currentTimeMillis() - 600_000L);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            OverviewPanel panel = new OverviewPanel(() -> { });
            panel.show(board, "probe");
            // the crash class this pins: a null accessible context inside
            // show() aborts the build partway — the tree then stops early
            assertThat(deepCount(panel)).isGreaterThan(20);
        });
    }

    @Test
    @DisplayName("An empty starter board still builds — the honest no-history face")
    void emptyBoardBuilds() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            OverviewPanel panel = new OverviewPanel(() -> { });
            panel.show(TaskBoard.starter(), null);
            assertThat(deepCount(panel)).isGreaterThan(10);
        });
    }
}
