package org.nmox.studio.ui.browser.fx;

import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.browser.devtools.ConsoleModel;
import org.nmox.studio.ui.browser.devtools.DevScripts;
import org.nmox.studio.ui.browser.devtools.NetworkModel;
import org.nmox.studio.ui.browser.devtools.ScriptRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pick poll's lifecycle law (v1.359.0 review find): every exit
 * path stops the 300ms timer. The bug it pins: a SUCCESSFUL pick
 * disarmed the toggle with {@code setSelected(false)}, which fires no
 * ActionListener — so the toggle-off branch (the only place the timer
 * was stopped) never ran, and PICK_POLL hammered every future page
 * for the life of the panel. The proof is behavioral: after the fake
 * page answers a pick, the poll count must stop growing.
 */
class PickPollLifecycleTest {

    /** A fake page: counts polls, answers the pick on the Nth poll. */
    private static final class FakePage implements ScriptRunner {

        final AtomicInteger polls = new AtomicInteger();
        volatile String pickAnswer = "";

        @Override
        public void run(String js, Consumer<String> onResult, Consumer<String> onError) {
            if (js.equals(DevScripts.PICK_POLL)) {
                polls.incrementAndGet();
                String answer = pickAnswer;
                pickAnswer = ""; // read-and-clear, like the real page
                onResult.accept(answer);
            } else {
                onResult.accept("");
            }
        }
    }

    @Test
    @DisplayName("a successful pick STOPS the poll — the count freezes after the answer")
    void successfulPickStopsPolling() throws Exception {
        FakePage page = new FakePage();
        final JToggleButton[] pick = new JToggleButton[1];
        SwingUtilities.invokeAndWait(() -> {
            DevToolsPanel panel = new DevToolsPanel(new ConsoleModel(), new NetworkModel(), page);
            pick[0] = findToggle(panel, "Pick element");
            assertThat(pick[0]).as("the Pick element toggle exists").isNotNull();
            pick[0].doClick(); // arm — fires the ActionListener like a user click
        });
        // let the poll run a few beats, then have the page answer the pick
        Thread.sleep(700);
        assertThat(page.polls.get()).as("armed pick is polling").isGreaterThan(0);
        page.pickAnswer = "[0]"; // documentElement's first child
        Thread.sleep(700);
        int afterAnswer = page.polls.get();
        // the frozen-count assertion: give a leaked timer time to show itself
        Thread.sleep(1200);
        assertThat(page.polls.get())
                .as("the poll must STOP after a successful pick (leaked timers keep counting)")
                .isEqualTo(afterAnswer);
        SwingUtilities.invokeAndWait(() ->
                assertThat(pick[0].isSelected()).as("toggle visually disarmed").isFalse());
    }

    private static JToggleButton findToggle(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JToggleButton b && text.equals(b.getText())) {
                return b;
            }
            if (c instanceof Container inner) {
                JToggleButton hit = findToggle(inner, text);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
