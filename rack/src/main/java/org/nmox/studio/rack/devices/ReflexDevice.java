package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.nmox.studio.rack.engine.FileWatcher;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * REFLEX File Watcher: the automation keystone. Arm it and every save
 * in the project fires the CHANGED trigger - patch it into VERITAS for
 * a TDD loop, into PURITY for lint-on-save, or into FORGE for rebuilds
 * with bundlers that have no watch mode of their own.
 */
public class ReflexDevice extends RackDevice {

    private static final String[] FILTERS = {"all", "code", "styles", "docs"};
    private static final List<Set<String>> FILTER_EXTENSIONS = List.of(
            Set.of(),  // unused for "all"
            Set.of("js", "jsx", "ts", "tsx", "mjs", "cjs", "vue", "svelte", "json",
                    "py", "rb", "rs", "go", "java", "c", "h", "cpp", "hpp", "hh", "php", "sh",
                    "ex", "exs", "erl", "hrl", "clj", "cljs", "cljc", "lisp", "cl",
                    "lua", "swift", "kt", "kts"),
            Set.of("css", "scss", "sass", "less", "styl"),
            Set.of("html", "htm", "md", "mdx", "xml", "svg"));

    private final ToggleSwitch armSwitch;
    private final Knob filterKnob;
    private final LcdDisplay lastChangeLcd;
    private final Led eyeLed;
    private final VuMeter meter;
    private FileWatcher watcher;

    public ReflexDevice() {
        super("reflex", "REFLEX", "FILE WATCHER", new Color(236, 106, 168), 2);

        armSwitch = place(new ToggleSwitch("WATCH", false), 44, 42);
        filterKnob = place(new Knob("FILTER", FILTERS, 0), 118, 40);
        lastChangeLcd = place(new LcdDisplay(300, 1), 196, 46);
        lastChangeLcd.setText("DISARMED");
        eyeLed = place(new Led("EYE", new Color(236, 106, 168)), 510, 52);
        meter = place(new VuMeter("CHANGES", false), 560, 46);

        armSwitch.addChangeListener(this::restartWatcher);
        filterKnob.addChangeListener(this::restartWatcher);

        addOutPort("changed", "CHANGED", SignalType.TRIGGER);
        addOutPort("path", "PATH", SignalType.DATA);

        param("armed", armSwitch);
        param("filter", filterKnob);
    }

    @Override
    protected void onAttached() {
        restartWatcher();
    }

    @Override
    public void projectChanged(File dir) {
        restartWatcher();
    }

    private synchronized void restartWatcher() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
        boolean armed = armSwitch.isOn();
        onEdt(() -> {
            eyeLed.setBlinking(armed);
            lastChangeLcd.setText(armed ? "WATCHING " + projectDir().getName() : "DISARMED");
        });
        if (!armed || getRack() == null) {
            return;
        }
        int filter = filterKnob.getSelectedIndex();
        Set<String> extensions = filter == 0 ? null : FILTER_EXTENSIONS.get(filter);
        int interval = org.openide.util.NbPreferences.forModule(ReflexDevice.class)
                .getInt("reflexIntervalMs", 1200);
        watcher = new FileWatcher(projectDir(), interval, extensions, this::filesChanged);
        watcher.start();
    }

    private void filesChanged(List<Path> changed) {
        Path first = changed.get(0);
        Path root = projectDir().toPath();
        String shown;
        try {
            shown = root.relativize(first).toString();
        } catch (IllegalArgumentException ex) {
            shown = first.getFileName().toString();
        }
        if (changed.size() > 1) {
            shown += "  (+" + (changed.size() - 1) + ")";
        }
        final String text = shown;
        onEdt(() -> lastChangeLcd.setText(text));
        meter.pulse(Math.min(1.0, 0.4 + changed.size() * 0.15));
        emit("changed", Signal.trigger());
        emit("path", Signal.data(first.toString()));
    }

    @Override
    public void applyState(java.util.Map<String, String> state) {
        super.applyState(state);
        restartWatcher();
    }

    @Override
    public void dispose() {
        synchronized (this) {
            if (watcher != null) {
                watcher.stop();
                watcher = null;
            }
        }
        super.dispose();
    }
}
