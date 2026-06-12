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
@TopComponent.Registration(mode = "editor", openAtStartup = false)
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
        graph.addListener(new InfraGraph.Listener() {
            @Override
            public void graphChanged() {
                SwingUtilities.invokeLater(() -> {
                    refreshCost();
                    if (!loading) {
                        saveDebounce.restart();
                    }
                });
            }
        });

        RackService.getDefault().getRack().addListener(new org.nmox.studio.rack.model.Rack.Listener() {
            @Override
            public void projectChanged() {
                SwingUtilities.invokeLater(InfraDesignerTopComponent.this::load);
            }
        });
        load();
        refreshToken();
        refreshCost();
    }

    private JToolBar buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JButton token = new JButton("Token…");
        token.setToolTipText("Set the DigitalOcean API token (or export DIGITALOCEAN_TOKEN)");
        token.addActionListener(e -> {
            JPasswordField field = new JPasswordField(32);
            int ok = JOptionPane.showConfirmDialog(this, field,
                    "DigitalOcean API token (stored in IDE settings)",
                    JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                DigitalOceanClient.storeToken(new String(field.getPassword()).trim());
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

        JButton fit = new JButton("Fit");
        fit.addActionListener(e -> canvas.fit());
        bar.add(fit);

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

        JTextArea area = new JTextArea(text.toString(), 18, 64);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        boolean live = DigitalOceanClient.hasToken();
        String title = live ? "Deploy to DigitalOcean?" : "Dry run (no API token set)";
        Object[] options = live ? new Object[]{"Deploy", "Cancel"} : new Object[]{"Close"};
        int choice = JOptionPane.showOptionDialog(this, new JScrollPane(area), title,
                JOptionPane.DEFAULT_OPTION, live ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        if (!live || choice != 0) {
            return;
        }
        Thread worker = new Thread(() -> {
            boolean ok = client.execute(plan, graph, (node, message) -> {
                if (node != null) {
                    graph.setStatus(node, message);
                }
            });
            SwingUtilities.invokeLater(() -> {
                save();
                JOptionPane.showMessageDialog(this,
                        ok ? "Deploy complete - nodes are live." : "Deploy stopped on a failure; see node status.",
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
        boolean has = DigitalOceanClient.hasToken();
        tokenLabel.setText(has ? "● connected" : "○ no token (dry-run)");
        tokenLabel.setForeground(has ? new Color(0x4E, 0xC9, 0x8B) : new Color(0xE8, 0xC4, 0x4A));
    }

    private void refreshCost() {
        costLabel.setText(String.format("≈ $%.2f/mo", graph.totalMonthlyUsd()));
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
    }
}
