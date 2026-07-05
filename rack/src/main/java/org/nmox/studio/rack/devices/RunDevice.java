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

    // APPEND-ONLY: patches persist the knob by index (static=23 since v1.34)
    private static final String[] TARGETS = {"auto", "node", "python", "go", "rust", "elixir", "erlang", "clojure", "swift", "dotnet", "dart", "scala", "haskell", "zig", "ocaml", "crystal", "maven", "gradle", "ruby", "php", "make", "bun", "deno", "static"};

    /** The static lane's fixed port: python3 -m http.server on 8000. */
    private static final String STATIC_PORT = "8000";

    /** The php lane's fixed address: php -S serves here (docroot-aware). */
    private static final String PHP_ADDRESS = "127.0.0.1:8000";

    private final Knob targetKnob;
    private final LcdDisplay argsLcd;
    private final Led liveLed;
    private final java.util.concurrent.atomic.AtomicBoolean readyFired =
            new java.util.concurrent.atomic.AtomicBoolean();
    /** True while the current launch is the webpack-serve lane. Test seam. */
    volatile boolean webpackLane;
    /** True while the current launch is the php built-in-server lane. Test seam. */
    volatile boolean phpLane;

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
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("running", "RUNNING", SignalType.GATE);
        // the static lane announces its address like every serve device
        addOutPort("ready", "READY", SignalType.TRIGGER);
        addOutPort("url", "URL", SignalType.DATA);

        param("target", targetKnob);
        param("args", argsLcd);
    }

    @Override
    protected void primaryAction() {
        readyFired.set(false);
        List<String> cmd = buildCommand();
        webpackLane = cmd.contains("webpack");
        phpLane = cmd.contains("php");
        emit("running", Signal.gate(true));
        onEdt(() -> liveLed.setOn(true));
        launch(cmd);
    }

    /**
     * The serving lanes' announcements: python's http.server prints
     * "Serving HTTP on ..." the moment it listens, webpack-dev-server
     * prints its local URL, php -S prints its "Development Server"
     * banner (the URL is the lane's pinned address — the shared
     * {@link ServeUrls} scan would drag the banner's closing paren,
     * the ARTISAN problem) — READY fires once and the URL jack carries
     * the address, SURGE-style. Other lanes announce nothing.
     */
    @Override
    protected void onLine(String line) {
        if (line.contains("Serving HTTP") && readyFired.compareAndSet(false, true)) {
            announceServing("http://localhost:" + STATIC_PORT);
            return;
        }
        if (phpLane && line.contains("Development Server")
                && readyFired.compareAndSet(false, true)) {
            announceServing("http://" + PHP_ADDRESS);
            return;
        }
        if (webpackLane && !readyFired.get()) {
            String url = ServeUrls.firstLocalUrl(line);
            if (url != null && readyFired.compareAndSet(false, true)) {
                announceServing(url);
            }
        }
    }

    private void announceServing(String url) {
        onEdt(() -> statusLcd.setText("SERVING  " + url));
        emit("url", Signal.data(url));
        emit("ready", Signal.trigger());
        registerServing(url, org.nmox.studio.rack.service.ServingRegistry.Kind.WEB);
    }

    @Override
    protected void onFinished(int exitCode) {
        deregisterServing();
        emit("running", Signal.gate(false));
        onEdt(() -> liveLed.setOn(false));
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("stop".equals(in.getId())) {
            stopProcess();
        } else if ("enable".equals(in.getId())) {
            enableGate(signal.high(), this::primaryAction, this::stopProcess);
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
            case BUN -> "bun";
            case DENO -> "deno";
            case RUST -> "rust";
            case ELIXIR -> "elixir";
            case ERLANG -> "erlang";
            case CLOJURE -> "clojure";
            case SWIFT -> "swift";
            case DOTNET -> "dotnet";
            case DART -> "dart";
            case SCALA -> "scala";
            case HASKELL -> "haskell";
            case ZIG -> "zig";
            case OCAML -> "ocaml";
            case CRYSTAL -> "crystal";
            case GO -> "go";
            case MAVEN -> "maven";
            case GRADLE -> "gradle";
            case PYTHON -> "python";
            case RUBY -> "ruby";
            case PHP -> "php";
            case MAKE, CMAKE -> "make";
            // the classic web kinds RUN by serving the site dir: a
            // grunt/bower-era project's runnable artifact IS its folder.
            // webpack runs its dev server — the same command the IDE's
            // Run action maps to, so F6 and IGNITION agree.
            case GRUNT, GULP, BOWER, STATIC -> "static";
            case WEBPACK -> "webpack"; // internal: resolved by AUTO, not on the knob
            default -> "node";
        };
    }


    private static ProjectInspector.ProjectKind kindForTarget(String target) {
        return switch (target) {
            case "static" -> ProjectInspector.ProjectKind.STATIC;
            case "webpack" -> ProjectInspector.ProjectKind.WEBPACK;
            case "rust" -> ProjectInspector.ProjectKind.RUST;
            case "elixir" -> ProjectInspector.ProjectKind.ELIXIR;
            case "erlang" -> ProjectInspector.ProjectKind.ERLANG;
            case "clojure" -> ProjectInspector.ProjectKind.CLOJURE;
            case "swift" -> ProjectInspector.ProjectKind.SWIFT;
            case "dotnet" -> ProjectInspector.ProjectKind.DOTNET;
            case "dart" -> ProjectInspector.ProjectKind.DART;
            case "scala" -> ProjectInspector.ProjectKind.SCALA;
            case "haskell" -> ProjectInspector.ProjectKind.HASKELL;
            case "zig" -> ProjectInspector.ProjectKind.ZIG;
            case "ocaml" -> ProjectInspector.ProjectKind.OCAML;
            case "crystal" -> ProjectInspector.ProjectKind.CRYSTAL;
            case "bun" -> ProjectInspector.ProjectKind.BUN;
            case "deno" -> ProjectInspector.ProjectKind.DENO;
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
            case "bun" -> List.of("bun", "run", "start");
            case "deno" -> List.of("deno", "task", "start");
            case "go" -> List.of("go", "run", ".");
            case "rust" -> List.of("cargo", "run");
            case "elixir" -> List.of("mix", "run", "--no-halt");
            case "erlang" -> List.of("rebar3", "compile"); // BEAM apps run under mix/releases; compile is the honest floor
            case "clojure" -> List.of("clojure", "-M:run"); // deps.edn :run alias convention
            case "swift" -> List.of("swift", "run");
            case "dotnet" -> List.of("dotnet", "run");
            case "dart" -> List.of("dart", "run");
            case "scala" -> List.of("sbt", "run");
            case "haskell" -> List.of("stack", "run");
            case "zig" -> List.of("zig", "build", "run");
            case "ocaml" -> List.of("dune", "exec", entryPoint("bin/main.exe", "./bin/main.exe"));
            case "crystal" -> List.of("shards", "run");
            case "maven" -> List.of("mvn", "-q", "compile", "exec:java");
            case "gradle" -> List.of("gradle", "run", "--quiet");
            case "ruby" -> new File(commandDir(), "config.ru").isFile()
                    ? List.of("rackup")
                    : List.of("ruby", entryPoint("main.rb", "app.rb"));
            // composer-era layout serves the public/ docroot; a bare tree
            // serves from the project root
            case "php" -> new File(commandDir(), "public").isDirectory()
                    ? List.of("php", "-S", PHP_ADDRESS, "-t", "public")
                    : List.of("php", "-S", PHP_ADDRESS);
            case "make" -> List.of("make", "run");
            // the 2005 stack: serve the folder itself; python3 is a
            // Doctor-probed staple, and READY/URL fire on its banner
            case "static" -> List.of("python3", "-m", "http.server", STATIC_PORT);
            case "webpack" -> List.of("npx", "webpack", "serve", "--mode", "development");
            default -> ProjectInspector.hasScript(projectDir(), "start")
                    ? List.of("npm", "start")
                    : List.of("node", entryPoint("index.js", "main.js", "src/index.js"));
        });
        String args = argsLcd.getText().trim();
        if (!args.isEmpty()) {
            if ("rust".equals(effectiveTarget())) {
                cmd.add("--");
            }
            cmd.addAll(parseArguments(args));
        }
        return cmd;
    }
}
