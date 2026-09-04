package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IGNITION's whole target table: every knob position must produce the
 * toolchain's own run argv, AUTO must resolve each detected kind to the
 * right position, and the serving lanes' banner-parsing must announce
 * URL/READY exactly once. Commands are read through the protected
 * buildCommand seam — the exact path the IGNITE button takes — with no
 * process ever spawned.
 */
class RunTargetMatrixTest {

    // Mirror of RunDevice.TARGETS (append-only by law); the assertions on
    // each argv's first token verify the index mapping stays true.
    private static final String[] TARGETS = {"auto", "node", "python", "go", "rust",
        "elixir", "erlang", "clojure", "swift", "dotnet", "dart", "scala", "haskell",
        "zig", "ocaml", "crystal", "maven", "gradle", "ruby", "php", "make", "bun",
        "deno", "static", "gleam", "julia", "nim", "dlang", "racket", "elm",
        "purescript", "vlang", "fortran", "ada", "cairo", "move", "aiken",
        "clarity", "tact"};

    @TempDir
    Path root;

    private int caseNo;

    private Path freshDir(String... files) throws IOException {
        Path dir = root.resolve("case-" + (caseNo++));
        Files.createDirectories(dir);
        for (String f : files) {
            Path p = dir.resolve(f);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, "{}");
        }
        return dir;
    }

    /** buildCommand with the TARGET knob dialed to the named position. */
    private List<String> commandFor(String target, String... files) throws IOException {
        int index = List.of(TARGETS).indexOf(target);
        assertThat(index).as("knob position " + target).isNotNegative();
        Rack rack = new Rack();
        rack.setProjectDir(freshDir(files).toFile());
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.applyState(Map.of("target", String.valueOf(index)));
            return run.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    /** buildCommand with the knob on AUTO over the given manifest. */
    private List<String> autoCommand(String... files) throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir(files).toFile());
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            return run.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("F7: a TypeScript entry runs without a build — node with the strip-types flag; a JS entry still wins")
    void typeScriptEntryRunsDirectly() throws IOException {
        assertThat(commandFor("node", "index.ts"))
                .containsExactly("node", "--experimental-strip-types", "index.ts");
        assertThat(commandFor("node", "src/index.ts"))
                .containsExactly("node", "--experimental-strip-types", "src/index.ts");
        assertThat(commandFor("node", "index.js", "index.ts")).containsExactly("node", "index.js");
    }

    @Test
    @DisplayName("Every explicit knob position speaks its toolchain's run verb")
    void explicitTargets() throws IOException {
        assertThat(commandFor("python")).containsExactly("python3", "main.py");
        assertThat(commandFor("go")).containsExactly("go", "run", ".");
        assertThat(commandFor("rust")).containsExactly("cargo", "run");
        assertThat(commandFor("elixir")).containsExactly("mix", "run", "--no-halt");
        assertThat(commandFor("erlang")).containsExactly("rebar3", "compile");
        assertThat(commandFor("clojure")).containsExactly("clojure", "-M:run");
        assertThat(commandFor("swift")).containsExactly("swift", "run");
        assertThat(commandFor("dotnet")).containsExactly("dotnet", "run");
        assertThat(commandFor("dart")).containsExactly("dart", "run");
        assertThat(commandFor("scala")).containsExactly("sbt", "run");
        assertThat(commandFor("haskell")).containsExactly("stack", "run");
        assertThat(commandFor("zig")).containsExactly("zig", "build", "run");
        assertThat(commandFor("ocaml")).containsExactly("dune", "exec", "bin/main.exe");
        assertThat(commandFor("crystal")).containsExactly("shards", "run");
        assertThat(commandFor("maven")).containsExactly("mvn", "-q", "compile", "exec:java");
        assertThat(commandFor("gradle")).containsExactly("gradle", "run", "--quiet");
        assertThat(commandFor("make")).containsExactly("make", "run");
        assertThat(commandFor("bun")).containsExactly("bun", "run", "start");
        assertThat(commandFor("deno")).containsExactly("deno", "task", "start");
        assertThat(commandFor("static"))
                .satisfies(cmd -> {
                    // probed port since v1.320.0: shape + range, not a literal
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 4))
                            .containsExactly("python3", "-u", "-m", "http.server");
                    org.assertj.core.api.Assertions.assertThat(
                            Integer.parseInt(c.get(4))).isBetween(8000, 8019);
                });
        assertThat(commandFor("gleam")).containsExactly("gleam", "run");
        assertThat(commandFor("julia")).containsExactly("julia", "--project=.", "main.jl");
        assertThat(commandFor("nim")).containsExactly("nimble", "run");
        assertThat(commandFor("dlang")).containsExactly("dub", "run");
        assertThat(commandFor("racket")).containsExactly("racket", "main.rkt");
        assertThat(commandFor("elm")).containsExactly("npx", "elm", "reactor");
        assertThat(commandFor("purescript")).containsExactly("spago", "run");
        assertThat(commandFor("vlang")).containsExactly("v", "run", ".");
        assertThat(commandFor("fortran")).containsExactly("fpm", "run");
        assertThat(commandFor("ada")).containsExactly("alr", "run");
        assertThat(commandFor("cairo")).containsExactly("scarb", "execute");
        assertThat(commandFor("move")).containsExactly("sui", "move", "build");
        assertThat(commandFor("aiken")).containsExactly("aiken", "build");
        assertThat(commandFor("clarity")).containsExactly("clarinet", "check");
        assertThat(commandFor("tact")).as("Tact has no run verb — IGNITION greys").isNull();
    }

    @Test
    @DisplayName("The scripting lanes probe for their entry files and docroots")
    void entryPointProbes() throws IOException {
        assertThat(commandFor("python", "app.py")).containsExactly("python3", "app.py");
        assertThat(commandFor("ruby")).containsExactly("ruby", "main.rb");
        assertThat(commandFor("ruby", "app.rb")).containsExactly("ruby", "app.rb");
        assertThat(commandFor("ruby", "config.ru")).containsExactly("rackup");
        assertThat(commandFor("php")).satisfies(cmd -> {
                    // probed port since v1.320.0
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 2))
                            .containsExactly("php", "-S");
                    org.assertj.core.api.Assertions.assertThat(c.get(2))
                            .matches("127\\.0\\.0\\.1:80[0-1][0-9]");
                    org.assertj.core.api.Assertions.assertThat(c).hasSize(3);
                });
        assertThat(commandFor("php", "public/index.php"))
                .satisfies(cmd -> {
                    // probed port since v1.320.0
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 2))
                            .containsExactly("php", "-S");
                    org.assertj.core.api.Assertions.assertThat(c.get(2))
                            .matches("127\\.0\\.0\\.1:80[0-1][0-9]");
                    org.assertj.core.api.Assertions.assertThat(
                            c.subList(3, 5)).containsExactly("-t", "public");
                });
        assertThat(commandFor("node")).containsExactly("node", "index.js");
        assertThat(commandFor("node", "main.js")).containsExactly("node", "main.js");
    }

    @Test
    @DisplayName("AUTO resolves each manifest to its toolchain's run command")
    void autoResolution() throws IOException {
        assertThat(autoCommand("Cargo.toml")).containsExactly("cargo", "run");
        assertThat(autoCommand("go.mod")).containsExactly("go", "run", ".");
        assertThat(autoCommand("mix.exs")).containsExactly("mix", "run", "--no-halt");
        assertThat(autoCommand("rebar.config")).containsExactly("rebar3", "compile");
        assertThat(autoCommand("gleam.toml")).containsExactly("gleam", "run");
        assertThat(autoCommand("Project.toml")).containsExactly("julia", "--project=.", "main.jl");
        assertThat(autoCommand("app.nimble")).containsExactly("nimble", "run");
        assertThat(autoCommand("dub.json")).containsExactly("dub", "run");
        assertThat(autoCommand("info.rkt")).containsExactly("racket", "main.rkt");
        assertThat(autoCommand("elm.json")).containsExactly("npx", "elm", "reactor");
        assertThat(autoCommand("spago.yaml")).containsExactly("spago", "run");
        assertThat(autoCommand("v.mod")).containsExactly("v", "run", ".");
        assertThat(autoCommand("Scarb.toml")).containsExactly("scarb", "execute");
        assertThat(autoCommand("Move.toml")).containsExactly("sui", "move", "build");
        assertThat(autoCommand("aiken.toml")).containsExactly("aiken", "build");
        assertThat(autoCommand("Clarinet.toml")).containsExactly("clarinet", "check");
        assertThat(autoCommand("tact.config.json")).isNull();
        assertThat(autoCommand("fpm.toml")).containsExactly("fpm", "run");
        assertThat(autoCommand("alire.toml")).containsExactly("alr", "run");
        assertThat(autoCommand("rescript.json")).as("build-only: greys").isNull();
        assertThat(autoCommand("deps.edn")).containsExactly("clojure", "-M:run");
        assertThat(autoCommand("Package.swift")).containsExactly("swift", "run");
        assertThat(autoCommand("app.csproj")).containsExactly("dotnet", "run");
        assertThat(autoCommand("pubspec.yaml")).containsExactly("dart", "run");
        assertThat(autoCommand("build.sbt")).containsExactly("sbt", "run");
        assertThat(autoCommand("stack.yaml")).containsExactly("stack", "run");
        assertThat(autoCommand("build.zig")).containsExactly("zig", "build", "run");
        assertThat(autoCommand("dune-project")).containsExactly("dune", "exec", "bin/main.exe");
        assertThat(autoCommand("shard.yml")).containsExactly("shards", "run");
        assertThat(autoCommand("pom.xml")).containsExactly("mvn", "-q", "compile", "exec:java");
        assertThat(autoCommand("build.gradle")).containsExactly("gradle", "run", "--quiet");
        assertThat(autoCommand("pyproject.toml")).containsExactly("python3", "main.py");
        assertThat(autoCommand("Gemfile")).containsExactly("ruby", "main.rb");
        assertThat(autoCommand("composer.json")).satisfies(cmd -> {
                    // probed port since v1.320.0
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 2))
                            .containsExactly("php", "-S");
                    org.assertj.core.api.Assertions.assertThat(c.get(2))
                            .matches("127\\.0\\.0\\.1:80[0-1][0-9]");
                    org.assertj.core.api.Assertions.assertThat(c).hasSize(3);
                });
        assertThat(autoCommand("Makefile")).containsExactly("make", "run");
        assertThat(autoCommand("CMakeLists.txt")).containsExactly("make", "run");
        // classic web kinds run by serving the folder itself
        assertThat(autoCommand("Gruntfile.js"))
                .satisfies(cmd -> {
                    // probed port since v1.320.0: shape + range, not a literal
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 4))
                            .containsExactly("python3", "-u", "-m", "http.server");
                    org.assertj.core.api.Assertions.assertThat(
                            Integer.parseInt(c.get(4))).isBetween(8000, 8019);
                });
        assertThat(autoCommand("gulpfile.js"))
                .satisfies(cmd -> {
                    // probed port since v1.320.0: shape + range, not a literal
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 4))
                            .containsExactly("python3", "-u", "-m", "http.server");
                    org.assertj.core.api.Assertions.assertThat(
                            Integer.parseInt(c.get(4))).isBetween(8000, 8019);
                });
        assertThat(autoCommand("bower.json"))
                .satisfies(cmd -> {
                    // probed port since v1.320.0: shape + range, not a literal
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 4))
                            .containsExactly("python3", "-u", "-m", "http.server");
                    org.assertj.core.api.Assertions.assertThat(
                            Integer.parseInt(c.get(4))).isBetween(8000, 8019);
                });
        assertThat(autoCommand("index.html"))
                .satisfies(cmd -> {
                    // probed port since v1.320.0: shape + range, not a literal
                    java.util.List<String> c = new java.util.ArrayList<>(cmd);
                    org.assertj.core.api.Assertions.assertThat(c.subList(0, 4))
                            .containsExactly("python3", "-u", "-m", "http.server");
                    org.assertj.core.api.Assertions.assertThat(
                            Integer.parseInt(c.get(4))).isBetween(8000, 8019);
                });
        assertThat(autoCommand("webpack.config.js"))
                .containsExactly("npx", "webpack", "serve", "--mode", "development");
        assertThat(autoCommand("bunfig.toml")).containsExactly("bun", "run", "start");
        assertThat(autoCommand("deno.json")).containsExactly("deno", "task", "start");
    }

    @Test
    @DisplayName("Program arguments append; the rust lane separates them with --")
    void argumentsAppend() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.applyState(Map.of("target",
                    String.valueOf(List.of(TARGETS).indexOf("rust")),
                    "args", "--verbose \"two words\""));
            assertThat(run.buildCommand())
                    .containsExactly("cargo", "run", "--", "--verbose", "two words");
            run.applyState(Map.of("target",
                    String.valueOf(List.of(TARGETS).indexOf("go")), "args", "-x"));
            assertThat(run.buildCommand()).containsExactly("go", "run", ".", "-x");
        } finally {
            rack.shutdown();
        }
    }

    /** Collects everything the serving announcement emits. */
    private static final class Probe extends RackDevice {
        final ConcurrentLinkedQueue<String> received = new ConcurrentLinkedQueue<>();

        Probe() {
            super("probe", "PROBE", "TEST PROBE", new Color(0, 0, 0), 1);
            addInPort("url", "URL", SignalType.DATA);
            addInPort("ready", "READY", SignalType.TRIGGER);
            addInPort("running", "RUNNING", SignalType.GATE);
        }

        @Override
        public void receive(Port in, Signal signal) {
            received.add(in.getId() + ":" + (signal.type() == SignalType.DATA
                    ? signal.payload() : String.valueOf(signal.high())));
        }
    }

    private static void settle(Rack rack) {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        } catch (Exception ignored) {
        }
        rack.awaitRouterIdle();
    }

    @Test
    @DisplayName("The static lane's banner announces URL and READY exactly once")
    void staticBannerAnnouncesOnce() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("index.html").toFile());
        try {
            RunDevice run = new RunDevice();
            Probe probe = new Probe();
            rack.addDevice(run);
            rack.addDevice(probe);
            rack.connect(run.getPort("url"), probe.getPort("url"));
            rack.connect(run.getPort("ready"), probe.getPort("ready"));
            rack.connect(run.getPort("running"), probe.getPort("running"));

            run.onLine("Serving HTTP on :: port 8000 (http://[::]:8000/) ...");
            run.onLine("Serving HTTP on :: port 8000 (http://[::]:8000/) ...");
            settle(rack);
            assertThat(probe.received)
                    .as("one URL, one READY — the second banner must not re-fire")
                    .filteredOn(s -> s.startsWith("url:"))
                    .containsExactly("url:http://localhost:8000");

            run.onFinished(0);
            settle(rack);
            assertThat(probe.received).contains("running:false");
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("The php and webpack lanes announce from their own banners")
    void phpAndWebpackBanners() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("composer.json").toFile());
        try {
            RunDevice php = new RunDevice();
            Probe probe = new Probe();
            rack.addDevice(php);
            rack.addDevice(probe);
            rack.connect(php.getPort("url"), probe.getPort("url"));
            php.phpLane = true; // the volatile lane flag primaryAction sets
            php.onLine("[Tue] PHP 8.3.0 Development Server (http://127.0.0.1:8000) started");
            settle(rack);
            assertThat(probe.received).contains("url:http://127.0.0.1:8000");
        } finally {
            rack.shutdown();
        }

        Rack rack2 = new Rack();
        rack2.setProjectDir(freshDir("webpack.config.js").toFile());
        try {
            RunDevice webpack = new RunDevice();
            Probe probe = new Probe();
            rack2.addDevice(webpack);
            rack2.addDevice(probe);
            rack2.connect(webpack.getPort("url"), probe.getPort("url"));
            webpack.webpackLane = true;
            webpack.onLine("nothing to see here");
            webpack.onLine("<i> [webpack-dev-server] Loopback: http://localhost:8080/");
            settle(rack2);
            assertThat(probe.received)
                    .filteredOn(s -> s.startsWith("url:"))
                    .containsExactly("url:http://localhost:8080/");
        } finally {
            rack2.shutdown();
        }
    }

    @Test
    @DisplayName("The STOP and ENABLE jacks route without a process, refusing safely")
    void stopAndEnableJacks() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            // no process: stop is a safe no-op
            run.receive(run.getPort("stop"), Signal.trigger(true));
            // enable-high fires primaryAction; the manifest-less dir refuses
            // the launch, so no process spawns and no gate goes high
            run.receive(run.getPort("enable"), Signal.gate(true));
            run.receive(run.getPort("enable"), Signal.gate(false));
            settle(rack);
            assertThat(run.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }
}
