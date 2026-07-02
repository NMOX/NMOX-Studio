package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * TAIL: tail -f as a rack unit. Servers that write logs/app.log were
 * invisible to the rack - process stdout is covered, files were not.
 * Dial a path (relative to the project), flip FOLLOW, and every
 * appended line rides the OUT jack into PHOSPHOR or MONITOR.
 * Truncation (log rotation) resets cleanly to the new head.
 */
public class TailDevice extends RackDevice {

    private final LcdDisplay pathLcd;
    private final ToggleSwitch followSwitch;
    private final Led eyeLed;
    private final VuMeter meter = new VuMeter("FEED", false);
    private final javax.swing.Timer poll = new javax.swing.Timer(1_000, e -> tick());
    private long position = -1;
    private final StringBuilder partial = new StringBuilder();

    public TailDevice() {
        super("tail", "TAIL", "LOG FOLLOWER", new Color(140, 190, 120), 2);

        pathLcd = place(new LcdDisplay(300, 1), 44, 46);
        pathLcd.setText("logs/app.log");
        pathLcd.setEditable("File to follow (relative to project)");
        followSwitch = place(new ToggleSwitch("FOLLOW", false), 366, 42);
        eyeLed = place(new Led("EYE", new Color(140, 190, 120)), 452, 52);
        place(meter, 500, 40);

        followSwitch.addChangeListener(this::sync);
        pathLcd.addEditListener(() -> {
            position = -1; // new target: start from its current end
            partial.setLength(0);
        });

        addOutPort("out", "OUT", SignalType.DATA);

        param("path", pathLcd);
        param("follow", followSwitch);
    }

    private void sync() {
        if (followSwitch.isOn()) {
            position = -1;
            poll.start();
        } else {
            poll.stop();
        }
        eyeLed.setOn(followSwitch.isOn());
    }

    private File target() {
        File f = new File(pathLcd.getText().trim());
        return f.isAbsolute() ? f : new File(projectDir(), f.getPath());
    }

    private void tick() {
        File file = target();
        if (!file.isFile()) {
            return; // not there (yet); keep watching quietly
        }
        long length = file.length();
        if (position < 0 || length < position) {
            position = position < 0 ? length : 0; // first sight: end; rotation: head
            partial.setLength(0);
        }
        if (length == position) {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(position);
            long budget = Math.min(length - position, 256 * 1024); // burst cap per tick
            byte[] chunk = new byte[(int) budget];
            int read = raf.read(chunk);
            if (read <= 0) {
                return;
            }
            position += read;
            partial.append(new String(chunk, 0, read, StandardCharsets.UTF_8));
            int nl;
            while ((nl = partial.indexOf("\n")) >= 0) {
                String line = partial.substring(0, nl).replace("\r", "");
                partial.delete(0, nl + 1);
                if (!line.isEmpty()) {
                    meter.pulse(0.5);
                    emit("out", Signal.data(line));
                }
            }
        } catch (IOException ex) {
            // unreadable this tick; try again on the next
        }
    }

    @Override
    public void projectChanged(File dir) {
        position = -1;
        partial.setLength(0);
    }

    @Override
    public void applyState(java.util.Map<String, String> state) {
        super.applyState(state);
        sync();
    }

    @Override
    public void dispose() {
        poll.stop();
        super.dispose();
    }
}
