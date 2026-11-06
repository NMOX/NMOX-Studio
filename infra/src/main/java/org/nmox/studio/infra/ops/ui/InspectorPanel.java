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
    "Inspector_connectHow=A click on the host opens {0}: {1}",
    "Inspector_planMosh=mosh",
    "Inspector_planSsh=ssh",
    "Inspector_whyNoLocalMosh=mosh is not installed on this machine",
    "Inspector_whyNoRemoteMosh=the host has no mosh-server",
    "Inspector_whyMosh=both ends have mosh",
    "Inspector_simulatedConnect=simulated host: there is nothing to connect to",
    "# {0} - seconds",
    "Inspector_secondsAgo={0} s ago",
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
    private OpsNode shown;

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
        JLabel trackerTitle = new JLabel(Bundle.Inspector_deployTracker());
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
        JLabel l = new JLabel(name);
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
        buttons.removeAll();
        edges.removeAll();
        hint.setVisible(node == null);
        title.setVisible(node != null);
        if (node == null) {
            title.setText("");
            connectHow.setText("");
            gauges.setVisible(false);
            deployBox.setVisible(false);
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
            row = fact(row, Bundle.Inspector_factLastSeen(), node.lastSeenMillis == 0 ? Bundle.Inspector_never()
                    : Bundle.Inspector_secondsAgo(Long.toString(Math.max(0, (System.currentTimeMillis() - node.lastSeenMillis) / 1000))));
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
        if (node.kind == OpsKind.HOST) {
            boolean canConnect = node.address != null && !node.simulated;
            JButton mosh = button(Bundle.Inspector_connectMosh(), Bundle.Inspector_connectMoshTooltip(),
                    () -> actions.connect(node, true));
            mosh.setEnabled(canConnect && hub.moshOnPath());
            JButton ssh = button(Bundle.Inspector_connectSsh(), Bundle.Inspector_connectSshTooltip(),
                    () -> actions.connect(node, false));
            ssh.setEnabled(canConnect);
            JButton deploy = button(Bundle.Inspector_deploy(), Bundle.Inspector_deployTooltip(), () -> actions.deploy(node));
            deploy.setEnabled(node.status.reachable());
            button(Bundle.Inspector_probe(), Bundle.Inspector_probeTooltip(), () -> hub.probe(node))
                    .setEnabled(node.address != null);
            if (node.providerId != null) {
                button(Bundle.Inspector_destroy(), Bundle.Inspector_destroyTooltip(), () -> actions.destroy(node));
            }
            if ("manual".equals(node.provider)) {
                button(Bundle.Inspector_forget(), Bundle.Inspector_forgetTooltip(), () -> actions.forget(node));
            }
            if (node.simulated) {
                connectHow.setText(PlainText.plain(Bundle.Inspector_simulatedConnect()));
            } else {
                ConnectPlan plan = hub.connectPlan(node);
                connectHow.setText(PlainText.plain(Bundle.Inspector_connectHow(
                        plan.mosh() ? Bundle.Inspector_planMosh() : Bundle.Inspector_planSsh(), why(plan))));
            }
        } else if (node.kind == OpsKind.SERVICE) {
            connectHow.setText("");
            Integer port = firstPort(node);
            if (port != null) {
                button(Bundle.Inspector_open(), Bundle.Inspector_openTooltip(), () -> actions.openInBrowser(node, port))
                        .setEnabled(!node.simulated);
            }
        } else {
            connectHow.setText("");
        }
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

    private void showDeploy(OpsNode node) {
        DeployRun run = node.kind == OpsKind.HOST ? hub.deployOf(node.id) : null;
        deployBox.setVisible(run != null);
        if (run == null) {
            return;
        }
        steps.removeAll();
        for (Deployer.Step step : Deployer.Step.values()) {
            String name = OpsHub.stepName(step);
            String text = switch (run.steps.get(step)) {
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

    private JButton button(String text, String tooltip, Runnable action) {
        JButton b = new JButton(PlainText.plain(text));
        b.setToolTipText(PlainText.plain(tooltip));
        b.getAccessibleContext().setAccessibleName(text);
        b.addActionListener(e -> action.run());
        buttons.add(b);
        return b;
    }

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
