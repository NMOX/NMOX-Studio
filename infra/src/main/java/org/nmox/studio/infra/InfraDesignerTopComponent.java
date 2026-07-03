package org.nmox.studio.infra;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.infra.api.DeployPlanner;
import org.nmox.studio.infra.api.DigitalOceanClient;
import org.nmox.studio.infra.api.DoRequest;
import org.nmox.studio.infra.model.GraphIO;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.ui.FlowCanvas;
import org.nmox.studio.infra.ui.InfraPalette;
import org.nmox.studio.infra.ui.PropertyPanel;
import org.nmox.studio.rack.service.RackService;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/**
 * The Infra Designer: DigitalOcean's catalog as a Node-RED-style flow.
 * Drag offerings from the palette, wire relationships, watch the cost
 * meter, then hit DEPLOY - dry-run plan without a token, real API
 * calls with one. The design persists with the project, exactly like
 * the rack patch.
 */
@ConvertAsProperties(dtd = "-//org.nmox.studio.infra//InfraDesigner//EN", autostore = false)
@TopComponent.Description(preferredID = "InfraDesignerTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 300)
@ActionID(category = "Window", id = "org.nmox.studio.infra.InfraDesignerTopComponent")
@ActionReference(path = "Menu/Window", position = 260)
@TopComponent.OpenActionRegistration(displayName = "#CTL_InfraAction",
        preferredID = "InfraDesignerTopComponent")
@Messages({
    "CTL_InfraAction=Infra Designer",
    "CTL_InfraTopComponent=Infra Designer",
    "HINT_InfraTopComponent=Drag-and-drop DigitalOcean infrastructure designer"
})
public final class InfraDesignerTopComponent extends TopComponent {

    private final InfraGraph graph = new InfraGraph();
    private final DigitalOceanClient client = new DigitalOceanClient();
    private final FlowCanvas canvas;
    private final PropertyPanel properties;
    private final JLabel tokenLabel = new JLabel();
    private final JLabel costLabel = new JLabel();
    private final Timer saveDebounce;
    private final InfraGraph.Listener graphListener;
    private final org.nmox.studio.rack.model.Rack.Listener rackListener;
    private boolean loading;

    public InfraDesignerTopComponent() {
        setName(org.openide.util.NbBundle.getMessage(InfraDesignerTopComponent.class, "CTL_InfraTopComponent"));
        setToolTipText(org.openide.util.NbBundle.getMessage(InfraDesignerTopComponent.class, "HINT_InfraTopComponent"));
        setLayout(new BorderLayout());

        properties = new PropertyPanel(graph);
        canvas = new FlowCanvas(graph, new FlowCanvas.Callbacks() {
            @Override
            public void nodeDoubleClicked(InfraNode node) {
                properties.show(node);
            }

            @Override
            public void nodeContextMenu(InfraNode node, Point screenPoint) {
                showNodeMenu(node, screenPoint);
            }

            @Override
            public void selectionChanged(InfraNode node) {
                properties.show(node);
            }
        });

        add(new InfraPalette(graph), BorderLayout.WEST);
        add(canvas, BorderLayout.CENTER);
        add(properties, BorderLayout.EAST);
        add(buildToolbar(), BorderLayout.NORTH);

        saveDebounce = new Timer(1000, e -> save());
        saveDebounce.setRepeats(false);
        graphListener = new InfraGraph.Listener() {
            @Override
            public void graphChanged() {
                SwingUtilities.invokeLater(() -> {
                    refreshCost();
                    if (!loading) {
                        saveDebounce.restart();
                    }
                });
            }
        };
        graph.addListener(graphListener);

        rackListener = new org.nmox.studio.rack.model.Rack.Listener() {
            @Override
            public void projectChanged() {
                SwingUtilities.invokeLater(InfraDesignerTopComponent.this::load);
            }
        };
        RackService.getDefault().getRack().addListener(rackListener);
        load();
        refreshToken();
        refreshCost();
    }

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton token = new JButton("Tokens…");
        token.setToolTipText("Set API tokens: DigitalOcean / Hetzner Cloud / Cloudflare "
                + "(or export DIGITALOCEAN_TOKEN / HCLOUD_TOKEN / CLOUDFLARE_API_TOKEN)");
        token.addActionListener(e -> {
            var providers = org.nmox.studio.infra.api.CloudProvider.values();
            JPasswordField[] fields = new JPasswordField[providers.length];
            javax.swing.JPanel panel = new javax.swing.JPanel(
                    new java.awt.GridLayout(providers.length * 2, 1, 0, 2));
            for (int i = 0; i < providers.length; i++) {
                fields[i] = new JPasswordField(32);
                String current = providers[i].hasToken() ? "  (token set)" : "  (no token)";
                panel.add(new JLabel(providers[i].displayName() + current));
                panel.add(fields[i]);
            }
            int ok = JOptionPane.showConfirmDialog(this, panel,
                    "Cloud API tokens (stored in IDE settings; blank = keep current)",
                    JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                for (int i = 0; i < providers.length; i++) {
                    String value = new String(fields[i].getPassword()).trim();
                    if (!value.isEmpty()) {
                        providers[i].storeToken(value);
                    }
                }
                refreshToken();
            }
        });
        bar.add(token);
        tokenLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 12));
        bar.add(tokenLabel);

        JButton sync = new JButton("Sync from cloud");
        sync.setToolTipText("Import existing DigitalOcean resources as live nodes");
        sync.addActionListener(e -> syncFromCloud());
        bar.add(sync);

        JButton refresh = new JButton("Refresh");
        refresh.setToolTipText("Ask the cloud whether every deployed node still exists — deletions show as drifted");
        refresh.addActionListener(e -> refreshDrift());
        bar.add(refresh);

        JButton destroyStack = new JButton("Destroy stack…");
        destroyStack.setForeground(new Color(0xC6, 0x2B, 0x2B));
        destroyStack.setToolTipText("Tear down every deployed resource in reverse dependency order");
        destroyStack.addActionListener(e -> destroyStack());
        bar.add(destroyStack);

        JButton zoomOut = new JButton("−");
        zoomOut.setToolTipText("Zoom out");
        zoomOut.addActionListener(e -> canvas.zoomOut());
        bar.add(zoomOut);

        JButton fit = new JButton("Fit");
        fit.setToolTipText("Fit the whole design in view");
        fit.addActionListener(e -> canvas.fit());
        bar.add(fit);

        JButton zoomIn = new JButton("+");
        zoomIn.setToolTipText("Zoom in");
        zoomIn.addActionListener(e -> canvas.zoomIn());
        bar.add(zoomIn);

        bar.add(javax.swing.Box.createHorizontalGlue());

        costLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        costLabel.setForeground(new Color(0x4E, 0xC9, 0x8B));
        costLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 12));
        bar.add(costLabel);

        // THE button, Node-RED red
        JButton deploy = new JButton("DEPLOY");
        deploy.setBackground(new Color(0xC6, 0x2B, 0x2B));
        deploy.setForeground(Color.WHITE);
        deploy.setOpaque(true);
        deploy.setBorderPainted(false);
        deploy.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        deploy.setToolTipText("Create everything in this design via the DigitalOcean API");
        deploy.addActionListener(e -> deploy());
        bar.add(deploy);
        return bar;
    }

    // ---- deploy ----

    private void deploy() {
        List<DoRequest> plan = DeployPlanner.plan(graph);
        if (plan.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nothing to deploy - the design is empty or all live.",
                    "Infra Designer", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder text = new StringBuilder();
        int step = 1;
        for (DoRequest request : plan) {
            text.append(String.format("%2d. %s %s%n      %s%n", step++,
                    request.skipped() ? "SKIP" : request.method(),
                    request.skipped() ? "" : request.path(),
                    request.description()));
        }
        text.append(String.format("%nEstimated monthly cost: $%.2f", graph.totalMonthlyUsd()));

        // every provider this plan touches must have a token BEFORE the
        // worker starts - failing on node 7 of 12 leaves half a deployment
        java.util.Set<org.nmox.studio.infra.api.CloudProvider> missing =
                new java.util.LinkedHashSet<>();
        for (DoRequest request : plan) {
            if (!request.skipped()) {
                var node = graph.node(request.nodeId());
                if (node != null && node.kind.provider().token() == null) {
                    missing.add(node.kind.provider());
                }
            }
        }
        if (!missing.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (var provider : missing) {
                names.append(names.length() > 0 ? ", " : "").append(provider.displayName());
            }
            text.insert(0, "MISSING API TOKENS: " + names
                    + " — set them via the Tokens button to go live.\n\n");
        }

        JTextArea area = new JTextArea(text.toString(), 18, 64);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        boolean live = missing.isEmpty() && DigitalOceanClient.hasToken();
        String title = live ? "Deploy?"
                : missing.isEmpty() ? "Dry run (no API token set)"
                        : "Dry run (missing API tokens)";
        Object[] options = live ? new Object[]{"Deploy", "Cancel"} : new Object[]{"Close"};
        int choice = JOptionPane.showOptionDialog(this, new JScrollPane(area), title,
                JOptionPane.DEFAULT_OPTION, live ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        if (!live || choice != 0) {
            return;
        }
        Thread worker = new Thread(() -> {
            StringBuilder log = new StringBuilder("NMOX deploy " + java.time.LocalDateTime.now() + "\n");
            boolean ok = client.execute(plan, graph, (node, message) -> {
                if (node != null) {
                    graph.setStatus(node, message);
                    log.append("  ").append(node.kind.getDisplayName())
                            .append(" ").append(node.label).append(": ").append(message).append('\n');
                }
            });
            writeDeployLog(log.toString());
            SwingUtilities.invokeLater(() -> {
                save();
                JOptionPane.showMessageDialog(this,
                        ok ? "Deploy complete - nodes are live. Log: .nmox/deploy-log"
                           : "Deploy stopped on a failure; see node status and .nmox/deploy-log.",
                        "Infra Designer", ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
            });
        }, "nmox-infra-deploy");
        worker.setDaemon(true);
        worker.start();
    }

    private void syncFromCloud() {
        if (!DigitalOceanClient.hasToken()) {
            JOptionPane.showMessageDialog(this, "Set an API token first.", "Infra Designer",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                int imported = client.sync(graph);
                SwingUtilities.invokeLater(() -> {
                    canvas.fit();
                    save();
                    JOptionPane.showMessageDialog(this, "Imported " + imported + " live resources.",
                            "Infra Designer", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Sync failed: " + ex.getMessage(), "Infra Designer", JOptionPane.ERROR_MESSAGE));
            }
        }, "nmox-infra-sync");
        worker.setDaemon(true);
        worker.start();
    }

    /** The drift check: designed vs actual, node by node. */
    private void refreshDrift() {
        Thread worker = new Thread(() -> {
            try {
                client.refreshDrift(graph, (node, status) ->
                        SwingUtilities.invokeLater(() -> graph.setStatus(node, status)));
                SwingUtilities.invokeLater(this::save);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Refresh failed: " + ex.getMessage(), "Refresh", JOptionPane.ERROR_MESSAGE));
            }
        }, "nmox-infra-refresh");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * One decision, whole stack: name every deployed resource and the
     * monthly bill it carries, then tear down in reverse dependency
     * order. The design stays on the canvas, ready to deploy again.
     */
    private void destroyStack() {
        java.util.List<InfraNode> order = DeployPlanner.teardownOrder(graph);
        if (order.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nothing deployed to destroy.");
            return;
        }
        double monthly = 0;
        StringBuilder names = new StringBuilder();
        for (InfraNode node : order) {
            monthly += node.monthlyUsd();
            names.append("  • ").append(node.kind.getDisplayName())
                    .append("  ").append(node.label).append('\n');
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Destroy " + order.size() + " cloud resource" + (order.size() == 1 ? "" : "s")
                + ", saving ~$" + String.format("%.2f", monthly) + "/month?\n\n" + names
                + "\nReverse dependency order. The design stays on the canvas.",
                "Destroy stack", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        Thread worker = new Thread(() -> {
            int failures = client.destroyAll(order, (node, status) ->
                    SwingUtilities.invokeLater(() -> graph.setStatus(node, status)));
            SwingUtilities.invokeLater(() -> {
                save();
                if (failures > 0) {
                    JOptionPane.showMessageDialog(this,
                            failures + " resource(s) could not be destroyed — check their status lines.",
                            "Destroy stack", JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "nmox-infra-destroy-stack");
        worker.setDaemon(true);
        worker.start();
    }

    /** Appends a deploy run to .nmox/deploy-log beside the aimed project. */
    private void writeDeployLog(String entry) {
        try {
            java.io.File root = org.nmox.studio.rack.service.RackService.getDefault()
                    .getRack().getProjectDir();
            if (root == null) {
                return;
            }
            java.io.File dir = new java.io.File(root, ".nmox");
            java.nio.file.Files.createDirectories(dir.toPath());
            java.nio.file.Files.writeString(new java.io.File(dir, "deploy-log").toPath(),
                    entry + "\n", java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ex) {
            // a log is a courtesy, never a blocker
        }
    }

    private void showNodeMenu(InfraNode node, Point screenPoint) {
        JPopupMenu menu = new JPopupMenu();
        if (node.doId != null) {
            JMenuItem destroy = new JMenuItem("Destroy in cloud (" + node.doId + ")");
            destroy.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(this,
                        "Really destroy " + node.kind.getDisplayName() + " " + node.label + " on DigitalOcean?",
                        "Destroy resource", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                    Thread worker = new Thread(() -> {
                        try {
                            client.destroy(node);
                            graph.setStatus(node, "destroyed");
                            SwingUtilities.invokeLater(this::save);
                        } catch (Exception ex) {
                            graph.setStatus(node, "destroy failed: " + ex.getMessage());
                        }
                    }, "nmox-infra-destroy");
                    worker.setDaemon(true);
                    worker.start();
                }
            });
            menu.add(destroy);
            menu.addSeparator();
        }
        if (node.ip != null && !node.ip.isBlank()) {
            JMenuItem ssh = new JMenuItem("Copy SSH command  (root@" + node.ip + ")");
            ssh.addActionListener(e -> {
                java.awt.datatransfer.StringSelection sel =
                        new java.awt.datatransfer.StringSelection("ssh root@" + node.ip);
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
                org.openide.awt.StatusDisplayer.getDefault()
                        .setStatusText("Copied: ssh root@" + node.ip);
            });
            menu.add(ssh);
            menu.addSeparator();
        }
        JMenuItem remove = new JMenuItem("Remove from design");
        remove.addActionListener(e -> graph.removeNode(node));
        menu.add(remove);
        Point local = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(local, canvas);
        menu.show(canvas, local.x, local.y);
    }

    // ---- persistence & labels ----

    private File designFile() {
        return new File(RackService.getDefault().getRack().getProjectDir(), GraphIO.DEFAULT_FILENAME);
    }

    private void load() {
        loading = true;
        try {
            File file = designFile();
            if (file.isFile()) {
                GraphIO.load(graph, file);
            } else {
                graph.clear();
            }
            canvas.fit();
        } catch (Exception ex) {
            // unreadable design: start clean rather than block the window
            graph.clear();
        } finally {
            loading = false;
            refreshCost();
        }
    }

    private void save() {
        try {
            GraphIO.save(graph, designFile());
        } catch (Exception ex) {
            // disk hiccup; the next change retries
        }
    }

    private void refreshToken() {
        StringBuilder sb = new StringBuilder();
        int connected = 0;
        for (var provider : org.nmox.studio.infra.api.CloudProvider.values()) {
            boolean has = provider.hasToken();
            connected += has ? 1 : 0;
            sb.append(has ? "●" : "○");
        }
        boolean any = connected > 0;
        tokenLabel.setText(sb + (any ? " " + connected + "/3 clouds" : " no tokens (dry-run)"));
        tokenLabel.setToolTipText("DigitalOcean / Hetzner / Cloudflare");
        tokenLabel.setForeground(any ? new Color(0x4E, 0xC9, 0x8B) : new Color(0xE8, 0xC4, 0x4A));
    }

    private void refreshCost() {
        costLabel.setText(String.format("≈ $%.2f/mo", graph.totalMonthlyUsd()));
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
    }

    @Override
    protected void componentClosed() {
        // stop the debounce timer and drop our listeners, so a close/reopen
        // cycle doesn't accumulate zombie listeners on the graph and the rack
        saveDebounce.stop();
        graph.removeListener(graphListener);
        try {
            RackService.getDefault().getRack().removeListener(rackListener);
        } catch (RuntimeException | LinkageError ignore) {
            // rack already gone; nothing left to detach
        }
    }
}
