package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import org.nmox.studio.rack.docker.DockerClient;
import org.nmox.studio.rack.docker.DockerPanelTopComponent;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * HARBOR Docker Engine: the rack-mounted face of the container runtime.
 * The ENGINE LED tracks the daemon, the LCDs carry the three numbers a
 * developer actually wants at a glance (containers up, images held,
 * disk reclaimable), and PANEL opens the full control room - the
 * Docker Panel - where the real power lives.
 */
public class DockerDevice extends RackDevice {

    private static final String[] POLL_RATES = {"off", "10s", "30s", "60s"};
    private static final int[] POLL_MS = {0, 10_000, 30_000, 60_000};

    private final Led engineLed;
    private final LcdDisplay containersLcd;
    private final LcdDisplay imagesLcd;
    private final LcdDisplay reclaimLcd;
    private final Knob pollKnob;
    private final javax.swing.Timer poller;
    private volatile boolean engineUp;

    public DockerDevice() {
        super("docker", "HARBOR", "DOCKER ENGINE", new Color(36, 150, 237), 2);

        RackButton panel = place(new RackButton("PANEL", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        RackButton refresh = place(new RackButton("RFRSH", RackStyle.QUERY), RackStyle.TRANSPORT_STOP_X, 52);
        RackButton prune = place(new RackButton("PRUNE", RackStyle.MUTATE), 180, 52);
        pollKnob = place(new Knob("POLL", POLL_RATES, 2), 254, 40);
        engineLed = place(new Led("ENGINE", new Color(36, 150, 237)), 326, 58);

        int lcdX = 396;
        containersLcd = place(new LcdDisplay(150, 1), lcdX, 34);
        imagesLcd = place(new LcdDisplay(150, 1), lcdX, 62);
        reclaimLcd = place(new LcdDisplay(150, 1), lcdX, 90);
        containersLcd.setText("ENGINE?");
        imagesLcd.setText("—");
        reclaimLcd.setText("—");
        containersLcd.setToolTipText("running / total containers");
        imagesLcd.setToolTipText("images held and their disk size");
        reclaimLcd.setToolTipText("disk you could get back with PRUNE / the panel");

        panel.setToolTipText("Open the Docker Panel — containers, images, volumes, networks, dockerize");
        prune.setToolTipText("Safe prune: dangling images + build cache (never touches tagged images or volumes)");
        refresh.setToolTipText("Re-read engine status now");

        panel.addActionListener(e -> openPanel());
        refresh.addActionListener(e -> refresh());
        prune.addActionListener(e -> safePrune());
        pollKnob.addChangeListener(this::restartPoller);

        param("poll", pollKnob);

        addInPort("run", "RUN", SignalType.TRIGGER);
        addOutPort("running", "RUNNING", SignalType.GATE);
        addOutPort("out", "OUT", SignalType.DATA);

        poller = new javax.swing.Timer(POLL_MS[2], e -> refresh());
        poller.setRepeats(true);
    }

    // ---- actions ----

    private void openPanel() {
        DockerPanelTopComponent.openPanel();
    }

    /** Reclaims only what is always safe: dangling images + build cache. */
    private void safePrune() {
        onEdt(() -> reclaimLcd.setText("PRUNING…"));
        DockerClient.getDefault().prune("image", false)
                .thenCompose(r -> DockerClient.getDefault().prune("builder", false))
                .whenComplete((r, ex) -> refresh());
    }

    private void refresh() {
        DockerClient client = DockerClient.getDefault();
        client.engineVersion().thenAccept(version -> {
            boolean up = version != null;
            boolean changed = up != engineUp;
            engineUp = up;
            onEdt(() -> {
                engineLed.setOn(up);
                if (!up) {
                    containersLcd.setTextColor(RackStyle.LCD_AMBER);
                    containersLcd.setText("ENGINE DOWN — START DOCKER");
                    imagesLcd.setText("—");
                    reclaimLcd.setText("—");
                }
            });
            if (changed) {
                emit("running", Signal.gate(up));
            }
            if (!up) {
                return;
            }
            client.containers().thenAccept(cs -> {
                long running = cs.stream().filter(DockerClient.ContainerInfo::running).count();
                onEdt(() -> {
                    containersLcd.setTextColor(RackStyle.LCD_TEXT);
                    containersLcd.setText(running + "/" + cs.size() + " CONTAINERS UP");
                });
                emit("out", Signal.data("docker: " + running + "/" + cs.size() + " containers up"));
            });
            client.systemDf().thenAccept(rows -> {
                String images = "—";
                String reclaim = "—";
                for (DockerClient.DfRow row : rows) {
                    if ("Images".equalsIgnoreCase(row.type())) {
                        images = row.totalCount() + " IMAGES · " + row.size();
                    }
                }
                String total = totalReclaimable(rows);
                if (!total.isEmpty()) {
                    reclaim = total + " RECLAIMABLE";
                }
                final String i = images;
                final String r = reclaim;
                onEdt(() -> {
                    imagesLcd.setText(i);
                    reclaimLcd.setText(r);
                });
            });
        });
    }

    /** Sums the human-readable reclaimable column well enough to glance at. */
    public static String totalReclaimable(List<DockerClient.DfRow> rows) {
        double gb = 0;
        for (DockerClient.DfRow row : rows) {
            String r = row.reclaimable();
            if (r == null || r.isEmpty()) {
                continue;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("([\\d.]+)\\s*(kB|KB|MB|GB|TB|B)").matcher(r);
            if (m.find()) {
                double v = Double.parseDouble(m.group(1));
                gb += switch (m.group(2)) {
                    case "TB" -> v * 1024;
                    case "GB" -> v;
                    case "MB" -> v / 1024;
                    case "kB", "KB" -> v / (1024 * 1024);
                    default -> 0;
                };
            }
        }
        if (gb <= 0) {
            return "";
        }
        return gb >= 1 ? String.format("%.1fGB", gb) : String.format("%.0fMB", gb * 1024);
    }

    // ---- polling lifecycle ----

    private void restartPoller() {
        int ms = POLL_MS[pollKnob.getSelectedIndex()];
        poller.stop();
        if (ms > 0) {
            poller.setDelay(ms);
            poller.setInitialDelay(ms);
            poller.start();
        }
    }

    @Override
    protected void onAttached() {
        refresh();
        restartPoller();
    }

    @Override
    public void dispose() {
        poller.stop();
        super.dispose();
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("run".equals(in.getId())) {
            refresh();
        }
    }
}
