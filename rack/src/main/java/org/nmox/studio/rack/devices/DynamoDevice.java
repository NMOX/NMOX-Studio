package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * DYNAMO Task Runner: NPM-9000's older sibling for the Grunt/Gulp era.
 * The TASK knob lists what the project's Gruntfile or gulpfile declares
 * — enumerated by static parse ({@link TaskfileParser}), so the list is
 * instant and honest even before node is installed — and GO runs the
 * dialed task through npx. RUNNER settles grunt-vs-gulp when both live
 * in one repo; AUTO prefers the Gruntfile.
 */
public class DynamoDevice extends CommandDevice {

    private static final String[] RUNNERS = {"auto", "grunt", "gulp"};

    private final Knob taskKnob;
    private final Knob runnerKnob;

    public DynamoDevice() {
        super("task-runner", "DYNAMO", "TASK RUNNER", new Color(210, 150, 50), 2);

        RackButton go = place(new RackButton("GO", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        go.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        taskKnob = place(new Knob("TASK", new String[]{"—"}, 0), 180, 40);
        runnerKnob = place(new Knob("RUNNER", RUNNERS, 0), 254, 40);
        runnerKnob.setToolTipText("AUTO follows the taskfile present; dial when a repo carries both");

        go.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        // by name: task indexes shift per project, names travel
        paramByName("task", taskKnob);
        param("runner", runnerKnob);
    }

    @Override
    protected void onAttached() {
        reloadTasks();
    }

    @Override
    public void projectChanged(File dir) {
        reloadTasks();
    }

    /** Manifest pulse: a saved Gruntfile/gulpfile refreshes the TASK knob. */
    @Override
    public void manifestChanged(java.util.List<java.nio.file.Path> changed) {
        if (anyNamed(changed, "Gruntfile.js", "Gruntfile.coffee",
                "gulpfile.js", "gulpfile.babel.js", "gulpfile.mjs")) {
            reloadTasks();
        }
    }

    /** The faceplate context menu's "Open Gruntfile/gulpfile". */
    @Override
    public java.util.Optional<File> primaryManifest() {
        return java.util.Optional.ofNullable(taskfile(effectiveRunner()));
    }

    /** The taskfile walk never runs on the EDT (window restore fires these). */
    private void reloadTasks() {
        offEdt(this::reloadTasksNow);
    }

    /** Parses the taskfile and repopulates the TASK knob. Synchronous test seam. */
    void reloadTasksNow() {
        String runner = effectiveRunner();
        File file = taskfile(runner);
        List<String> names = new ArrayList<>();
        if (file != null) {
            try {
                String source = Files.readString(file.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
                names.addAll("gulp".equals(runner)
                        ? TaskfileParser.gulpTasks(source)
                        : TaskfileParser.gruntTasks(source));
            } catch (Exception ex) {
                // unreadable taskfile: leave the placeholder
            }
        }
        boolean empty = names.isEmpty();
        if (empty) {
            names.add("—");
        }
        final List<String> options = names;
        final File parsed = file;
        onEdt(() -> {
            // equality-guarded: setOptions always fires a knob change, and a
            // reload that parsed the same tasks must not re-fire downstream
            String[] fresh = options.toArray(new String[0]);
            if (!java.util.Arrays.equals(fresh, taskKnob.getOptions())) {
                taskKnob.setOptions(fresh);
            }
            statusLcd.setText(parsed == null ? "NO GRUNTFILE OR GULPFILE"
                    : empty ? "NO TASKS IN " + parsed.getName().toUpperCase(java.util.Locale.ROOT)
                    : options.size() + " TASKS — " + parsed.getName());
        });
    }

    /** Test seam: the TASK knob, so tests can count its change events. */
    Knob taskKnobForTest() {
        return taskKnob;
    }

    /** The dialed runner, or in AUTO whichever taskfile the project carries. */
    private String effectiveRunner() {
        String selected = runnerKnob.getSelectedOption();
        if (!"auto".equals(selected)) {
            return selected;
        }
        // Gruntfile present → grunt; gulpfile → gulp; both → the knob decides
        if (ProjectInspector.manifestDir(projectDir(), ProjectInspector.ProjectKind.GRUNT) != null) {
            return "grunt";
        }
        if (ProjectInspector.manifestDir(projectDir(), ProjectInspector.ProjectKind.GULP) != null) {
            return "gulp";
        }
        return "grunt";
    }

    private static ProjectInspector.ProjectKind kindFor(String runner) {
        return "gulp".equals(runner)
                ? ProjectInspector.ProjectKind.GULP
                : ProjectInspector.ProjectKind.GRUNT;
    }

    /** The actual taskfile for the effective runner, or null when absent. */
    private File taskfile(String runner) {
        File dir = ProjectInspector.manifestDir(projectDir(), kindFor(runner));
        if (dir == null) {
            return null;
        }
        String[] candidates = "gulp".equals(runner)
                ? new String[]{"gulpfile.js", "gulpfile.babel.js", "gulpfile.mjs"}
                : new String[]{"Gruntfile.js", "Gruntfile.coffee"};
        for (String name : candidates) {
            File file = new File(dir, name);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    /** Tasks run where the taskfile lives, monorepo or not. */
    @Override
    protected File commandDir() {
        return ProjectInspector.kindDir(projectDir(), kindFor(effectiveRunner()));
    }

    @Override
    protected List<String> buildCommand() {
        String task = taskKnob.getSelectedOption();
        if (task == null || "—".equals(task)) {
            return null;
        }
        return List.of("npx", effectiveRunner(), task);
    }
}
