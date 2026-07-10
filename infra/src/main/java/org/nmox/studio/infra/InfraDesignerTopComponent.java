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
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
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
@TopComponent.Registration(mode = "editor", openAtStartup = true, position = 300)
@ActionID(category = "Window", id = "org.nmox.studio.infra.InfraDesignerTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 260),
    // Cmd+Alt (DA-) — the studio row lives in the one digit family no
    // shipped module claims. The old chord opened a platform window
    // instead of this one: ⇧⌘9 worked, but the studio row must be one family. Keymaps-profile
    // registrations beat Shortcuts-folder ones, so a layer-only audit
    // misses these; WindowShortcutsTest pins the reserved list.
    @ActionReference(path = "Shortcuts", name = "DA-9")
})
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
    private final JButton syncButton = new JButton("Sync from cloud");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton destroyStackButton = new JButton("Destroy stack…");
    private final JButton deployButton = new JButton("DEPLOY");
    private final Timer saveDebounce;
    private final InfraGraph.Listener graphListener;
    private final org.nmox.studio.rack.model.Rack.Listener rackListener;
    private boolean loading;
    /** Once-per-open attach/detach of our graph + rack listeners (tested core). */
    private final ListenerLifecycle listenerLifecycle =
            new ListenerLifecycle(this::attachListeners, this::detachListeners);
    /**
     * Warn-once guard for autosave failures — reset by the next
     * success. Save-lane-thread confined: only writeSnapshot touches it.
     */
    private boolean saveFailureNotified;

    /** Off-EDT file stats for the external-edit check. */
    private static final org.openide.util.RequestProcessor RP =
            new org.openide.util.RequestProcessor("Infra Designer", 1);
    /**
     * Design writes ride their own single-throughput lane, NOT
     * {@link #RP} — RP carries multi-second cloud syncs, and a close
     * flush must never queue behind one (debt #16; the careful parts
     * are documented on the lane class).
     */
    private static final org.nmox.studio.infra.model.SaveLane SAVES =
            new org.nmox.studio.infra.model.SaveLane("Infra Designer saves");
    /** Discriminates our own .nmoxinfra.json writes from foreign edits. */
    private final org.nmox.studio.infra.model.DesignSync designSync =
            new org.nmox.studio.infra.model.DesignSync();
    /** Polls the design file's stamp while the tab is open; never runs closed. */
    private final Timer externalCheck;
    /** True while a stat is on its way to the EDT — checks never overlap. */
    private boolean externalStatInFlight;

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
        externalCheck = new Timer(2000, e -> checkExternalEdit());
        externalCheck.setRepeats(true);
        graphListener = new InfraGraph.Listener() {
            @Override
            public void graphChanged() {
                // Capture `loading` HERE, synchronously with the change, not in
                // the deferred runnable: load() fires graphChanged while loading
                // is true but resets it in its finally BEFORE this invokeLater
                // runs, so a deferred check would miss the guard and schedule a
                // spurious save that writes .nmoxinfra.json on a plain load.
                boolean fromLoad = loading;
                SwingUtilities.invokeLater(() -> {
                    refreshCost();
                    if (!fromLoad) {
                        saveDebounce.restart();
                    }
                });
            }
        };
        rackListener = new org.nmox.studio.rack.model.Rack.Listener() {
            @Override
            public void projectChanged() {
                SwingUtilities.invokeLater(InfraDesignerTopComponent.this::load);
            }
        };
        // listeners attach in componentOpened (and load() runs there), so a
        // close→reopen cycle re-arms autosave/dirty-tracking and re-syncs to
        // the currently-aimed project — the ApiClientTopComponent idiom.
        // Until v1.36 they attached here and were removed for good on the
        // first close: reopened designers silently stopped saving.
        refreshToken();
        refreshCost();
    }

    /** The attach action {@link #listenerLifecycle} runs once per open. */
    private void attachListeners() {
        graph.addListener(graphListener);
        try {
            RackService.getDefault().getRack().addListener(rackListener);
        } catch (RuntimeException | LinkageError ignored) {
            // rack unavailable (tests, stripped platform): no project switches to follow
        }
    }

    /** The detach action {@link #listenerLifecycle} runs once per close. */
    private void detachListeners() {
        graph.removeListener(graphListener);
        try {
            RackService.getDefault().getRack().removeListener(rackListener);
        } catch (RuntimeException | LinkageError ignored) {
            // rack already gone; nothing left to detach
        }
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
            DialogDescriptor dd = new DialogDescriptor(panel,
                    "Cloud API tokens (stored in the OS keychain; blank = keep current)");
            if (DialogDisplayer.getDefault().notify(dd) == DialogDescriptor.OK_OPTION) {
                java.util.Map<org.nmox.studio.infra.api.CloudProvider, String> entered =
                        new java.util.LinkedHashMap<>();
                for (int i = 0; i < providers.length; i++) {
                    String value = new String(fields[i].getPassword()).trim();
                    if (!value.isEmpty()) {
                        entered.put(providers[i], value);
                    }
                }
                // keychain writes may block on OS calls — off the EDT, like
                // every other keyring user in the suite
                RP.post(() -> {
                    entered.forEach(org.nmox.studio.infra.api.CloudProvider::storeToken);
                    refreshToken();
                });
            }
        });
        bar.add(token);
        tokenLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 12));
        bar.add(tokenLabel);

        // tooltip is kept current by refreshToken(): it names the providers with tokens
        syncButton.addActionListener(e -> syncFromCloud());
        bar.add(syncButton);

        refreshButton.setToolTipText("Ask the cloud whether every deployed node still exists — deletions show as drifted");
        refreshButton.addActionListener(e -> refreshDrift());
        bar.add(refreshButton);

        destroyStackButton.setForeground(new Color(0xC6, 0x2B, 0x2B));
        destroyStackButton.setToolTipText("Tear down every deployed resource in reverse dependency order");
        destroyStackButton.addActionListener(e -> destroyStack());
        bar.add(destroyStackButton);

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
        deployButton.setBackground(new Color(0xC6, 0x2B, 0x2B));
        deployButton.setForeground(Color.WHITE);
        deployButton.setOpaque(true);
        deployButton.setBorderPainted(false);
        deployButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        deployButton.setToolTipText("Create everything in this design via each node's cloud API");
        deployButton.addActionListener(e -> deploy());
        bar.add(deployButton);
        return bar;
    }

    /**
     * Runs a cloud worker on {@link #RP} (throughput 1 — deploys, syncs
     * and destroys are mutually exclusive by construction, so repeated
     * clicks can't interleave graph mutations and save() writes), with
     * the triggering button disabled until it finishes.
     */
    private void runExclusive(JButton trigger, Runnable work) {
        if (trigger != null) {
            trigger.setEnabled(false);
        }
        RP.post(() -> {
            try {
                work.run();
            } finally {
                if (trigger != null) {
                    SwingUtilities.invokeLater(() -> trigger.setEnabled(true));
                }
            }
        });
    }

    /** The live design shown in this window - read by Quick Search. */
    public InfraGraph getGraph() {
        return graph;
    }

    /**
     * Brings this window forward and jumps the canvas to the given node,
     * selecting it. The entry point Quick Search invokes after the user
     * picks a node from the search results.
     */
    public void focusNode(InfraNode node) {
        open();
        requestActive();
        canvas.selectNode(node);
        properties.show(node);
    }

    // ---- deploy ----

    private void deploy() {
        List<DoRequest> plan = DeployPlanner.plan(graph);
        if (plan.isEmpty()) {
            info("Nothing to deploy - the design is empty or all live.");
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
        java.util.Set<org.nmox.studio.infra.api.CloudProvider> used =
                DeployPlanner.providersUsed(plan, graph);
        StringBuilder names = new StringBuilder();
        for (var provider : used) {
            if (!provider.hasToken()) {
                names.append(names.length() > 0 ? ", " : "").append(provider.displayName());
            }
        }
        if (names.length() > 0) {
            text.insert(0, "MISSING API TOKENS: " + names
                    + " — set them via the Tokens button to go live.\n\n");
        }

        JTextArea area = new JTextArea(text.toString(), 18, 64);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        // live when every cloud the plan actually calls has its token - a
        // pure-Hetzner or pure-Cloudflare stack needs no DigitalOcean key
        boolean live = DeployPlanner.liveEligible(used,
                org.nmox.studio.infra.api.CloudProvider::hasToken);
        String title = live ? "Deploy?" : "Dry run (missing tokens: " + names + ")";
        DialogDescriptor dd = new DialogDescriptor(new JScrollPane(area), title);
        if (live) {
            Object deploy = "Deploy";
            dd.setOptions(new Object[]{deploy, "Cancel"});
            dd.setValue("Cancel");
            if (DialogDisplayer.getDefault().notify(dd) != deploy) {
                return;
            }
        } else {
            dd.setOptions(new Object[]{"Close"});
            DialogDisplayer.getDefault().notify(dd);
            return;
        }
        runExclusive(deployButton, () -> {
            StringBuilder log = new StringBuilder("NMOX deploy " + java.time.LocalDateTime.now() + "\n");
            boolean ok = client.execute(plan, graph, (node, message) -> {
                if (node != null) {
                    // status updates land on the EDT, same as drift/destroy
                    SwingUtilities.invokeLater(() -> graph.setStatus(node, message));
                    log.append("  ").append(node.kind.getDisplayName())
                            .append(" ").append(node.label).append(": ").append(message).append('\n');
                }
            });
            writeDeployLog(log.toString());
            SwingUtilities.invokeLater(() -> {
                save();
                if (ok) {
                    info("Deploy complete - nodes are live. Log: .nmox/deploy-log");
                } else {
                    error("Deploy stopped on a failure; see node status and .nmox/deploy-log.");
                }
            });
        });
    }

    /**
     * Provider-aware sync: every cloud with a token, sequentially, off
     * the EDT. One provider's failure never aborts the others; the
     * completion dialog reports per-provider counts honestly.
     */
    private void syncFromCloud() {
        java.util.Set<org.nmox.studio.infra.api.CloudProvider> syncing =
                org.nmox.studio.infra.api.DigitalOceanClient.providersToSync(
                        org.nmox.studio.infra.api.CloudProvider::hasToken);
        if (syncing.isEmpty()) {
            info("Set a cloud API token first (DigitalOcean, Hetzner, or Cloudflare).");
            return;
        }
        runExclusive(syncButton, () -> {
            // a three-provider sync can run many seconds: a real
            // ProgressHandle in the status line, ticking per provider
            // (debt #34). No Cancellable — syncAll has no interrupt seam.
            org.netbeans.api.progress.ProgressHandle progress =
                    org.netbeans.api.progress.ProgressHandle.createHandle("Syncing "
                            + syncing.stream()
                                    .map(org.nmox.studio.infra.api.CloudProvider::displayName)
                                    .collect(java.util.stream.Collectors.joining("/"))
                            + "…");
            progress.start();
            java.util.Map<org.nmox.studio.infra.api.CloudProvider,
                    org.nmox.studio.infra.api.DigitalOceanClient.SyncOutcome> outcomes;
            try {
                outcomes = client.syncAll(syncing, graph, provider -> {
                    progress.progress("Syncing from " + provider.displayName() + "…");
                    SwingUtilities.invokeLater(() -> org.openide.awt.StatusDisplayer.getDefault()
                            .setStatusText("Syncing from " + provider.displayName() + "…"));
                });
            } finally {
                progress.finish();
            }
            SwingUtilities.invokeLater(() -> {
                canvas.fit();
                save();
                StringBuilder summary = new StringBuilder();
                boolean anyFailed = false;
                boolean firstCount = true;
                for (var entry : outcomes.entrySet()) {
                    if (summary.length() > 0) {
                        summary.append("  ·  ");
                    }
                    summary.append(entry.getKey().displayName()).append(": ");
                    var outcome = entry.getValue();
                    if (outcome.failed()) {
                        anyFailed = true;
                        summary.append("failed (").append(outcome.error()).append(')');
                    } else {
                        summary.append(outcome.imported());
                        if (firstCount) {
                            summary.append(outcome.imported() == 1 ? " node" : " nodes");
                            firstCount = false;
                        }
                    }
                }
                org.openide.awt.StatusDisplayer.getDefault().setStatusText("Cloud sync finished");
                if (anyFailed) {
                    error("Sync finished with failures — " + summary);
                } else {
                    info("Imported live resources — " + summary);
                }
            });
        });
    }

    /** The drift check: designed vs actual, node by node. */
    private void refreshDrift() {
        runExclusive(refreshButton, () -> {
            try {
                client.refreshDrift(graph, (node, status) ->
                        SwingUtilities.invokeLater(() -> graph.setStatus(node, status)));
                SwingUtilities.invokeLater(this::save);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> error("Refresh failed: " + ex.getMessage()));
            }
        });
    }

    /**
     * One decision, whole stack: name every deployed resource and the
     * monthly bill it carries, then tear down in reverse dependency
     * order. The design stays on the canvas, ready to deploy again.
     */
    private void destroyStack() {
        java.util.List<InfraNode> order = DeployPlanner.teardownOrder(graph);
        if (order.isEmpty()) {
            info("Nothing deployed to destroy.");
            return;
        }
        double monthly = 0;
        StringBuilder names = new StringBuilder();
        for (InfraNode node : order) {
            monthly += node.monthlyUsd();
            names.append("  • ").append(node.kind.getDisplayName())
                    .append("  ").append(node.label).append('\n');
        }
        boolean go = confirm("Destroy " + order.size() + " cloud resource"
                + (order.size() == 1 ? "" : "s")
                + ", saving ~$" + String.format("%.2f", monthly) + "/month?\n\n" + names
                + "\nReverse dependency order. The design stays on the canvas.",
                "Destroy stack");
        if (!go) {
            return;
        }
        runExclusive(destroyStackButton, () -> {
            int failures = client.destroyAll(order, (node, status) ->
                    SwingUtilities.invokeLater(() -> graph.setStatus(node, status)));
            SwingUtilities.invokeLater(() -> {
                save();
                if (failures > 0) {
                    error(failures + " resource(s) could not be destroyed — check their status lines.");
                }
            });
        });
    }

    // ---- platform dialogs (parented, keyboard-correct, consistent chrome) ----

    private void info(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message, NotifyDescriptor.INFORMATION_MESSAGE));
    }

    private void error(String message) {
        DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                message, NotifyDescriptor.ERROR_MESSAGE));
    }

    private boolean confirm(String message, String title) {
        NotifyDescriptor d = new NotifyDescriptor.Confirmation(message, title,
                NotifyDescriptor.YES_NO_OPTION, NotifyDescriptor.WARNING_MESSAGE);
        return DialogDisplayer.getDefault().notify(d) == NotifyDescriptor.YES_OPTION;
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
                if (confirm("Really destroy " + node.kind.getDisplayName() + " "
                        + node.label + " on " + node.kind.provider().displayName() + "?",
                        "Destroy resource")) {
                    // the menu item dies with the menu, so there is no button to
                    // grey — RP's throughput 1 still serializes repeated clicks
                    runExclusive(null, () -> {
                        try {
                            client.destroy(node);
                            SwingUtilities.invokeLater(() -> {
                                graph.setStatus(node, "destroyed");
                                save();
                            });
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() ->
                                    graph.setStatus(node, "destroy failed: " + ex.getMessage()));
                        }
                    });
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
        // the read below must see every queued write — an A→B→A re-aim
        // bounce could otherwise read A's file before A's last save lands
        // (bounded ms drain; see SaveLane.flush)
        SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
        loading = true;
        try {
            File file = designFile();
            if (file.isFile()) {
                // corrupt file: GraphIO copies it to .bak BEFORE handing back
                // the empty fallback, so the next autosave can't destroy the
                // user's only copy
                File backup = GraphIO.loadGuarded(graph, file);
                if (backup != null) {
                    balloon("Couldn't read " + GraphIO.DEFAULT_FILENAME + " — starting empty",
                            "The unreadable original was kept at " + backup.getName() + ".",
                            null);
                }
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
            // the loaded version is now "ours": only later foreign writes
            // should trigger the external-reload flow
            designSync.recordOwn(org.nmox.studio.infra.model.DesignSync.Stamp.of(designFile()));
        }
    }

    /**
     * EDT: the graph is EDT-confined, so the JSON snapshot is taken here
     * and only the disk write rides the save lane (debt #16). The design
     * file binds NOW — a project re-aim between the debounce fire and
     * the write must not retarget the edits.
     */
    private void save() {
        File file = designFile();
        String json = GraphIO.toJson(graph).toString(2);
        SAVES.save(() -> writeSnapshot(file, json));
    }

    /**
     * Save lane only: the write and its self-stamp are ONE task (the
     * same atomic-rename write GraphIO.save performs), so a lane-ordered
     * external-edit stat can never see the write without the stamp.
     */
    private void writeSnapshot(File file, String json) {
        try {
            org.nmox.studio.core.util.AtomicFiles.writeString(file.toPath(), json);
            designSync.recordOwn(org.nmox.studio.infra.model.DesignSync.Stamp.of(file));
            saveFailureNotified = false;
        } catch (Exception ex) {
            // a failed autosave never interrupts editing — but a chronically
            // failing one must not lose work silently: warn once per streak
            // (the ApiClientTopComponent shape; every other suite already had it)
            java.util.logging.Logger.getLogger(InfraDesignerTopComponent.class.getName())
                    .log(java.util.logging.Level.WARNING, "Infra design autosave failed", ex);
            if (!saveFailureNotified) {
                saveFailureNotified = true;
                try {
                    org.openide.awt.NotificationDisplayer.getDefault().notify(
                            "Couldn't save " + GraphIO.DEFAULT_FILENAME,
                            javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                            "Changes are not being persisted: " + ex.getMessage(), null);
                } catch (RuntimeException | LinkageError ignored) {
                    // notifications unavailable (tests, stripped platform)
                }
            }
        }
    }

    // ---- .nmoxinfra.json edited outside the designer ----

    /**
     * The 2-second foreign-change check, EDT-side but I/O-free: the
     * stat rides the SAVE lane (not {@link #RP}), so it queues behind
     * any write+stamp pair it may have raced — our own save mid-landing
     * can never stat as a foreign version (debt #16). The verdict lands
     * back on the EDT. Checks never overlap; a closed tab never checks
     * (the timer only runs between componentOpened and componentClosed).
     */
    private void checkExternalEdit() {
        if (externalStatInFlight) {
            return;
        }
        externalStatInFlight = true;
        File file = designFile();
        SAVES.classify(() -> {
            org.nmox.studio.infra.model.DesignSync.Stamp stamp =
                    org.nmox.studio.infra.model.DesignSync.Stamp.of(file);
            SwingUtilities.invokeLater(() -> {
                externalStatInFlight = false;
                handleExternalStamp(stamp);
            });
        });
    }

    /**
     * EDT: reacts to a design-file stamp per the tested
     * {@link org.nmox.studio.infra.model.DesignSync} conflict matrix.
     * A pending {@code saveDebounce} IS the dirty state — every canvas
     * edit restarts it and its save() ends it, so "running" exactly
     * means "unsaved local edits".
     */
    private void handleExternalStamp(org.nmox.studio.infra.model.DesignSync.Stamp onDisk) {
        if (!isOpened()) {
            return; // a closed tab reacts to nothing
        }
        switch (designSync.check(onDisk, saveDebounce.isRunning())) {
            case RELOAD -> {
                // clean canvas: follow the disk. load() raises `loading`, so
                // the graphChanged it fires never schedules a spurious save
                // (the v1.33.2 guard stays intact).
                load();
                balloon("Reloaded " + GraphIO.DEFAULT_FILENAME,
                        "The file changed outside the designer — the canvas follows it.",
                        null);
            }
            case CONFLICT -> {
                // Foreign edit vs unsaved canvas edits: NEVER clobber silently.
                // Hold the pending debounced save so it can't overwrite the
                // foreign version while the user decides. Clicking the balloon
                // reloads (discarding the local edits); ignoring it means the
                // NEXT canvas change restarts the debounce and that save wins —
                // the pre-existing last-writer-wins behavior, unchanged.
                saveDebounce.stop();
                balloon(GraphIO.DEFAULT_FILENAME + " changed on disk — Reload?",
                        "Click to reload; unsaved canvas edits are discarded. "
                        + "Keep editing to keep your version instead.",
                        e -> load());
            }
            case NONE -> {
            }
        }
    }

    /** Quiet corner notification — never a modal, never steals focus. */
    private static void balloon(String title, String detail,
            java.awt.event.ActionListener action) {
        try {
            org.openide.awt.NotificationDisplayer.getDefault().notify(
                    title,
                    javax.swing.UIManager.getIcon("OptionPane.informationIcon"),
                    detail == null ? "" : detail, action,
                    org.openide.awt.NotificationDisplayer.Priority.LOW);
        } catch (RuntimeException | LinkageError ignored) {
            // notifications unavailable (tests, stripped platform)
        }
    }

    /**
     * Recomputes the token indicator. The first token read per provider
     * may block on an OS keychain call, so the reads run on {@link #RP}
     * (priming CloudProvider's session cache for the EDT callers in
     * deploy()/syncFromCloud()) and only the label updates land on the
     * EDT. Callable from any thread.
     */
    private void refreshToken() {
        RP.post(() -> {
            StringBuilder sb = new StringBuilder();
            int connected = 0;
            for (var provider : org.nmox.studio.infra.api.CloudProvider.values()) {
                boolean has = provider.hasToken();
                connected += has ? 1 : 0;
                sb.append(has ? "●" : "○");
            }
            boolean any = connected > 0;
            String label = sb + (any ? " " + connected + "/3 clouds" : " no tokens (dry-run)");
            String tokened = org.nmox.studio.infra.api.DigitalOceanClient.providersToSync(
                            org.nmox.studio.infra.api.CloudProvider::hasToken).stream()
                    .map(org.nmox.studio.infra.api.CloudProvider::displayName)
                    .collect(java.util.stream.Collectors.joining(", "));
            SwingUtilities.invokeLater(() -> {
                tokenLabel.setText(label);
                tokenLabel.setToolTipText("DigitalOcean / Hetzner / Cloudflare");
                tokenLabel.setForeground(any ? new Color(0x4E, 0xC9, 0x8B) : new Color(0xE8, 0xC4, 0x4A));
                syncButton.setToolTipText("Import existing cloud resources as live nodes — "
                        + (tokened.isEmpty() ? "no tokens set (use Tokens…)" : "syncs: " + tokened));
            });
        });
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
    protected void componentOpened() {
        // attach BEFORE load(): listeners must not stack (CopyOnWriteArrayList
        // double-attach = double-fire), and load() itself is idempotent — it
        // re-reads the currently-aimed project's design, so an aim that moved
        // while the tab was closed is picked up here.
        listenerLifecycle.open();
        load();
        refreshToken();
        externalCheck.start(); // foreign-change checks run only while open
    }

    @Override
    protected void componentClosed() {
        // stop the timers, flush any edits still sitting in the debounce (the
        // last ~1s of canvas work must not die with the tab), and drop our
        // listeners so a close/reopen cycle doesn't accumulate zombies
        externalCheck.stop();
        if (saveDebounce.isRunning()) {
            saveDebounce.stop();
            save();
        }
        // a write may sit queued on the lane even when the timer isn't
        // running (a debounce that fired moments ago) — drain it before
        // the designer is torn down (bounded; see SaveLane.flush)
        SAVES.flush(5, java.util.concurrent.TimeUnit.SECONDS);
        listenerLifecycle.close();
    }
}
