package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * IGNITION Runtime: starts the program, whatever language it speaks.
 * AUTO reads the project manifest - cargo run for Rust, go run for Go,
 * npm start for Node, the main script for Python and Ruby, the built-in
 * server for PHP, make run for Makefile projects. The RUNNING gate is
 * high while the process lives.
 */
public class RunDevice extends CommandDevice {

    private static final String[] TARGETS = {"auto", "node", "python", "go", "rust", "elixir", "erlang", "clojure", "swift", "maven", "gradle", "ruby", "php", "make"};

    private final Knob targetKnob;
    private final LcdDisplay argsLcd;
    private final Led liveLed;

    public RunDevice() {
        super("run", "IGNITION", "POLYGLOT RUNTIME", new Color(255, 94, 58), 2);

        argsLcd = place(new LcdDisplay(180, 1), 254, 46);
        argsLcd.setText("");
        argsLcd.setEditable("Program arguments");
        argsLcd.setToolTipText("Arguments passed to the program (double-click to edit)");
        RackButton ignite = place(new RackButton("IGNITE", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        ignite.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        targetKnob = place(new Knob("TARGET", TARGETS, 0), 180, 40);
        liveLed = place(new Led("LIVE", new Color(255, 94, 58)), 444, 58);

        ignite.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        addInPort("stop", "STOP", SignalType.TRIGGER);
        addOutPort("running", "RUNNING", SignalType.GATE);

        param("target", targetKnob);
        param("args", argsLcd);
    }

    @Override
    protected void primaryAction() {
        emit("running", Signal.gate(true));
        onEdt(() -> liveLed.setOn(true));
        launch(buildCommand());
    }

    @Override
    protected void onFinished(int exitCode) {
        emit("running", Signal.gate(false));
        onEdt(() -> liveLed.setOn(false));
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("stop".equals(in.getId())) {
            stopProcess();
        } else {
            super.receive(in, signal);
        }
    }

    private String effectiveTarget() {
        String target = targetKnob.getSelectedOption();
        if (!"auto".equals(target)) {
            return target;
        }
        return switch (effectiveKind()) {
            case NODE -> "node";
            case RUST -> "rust";
            case ELIXIR -> "elixir";
            case ERLANG -> "erlang";
            case CLOJURE -> "clojure";
            case SWIFT -> "swift";
            case GO -> "go";
            case MAVEN -> "maven";
            case GRADLE -> "gradle";
            case PYTHON -> "python";
            case RUBY -> "ruby";
            case PHP -> "php";
            case MAKE, CMAKE -> "make";
            default -> "node";
        };
    }


    private static ProjectInspector.ProjectKind kindForTarget(String target) {
        return switch (target) {
            case "rust" -> ProjectInspector.ProjectKind.RUST;
            case "elixir" -> ProjectInspector.ProjectKind.ELIXIR;
            case "erlang" -> ProjectInspector.ProjectKind.ERLANG;
            case "clojure" -> ProjectInspector.ProjectKind.CLOJURE;
            case "swift" -> ProjectInspector.ProjectKind.SWIFT;
            case "go" -> ProjectInspector.ProjectKind.GO;
            case "maven" -> ProjectInspector.ProjectKind.MAVEN;
            case "gradle" -> ProjectInspector.ProjectKind.GRADLE;
            case "python" -> ProjectInspector.ProjectKind.PYTHON;
            case "ruby" -> ProjectInspector.ProjectKind.RUBY;
            case "php" -> ProjectInspector.ProjectKind.PHP;
            case "make" -> ProjectInspector.ProjectKind.MAKE;
            default -> ProjectInspector.ProjectKind.NODE;
        };
    }

    /** Commands run where the selected target's manifest lives. */
    @Override
    protected java.io.File commandDir() {
        return ProjectInspector.kindDir(projectDir(), kindForTarget(effectiveTarget()));
    }

    /** First existing candidate file, else the first candidate. */
    private String entryPoint(String... candidates) {
        for (String candidate : candidates) {
            if (new File(commandDir(), candidate).isFile()) {
                return candidate;
            }
        }
        return candidates[0];
    }

    @Override
    protected List<String> buildCommand() {
        List<String> cmd = new ArrayList<>(switch (effectiveTarget()) {
            case "python" -> List.of("python3", entryPoint("main.py", "app.py", "src/main.py"));
            case "go" -> List.of("go", "run", ".");
            case "rust" -> List.of("cargo", "run");
            case "elixir" -> List.of("mix", "run", "--no-halt");
            case "erlang" -> List.of("rebar3", "compile"); // BEAM apps run under mix/releases; compile is the honest floor
            case "clojure" -> List.of("clojure", "-M:run"); // deps.edn :run alias convention
            case "swift" -> List.of("swift", "run");
            case "maven" -> List.of("mvn", "-q", "compile", "exec:java");
            case "gradle" -> List.of("gradle", "run", "--quiet");
            case "ruby" -> new File(commandDir(), "config.ru").isFile()
                    ? List.of("rackup")
                    : List.of("ruby", entryPoint("main.rb", "app.rb"));
            case "php" -> List.of("php", "-S", "localhost:8000",
                    "-t", new File(commandDir(), "public").isDirectory() ? "public" : ".");
            case "make" -> List.of("make", "run");
            default -> ProjectInspector.hasScript(projectDir(), "start")
                    ? List.of("npm", "start")
                    : List.of("node", entryPoint("index.js", "main.js", "src/index.js"));
        });
        String args = argsLcd.getText().trim();
        if (!args.isEmpty()) {
            if ("rust".equals(effectiveTarget())) {
                cmd.add("--");
            }
            cmd.addAll(List.of(args.split("\\s+")));
        }
        return cmd;
    }
}
