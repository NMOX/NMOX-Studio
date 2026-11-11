package org.nmox.studio.infra.ops.ui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.infra.ops.OpsHubFixture;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The inspector under refresh. It is shown again on every probe of its
 * node, so what is held here is that the action row is left alone when
 * nothing about the actions changed (a button replaced under the user's
 * hand takes the focus with it), that a button acts on the node shown
 * when it is pressed, and that every button it ever builds has a name.
 */
class InspectorPanelTest {

    /** Records what the panel pressed. */
    static final class Pressed implements InspectorPanel.Actions {
        final List<String> log = new ArrayList<>();

        public void connect(OpsNode host, boolean mosh) { log.add((mosh ? "mosh " : "ssh ") + host.id); }
        public void deploy(OpsNode host) { log.add("deploy " + host.id); }
        public void openInBrowser(OpsNode node, int port) { log.add("open " + node.id + " " + port); }
        public void destroy(OpsNode host) { log.add("destroy " + host.id); }
        public void forget(OpsNode host) { log.add("forget " + host.id); }
        public void logs(OpsNode service) { log.add("logs " + service.id); }
        public void shell(OpsNode service) { log.add("shell " + service.id); }
        public void rollback(OpsNode service) { log.add("rollback " + service.id); }
    }

    private static List<JButton> buttons(Container c) {
        List<JButton> out = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child instanceof JButton b && !b.getClass().getName().startsWith("javax.swing.plaf")) {
                out.add(b);
            } else if (child instanceof Container cc) {
                out.addAll(buttons(cc));
            }
        }
        return out;
    }

    private static JButton named(Container c, String name) {
        return buttons(c).stream().filter(b -> name.equals(b.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow(() -> new AssertionError("no button named " + name + " among "
                        + buttons(c).stream().map(b -> b.getAccessibleContext().getAccessibleName()).toList()));
    }

    @Test
    @DisplayName("a probe of the shown host leaves its action buttons alone: the same objects, not rebuilt")
    void probeLeavesTheButtonsAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OpsHubFixture f = new OpsHubFixture();
            f.mosh(false);
            OpsNode host = f.host("box", 5);
            f.answer();
            f.drain();
            InspectorPanel panel = new InspectorPanel(f.hub, new Pressed());
            panel.show(host);
            List<JButton> before = buttons(panel);
            int builds = panel.buttonRowBuilds;
            assertThat(before).isNotEmpty();
            // three more probes land: new telemetry, a new "last seen", the same actions
            for (int i = 0; i < 3; i++) {
                f.clock.addAndGet(15_000);
                f.hub.probe(host);
                f.drain();
                panel.show(host);
            }
            assertThat(panel.buttonRowBuilds).as("the row was not rebuilt").isEqualTo(builds);
            assertThat(buttons(panel)).as("the very same buttons").containsExactlyElementsOf(before);
        });
    }

    @Test
    @DisplayName("the row is rebuilt when an action's enablement really changes, and only then")
    void rebuiltWhenTheActionsChange() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OpsHubFixture f = new OpsHubFixture();
            f.mosh(false);
            OpsNode host = f.host("box", 5);
            InspectorPanel panel = new InspectorPanel(f.hub, new Pressed());
            panel.show(host);
            assertThat(named(panel, "Deploy…").isEnabled()).as("not reachable yet").isFalse();
            int builds = panel.buttonRowBuilds;
            f.answer();
            f.drain();
            panel.show(host);
            assertThat(panel.buttonRowBuilds).isEqualTo(builds + 1);
            assertThat(named(panel, "Deploy…").isEnabled()).isTrue();
            panel.show(null);
            assertThat(buttons(panel)).as("nothing selected, nothing to press").isEmpty();
        });
    }

    @Test
    @DisplayName("a button acts on the node shown when it is pressed, and a service gets its doors")
    void buttonsActOnWhatIsShown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OpsHubFixture f = new OpsHubFixture();
            f.mosh(false);
            OpsNode host = f.host("box", 5);
            f.answer(OpsHubFixture.container("nmox-shop", "nmox/shop:300", "0.0.0.0:8080->3000/tcp", "nmox.ops.app=shop"),
                    OpsHubFixture.container("cache", "redis:7", "", ""));
            f.drain();
            Pressed pressed = new Pressed();
            InspectorPanel panel = new InspectorPanel(f.hub, pressed);
            OpsNode ours = f.hub.topology().node(host.id + "/nmox-shop");
            OpsNode neighbour = f.hub.topology().node(host.id + "/cache");
            panel.show(ours);
            named(panel, "Logs").doClick();
            named(panel, "Shell").doClick();
            named(panel, "Roll back…").doClick();
            named(panel, "Open in Browser").doClick();
            assertThat(pressed.log).containsExactly("logs " + ours.id, "shell " + ours.id, "rollback " + ours.id,
                    "open " + ours.id + " 8080");
            panel.show(neighbour);
            assertThat(buttons(panel)).extracting(b -> b.getAccessibleContext().getAccessibleName())
                    .as("a neighbour's container has logs and a shell, but no history of ours and no port")
                    .containsExactly("Logs", "Shell");
            pressed.log.clear();
            named(panel, "Logs").doClick();
            assertThat(pressed.log).containsExactly("logs " + neighbour.id);
        });
    }

    @Test
    @DisplayName("every button the inspector builds, for a host and for a service, carries an accessible name and a tooltip")
    void everyButtonIsNamed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OpsHubFixture f = new OpsHubFixture();
            f.mosh(true);
            OpsNode host = f.host("box", 5);
            f.answer(OpsHubFixture.container("nmox-shop", "nmox/shop:300", "0.0.0.0:8080->3000/tcp", "nmox.ops.app=shop"));
            f.drain();
            InspectorPanel panel = new InspectorPanel(f.hub, new Pressed());
            int seen = 0;
            for (OpsNode n : List.of(host, f.hub.topology().nodes(OpsKind.SERVICE).get(0))) {
                panel.show(n);
                for (JButton b : buttons(panel)) {
                    seen++;
                    assertThat(b.getAccessibleContext().getAccessibleName()).as(n.id).isNotBlank();
                    assertThat(b.getToolTipText()).as(b.getAccessibleContext().getAccessibleName()).isNotBlank();
                }
            }
            assertThat(seen).as("a selection really builds buttons").isGreaterThanOrEqualTo(8);
        });
    }

    /** Lays the panel out at a size with no window, as a dock would. */
    private static void layOut(InspectorPanel panel, int width, int height) {
        panel.setSize(width, height);
        // by hand, top down: validate() lays nothing out in a hierarchy that has no window (measured on JDK 25)
        layOutTree(panel);
    }

    private static void layOutTree(Container c) {
        c.doLayout();
        for (Component child : c.getComponents()) {
            if (child instanceof Container cc) {
                layOutTree(cc);
            }
        }
    }

    private static java.awt.Rectangle inPanel(InspectorPanel panel, Component c) {
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), panel);
    }

    private static List<javax.swing.JTextArea> areas(Container c) {
        List<javax.swing.JTextArea> out = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (child instanceof javax.swing.JTextArea a) {
                out.add(a);
            } else if (child instanceof Container cc) {
                out.addAll(areas(cc));
            }
        }
        return out;
    }

    @Test
    @DisplayName("at the pane's own width every action is inside the pane: the row wraps, it does not run past the edge")
    void everyActionIsReachableAtThePanesWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OpsHubFixture f = new OpsHubFixture();
            f.mosh(true);
            OpsNode host = f.host("box", 5);
            f.answer(OpsHubFixture.container("nmox-shop", "nmox/shop:300", "0.0.0.0:8080->3000/tcp", "nmox.ops.app=shop"));
            f.drain();
            InspectorPanel panel = new InspectorPanel(f.hub, new Pressed());
            for (OpsNode n : List.of(host, f.hub.topology().nodes(OpsKind.SERVICE).get(0))) {
                panel.show(n);
                layOut(panel, 300, 600);
                List<JButton> row = buttons(panel);
                assertThat(row).as(n.id).hasSizeGreaterThanOrEqualTo(4);
                int rows = (int) row.stream().map(b -> inPanel(panel, b).y).distinct().count();
                assertThat(rows).as("four or five buttons do not fit one row of 300 pixels: " + n.id).isGreaterThan(1);
                for (JButton b : row) {
                    java.awt.Rectangle r = inPanel(panel, b);
                    assertThat(r.x).as(b.getAccessibleContext().getAccessibleName()).isGreaterThanOrEqualTo(0);
                    assertThat(r.x + r.width).as(b.getAccessibleContext().getAccessibleName() + " ends inside the pane")
                            .isLessThanOrEqualTo(300);
                    assertThat(r.width).as("and is whole").isGreaterThanOrEqualTo(b.getPreferredSize().width);
                    // a FlowLayout also breaks the row at the edge — and then says it is one row tall, so
                    // the second row is laid out below the panel's own bottom and never painted
                    assertThat(b.getY() + b.getHeight()).as(b.getAccessibleContext().getAccessibleName() + " is inside its row's own height")
                            .isLessThanOrEqualTo(b.getParent().getHeight());
                }
            }
        });
    }

    @Test
    @DisplayName("the last problem is shown whole and wrapped, and the evidence under Relationships with it")
    void theProblemWraps() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OpsHubFixture f = new OpsHubFixture();
            f.mosh(false);
            OpsNode host = f.host("box", 5);
            f.answer(OpsHubFixture.container("nmox-shop", "nmox/shop:300", "0.0.0.0:8080->3000/tcp", "nmox.ops.app=shop"));
            f.drain();
            String said = "box stopped answering ssh: ssh: connect to host 203.0.113.5 port 22: Operation timed out after a long while";
            host.lastProblem = said;
            InspectorPanel panel = new InspectorPanel(f.hub, new Pressed());
            panel.show(host);
            layOut(panel, 300, 700);
            javax.swing.JTextArea problem = areas(panel).stream().filter(a -> said.equals(a.getText())).findFirst()
                    .orElseThrow(() -> new AssertionError("the problem is not on the panel whole"));
            int line = problem.getFontMetrics(problem.getFont()).getHeight();
            java.awt.Rectangle r = inPanel(panel, problem);
            assertThat(r.x + r.width).as("inside the pane").isLessThanOrEqualTo(300);
            assertThat(r.height).as("more than one line: it wrapped instead of running on").isGreaterThanOrEqualTo(2 * line);
            javax.swing.JTextArea evidence = areas(panel).stream().filter(a -> a.getText().contains("docker ps")).findFirst()
                    .orElseThrow(() -> new AssertionError("the evidence of the runs edge is not on the panel"));
            java.awt.Rectangle e = inPanel(panel, evidence);
            assertThat(e.x + e.width).isLessThanOrEqualTo(300);
            panel.show(null);
            layOut(panel, 300, 700);
            assertThat(problem.isVisible()).as("nothing selected, no problem shown").isFalse();
        });
    }

    @Test
    @DisplayName("the engine's fact keys are data: ordinary words are said by the inspector, names are left as they are")
    void factsAreWorded() {
        assertThat(InspectorPanel.factLabel("made here")).as("the engine's key is not the reader's word").isEqualTo("created here");
        assertThat(InspectorPanel.factLabel("containers not drawn")).isEqualTo(Bundle.Inspector_factContainersNotDrawn());
        assertThat(InspectorPanel.factLabel("mosh-server")).as("a name").isEqualTo("mosh-server");
        assertThat(InspectorPanel.factLabel("vpc")).isEqualTo("vpc");
        assertThat(InspectorPanel.factValue("made here", "yes")).isEqualTo(Bundle.Inspector_yes());
        assertThat(InspectorPanel.factValue("docker", "yes")).as("a has-question is not answered yes").isEqualTo("installed");
        assertThat(InspectorPanel.factValue("mosh-server", "no")).isEqualTo("not installed");
        assertThat(InspectorPanel.factValue("image", "installed")).as("only the flags are reworded").isEqualTo("installed");
        assertThat(InspectorPanel.factValue("region", "no")).as("a region named no is a region").isEqualTo("no");
        assertThat(InspectorPanel.factValue("docker", "no")).isNotEqualTo("no");
        assertThat(InspectorPanel.factValue("docker", "maybe")).isEqualTo("maybe");
        // a provider's instant is shown as a date a person reads; what is not an instant is shown as it came
        assertThat(InspectorPanel.factValue("created", "2026-11-10T18:00:00Z"))
                .isEqualTo(org.nmox.studio.core.util.Clocks.displayDateTime(java.time.Instant.parse("2026-11-10T18:00:00Z").toEpochMilli()))
                .doesNotContain("T18:00:00Z");
        assertThat(InspectorPanel.factValue("created", "last Tuesday")).isEqualTo("last Tuesday");
        assertThat(InspectorPanel.kindWord("depends", true)).as("the machine word is not the reader's").isEqualTo("depends on");
        assertThat(InspectorPanel.kindWord("depends", false)).as("read from the other end").isEqualTo("needed by");
        assertThat(InspectorPanel.kindWord("runs", false)).isEqualTo("runs on");
        assertThat(InspectorPanel.kindWord("ssh", false)).as("a name").isEqualTo("ssh");
        // the arrow points the way the line is read for an outgoing edge, whichever way that is
        assertThat(InspectorPanel.arrow(true, true)).isEqualTo("→ ");
        assertThat(InspectorPanel.arrow(false, true)).isEqualTo("← ");
        assertThat(InspectorPanel.arrow(true, false)).isEqualTo("← ");
        assertThat(InspectorPanel.arrow(false, false)).isEqualTo("→ ");
    }

    @Test
    @DisplayName("a refresh never scrolls the inspector: its wrapped labels keep their carets, and another node starts at its title")
    void refreshDoesNotScroll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OpsHubFixture f = new OpsHubFixture();
            f.mosh(false);
            OpsNode host = f.host("box", 5);
            f.answer(OpsHubFixture.container("nmox-shop", "nmox/shop:300", "0.0.0.0:8080->3000/tcp", "nmox.ops.app=shop"));
            f.drain();
            host.lastProblem = "a sentence long enough to matter, set on every refresh of the node it belongs to";
            InspectorPanel panel = new InspectorPanel(f.hub, new Pressed());
            panel.show(host);
            for (javax.swing.JTextArea a : areas(panel)) {
                if (!a.isEditable() && a.getCaret() instanceof javax.swing.text.DefaultCaret c && a.getLineWrap()) {
                    assertThat(c.getUpdatePolicy()).as("a label that wraps does not follow its text: " + a.getAccessibleContext().getAccessibleName())
                            .isEqualTo(javax.swing.text.DefaultCaret.NEVER_UPDATE);
                }
            }
            // a short dock: the column is taller than the pane
            layOut(panel, 300, 160);
            javax.swing.JViewport port = (javax.swing.JViewport) SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, areas(panel).get(0));
            port.setViewPosition(new java.awt.Point(0, 60));
            panel.show(host);
            assertThat(port.getViewPosition().y).as("the same node, refreshed, stays where the reader left it").isEqualTo(60);
            panel.show(f.hub.topology().nodes(OpsKind.SERVICE).get(0));
            assertThat(port.getViewPosition().y).as("another node starts at its title").isZero();
        });
    }
}
