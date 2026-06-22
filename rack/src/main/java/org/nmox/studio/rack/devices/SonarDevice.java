package org.nmox.studio.rack.devices;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.nmox.studio.rack.docker.DockerClient;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.engine.PortScanner;
import org.nmox.studio.rack.engine.PortScanner.PortInfo;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * SONAR Port Radar: who is listening on localhost, with one-click
 * resolution of the daily EADDRINUSE fight. The sweep maps each port
 * to its owning process - and to its docker container when that's the
 * real owner - and VIEW opens the field with BROWSE and KILL per row.
 */
public class SonarDevice extends RackDevice {

    private static final String[] POLL_RATES = {"off", "10s", "30s", "60s"};
    private static final int[] POLL_MS = {0, 10_000, 30_000, 60_000};

    private final LcdDisplay countLcd;
    private final LcdDisplay fieldLcd;
    private final Led pingLed;
    private final Knob pollKnob;
    private final javax.swing.Timer poller;
    private final javax.swing.Timer pingFade = new javax.swing.Timer(400, e -> pingOff());
    private volatile List<PortInfo> lastScan = List.of();
    private volatile Map<Integer, String> dockerOwners = Map.of();

    public SonarDevice() {
        super("sonar", "SONAR", "PORT RADAR", new Color(80, 220, 190), 2);

        RackButton sweep = place(new RackButton("SWEEP", RackStyle.QUERY), RackStyle.TRANSPORT_X, 52);
        RackButton view = place(new RackButton("VIEW", RackStyle.QUERY), RackStyle.TRANSPORT_STOP_X, 52);
        pollKnob = place(new Knob("POLL", POLL_RATES, 2), 184, 40);
        pingLed = place(new Led("PING", new Color(80, 220, 190)), 256, 58);

        countLcd = place(new LcdDisplay(330, 1), 320, 34);
        fieldLcd = place(new LcdDisplay(330, 1), 320, 66);
        countLcd.setText("SWEEP TO SCAN");
        fieldLcd.setText("—");
        countLcd.setToolTipText("listening TCP ports on this machine");
        fieldLcd.setToolTipText("the low band of the field — dev servers live here");

        sweep.setToolTipText("Scan localhost for listening ports now");
        view.setToolTipText("The full field: every port, its owner, BROWSE and KILL per row");
        sweep.addActionListener(e -> sweep());
        view.addActionListener(e -> showField());
        pollKnob.addChangeListener(this::restartPoller);

        param("poll", pollKnob);

        addInPort("run", "RUN", SignalType.TRIGGER);
        addOutPort("out", "OUT", SignalType.DATA);

        poller = new javax.swing.Timer(POLL_MS[2], e -> sweep());
        poller.setRepeats(true);
        pingFade.setRepeats(false);
    }

    private void pingOff() {
        pingLed.setOn(false);
    }

    // ---- sweeping ----

    private void sweep() {
        PortScanner.scan().thenAccept(ports -> {
            lastScan = ports;
            onEdt(() -> {
                pingLed.setOn(true);
                pingFade.restart();
                countLcd.setText(ports.size() + " PORTS LISTENING");
                StringBuilder low = new StringBuilder();
                for (PortInfo p : ports) {
                    if (p.port() >= 3000 && p.port() <= 9999 && low.length() < 30) {
                        if (low.length() > 0) {
                            low.append(" ");
                        }
                        low.append(p.port());
                    }
                }
                fieldLcd.setText(low.length() == 0 ? "DEV BAND QUIET" : "DEV: " + low);
            });
            emit("out", org.nmox.studio.rack.model.Signal.data(listeningPorts(ports)));
        });
        // docker's published ports belong to containers, not the daemon pid
        DockerClient.getDefault().containers().thenAccept(cs -> {
            Map<Integer, String> owners = new HashMap<>();
            for (DockerClient.ContainerInfo c : cs) {
                for (Integer port : c.hostPorts()) {
                    owners.put(port, c.name());
                }
            }
            dockerOwners = owners;
        });
    }

    /**
     * The sweep result as a machine-usable payload: the listening port
     * numbers, ascending and de-duplicated, comma-separated - so a wired
     * downstream device can act on the field instead of parsing prose.
     */
    static String listeningPorts(List<PortInfo> ports) {
        return ports.stream()
                .map(PortInfo::port)
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

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
        sweep();
        restartPoller();
    }

    @Override
    public void dispose() {
        poller.stop();
        pingFade.stop();
        super.dispose();
    }

    @Override
    public void receive(org.nmox.studio.rack.model.Port in,
            org.nmox.studio.rack.model.Signal signal) {
        if ("run".equals(in.getId())) {
            sweep();
        }
    }

    // ---- the field viewer ----

    private void showField() {
        sweep();
        JDialog dialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "SONAR — the port field", false);
        dialog.setLayout(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"PORT", "OWNER", "PID", "VIA"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        table.setRowHeight(22);

        Runnable fill = () -> {
            model.setRowCount(0);
            for (PortInfo p : lastScan) {
                String docker = dockerOwners.get(p.port());
                model.addRow(new Object[]{p.port(), p.command(), p.pid(),
                    docker != null ? "docker: " + docker : ""});
            }
        };
        fill.run();

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton rescan = new JButton("Sweep again");
        rescan.addActionListener(e -> {
            sweep();
            new javax.swing.Timer(900, ev -> {
                fill.run();
                ((javax.swing.Timer) ev.getSource()).stop();
            }) {
                {
                    setRepeats(false);
                    start();
                }
            };
        });
        south.add(rescan);
        JButton browse = new JButton("Browse");
        browse.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                try {
                    java.awt.Desktop.getDesktop().browse(
                            java.net.URI.create("http://localhost:" + model.getValueAt(row, 0)));
                } catch (Exception ignored) {
                }
            }
        });
        south.add(browse);
        JButton kill = new JButton("Kill owner");
        kill.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            Object via = model.getValueAt(row, 3);
            if (via != null && via.toString().startsWith("docker:")) {
                JOptionPane.showMessageDialog(dialog,
                        "Port " + model.getValueAt(row, 0) + " belongs to container "
                        + via.toString().substring(8).trim()
                        + " — stop it from HARBOR's Docker Panel instead of killing the daemon.",
                        "SONAR", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            long pid = Long.parseLong(String.valueOf(model.getValueAt(row, 2)));
            String owner = String.valueOf(model.getValueAt(row, 1));
            if (JOptionPane.showConfirmDialog(dialog,
                    "Kill " + owner + " (pid " + pid + ") holding port "
                    + model.getValueAt(row, 0) + "?", "SONAR",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                    == JOptionPane.YES_OPTION) {
                PortScanner.kill(pid);
                rescan.doClick();
            }
        });
        south.add(kill);

        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(640, 480);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
