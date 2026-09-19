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

    // APPEND-ONLY: patches persist the knob by index (static=23 since v1.34),
    // so a saved patch reads a REORDERED array as a different target. New
    // positions go at the END; KindVocabularyGateTest pins this exact order.
    private static final String[] TARGETS = {"auto", "node", "python", "go", "rust", "elixir", "erlang", "clojure", "swift", "dotnet", "dart", "scala", "haskell", "zig", "ocaml", "crystal", "maven", "gradle", "ruby", "php", "make", "bun", "deno", "static", "gleam", "julia", "nim", "dlang", "racket", "elm", "purescript", "vlang", "fortran", "ada", "cairo", "move", "aiken", "clarity", "tact",
        // appended v2.186.0: both were already PRODUCIBLE by AUTO with a
        // buildCommand arm of their own, and neither could be dialled by
        // hand — webpack served a dev server nobody could ask for, rescript
        // greyed for a reason nobody could make it say
        "rescript", "webpack"};

    /** The TARGET knob's positions, in their on-disk index order. */
    static java.util.List<String> targets() {
        return List.of(TARGETS);
    }

    /** The static lane's preferred port; probed upward when busy (v1.320.0). */
    private static final String STATIC_PORT = "8000";

    /** The php lane's preferred port; probed upward when busy (v1.320.0). */
    private static final int PHP_PORT = 8000;

    /** php -S's bind address for this launch — port probed, not pinned. */
    private static String phpAddress() {
        return "127.0.0.1:" + org.nmox.studio.core.util.FreePorts
                .firstFreeFrom(PHP_PORT);
    }

    private final Knob targetKnob;
    private final LcdDisplay argsLcd;
    private final Led liveLed;
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
        stop.addActionListener(e -> stopByUser());

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
        List<String> cmd = buildCommand();
        // a null command is this device's own grey contract, and these two
        // lane flags dereferenced it: pressing IGNITE on a Tact or ReScript
        // project threw a NullPointerException here instead of reaching the
        // honest refusal three lines down — the grey the comments beside
        // those two arms have promised since v1.161.0 was unreachable.
        // Only the AUTO path could produce it before v2.186.0, so no test
        // ever pressed the button on a null.
        webpackLane = cmd != null && cmd.contains("webpack");
        phpLane = cmd != null && cmd.contains("php");
        if (launch(cmd)) {
            emit("running", Signal.gate(true));
            onEdt(() -> liveLed.setOn(true));
        }
    }

    /**
     * The serving lanes' announcements: python's http.server prints
     * "Serving HTTP on ..." the moment it listens, webpack-dev-server
     * prints its local URL, php -S prints its "Development Server"
     * banner (its port is read via {@link ServeUrls#bannerPort} — the
     * shared full-URL scan would drag the banner's closing paren,
     * the ARTISAN problem) — READY fires once and the URL jack carries
     * the address, SURGE-style. Other lanes announce nothing.
     */
    @Override
    protected void onLine(String line) {
        // Both fixed-port lanes became probed-port lanes (v1.320.0), so the
        // announce reads the port from the server's OWN banner instead of a
        // constant — announcing 8000 while the server bound 8001 would put
        // the serving chip on a port nothing listens on (the v1.93.0 class).
        // each lane announces ONCE per run: the base helper dedupes the URL
        // and latches READY, so the "already announced" check is announcedUrl()
        if (announcedUrl() != null) {
            return;
        }
        if (line.contains("Serving HTTP")) {
            announceWeb("http://localhost:" + ServeUrls.bannerPort(
                    line, Integer.parseInt(STATIC_PORT)));
            return;
        }
        if (phpLane && line.contains("Development Server")) {
            announceWeb("http://127.0.0.1:" + ServeUrls.bannerPort(
                    line, PHP_PORT));
            return;
        }
        if (webpackLane) {
            String url = ServeUrls.firstLocalUrl(line);
            if (url != null) {
                announceWeb(url);
            }
        }
    }

    /** URL then READY through the one home (CommandDevice.announceServing). */
    private void announceWeb(String url) {
        onEdt(() -> statusLcd.setText("SERVING  " + url));
        announceServing(url, org.nmox.studio.rack.service.ServingRegistry.Kind.WEB);
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
            stopByUser();
        } else if ("enable".equals(in.getId())) {
            enableGate(signal.high(), this::primaryAction, this::stopProcess);
        } else {
            super.receive(in, signal);
        }
    }

    /**
     * The target this run speaks: the dialled knob position, or — on AUTO
     * — the detected toolchain's own token. Null when that toolchain names
     * no run verb at all, which {@link #buildCommand()} turns into the
     * honest grey every console shares. It used to answer "node" for any
     * kind the switch had not listed, so a Foundry repo, a learning space
     * and an unaimed rack all quietly ran a node command.
     */
    private String effectiveTarget() {
        String target = targetKnob.getSelectedOption();
        if (!"auto".equals(target)) {
            return target;
        }
        return effectiveKind().runTarget();
    }

    /** Commands run where the selected target's manifest lives. */
    @Override
    protected java.io.File commandDir() {
        ProjectInspector.ProjectKind kind =
                ProjectInspector.ProjectKind.forRunTarget(effectiveTarget());
        // no kind names this target (or there is no target): kindDir falls
        // back to the project root, which is where a rack with nothing
        // detected would run anyway
        return ProjectInspector.kindDir(projectDir(),
                kind == null ? ProjectInspector.ProjectKind.NONE : kind);
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
        String target = effectiveTarget();
        if (target == null) {
            // the toolchain names no run verb — IGNITION greys and says so
            return null;
        }
        List<String> base = switch (target) {
            case "python" -> List.of("python3", entryPoint("main.py", "app.py", "src/main.py"));
            case "bun" -> List.of("bun", "run", "start");
            case "deno" -> List.of("deno", "task", "start");
            case "go" -> List.of("go", "run", ".");
            case "rust" -> List.of("cargo", "run");
            case "elixir" -> List.of("mix", "run", "--no-halt");
            case "erlang" -> List.of("rebar3", "compile"); // BEAM apps run under mix/releases; compile is the honest floor
            case "gleam" -> List.of("gleam", "run");
            // julia: main.jl is the script-project convention; entryPoint
            // falls back honestly and the missing-file error names it
            case "julia" -> List.of("julia", "--project=.",
                    entryPoint("main.jl", "src/main.jl"));
            case "nim" -> List.of("nimble", "run");
            case "dlang" -> List.of("dub", "run");
            case "racket" -> List.of("racket", entryPoint("main.rkt", "src/main.rkt"));
            // elm reactor is the framework's own dev server (port 8000)
            case "elm" -> List.of("npx", "elm", "reactor");
            case "purescript" -> List.of("spago", "run");
            case "vlang" -> List.of("v", "run", ".");
            case "cairo" -> List.of("scarb", "execute"); // executable targets; libs get scarb's own honest error
            case "move" -> ProjectInspector.moveBuildCommand(commandDir()); // Move has no run verb — build is the honest "make my code"; dialect-aware (Sui/Aptos)
            case "aiken" -> List.of("aiken", "build"); // validators have no run verb — build is the honest "make my code" (the Move rule)
            case "clarity" -> List.of("clarinet", "check"); // Clarity is interpreted on-chain: check IS the compile, and there is nothing else to "run" locally
            case "tact" -> null; // Tact rides npm scripts (build/test) and has no run verb — IGNITION greys
            case "fortran" -> List.of("fpm", "run");
            case "ada" -> List.of("alr", "run");
            // ReScript compiles but has no run entry point — grey IGNITION
            case "rescript" -> null;
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
                    ? List.of("php", "-S", phpAddress(), "-t", "public")
                    : List.of("php", "-S", phpAddress());
            case "make" -> List.of("make", "run");
            // the 2005 stack: serve the folder itself; python3 is a
            // Doctor-probed staple, and READY/URL fire on its banner
            // -u is load-bearing: http.server prints its "Serving HTTP on"
            // banner to stdout, which python block-buffers when it isn't a
            // TTY. Without -u the banner sits in the buffer, onLine never
            // sees it, and the lane serves without ever announcing itself —
            // no READY, no URL jack, no serving chip. (The access log is
            // stderr, so output looked fine while the announcement was lost.)
            // port probed, not pinned (v1.320.0): python has no upward scan,
            // so a busy 8000 killed space #89's first Run — the front-door
            // space whose promise is "nothing to configure". READY/URL ride
            // the banner, so a shifted port announces itself correctly.
            case "static" -> List.of("python3", "-u", "-m", "http.server",
                    String.valueOf(org.nmox.studio.core.util.FreePorts
                            .firstFreeFrom(Integer.parseInt(STATIC_PORT))));
            case "webpack" -> List.of("npx", "webpack", "serve", "--mode", "development");
            // a TypeScript entry runs without a build (futures F7, v2.69.0):
            // JavaScript entries keep precedence, then index.ts / main.ts /
            // src/index.ts run through NodeTypeStripping's one argv
            case "node" -> ProjectInspector.hasScript(projectDir(), "start")
                    ? List.of("npm", "start")
                    : org.nmox.studio.core.util.NodeTypeStripping.argv(entryPoint(
                            "index.js", "main.js", "src/index.js", "index.ts", "main.ts", "src/index.ts"));
            // the node lane is a CASE now, not the default: a target with no
            // arm is a target this device cannot run, and saying so beats
            // running somebody else's toolchain under its name
            default -> null;
        };
        if (base == null) {
            return null; // the toolchain has no run verb — IGNITION greys
        }
        List<String> cmd = new ArrayList<>(base);
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
