package org.nmox.studio.infra.ops.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.core.util.TextDirection;
import org.nmox.studio.infra.ops.OpsEvent;
import org.nmox.studio.infra.ops.OpsHub;
import org.nmox.studio.infra.ops.model.OpsKind;
import org.nmox.studio.infra.ops.model.OpsNode;
import org.nmox.studio.infra.ops.provider.OpsProvider;
import org.nmox.studio.infra.ops.provider.ProviderOptions;
import org.nmox.studio.infra.ops.remote.DeployPlan;
import org.nmox.studio.infra.ops.remote.SshTarget;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * The Ops Hub: the DevOps HUD as a studio tab. The toolbar holds the
 * doors (New Server, Add Host, Simulation, the token), the HUD is the
 * picture, the inspector is what a selected node is and does, and the
 * feed is what happened. Everything the window shows comes from
 * {@link OpsHub}; the window never spawns or calls a cloud itself.
 *
 * <p>Boot cost is zero: the engine is made when this window first opens,
 * polling runs only while the window is showing, and nothing here
 * touches a network until a button asks.
 */
@ConvertAsProperties(dtd = "-//org.nmox.studio.infra//OpsHub//EN", autostore = false)
@TopComponent.Description(preferredID = "OpsHubTopComponent", persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 305)
@ActionID(category = "Window", id = "org.nmox.studio.infra.ops.ui.OpsHubTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 262)
})
@TopComponent.OpenActionRegistration(displayName = "#CTL_OpsHubAction", preferredID = "OpsHubTopComponent")
@Messages({
    "CTL_OpsHubAction=Ops Hub",
    "CTL_OpsHubTopComponent=Ops Hub",
    "HINT_OpsHubTopComponent=The DevOps HUD: create servers, connect over mosh or ssh, deploy, watch",
    "OpsHub_newServer=New Server…",
    "OpsHub_newServerTooltip=Ask a provider for a server, watch it come up",
    "OpsHub_addHost=Add Host…",
    "OpsHub_addHostTooltip=Reach a server you already have over ssh",
    "OpsHub_refresh=Refresh",
    "OpsHub_refreshTooltip=Re-read every provider's servers and probe every host",
    "OpsHub_simulation=Simulation",
    "OpsHub_simulationTooltip=A pretend provider with pretend servers, clearly marked — no bill, no network",
    "OpsHub_token=Token…",
    "OpsHub_tokenTooltip=Set the DigitalOcean API token (the Infra Designer's Tokens… sets the same one)",
    "OpsHub_viewFleet=Fleet",
    "OpsHub_viewServices=Workloads",
    "OpsHub_viewTooltip=The physical picture, or the logical one",
    "OpsHub_fit=Fit",
    "OpsHub_fitTooltip=Fit everything in view again (press 0). The wheel zooms in and out, and so do + and -; a drag on empty space pans.",
    "OpsHub_motion=Motion",
    "OpsHub_motionTooltip=Let the HUD glide; off snaps every change into place (the system's Reduce Motion is honoured when this is on)",
    "# {0} - the provider's name (DigitalOcean, Hetzner Cloud)",
    "OpsHub_providerLive={0}: live",
    "# {0} - the provider's name (DigitalOcean, Hetzner Cloud)",
    "OpsHub_providerNoToken={0}: no token",
    "# {0} - the provider",
    "OpsHub_readingOptions=Reading {0}''s regions, sizes and keys…",
    "# {0} - the error",
    "OpsHub_optionsFailed=Could not read the provider''s options: {0}",
    "OpsHub_noProvider=No provider is configured: set a DigitalOcean token, or switch the simulation on.",
    "OpsHub_pickProvider=Create the server at",
    "OpsHub_noProject=No project is aimed — open one first; the deploy sends its Dockerfile",
    "# {0} - the terminal title",
    "OpsHub_opened=Opened {0} in the Terminal",
    "OpsHub_noMosh=mosh is not installed on this machine — opening ssh instead",
    "OpsHub_noSsh=ssh is not on the PATH",
    "OpsHub_copied=The Terminal could not run it here; the command is on the clipboard — paste it into the terminal that opened",
    "# {0} - the url",
    "OpsHub_openedUrl=Opened {0}",
    "OpsHub_noBrowser=The in-app Browser is unavailable; the address is on the clipboard",
    "OpsHub_keyAdded=Added this machine''s key to the account",
    "# {0} - the error",
    "OpsHub_keyFailed=Could not add the key: {0}",
    "OpsHub_menuConnectMosh=Connect with mosh",
    "OpsHub_menuConnectSsh=Connect with ssh",
    "OpsHub_menuOpen=Open host view",
    "OpsHub_menuDeploy=Deploy here…",
    "OpsHub_menuProbe=Probe now",
    "OpsHub_menuDestroy=Destroy…",
    "OpsHub_menuForget=Forget",
    "OpsHub_menuLogs=Follow logs",
    "OpsHub_menuShell=Shell in container",
    "OpsHub_menuRollback=Roll back…",
    "# {0} - the container's name as the host printed it",
    "OpsHub_badContainerName=The host reports a container named \u201c{0}\u201d, which is not a name docker accepts, so Logs and Shell cannot be opened for it.",
    "OpsHub_hostGone=That service or its host is no longer on the Hub."
})
public final class OpsHubTopComponent extends TopComponent {

    private static final RequestProcessor DIALOG_PREP = new RequestProcessor("Ops Hub dialogs", 1, true);

    private final OpsHub hub = OpsHub.getDefault();
    private final HudCanvas canvas = new HudCanvas(hub);
    private final InspectorPanel inspector;
    private final EventFeedPanel feed = new EventFeedPanel(hub);
    private final JLabel providerChip = new JLabel();
    private final JCheckBox simulation = new JCheckBox(Bundle.OpsHub_simulation());
    private final JToggleButton fleetView = new JToggleButton(Bundle.OpsHub_viewFleet(), true);
    private final JToggleButton servicesView = new JToggleButton(Bundle.OpsHub_viewServices());
    private final JCheckBox motion = new JCheckBox(Bundle.OpsHub_motion());
    private final JButton fit = button(Bundle.OpsHub_fit(), Bundle.OpsHub_fitTooltip(), () -> { });
    private final OpsHub.Listener hubListener = new OpsHub.Listener() {
        @Override
        public void event(OpsEvent event) {
        }

        @Override
        public void stateChanged() {
            syncChrome();
            inspector.refreshDeploy();
        }
    };
    private final org.nmox.studio.infra.ops.model.Topology.Listener topologyListener =
            new org.nmox.studio.infra.ops.model.Topology.Listener() {
        @Override
        public void structureChanged() {
            showSelected();
        }

        @Override
        public void nodeChanged(OpsNode node) {
            if (node.id.equals(canvas.selected())) {
                inspector.show(node);
            }
        }
    };

    public OpsHubTopComponent() {
        setName(Bundle.CTL_OpsHubTopComponent());
        setToolTipText(Bundle.HINT_OpsHubTopComponent());
        setLayout(new BorderLayout());
        inspector = new InspectorPanel(hub, new InspectorActions());
        hub.topology().workstation().label = HudCanvas.kindWord(OpsKind.WORKSTATION);
        add(toolbar(), BorderLayout.PAGE_START);
        JSplitPane split = TextDirection.sidesFollowReader(new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, inspector));
        split.setResizeWeight(0.72);
        split.setContinuousLayout(true);
        split.setBorder(null);
        JSplitPane vertical = TextDirection.sidesFollowReader(new JSplitPane(JSplitPane.VERTICAL_SPLIT, split, feed));
        vertical.setResizeWeight(0.82);
        vertical.setContinuousLayout(true);
        vertical.setBorder(null);
        add(vertical, BorderLayout.CENTER);
        canvas.onSelect(id -> showSelected());
        canvas.onViewChanged(v -> {
            fleetView.setSelected(v.level() == HudView.Level.FLEET);
            servicesView.setSelected(v.level() == HudView.Level.SERVICES);
        });
        canvas.onContextMenu(this::contextMenu);
        fit.addActionListener(e -> {
            canvas.fit();
            canvas.requestFocusInWindow();
        });
        // Fit is a door only while there is somewhere to come back from
        canvas.onCameraChanged(() -> fit.setEnabled(canvas.camera().manual() || !canvas.camera().identity()));
        fit.setEnabled(false);
        getAccessibleContext().setAccessibleName(Bundle.CTL_OpsHubTopComponent());
        getAccessibleContext().setAccessibleDescription(Bundle.HINT_OpsHubTopComponent());
    }

    private JPanel toolbar() {
        // WrapLayout, not FlowLayout: nine controls do not fit a narrow dock, and a FlowLayout's
        // preferred height lies one row (ledger 75), clipping the second row off the edge
        JPanel bar = new JPanel(new org.nmox.studio.core.util.WrapLayout(FlowLayout.LEADING, 6, 4));
        bar.add(button(Bundle.OpsHub_newServer(), Bundle.OpsHub_newServerTooltip(), this::newServer));
        bar.add(button(Bundle.OpsHub_addHost(), Bundle.OpsHub_addHostTooltip(), this::addHost));
        bar.add(button(Bundle.OpsHub_refresh(), Bundle.OpsHub_refreshTooltip(), () -> {
            hub.refresh();
            hub.probeAll();
        }));
        bar.add(button(Bundle.OpsHub_token(), Bundle.OpsHub_tokenTooltip(), () -> OpsDialogs.tokens(() -> {
            syncChrome();
            hub.refresh();
        })));
        simulation.setToolTipText(PlainText.plain(Bundle.OpsHub_simulationTooltip()));
        simulation.getAccessibleContext().setAccessibleName(Bundle.OpsHub_simulation());
        simulation.addActionListener(e -> hub.setSimulation(simulation.isSelected()));
        bar.add(simulation);
        fleetView.setToolTipText(PlainText.plain(Bundle.OpsHub_viewTooltip()));
        servicesView.setToolTipText(PlainText.plain(Bundle.OpsHub_viewTooltip()));
        fleetView.getAccessibleContext().setAccessibleName(Bundle.OpsHub_viewFleet());
        servicesView.getAccessibleContext().setAccessibleName(Bundle.OpsHub_viewServices());
        javax.swing.ButtonGroup views = new javax.swing.ButtonGroup();
        views.add(fleetView);
        views.add(servicesView);
        fleetView.addActionListener(e -> canvas.setView(HudView.FLEET));
        servicesView.addActionListener(e -> canvas.setView(HudView.SERVICES));
        bar.add(fleetView);
        bar.add(servicesView);
        bar.add(fit);
        motion.setSelected(ReducedMotion.choice() != ReducedMotion.Choice.OFF);
        motion.setToolTipText(PlainText.plain(Bundle.OpsHub_motionTooltip()));
        motion.getAccessibleContext().setAccessibleName(Bundle.OpsHub_motion());
        motion.addActionListener(e -> {
            ReducedMotion.setChoice(motion.isSelected() ? ReducedMotion.Choice.AUTO : ReducedMotion.Choice.OFF);
            canvas.relayout();
        });
        bar.add(motion);
        providerChip.setFont(HudStyle.ui(11f, false));
        bar.add(providerChip);
        syncChrome();
        return bar;
    }

    private static JButton button(String text, String tooltip, Runnable action) {
        JButton b = new JButton(PlainText.plain(text));
        b.setToolTipText(PlainText.plain(tooltip));
        b.getAccessibleContext().setAccessibleName(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * The toolbar's facts. Whether a token is set is a KEYCHAIN read the
     * first time it is asked (the provider caches it after), so until
     * {@link #primeOffEdt} has asked once on a worker the chip says
     * nothing rather than ask on the event thread.
     */
    private void syncChrome() {
        simulation.setSelected(hub.simulating());
        if (!primed) {
            providerChip.setText("");
            return;
        }
        // every real provider says whether it has its token: with two of them, "DigitalOcean: no token"
        // alone would be true and still leave a Hetzner user wondering whether theirs was seen
        boolean anyLive = false;
        StringBuilder chip = new StringBuilder();
        for (OpsProvider p : hub.providers()) {
            if (p.simulated()) {
                continue;
            }
            boolean live = p.configured();
            anyLive |= live;
            chip.append(chip.length() == 0 ? "" : "  \u00b7  ")
                    .append(live ? Bundle.OpsHub_providerLive(p.displayName()) : Bundle.OpsHub_providerNoToken(p.displayName()));
        }
        providerChip.setText(PlainText.plain(chip.toString()));
        providerChip.setForeground(anyLive ? HudStyle.READY.darker() : javax.swing.UIManager.getColor("Label.disabledForeground"));
    }

    private volatile boolean primed;

    /** Asks the two slow questions once, on a worker: is there a token (the keychain), is mosh here (the PATH). */
    private void primeOffEdt(Runnable then) {
        DIALOG_PREP.post(() -> {
            for (OpsProvider p : hub.providers()) {
                p.configured();
            }
            hub.moshOnPath();
            primed = true;
            javax.swing.SwingUtilities.invokeLater(then);
        });
    }

    private void showSelected() {
        String id = canvas.selected();
        inspector.show(id == null ? null : hub.topology().node(id));
    }

    // ---- doors ----

    private void newServer() {
        List<OpsProvider> ready = new ArrayList<>();
        for (OpsProvider p : hub.providers()) {
            if (p.configured()) {
                ready.add(p);
            }
        }
        if (ready.isEmpty()) {
            OpsDialogs.message(Bundle.OpsHub_noProvider());
            return;
        }
        OpsProvider provider = ready.get(0);
        if (ready.size() > 1) {
            Object[] names = ready.stream().map(OpsProvider::displayName).toArray();
            Object picked = org.openide.DialogDisplayer.getDefault().notify(new org.openide.NotifyDescriptor(
                    org.nmox.studio.core.util.PlainDialogs.plain(Bundle.OpsHub_pickProvider(), Bundle.OpsHub_newServer()),
                    Bundle.OpsHub_newServer(), org.openide.NotifyDescriptor.DEFAULT_OPTION,
                    org.openide.NotifyDescriptor.QUESTION_MESSAGE, names, names[0]));
            int at = java.util.Arrays.asList(names).indexOf(picked);
            if (at < 0) {
                return;
            }
            provider = ready.get(at);
        }
        OpsProvider chosen = provider;
        StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.OpsHub_readingOptions(chosen.displayName())));
        DIALOG_PREP.post(() -> {
            ProviderOptions options;
            try {
                options = chosen.options();
            } catch (java.io.IOException | RuntimeException ex) {
                javax.swing.SwingUtilities.invokeLater(() -> OpsDialogs.message(Bundle.OpsHub_optionsFailed(ex.getMessage())));
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            String[] local = chosen.simulated() ? null : OpsDialogs.localPublicKey();
            javax.swing.SwingUtilities.invokeLater(() -> {
                OpsDialogs.NewServer answer = OpsDialogs.newServer(chosen, options, local == null ? null : local[1]);
                if (answer == null) {
                    return;
                }
                if (answer.addLocalKey() && local != null) {
                    createWithLocalKey(chosen, answer.spec(), local);
                } else {
                    hub.createHost(chosen, answer.spec());
                }
            });
        });
    }

    /**
     * The user said yes to the price AND asked for this machine's key on
     * the account: add the key, then create with it. Both are the
     * provider's network, so both happen off the event thread (the first
     * cut waited for the upload ON it, inside the dialog, and uploaded
     * before the price was confirmed — a No left a key behind).
     */
    private void createWithLocalKey(OpsProvider provider, org.nmox.studio.infra.ops.provider.HostSpec spec, String[] local) {
        DIALOG_PREP.post(() -> {
            String id;
            try {
                id = provider.addSshKey("NMOX Studio " + local[1], local[0]);
            } catch (java.io.IOException | RuntimeException ex) {
                javax.swing.SwingUtilities.invokeLater(() -> OpsDialogs.message(Bundle.OpsHub_keyFailed(String.valueOf(ex.getMessage()))));
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            List<String> keys = new ArrayList<>(spec.sshKeyIds());
            if (id != null) {
                keys.add(id);
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.OpsHub_keyAdded()));
                hub.createHost(provider, new org.nmox.studio.infra.ops.provider.HostSpec(spec.name(), spec.region(),
                        spec.size(), spec.image(), keys, spec.userData()));
            });
        });
    }

    private void addHost() {
        OpsDialogs.AddHost answer = OpsDialogs.addHost();
        if (answer != null) {
            OpsNode node = hub.addManualHost(answer.target(), answer.label());
            canvas.select(node.id);
        }
    }

    private void contextMenu(String id, Point2D at) {
        OpsNode node = hub.topology().node(id);
        if (node == null) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        if (node.kind == OpsKind.HOST) {
            JMenuItem mosh = item(menu, Bundle.OpsHub_menuConnectMosh(), () -> connect(node, true));
            mosh.setEnabled(node.address != null && !node.simulated && hub.moshOnPath());
            JMenuItem ssh = item(menu, Bundle.OpsHub_menuConnectSsh(), () -> connect(node, false));
            ssh.setEnabled(node.address != null && !node.simulated);
            item(menu, Bundle.OpsHub_menuOpen(), () -> canvas.setView(HudView.host(node.id)));
            item(menu, Bundle.OpsHub_menuDeploy(), () -> deploy(node)).setEnabled(node.status.reachable());
            item(menu, Bundle.OpsHub_menuProbe(), () -> hub.probe(node)).setEnabled(node.address != null);
            if (node.providerId != null) {
                item(menu, Bundle.OpsHub_menuDestroy(), () -> destroy(node));
            }
            if ("manual".equals(node.provider)) {
                item(menu, Bundle.OpsHub_menuForget(), () -> hub.forgetManualHost(node));
            }
        } else if (node.kind == OpsKind.SERVICE) {
            Integer port = InspectorPanel.firstPort(node);
            if (port != null) {
                item(menu, Bundle.Inspector_open(), () -> openInBrowser(node, port)).setEnabled(!node.simulated);
            }
            item(menu, Bundle.OpsHub_menuLogs(), () -> serviceDoor(node, false)).setEnabled(!node.simulated);
            item(menu, Bundle.OpsHub_menuShell(), () -> serviceDoor(node, true)).setEnabled(!node.simulated);
            if (node.app != null) {
                item(menu, Bundle.OpsHub_menuRollback(), () -> rollback(node));
            }
            if (node.hostId != null) {
                item(menu, Bundle.OpsHub_menuOpen(), () -> canvas.setView(HudView.host(node.hostId)));
            }
        }
        if (menu.getComponentCount() > 0) {
            menu.show(canvas, (int) at.getX(), (int) at.getY());
        }
    }

    private static JMenuItem item(JPopupMenu menu, String text, Runnable action) {
        JMenuItem i = new JMenuItem(PlainText.plain(text));
        i.addActionListener(e -> action.run());
        menu.add(i);
        return i;
    }

    /** Opens the host's terminal: mosh when asked and possible, else ssh; says which. */
    private void connect(OpsNode node, boolean wantMosh) {
        SshTarget target = hub.targetOf(node);
        if (target == null || node.simulated) {
            return;
        }
        boolean mosh = wantMosh && hub.moshOnPath();
        if (wantMosh && !mosh) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.OpsHub_noMosh()));
        }
        String how = mosh ? "mosh" : "ssh";
        String title = Bundle.TerminalDoor_tabTitle(node.label, how);
        List<String> argv = mosh ? target.interactiveMosh(hub.knownHosts()) : target.interactiveSsh(hub.knownHosts());
        TerminalDoor.open(title, argv, System.getProperty("user.home"), outcome -> {
            String status = switch (outcome) {
                case OPENED -> Bundle.OpsHub_opened(title);
                case COPIED_FALLBACK -> Bundle.OpsHub_copied();
                case NO_TOOL -> mosh ? Bundle.OpsHub_noMosh() : Bundle.OpsHub_noSsh();
            };
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(status));
            if (outcome == TerminalDoor.Outcome.NO_TOOL && mosh) {
                connect(node, false);
            }
        });
    }

    /**
     * A service's two terminal doors: follow its logs, or a shell inside
     * it. The container's name is the host's word, so it is checked
     * against docker's own rule before it is put in a remote line, and a
     * name that fails is refused by name.
     */
    private void serviceDoor(OpsNode service, boolean shell) {
        OpsNode host = service.hostId == null ? null : hub.topology().node(service.hostId);
        SshTarget target = host == null ? null : hub.targetOf(host);
        if (target == null || service.simulated) {
            if (!service.simulated) {
                OpsDialogs.message(Bundle.OpsHub_hostGone());
            }
            return;
        }
        String container = service.facts.get("container");
        if (!org.nmox.studio.infra.ops.remote.ContainerDoors.nameIsSafe(container)) {
            OpsDialogs.message(Bundle.OpsHub_badContainerName(String.valueOf(container)));
            return;
        }
        String remote = shell ? org.nmox.studio.infra.ops.remote.ContainerDoors.shell(container)
                : org.nmox.studio.infra.ops.remote.ContainerDoors.logs(container);
        String title = shell ? Bundle.TerminalDoor_shellTitle(service.label) : Bundle.TerminalDoor_logsTitle(service.label);
        TerminalDoor.open(title, target.interactiveSsh(hub.knownHosts(), remote), System.getProperty("user.home"), outcome -> {
            String status = switch (outcome) {
                case OPENED -> Bundle.OpsHub_opened(title);
                case COPIED_FALLBACK -> Bundle.OpsHub_copied();
                case NO_TOOL -> Bundle.OpsHub_noSsh();
            };
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(status));
        });
    }

    /** Asks the host what it still holds, shows what going back would run, and runs it on a yes. */
    private void rollback(OpsNode service) {
        hub.rollbackOffer(service, offer -> {
            if (!offer.ok()) {
                OpsDialogs.message(offer.refusal());
                return;
            }
            // the answer came back from the host: the service or its host may be gone by now
            OpsNode host = service.hostId == null ? null : hub.topology().node(service.hostId);
            if (host == null || hub.topology().node(service.id) == null) {
                OpsDialogs.message(Bundle.OpsHub_hostGone());
                return;
            }
            if (OpsDialogs.confirmRollback(host, offer.plan(), offer.fromStamp())) {
                hub.rollback(host, offer.plan(), offer.fromStamp());
            }
        });
    }

    private void deploy(OpsNode node) {
        ProjectAim aim = ProjectAim.find();
        File project = aim == null ? null : aim.projectDir();
        if (project == null) {
            OpsDialogs.message(Bundle.OpsHub_noProject());
            return;
        }
        String refusal = hub.deployRefusal(node, project);
        if (refusal != null) {
            OpsDialogs.message(refusal);
            return;
        }
        DeployPlan plan = OpsDialogs.deploy(node, project);
        if (plan != null) {
            hub.deploy(node, project, plan);
            canvas.select(node.id);
        }
    }

    private void destroy(OpsNode node) {
        OpsProvider p = hub.provider(node.provider);
        if (p != null && OpsDialogs.confirmDestroy(node, p.displayName())) {
            hub.destroyHost(node);
        }
    }

    private void openInBrowser(OpsNode service, int port) {
        OpsNode host = service.hostId == null ? null : hub.topology().node(service.hostId);
        if (host == null || host.address == null) {
            return;
        }
        String url = "http://" + host.address + (port == 80 ? "" : ":" + port) + "/";
        org.nmox.studio.core.spi.EmbeddedBrowser browser = org.nmox.studio.core.spi.EmbeddedBrowser.find();
        if (browser != null && browser.open(url)) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.OpsHub_openedUrl(url)));
            return;
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(url), null);
        } catch (IllegalStateException busy) {
            // nothing to copy to; the status still names the address
        }
        StatusDisplayer.getDefault().setStatusText(PlainStatus.text(Bundle.OpsHub_noBrowser() + " — " + url));
    }

    private final class InspectorActions implements InspectorPanel.Actions {
        @Override
        public void connect(OpsNode host, boolean mosh) {
            OpsHubTopComponent.this.connect(host, mosh);
        }

        @Override
        public void deploy(OpsNode host) {
            OpsHubTopComponent.this.deploy(host);
        }

        @Override
        public void openInBrowser(OpsNode node, int port) {
            OpsHubTopComponent.this.openInBrowser(node, port);
        }

        @Override
        public void destroy(OpsNode host) {
            OpsHubTopComponent.this.destroy(host);
        }

        @Override
        public void forget(OpsNode host) {
            hub.forgetManualHost(host);
        }

        @Override
        public void logs(OpsNode service) {
            serviceDoor(service, false);
        }

        @Override
        public void shell(OpsNode service) {
            serviceDoor(service, true);
        }

        @Override
        public void rollback(OpsNode service) {
            OpsHubTopComponent.this.rollback(service);
        }
    }

    /** Opens (or fronts) the Hub on one node — Quick Search's landing. */
    public static void focusNode(String id) {
        TopComponent tc = org.openide.windows.WindowManager.getDefault().findTopComponent("OpsHubTopComponent");
        if (tc instanceof OpsHubTopComponent hubWindow) {
            hubWindow.open();
            hubWindow.requestActive();
            if (hubWindow.canvas.show(id)) {
                hubWindow.canvas.requestFocusInWindow();
            }
        }
    }

    // ---- lifecycle ----

    @Override
    protected void componentOpened() {
        hub.addListener(hubListener);
        hub.topology().addListener(topologyListener);
        ReducedMotion.readSystemSettingAsync();
        syncChrome();
        primeOffEdt(() -> {
            syncChrome();
            showSelected();
            hub.refresh();
        });
    }

    @Override
    protected void componentShowing() {
        hub.startPolling();
    }

    @Override
    protected void componentHidden() {
        hub.stopPolling();
    }

    @Override
    protected void componentClosed() {
        hub.stopPolling();
        hub.removeListener(hubListener);
        hub.topology().removeListener(topologyListener);
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
    }
}
