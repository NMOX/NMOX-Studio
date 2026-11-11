package org.nmox.studio.infra.ops.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.infra.ops.DeployRun;
import org.nmox.studio.infra.ops.OpsHub;
import org.nmox.studio.infra.ops.model.OpsEdge;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.model.Telemetry;
import org.nmox.studio.infra.ops.remote.ConnectPlan;
import org.nmox.studio.infra.ops.remote.Deployer;
import org.openide.util.NbBundle.Messages;

/**
 * What the selected node is and what can be done with it: its facts as
 * the Hub observed them, its gauges, the evidence behind every edge that
 * touches it, and the actions — mosh, ssh, deploy, probe, destroy —
 * each a real button with a real name. Below, the deploy tracker for a
 * host with a run in flight. Ordinary Swing, so a screen reader and the
 * Tab key get it for free.
 *
 * <p>The panel is refreshed on every probe of the node it shows — every
 * fifteen seconds, more often while a server is coming up — so what it
 * rebuilds matters: the action buttons are rebuilt ONLY when the set of
 * actions or their enablement changes (and focus returns to the same
 * action when they are), because a button replaced under the user's
 * hand takes the keyboard focus, the hover and the press with it. A
 * button acts on whatever node is shown when it is pressed, never on
 * one captured when it was made.
 */
@Messages({
    "Inspector_nothingSelected=Select a node on the HUD — or press Tab, then the arrow keys",
    "Inspector_title=Inspector",
    "Inspector_connectMosh=mosh",
    "Inspector_connectMoshTooltip=Open a mosh session to this host in the IDE's Terminal",
    "Inspector_connectSsh=ssh",
    "Inspector_connectSshTooltip=Open an ssh session to this host in the IDE's Terminal",
    "Inspector_deploy=Deploy…",
    "Inspector_deployTooltip=Build the aimed project's Dockerfile on this host and run it",
    "Inspector_probe=Probe now",
    "Inspector_probeTooltip=Ask the host for its load, memory, disk and containers",
    "Inspector_open=Open in Browser",
    "Inspector_openTooltip=Open the first published port in the in-app Browser",
    "Inspector_destroy=Destroy…",
    "Inspector_destroyTooltip=Destroy this host at the provider — billing stops, the data is gone",
    "Inspector_forget=Forget",
    "Inspector_forgetTooltip=Remove this host from the Hub; the server is untouched",
    "Inspector_factStatus=status",
    "Inspector_factAddress=address",
    "Inspector_factProvider=provider",
    "Inspector_factHost=host",
    "Inspector_factLastSeen=last seen",
    "Inspector_factProblem=problem",
    "Inspector_gaugeLoad=load per core",
    "Inspector_gaugeMemory=memory",
    "Inspector_gaugeDisk=disk",
    "Inspector_gaugeUnknown=not reported",
    "Inspector_edges=Relationships",
    "# {0} - the edge kind",
    "# {1} - the other node",
    "# {2} - observed / inferred / declared",
    "# {3} - where it was read",
    "Inspector_edgeRow={0} {1} — {2}: {3}",
    "Inspector_evidenceObserved=observed",
    "Inspector_evidenceInferred=inferred",
    "Inspector_evidenceDeclared=declared",
    "Inspector_deployTracker=Deploy",
    "Inspector_deployLog=Deploy output",
    "# {0} - the step",
    "Inspector_stepPending=○ {0}",
    "# {0} - the step",
    "Inspector_stepRunning=◐ {0}…",
    "# {0} - the step",
    "Inspector_stepOk=● {0}",
    "# {0} - the step",
    "Inspector_stepFailed=✕ {0}",
    "# {0} - mosh or ssh",
    "# {1} - why",
    "Inspector_connectHow=Recommended door: {0} — {1}",
    "Inspector_logs=Logs",
    "Inspector_logsTooltip=Follow this container's output in the IDE's Terminal (docker logs -f); Ctrl+C ends the follow, not the container",
    "Inspector_shell=Shell",
    "Inspector_shellTooltip=Open a shell inside this container in the IDE's Terminal (docker exec)",
    "Inspector_rollback=Roll Back…",
    "Inspector_rollbackTooltip=Start the image that ran before this one — the host still holds it",
    "Inspector_rollbackTracker=Rollback",
    "Inspector_simulatedService=simulated service: there is nothing to follow or enter",
    "Inspector_planMosh=mosh",
    "Inspector_planSsh=ssh",
    "Inspector_whyNoLocalMosh=mosh is not installed on this machine",
    "Inspector_whyNoRemoteMosh=the host has no mosh-server",
    "Inspector_whyMosh=both ends have mosh",
    "Inspector_simulatedConnect=simulated host: there is nothing to connect to",
    "Inspector_never=never"
})
public final class InspectorPanel extends JPanel {

    /** The actions the window wires; the panel only presses them. */
    public interface Actions {
        void connect(OpsNode host, boolean mosh);

        void deploy(OpsNode host);

        void openInBrowser(OpsNode node, int port);

        void destroy(OpsNode host);

        void forget(OpsNode host);

        /** Follow a service's logs in the Terminal. */
        void logs(OpsNode service);

        /** A shell inside a service's container, in the Terminal. */
        void shell(OpsNode service);

        /** Offer to start the image that ran before. */
        void rollback(OpsNode service);
    }

    /** One action button as data: what it says, whether it can be pressed, what it does to the node shown. */
    private record ButtonSpec(String text, String tooltip, boolean enabled, Consumer<OpsNode> action) {
    }

    private final OpsHub hub;
    private final Actions actions;
    private final JLabel title = new JLabel();
    private final JTextArea hint = org.nmox.studio.core.util.PlainDialogs.plain(Bundle.Inspector_nothingSelected(), Bundle.Inspector_title());
    private final JPanel facts = new JPanel(new GridBagLayout());
    private final JProgressBar load = new JProgressBar(0, 100);
    private final JProgressBar mem = new JProgressBar(0, 100);
    private final JProgressBar disk = new JProgressBar(0, 100);
    private final JPanel gauges = new JPanel(new GridBagLayout());
    private final JPanel edges = new JPanel();
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 2));
    private final JLabel connectHow = new JLabel();
    private final JPanel deployBox = new JPanel(new BorderLayout(0, 4));
    private final JPanel steps = new JPanel();
    private final JTextArea deployLog = TextDirection.keepLeftToRight(new JTextArea(6, 30));
    private final JLabel trackerTitle = new JLabel(Bundle.Inspector_deployTracker());
    private OpsNode shown;
    /** The node and the (text, enabled) of every button last built; equal means the row is left alone. */
    private java.util.List<String> buttonsBuiltFor = java.util.List.of();

    public InspectorPanel(OpsHub hub, Actions actions) {
        super(new BorderLayout());
        this.hub = hub;
        this.actions = actions;
        setBorder(new EmptyBorder(8, 10, 8, 10));
        title.setFont(HudStyle.ui(14f, true));
        title.getAccessibleContext().setAccessibleName(Bundle.Inspector_title());
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.PAGE_AXIS));
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setColumns(24);
        column.add(leading(hint));
        column.add(leading(title));
        column.add(Box.createVerticalStrut(6));
        column.add(leading(buttons));
        connectHow.setFont(HudStyle.ui(10.5f, false));
        column.add(leading(connectHow));
        column.add(Box.createVerticalStrut(8));
        column.add(leading(facts));
        column.add(Box.createVerticalStrut(8));
        gauge(gauges, 0, Bundle.Inspector_gaugeLoad(), load);
        gauge(gauges, 1, Bundle.Inspector_gaugeMemory(), mem);
        gauge(gauges, 2, Bundle.Inspector_gaugeDisk(), disk);
        column.add(leading(gauges));
        column.add(Box.createVerticalStrut(8));
        edges.setLayout(new BoxLayout(edges, BoxLayout.PAGE_AXIS));
        column.add(leading(edges));
        column.add(Box.createVerticalStrut(8));
        steps.setLayout(new BoxLayout(steps, BoxLayout.PAGE_AXIS));
        deployLog.setEditable(false);
        deployLog.setFont(HudStyle.mono(10.5f));
        deployLog.setLineWrap(false);
        deployLog.getAccessibleContext().setAccessibleName(Bundle.Inspector_deployLog());
        trackerTitle.setFont(HudStyle.ui(12f, true));
        deployBox.add(trackerTitle, BorderLayout.PAGE_START);
        deployBox.add(steps, BorderLayout.CENTER);
        deployBox.add(new JScrollPane(deployLog), BorderLayout.PAGE_END);
        deployBox.setVisible(false);
        column.add(leading(deployBox));
        column.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(column);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 400));
        show(null);
    }

    private static JPanel leading(javax.swing.JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(c, BorderLayout.LINE_START);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static void gauge(JPanel into, int row, String name, JProgressBar bar) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(2, 0, 2, 8);
        c.anchor = GridBagConstraints.LINE_START;
        JLabel l = new JLabel(PlainText.plain(name));
        l.setLabelFor(bar);
        into.add(l, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        bar.setStringPainted(true);
        bar.setPreferredSize(new Dimension(140, 16));
        bar.getAccessibleContext().setAccessibleName(name);
        into.add(bar, c);
    }

    /** Shows a node (or the empty hint for null). Cheap; called on every change of the selection or the node. */
    public void show(OpsNode node) {
        shown = node;
        facts.removeAll();
        edges.removeAll();
        hint.setVisible(node == null);
        title.setVisible(node != null);
        if (node == null) {
            title.setText("");
            connectHow.setText("");
            gauges.setVisible(false);
            deployBox.setVisible(false);
            setButtons(null, java.util.List.of());
            revalidate();
            repaint();
            return;
        }
        title.setText(PlainText.plain(node.label + "  ·  " + HudCanvas.kindWord(node.kind)
                + (node.simulated ? "  ·  " + Bundle.HudCanvas_simBadge() : "")));
        int row = 0;
        if (node.kind == OpsKind.HOST) {
            row = fact(row, Bundle.Inspector_factStatus(), HudCanvas.statusWord(node.status));
            if (node.address != null) {
                row = fact(row, Bundle.Inspector_factAddress(), node.sshSpec());
            }
            row = fact(row, Bundle.Inspector_factProvider(), providerName(node));
            // a clock time, not an age: the panel is repainted when the host ANSWERS, so an age would read
            // "0 s ago" for as long as it answers and freeze at its last value the moment it stops
            row = fact(row, Bundle.Inspector_factLastSeen(), node.lastSeenMillis == 0 ? Bundle.Inspector_never()
                    : org.nmox.studio.core.util.Clocks.display(node.lastSeenMillis));
        } else if (node.kind == OpsKind.SERVICE) {
            OpsNode host = node.hostId == null ? null : hub.topology().node(node.hostId);
            row = fact(row, Bundle.Inspector_factHost(), host == null ? "?" : host.label);
        }
        for (Map.Entry<String, String> e : node.facts.entrySet()) {
            if (e.getKey().equals("watch")) {
                continue;
            }
            row = fact(row, e.getKey(), e.getValue());
        }
        if (node.lastProblem != null) {
            fact(row, Bundle.Inspector_factProblem(), node.lastProblem);
        }
        // gauges
        Telemetry.Sample s = node.telemetry.latest();
        gauges.setVisible(node.kind == OpsKind.HOST && s != null);
        if (s != null) {
            share(load, s.loadShare(), s.loadShare() == s.loadShare() ? HudCanvas.fmt(s.load1()) + " / " + s.cpus() : null);
            share(mem, s.memShare(), s.memTotalKb() > 0 ? (s.memUsedKb() / 1024) + " / " + (s.memTotalKb() / 1024) + " MB" : null);
            share(disk, s.diskShare(), s.diskTotalKb() > 0 ? (s.diskUsedKb() / 1048576) + " / " + (s.diskTotalKb() / 1048576) + " GB" : null);
        }
        // edges with their evidence
        java.util.List<OpsEdge> touching = hub.topology().edgesOf(node.id);
        if (!touching.isEmpty()) {
            JLabel h = new JLabel(Bundle.Inspector_edges());
            h.setFont(HudStyle.ui(12f, true));
            edges.add(h);
            for (OpsEdge e : touching) {
                String otherId = e.from().equals(node.id) ? e.to() : e.from();
                OpsNode other = hub.topology().node(otherId);
                String arrow = e.from().equals(node.id) ? "→ " : "← ";
                JLabel l = new JLabel(PlainText.plain(Bundle.Inspector_edgeRow(arrow + e.kind(),
                        other == null ? otherId : other.label, evidenceWord(e), e.source())));
                l.setFont(HudStyle.ui(11f, false));
                edges.add(l);
            }
        }
        // actions
        java.util.List<ButtonSpec> specs = new java.util.ArrayList<>();
        if (node.kind == OpsKind.HOST) {
            boolean canConnect = node.address != null && !node.simulated;
            specs.add(new ButtonSpec(Bundle.Inspector_connectMosh(), Bundle.Inspector_connectMoshTooltip(),
                    canConnect && hub.moshOnPath(), n -> actions.connect(n, true)));
            specs.add(new ButtonSpec(Bundle.Inspector_connectSsh(), Bundle.Inspector_connectSshTooltip(),
                    canConnect, n -> actions.connect(n, false)));
            specs.add(new ButtonSpec(Bundle.Inspector_deploy(), Bundle.Inspector_deployTooltip(),
                    node.status.reachable(), actions::deploy));
            specs.add(new ButtonSpec(Bundle.Inspector_probe(), Bundle.Inspector_probeTooltip(),
                    node.address != null, hub::probe));
            if (node.providerId != null) {
                specs.add(new ButtonSpec(Bundle.Inspector_destroy(), Bundle.Inspector_destroyTooltip(), true, actions::destroy));
            }
            if ("manual".equals(node.provider)) {
                specs.add(new ButtonSpec(Bundle.Inspector_forget(), Bundle.Inspector_forgetTooltip(), true, actions::forget));
            }
            if (node.simulated) {
                connectHow.setText(PlainText.plain(Bundle.Inspector_simulatedConnect()));
            } else {
                ConnectPlan plan = hub.connectPlan(node);
                connectHow.setText(PlainText.plain(Bundle.Inspector_connectHow(
                        plan.mosh() ? Bundle.Inspector_planMosh() : Bundle.Inspector_planSsh(), why(plan))));
            }
        } else if (node.kind == OpsKind.SERVICE) {
            connectHow.setText(node.simulated ? PlainText.plain(Bundle.Inspector_simulatedService()) : "");
            if (firstPort(node) != null) {
                specs.add(new ButtonSpec(Bundle.Inspector_open(), Bundle.Inspector_openTooltip(), !node.simulated, n -> {
                    Integer port = firstPort(n);
                    if (port != null) {
                        actions.openInBrowser(n, port);
                    }
                }));
            }
            specs.add(new ButtonSpec(Bundle.Inspector_logs(), Bundle.Inspector_logsTooltip(), !node.simulated, actions::logs));
            specs.add(new ButtonSpec(Bundle.Inspector_shell(), Bundle.Inspector_shellTooltip(), !node.simulated, actions::shell));
            if (node.app != null) {
                // only what this product deployed has a history the Hub can read
                specs.add(new ButtonSpec(Bundle.Inspector_rollback(), Bundle.Inspector_rollbackTooltip(), true, actions::rollback));
            }
        } else {
            connectHow.setText("");
        }
        setButtons(node, specs);
        showDeploy(node);
        revalidate();
        repaint();
    }

    /** Refreshes the deploy tracker for the shown host. */
    public void refreshDeploy() {
        if (shown != null) {
            showDeploy(shown);
        }
    }

    /**
     * The run to show beside a node: a host's own, or — on a service —
     * the run that is replacing THAT container (a rollback is asked for
     * on the service, and its progress belongs where it was asked).
     */
    private DeployRun runFor(OpsNode node) {
        if (node.kind == OpsKind.HOST) {
            return hub.deployOf(node.id);
        }
        if (node.kind == OpsKind.SERVICE && node.hostId != null) {
            DeployRun run = hub.deployOf(node.hostId);
            if (run != null && run.plan.container().equals(node.facts.get("container"))) {
                return run;
            }
        }
        return null;
    }

    private void showDeploy(OpsNode node) {
        DeployRun run = runFor(node);
        deployBox.setVisible(run != null);
        if (run == null) {
            return;
        }
        trackerTitle.setText(PlainText.plain(run.rollback ? Bundle.Inspector_rollbackTracker() : Bundle.Inspector_deployTracker()));
        steps.removeAll();
        for (Map.Entry<Deployer.Step, DeployRun.StepState> e : run.steps.entrySet()) {
            String name = OpsHub.stepName(e.getKey());
            String text = switch (e.getValue()) {
                case PENDING -> Bundle.Inspector_stepPending(name);
                case RUNNING -> Bundle.Inspector_stepRunning(name);
                case OK -> Bundle.Inspector_stepOk(name);
                case FAILED -> Bundle.Inspector_stepFailed(name);
            };
            JLabel l = new JLabel(PlainText.plain(text));
            l.setFont(HudStyle.mono(11f));
            steps.add(l);
        }
        deployLog.setText(String.join("\n", run.tail()));
        deployLog.setCaretPosition(deployLog.getDocument().getLength());
        steps.revalidate();
    }

    /**
     * Puts the action row in step with {@code specs} — and does nothing
     * when it already is, which is the usual case: the same node, probed
     * again. When the row must change, the action that had the keyboard
     * focus keeps it.
     */
    private void setButtons(OpsNode node, java.util.List<ButtonSpec> specs) {
        java.util.List<String> key = new java.util.ArrayList<>();
        key.add(node == null ? "" : node.id);
        for (ButtonSpec s : specs) {
            key.add(s.text() + (s.enabled() ? "|on" : "|off"));
        }
        if (key.equals(buttonsBuiltFor)) {
            return;
        }
        String focused = null;
        for (java.awt.Component c : buttons.getComponents()) {
            if (c instanceof JButton b && b.isFocusOwner()) {
                focused = b.getAccessibleContext().getAccessibleName();
            }
        }
        boolean sameNode = !buttonsBuiltFor.isEmpty() && buttonsBuiltFor.get(0).equals(key.get(0));
        buttonsBuiltFor = key;
        buttonRowBuilds++;
        buttons.removeAll();
        for (ButtonSpec s : specs) {
            JButton b = new JButton(PlainText.plain(s.text()));
            b.setToolTipText(PlainText.plain(s.tooltip()));
            b.getAccessibleContext().setAccessibleName(s.text());
            b.setEnabled(s.enabled());
            b.addActionListener(e -> {
                if (shown != null) {
                    s.action().accept(shown);
                }
            });
            buttons.add(b);
            if (sameNode && s.text().equals(focused) && s.enabled()) {
                b.requestFocusInWindow();
            }
        }
        buttons.revalidate();
        buttons.repaint();
    }

    /** How many times the action row has been rebuilt — a test's window onto "left alone". */
    int buttonRowBuilds;

    private int fact(int row, String key, String value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.FIRST_LINE_START;
        c.insets = new Insets(1, 0, 1, 10);
        JLabel k = new JLabel(PlainText.plain(key));
        k.setFont(HudStyle.ui(11f, false));
        k.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
        facts.add(k, c);
        c.gridx = 1;
        c.weightx = 1;
        JLabel v = new JLabel(PlainText.plain(value));
        v.setFont(HudStyle.ui(11f, false));
        k.setLabelFor(v);
        facts.add(v, c);
        return row + 1;
    }

    private static void share(JProgressBar bar, double share, String text) {
        if (Double.isNaN(share) || text == null) {
            bar.setValue(0);
            bar.setString(Bundle.Inspector_gaugeUnknown());
        } else {
            bar.setValue((int) Math.round(Math.min(1, share) * 100));
            bar.setString(text);
        }
    }

    private String providerName(OpsNode node) {
        if (node.provider == null) {
            return "";
        }
        var p = hub.provider(node.provider);
        return p != null ? p.displayName() : node.provider;
    }

    private static String evidenceWord(OpsEdge e) {
        return switch (e.evidence()) {
            case OBSERVED -> Bundle.Inspector_evidenceObserved();
            case INFERRED -> Bundle.Inspector_evidenceInferred();
            case DECLARED -> Bundle.Inspector_evidenceDeclared();
        };
    }

    private static String why(ConnectPlan plan) {
        return switch (plan) {
            case MOSH -> Bundle.Inspector_whyMosh();
            case SSH_NO_LOCAL_MOSH -> Bundle.Inspector_whyNoLocalMosh();
            case SSH_NO_REMOTE_MOSH, SSH_PREFERRED -> Bundle.Inspector_whyNoRemoteMosh();
        };
    }

    /** The first published port of a service, from docker's ports string; null when none. */
    static Integer firstPort(OpsNode service) {
        String ports = service.facts.get("ports");
        if (ports == null) {
            return null;
        }
        var fact = new org.nmox.studio.infra.ops.remote.HostFacts.ContainerFact("", "", "", "", ports, Map.of(), java.util.List.of());
        var published = fact.publishedPorts();
        return published.isEmpty() ? null : published.get(0);
    }
}
