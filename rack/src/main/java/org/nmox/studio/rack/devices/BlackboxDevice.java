package org.nmox.studio.rack.devices;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.engine.FlightRecorder;
import org.nmox.studio.rack.engine.FlightRecorder.Event;
import org.nmox.studio.rack.engine.FlightRecorder.Kind;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * BLACKBOX Flight Recorder: the rack's session memory. Every launch,
 * exit, duration, and error any device produces is on the tape; the
 * faceplate answers "what just happened" and VIEW scrolls the whole
 * timeline. The creep line is the part no other tool does: it knows
 * what your build USUALLY takes, and says so when it quietly doubles.
 */
public class BlackboxDevice extends RackDevice {

    private final LcdDisplay lastLcd;
    private final LcdDisplay healthLcd;
    private final Led recLed;
    private final javax.swing.Timer recFade = new javax.swing.Timer(350, e -> recOff());
    private final Runnable recorderListener = this::onRecorderChange;

    public BlackboxDevice() {
        super("blackbox", "BLACKBOX", "FLIGHT RECORDER", new Color(255, 122, 36), 2);

        RackButton view = place(new RackButton("VIEW", RackStyle.QUERY), RackStyle.TRANSPORT_X, 52);
        recLed = place(new Led("REC", new Color(255, 80, 60)), 116, 58);

        lastLcd = place(new LcdDisplay(420, 1), 180, 34);
        healthLcd = place(new LcdDisplay(420, 1), 180, 66);
        lastLcd.setText("RECORDING — RUN SOMETHING");
        healthLcd.setText("NO ERRORS ON TAPE");
        lastLcd.setToolTipText("the last completed run on the tape");
        healthLcd.setToolTipText("errors in the last 10 minutes, and any device running slower than its average");

        view.setToolTipText("Scroll the session timeline — every launch, exit, duration, and error");
        view.addActionListener(e -> showTimeline());

        addOutPort("out", "OUT", SignalType.DATA);

        recFade.setRepeats(false);
    }

    private void recOff() {
        recLed.setOn(false);
    }

    @Override
    protected void onAttached() {
        FlightRecorder.getDefault().addChangeListener(recorderListener);
        refreshFaceplate();
    }

    @Override
    public void dispose() {
        FlightRecorder.getDefault().removeChangeListener(recorderListener);
        recFade.stop();
        super.dispose();
    }

    private void onRecorderChange() {
        onEdt(() -> {
            recLed.setOn(true);
            recFade.restart();
            refreshFaceplate();
        });
    }

    private void refreshFaceplate() {
        FlightRecorder rec = FlightRecorder.getDefault();
        Event last = rec.last();
        if (last != null) {
            String dur = last.durationMs() >= 0 ? "  " + (last.durationMs() / 1000.0) + "s" : "";
            boolean ok = last.kind() == Kind.EXIT_OK;
            lastLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
            lastLcd.setText("LAST: " + last.device() + " " + (ok ? "OK" : last.text().toUpperCase()) + dur);
        }
        int errors = rec.errorsSince(System.currentTimeMillis() - 600_000).size();
        String creep = rec.slowCreep();
        if (creep != null) {
            FlightRecorder.Stats s = rec.statistics().get(creep);
            healthLcd.setTextColor(RackStyle.LCD_AMBER);
            healthLcd.setText(creep + " SLOWING: " + (s.lastMs() / 1000.0) + "s VS "
                    + (s.averageMs() / 1000.0) + "s AVG");
        } else if (errors > 0) {
            healthLcd.setTextColor(new Color(255, 90, 80));
            healthLcd.setText(errors + " ERROR" + (errors == 1 ? "" : "S") + " / 10 MIN — VIEW FOR THE TAPE");
        } else {
            healthLcd.setTextColor(RackStyle.LCD_TEXT);
            healthLcd.setText("NO ERRORS ON TAPE");
        }
    }

    // ---- the timeline viewer ----

    private void showTimeline() {
        JDialog dialog = new JDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(this),
                "BLACKBOX — session timeline", false);
        dialog.setLayout(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"TIME", "DEVICE", "EVENT", "DETAIL", "DURATION"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        table.setRowHeight(22);
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                java.awt.Component c = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                String kind = String.valueOf(t.getValueAt(row, 2));
                if (!sel) {
                    c.setForeground(switch (kind) {
                        case "EXIT_OK" -> new Color(80, 200, 110);
                        case "EXIT_FAIL", "ERROR" -> new Color(230, 80, 70);
                        case "LAUNCH" -> new Color(110, 160, 230);
                        default -> t.getForeground();
                    });
                }
                return c;
            }
        });

        JCheckBox errorsOnly = new JCheckBox("errors only");
        Runnable fill = () -> {
            model.setRowCount(0);
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss");
            List<Event> events = FlightRecorder.getDefault().timeline();
            for (Event e : events) {
                if (errorsOnly.isSelected()
                        && e.kind() != Kind.ERROR && e.kind() != Kind.EXIT_FAIL) {
                    continue;
                }
                model.addRow(new Object[]{
                    fmt.format(new Date(e.at())), e.device(), e.kind().name(),
                    e.text(), e.durationMs() >= 0 ? (e.durationMs() / 1000.0) + "s" : ""});
            }
            if (model.getRowCount() > 0) {
                table.scrollRectToVisible(table.getCellRect(model.getRowCount() - 1, 0, true));
            }
        };
        errorsOnly.addActionListener(e -> fill.run());
        fill.run();

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(errorsOnly);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> fill.run());
        south.add(refresh);
        JButton export = new JButton("Export to project");
        export.addActionListener(e -> {
            try {
                java.io.File dir = new java.io.File(projectDir(), ".nmox");
                java.nio.file.Files.createDirectories(dir.toPath());
                java.io.File log = new java.io.File(dir, "flight-log.txt");
                java.nio.file.Files.writeString(log.toPath(), FlightRecorder.getDefault().export());
                south.add(new JLabel("wrote " + log.getName()));
                south.revalidate();
            } catch (Exception ex) {
                javax.swing.JOptionPane.showMessageDialog(dialog, ex.getMessage());
            }
        });
        south.add(export);
        JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT));
        for (Map.Entry<String, FlightRecorder.Stats> e
                : FlightRecorder.getDefault().statistics().entrySet()) {
            JLabel l = new JLabel(e.getKey() + " avg " + (e.getValue().averageMs() / 1000.0)
                    + "s · last " + (e.getValue().lastMs() / 1000.0) + "s"
                    + (e.getValue().creeping() ? " ▲" : "") + "   ");
            if (e.getValue().creeping()) {
                l.setForeground(new Color(230, 150, 40));
            }
            stats.add(l);
        }

        dialog.add(stats, BorderLayout.NORTH);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(900, 560);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
