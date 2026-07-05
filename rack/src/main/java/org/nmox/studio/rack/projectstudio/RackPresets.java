package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackIO;

/**
 * Ready-made infra wirings. Each preset builds a throwaway rack
 * programmatically and serializes it through RackIO, so a preset can
 * never drift from the real patch schema - if a port or param is
 * renamed, the preset breaks at build time, not in a user's hands.
 */
public enum RackPresets {

    WEB_PIPELINE("Web Pipeline",
            "MAESTRO fires install → build → test; SURGE pops the browser when serving") {
        @Override
        void wire(Rack rack) {
            RackDevice master = add(rack, DeviceType.MASTER, null);
            RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
            RackDevice build = add(rack, DeviceType.BUILD, null);
            RackDevice test = add(rack, DeviceType.TEST, null);
            RackDevice server = add(rack, DeviceType.DEV_SERVER, null);
            RackDevice browser = add(rack, DeviceType.BROWSER, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(master.getPort("trig1"), deps.getPort("run"));
            rack.connect(deps.getPort("ok"), build.getPort("run"));
            rack.connect(build.getPort("ok"), test.getPort("run"));
            rack.connect(test.getPort("out"), console.getPort("in"));
            rack.connect(server.getPort("url"), browser.getPort("url"));
            rack.connect(server.getPort("ready"), browser.getPort("open"));
        }
    },

    DEV_LOOP("Dev Loop",
            "Dev server auto-opens the browser; saves run the tests") {
        @Override
        void wire(Rack rack) {
            RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "false", "filter", "1"));
            RackDevice server = add(rack, DeviceType.DEV_SERVER, null);
            RackDevice browser = add(rack, DeviceType.BROWSER, null);
            RackDevice test = add(rack, DeviceType.TEST, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(server.getPort("url"), browser.getPort("url"));
            rack.connect(server.getPort("ready"), browser.getPort("open"));
            rack.connect(reflex.getPort("changed"), test.getPort("run"));
            rack.connect(test.getPort("out"), console.getPort("in"));
        }
    },

    TDD_LOOP("TDD Loop",
            "REFLEX armed: every save runs the tests, tally on the console") {
        @Override
        void wire(Rack rack) {
            RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "true", "filter", "1"));
            RackDevice test = add(rack, DeviceType.TEST, null);
            RackDevice lint = add(rack, DeviceType.LINT, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(reflex.getPort("changed"), test.getPort("run"));
            rack.connect(test.getPort("out"), console.getPort("in"));
            rack.connect(test.getPort("ok"), lint.getPort("run"));
            rack.connect(lint.getPort("out"), console.getPort("in"));
        }
    },

    CI_LANE("CI Lane",
            "MAESTRO fires install → build → test, output on the console") {
        @Override
        void wire(Rack rack) {
            RackDevice master = add(rack, DeviceType.MASTER, null);
            RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
            RackDevice build = add(rack, DeviceType.BUILD, null);
            RackDevice test = add(rack, DeviceType.TEST, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(master.getPort("trig1"), deps.getPort("run"));
            rack.connect(deps.getPort("ok"), build.getPort("run"));
            rack.connect(build.getPort("ok"), test.getPort("run"));
            rack.connect(test.getPort("out"), console.getPort("in"));
        }
    },

    UPTIME_WATCH("Uptime Watch",
            "While the dev server runs, TEMPO clocks PING health checks") {
        @Override
        void wire(Rack rack) {
            RackDevice server = add(rack, DeviceType.DEV_SERVER, null);
            RackDevice browser = add(rack, DeviceType.BROWSER, null);
            // 30s health-check clock, gated by the server's RUNNING state
            RackDevice tempo = add(rack, DeviceType.TEMPO, Map.of("rate", "2", "running", "false"));
            RackDevice ping = add(rack, DeviceType.HTTP, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(server.getPort("url"), browser.getPort("url"));
            rack.connect(server.getPort("ready"), browser.getPort("open"));
            rack.connect(server.getPort("running"), tempo.getPort("enable"));
            rack.connect(server.getPort("url"), ping.getPort("url"));
            rack.connect(tempo.getPort("tick"), ping.getPort("send"));
            rack.connect(ping.getPort("body"), console.getPort("in"));
        }
    },

    MONOREPO_LANES("Monorepo Lanes",
            "Mixed repo: ROSETTA shows the mix, saves fan out to node + cargo test lanes") {
        @Override
        void wire(Rack rack) {
            RackDevice rosetta = add(rack, DeviceType.ROSETTA, null);
            RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "true", "filter", "1"));
            RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
            RackDevice nodeTests = add(rack, DeviceType.TEST, Map.of("framework", "1"));
            RackDevice cargoTests = add(rack, DeviceType.TEST, Map.of("framework", "7"));
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            // one save, two toolchains tested in their own directories
            rack.connect(reflex.getPort("changed"), nodeTests.getPort("run"));
            rack.connect(reflex.getPort("changed"), cargoTests.getPort("run"));
            rack.connect(nodeTests.getPort("out"), console.getPort("in"));
            rack.connect(cargoTests.getPort("out"), console.getPort("in"));
            // INSTALL on CRATE bootstraps every toolchain in sequence
            rack.connect(deps.getPort("out"), console.getPort("in"));
            // ROSETTA needs no wiring in this preset — it works by being present
        }
    },

    SHIP_LANE("Ship Lane",
            "Prod build → security scan → armed deploy, with console trail") {
        @Override
        void wire(Rack rack) {
            RackDevice master = add(rack, DeviceType.MASTER, null);
            RackDevice build = add(rack, DeviceType.BUILD, Map.of("prod", "true"));
            RackDevice audit = add(rack, DeviceType.AUDIT, null);
            RackDevice deploy = add(rack, DeviceType.DEPLOY, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(master.getPort("trig1"), build.getPort("run"));
            rack.connect(build.getPort("ok"), audit.getPort("run"));
            rack.connect(audit.getPort("ok"), deploy.getPort("run"));
            rack.connect(deploy.getPort("out"), console.getPort("in"));
        }
    },

    POLYGLOT_GAUNTLET("Polyglot Gauntlet",
            "Per-language saves drive their own lane; QUORUM clears PREFLIGHT only when every lane is green") {
        @Override
        void wire(Rack rack) {
            add(rack, DeviceType.ROSETTA, null);
            RackDevice webWatch = add(rack, DeviceType.REFLEX,
                    Map.of("armed", "true", "glob", "ts,tsx,js,jsx,vue,svelte"));
            RackDevice apiWatch = add(rack, DeviceType.REFLEX,
                    Map.of("armed", "true", "glob", "rs"));
            RackDevice webTests = add(rack, DeviceType.TEST, Map.of("framework", "1"));
            RackDevice apiTests = add(rack, DeviceType.TEST, Map.of("framework", "7"));
            RackDevice quorum = add(rack, DeviceType.JOIN, null);
            RackDevice preflight = add(rack, DeviceType.PREFLIGHT, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(webWatch.getPort("changed"), webTests.getPort("run"));
            rack.connect(apiWatch.getPort("changed"), apiTests.getPort("run"));
            rack.connect(webTests.getPort("done"), quorum.getPort("in1"));
            rack.connect(apiTests.getPort("done"), quorum.getPort("in2"));
            rack.connect(quorum.getPort("ok"), preflight.getPort("run"));
            rack.connect(webTests.getPort("out"), console.getPort("in"));
            rack.connect(apiTests.getPort("out"), console.getPort("in"));
        }
    },

    SHIP_GATE("Ship Gate",
            "Prod build → Lighthouse floor → bundle budget → PREFLIGHT verdict → armed deploy") {
        @Override
        void wire(Rack rack) {
            RackDevice master = add(rack, DeviceType.MASTER, null);
            RackDevice build = add(rack, DeviceType.BUILD, Map.of("prod", "true"));
            // floor 90 held across all four Lighthouse categories
            RackDevice vitals = add(rack, DeviceType.VITALS, Map.of("min", "4", "gate", "5"));
            RackDevice bundle = add(rack, DeviceType.BUNDLE_SIZE, null);
            RackDevice preflight = add(rack, DeviceType.PREFLIGHT, null);
            RackDevice deploy = add(rack, DeviceType.DEPLOY, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            // every gate's OK feeds the next; a single FAIL anywhere ships nothing
            rack.connect(master.getPort("trig1"), build.getPort("run"));
            rack.connect(build.getPort("ok"), vitals.getPort("run"));
            rack.connect(vitals.getPort("ok"), bundle.getPort("run"));
            rack.connect(bundle.getPort("ok"), preflight.getPort("run"));
            rack.connect(preflight.getPort("ok"), deploy.getPort("run"));
            rack.connect(preflight.getPort("out"), console.getPort("in"));
            rack.connect(deploy.getPort("out"), console.getPort("in"));
        }
    },

    DEV_INTELLIGENCE("Dev Intelligence",
            "Serve with full awareness: clocked health probes, port radar, flight recorder, log tail") {
        @Override
        void wire(Rack rack) {
            RackDevice server = add(rack, DeviceType.DEV_SERVER, null);
            // 30s health-check clock, gated by the server's RUNNING state
            RackDevice tempo = add(rack, DeviceType.TEMPO, Map.of("rate", "2", "running", "false"));
            RackDevice ping = add(rack, DeviceType.HTTP, null);
            RackDevice sonar = add(rack, DeviceType.SONAR, null);
            RackDevice blackbox = add(rack, DeviceType.BLACKBOX, null);
            RackDevice tail = add(rack, DeviceType.TAIL, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(server.getPort("running"), tempo.getPort("enable"));
            rack.connect(server.getPort("url"), ping.getPort("url"));
            rack.connect(tempo.getPort("tick"), ping.getPort("send"));
            rack.connect(ping.getPort("body"), console.getPort("in"));
            // awareness lane: the clock sweeps the ports, the recorders feed the console
            rack.connect(tempo.getPort("bar"), sonar.getPort("run"));
            rack.connect(sonar.getPort("out"), console.getPort("in"));
            rack.connect(blackbox.getPort("out"), console.getPort("in"));
            rack.connect(tail.getPort("out"), console.getPort("in"));
        }
    },

    LAMP_BENCH("LAMP Bench",
            "Composer install fans out to phpunit + phpstan + Pint; IGNITION serves public/") {
        @Override
        void wire(Rack rack) {
            RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
            // VERITAS pinned to phpunit
            RackDevice test = add(rack, DeviceType.TEST, Map.of("framework", "11"));
            // TYPEGUARD and GLOSS auto-detect the PHP lane (phpstan / Pint);
            // GLOSS in CHECK mode - a bench verifies, it doesn't rewrite
            RackDevice typecheck = add(rack, DeviceType.TYPECHECK, null);
            RackDevice format = add(rack, DeviceType.FORMAT, Map.of("write", "false"));
            // IGNITION pinned to php: `php -S 127.0.0.1:8000 -t public`
            RackDevice serve = add(rack, DeviceType.RUN, Map.of("target", "19"));
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            // one green install fans out to every checker in the lane
            rack.connect(deps.getPort("ok"), test.getPort("run"));
            rack.connect(deps.getPort("ok"), typecheck.getPort("run"));
            rack.connect(deps.getPort("ok"), format.getPort("run"));
            rack.connect(test.getPort("out"), console.getPort("in"));
            rack.connect(typecheck.getPort("out"), console.getPort("in"));
            rack.connect(format.getPort("out"), console.getPort("in"));
            rack.connect(serve.getPort("out"), console.getPort("in"));
            rack.connect(deps.getPort("out"), console.getPort("in"));
        }
    },

    WEB3_BENCH("Web3 Bench",
            "ANVIL chain + forge build/test + GOVERNOR gas gate, MONITOR watching") {
        @Override
        void wire(Rack rack) {
            RackDevice master = add(rack, DeviceType.MASTER, null);
            // FORGE auto-detects the Foundry lane (forge build via foundry.toml)
            RackDevice build = add(rack, DeviceType.BUILD, null);
            // VERITAS pinned to forge
            RackDevice test = add(rack, DeviceType.TEST, Map.of("framework", "25"));
            RackDevice governor = add(rack, DeviceType.GAS_BUDGET, null);
            // the devnet free-runs beside the lane: START it when Contract
            // Studio or a deploy script needs a chain; its SERVING gate and
            // ENABLE input follow the long-runner conventions for wiring up
            RackDevice anvil = add(rack, DeviceType.LOCAL_CHAIN, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            // build → test → gas gate: a regression stops the train
            rack.connect(master.getPort("trig1"), build.getPort("run"));
            rack.connect(build.getPort("ok"), test.getPort("run"));
            rack.connect(test.getPort("ok"), governor.getPort("run"));
            // every lane lands on the console, the chain's banner included
            rack.connect(build.getPort("out"), console.getPort("in"));
            rack.connect(test.getPort("out"), console.getPort("in"));
            rack.connect(governor.getPort("out"), console.getPort("in"));
            rack.connect(anvil.getPort("out"), console.getPort("in"));
        }
    },

    CLASSIC_WEB("Classic Web Bench",
            "CRATE installs (npm + bower), DYNAMO runs the taskfile, IGNITION serves the site, VITALS scores it") {
        @Override
        void wire(Rack rack) {
            RackDevice master = add(rack, DeviceType.MASTER, null);
            RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
            RackDevice tasks = add(rack, DeviceType.TASK_RUNNER, null);
            // IGNITION pinned to the static lane: python3 -m http.server
            // from the site root (TARGETS index 23 = static)
            RackDevice serve = add(rack, DeviceType.RUN, Map.of("target", "23"));
            RackDevice vitals = add(rack, DeviceType.VITALS, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            // install → task run → serve: the 2012 pipeline, one press
            rack.connect(master.getPort("trig1"), deps.getPort("run"));
            rack.connect(deps.getPort("ok"), tasks.getPort("run"));
            rack.connect(tasks.getPort("ok"), serve.getPort("run"));
            // the serve URL feeds the quality gate; READY pulls its trigger
            rack.connect(serve.getPort("url"), vitals.getPort("url"));
            rack.connect(serve.getPort("ready"), vitals.getPort("run"));
            // MONITOR tapped on every lane
            rack.connect(deps.getPort("out"), console.getPort("in"));
            rack.connect(tasks.getPort("out"), console.getPort("in"));
            rack.connect(serve.getPort("out"), console.getPort("in"));
        }
    };

    private final String displayName;
    private final String description;

    RackPresets(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    abstract void wire(Rack rack);

    /** Serializes this preset to patch JSON, loadable via RackIO. */
    public JSONObject buildPatch() {
        return buildPatchFrom(this::wire);
    }

    // ---- shared patch-building machinery (also used by templates) ----

    /** Builds a patch JSON by wiring a throwaway rack. */
    public static JSONObject buildPatchFrom(Consumer<Rack> wiring) {
        Rack rack = new Rack();
        try {
            // Point the throwaway rack at an empty scratch dir: a fresh Rack
            // defaults to user.home, and devices that read the project on
            // attach (BLACKBOX's changed-since scan, REFLEX's watcher
            // baseline) must not walk the user's whole home directory just
            // to serialize a preset.
            rack.setProjectDir(scratchDir());
            wiring.accept(rack);
            return RackIO.toJson(rack);
        } finally {
            rack.shutdown();
        }
    }

    private static volatile File scratchDir;

    private static File scratchDir() {
        if (scratchDir == null) {
            try {
                File dir = java.nio.file.Files.createTempDirectory("nmox-preset").toFile();
                dir.deleteOnExit();
                scratchDir = dir;
            } catch (IOException ex) {
                scratchDir = new File(System.getProperty("java.io.tmpdir"));
            }
        }
        return scratchDir;
    }

    /**
     * Creates a device, applies state BEFORE racking it (so stateful
     * devices like REFLEX don't act on the throwaway rack), then mounts it.
     */
    public static RackDevice add(Rack rack, DeviceType type, Map<String, String> state) {
        RackDevice device = type.create();
        if (state != null) {
            device.applyState(state);
        }
        rack.addDevice(device);
        return device;
    }
}
