package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.function.Supplier;
import org.nmox.studio.rack.model.RackDevice;

/**
 * Catalog of every device that can be racked. The palette lists these;
 * patch persistence stores devices by their type id.
 */
public enum DeviceType {

    MASTER("master", "MAESTRO", "Master Control — fire whole pipelines", new Color(240, 196, 25), MasterControlDevice::new),
    REFLEX("reflex", "REFLEX", "File Watcher — fire pipelines on save", new Color(236, 106, 168), ReflexDevice::new),
    JOIN("join", "QUORUM", "Lane Join — barrier: fire OK when all lanes pass", new Color(90, 190, 210), JoinDevice::new),
    ROSETTA("rosetta", "ROSETTA", "Language Selector — steer AUTO knobs in mixed repos", new Color(64, 224, 178), RosettaDevice::new),
    RUN("run", "IGNITION", "Polyglot Runtime — run anything: node/go/rust/py/rb/php", new Color(255, 94, 58), RunDevice::new),
    DEBUG("debug", "INSPECTOR", "Debug Launcher — debug servers with attach endpoints", new Color(186, 85, 255), DebugDevice::new),
    ANGULAR("angular", "HALO", "Angular Console — serve/generate/update, stays current", new Color(0xDD, 0x00, 0x31), AngularDevice::new),
    PHOENIX("phoenix", "PHOENIX", "Phoenix Console — phx.server/gen/ecto, Hex currency", new Color(0xFD, 0x4F, 0x00), PhoenixDevice::new),
    NEXTJS("nextjs", "NEXUS", "Next.js Console — dev/build/start, registry currency", new Color(0xED, 0xED, 0xED), NextDevice::new),
    NPM_SCRIPT("npm-script", "NPM-9000", "Script Sequencer — run package.json scripts", new Color(203, 56, 55), NpmScriptDevice::new),
    PACKAGE_MANAGER("package-manager", "CRATE", "Package Manager — install & update deps", new Color(214, 121, 41), PackageManagerDevice::new),
    BUILD("build", "FORGE", "Build Engine — vite/webpack/rollup & co", new Color(232, 166, 35), BuildDevice::new),
    TEST("test", "VERITAS", "Test Harness — jest/vitest/mocha...", new Color(99, 197, 70), TestDevice::new),
    DEV_SERVER("dev-server", "SURGE", "Dev Server — start/stop local serving", new Color(64, 156, 255), DevServerDevice::new),
    LINT("lint", "PURITY", "Lint Filter — eslint/stylelint", new Color(168, 110, 221), LintDevice::new),
    FORMAT("format", "GLOSS", "Code Formatter — prettier", new Color(73, 196, 184), FormatDevice::new),
    GIT("git", "TIMELINE", "Git Sequencer — status/pull/commit/push", new Color(222, 78, 54), GitDevice::new),
    AUDIT("audit", "SENTRY", "Security Analyzer — npm audit meters", new Color(188, 42, 48), AuditDevice::new),
    DEPLOY("deploy", "LAUNCHPAD", "Deploy Output — armed deploys only", new Color(231, 52, 99), DeployDevice::new),
    HTTP("http", "PING", "Request Probe — HTTP smoke tests", new Color(96, 180, 100), HttpDevice::new),
    BROWSER("browser", "SCOPE", "Browser Link — open URLs on trigger", new Color(54, 174, 222), BrowserDevice::new),
    CONSOLE("console", "MONITOR", "Output Console — watch any OUT jack", new Color(80, 200, 120), ConsoleDevice::new),
    TERMINAL("terminal", "PHOSPHOR", "Scrollback Terminal — 5k lines, selectable", new Color(57, 255, 20), TerminalDevice::new),
    ENV("env", "ATMOS", "Env Mixer — NODE_ENV/CI/custom vars", new Color(120, 144, 220), EnvDevice::new),
    TEMPO("tempo", "TEMPO", "Step Sequencer — fire pipelines on a clock", new Color(255, 211, 105), TempoDevice::new),
    TYPECHECK("typecheck", "TYPEGUARD", "Type Checker — tsc, watch-aware", new Color(49, 120, 198), TypecheckDevice::new),
    TUNNEL("tunnel", "WORMHOLE", "Public Tunnel — cloudflared/ngrok/localtunnel", new Color(156, 89, 209), TunnelDevice::new),
    BENCH("bench", "GAUNTLET", "Load Bench — autocannon throughput", new Color(224, 122, 47), BenchDevice::new),
    DOCKER("docker", "HARBOR", "Docker Engine — panel, prune, status", new Color(36, 150, 237), DockerDevice::new),
    BLACKBOX("blackbox", "BLACKBOX", "Flight Recorder — session timeline, slow-creep alarm", new Color(255, 122, 36), BlackboxDevice::new),
    SONAR("sonar", "SONAR", "Port Radar — who owns every port, one-click kill", new Color(80, 220, 190), SonarDevice::new),
    PREFLIGHT("preflight", "PREFLIGHT", "Ship Check — git/tests/build/lint/audit, one verdict", new Color(126, 217, 87), PreflightDevice::new),
    DATABASE("database", "NEPTUNE", "Database Console — ping databases & trigger schemas", new Color(51, 153, 255), DatabaseDevice::new),
    CMD("cmd", "SOLDER", "Custom Command — run anything as a pipeline step", new Color(176, 141, 87), CommandLineDevice::new),
    TAIL("tail", "TAIL", "Log Follower — tail -f any file onto the patch bay", new Color(140, 190, 120), TailDevice::new),
    VITALS("vitals", "VITALS", "Web Quality Gate — Lighthouse scores with a shipping floor", new Color(255, 105, 97), VitalsDevice::new);

    private final String id;
    private final String title;
    private final String description;
    private final Color accent;
    private final Supplier<RackDevice> factory;

    DeviceType(String id, String title, String description, Color accent, Supplier<RackDevice> factory) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.accent = accent;
        this.factory = factory;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Color getAccent() {
        return accent;
    }

    /** The shelf groups devices by the job you're trying to do. */
    public enum PaletteCategory {
        AUTOMATE("Run & Automate"),
        VERIFY("Build & Verify"),
        SERVE("Serve & Expose"),
        FRAMEWORKS("Frameworks"),
        OBSERVE("Observe"),
        SHIP("Ship"),
        UTILITY("Utility");

        public final String label;

        PaletteCategory(String label) {
            this.label = label;
        }
    }

    public PaletteCategory getPaletteCategory() {
        return switch (this) {
            case MASTER, REFLEX, JOIN, TEMPO, RUN, NPM_SCRIPT, CMD -> PaletteCategory.AUTOMATE;
            case PACKAGE_MANAGER, BUILD, TEST, LINT, FORMAT, TYPECHECK, VITALS -> PaletteCategory.VERIFY;
            case DEV_SERVER, TUNNEL, BROWSER, HTTP, DATABASE -> PaletteCategory.SERVE;
            case ANGULAR, PHOENIX, NEXTJS -> PaletteCategory.FRAMEWORKS;
            case CONSOLE, TERMINAL, BENCH, DEBUG, BLACKBOX, SONAR, TAIL -> PaletteCategory.OBSERVE;
            case GIT, AUDIT, DEPLOY, DOCKER, PREFLIGHT -> PaletteCategory.SHIP;
            case ENV, ROSETTA -> PaletteCategory.UTILITY;
        };
    }

    /** Concrete patch recipes, shown in the How-to-use card. */
    public String getUsage() {
        return switch (this) {
            case MASTER -> "Press RUN SEQUENCE to fire all four TRIG outs at once.\nPatch TRIG 1 → CRATE RUN, then chain OK jacks: install → build → test.";
            case REFLEX -> "Flip WATCH on and every file save fires CHANGED.\nPatch CHANGED → VERITAS RUN for test-on-save; FILTER narrows to code/styles/docs, or GLOB to one lane (*.rs).";
            case JOIN -> "The barrier where lanes converge: ALL fires OK once every wired IN has arrived and all passed.\nPatch each lane's DONE → IN, OK → LAUNCHPAD. MODE=ANY relays the first instead.";
            case TEMPO -> "A clock: TICK fires at the dialed rate, BAR every 4th tick.\nGate it with ENABLE (patch SURGE RUNNING in) for health checks only while serving.";
            case RUN -> "Runs your project's main: cargo run, go run, mix run, python…\nTARGET=auto follows the detected toolchain; ARGS feed the command line.";
            case NPM_SCRIPT -> "One package.json script per press. SCRIPT knob lists your scripts.\nPatch OK into the next device to chain scripts into pipelines.";
            case PACKAGE_MANAGER -> "INSTALL readies dependencies - in mixed repos it sequences every toolchain.\nUPDATE upgrades, CHECK reports outdated. OK fires when done.";
            case BUILD -> "BUILD compiles with the detected tool (vite/cargo/mix/swift…).\nWATCH mode fires OK on every rebuild - patch OK → VERITAS for build-then-test.";
            case TEST -> "Runs the suite; the tally LCD shows live pass/fail.\nRUNNER=auto picks jest/pytest/cargo/mix… Patch OUT → MONITOR to read output.";
            case LINT -> "Static analysis; E/W counts land on the LCD, CLEAN lights when spotless.\nFIX rewrites violations in place (amber = it mutates your files).";
            case FORMAT -> "Prettier over the project. WRITE rewrites; CHECK only verifies.\nPatch REFLEX CHANGED → RUN for format-on-save.";
            case TYPECHECK -> "tsc --noEmit. WATCH keeps the compiler resident and fires OK/FAIL per check.\nSTRICT adds --strict. E: count on the LCD.";
            case DEV_SERVER -> "START serves your project; URL and READY outs feed SCOPE so the\nbrowser opens itself at the real address. RUNNING gates TEMPO nicely.";
            case TUNNEL -> "OPEN exposes a local port to the internet via cloudflared/ngrok.\nThe public URL lands on the LCD and the URL jack - patch into SCOPE to pop it.";
            case BROWSER -> "Opens the system browser at the dialed URL on OPEN or any trigger in.\nPatch a URL data jack in and SCOPE follows wherever the server actually is.";
            case HTTP -> "Fires one HTTP request; status + latency on the LCDs, OK/FAIL triggers out.\nPatch TEMPO TICK → SEND for a heartbeat monitor.";
            case ANGULAR -> "SERVE/BUILD/TEST drive ng; GEN scaffolds with the SCHEMATIC knob.\nThe version cluster nags when Angular moves - UPDATE runs ng update.";
            case PHOENIX -> "SERVER runs mix phx.server; GEN row drives phx.gen.*; MIGRATE runs ecto.\nVersion cluster tracks :phoenix against Hex.";
            case NEXTJS -> "DEV serves with the URL out feeding SCOPE; BUILD then START runs production.\nVersion cluster tracks next against the registry.";
            case CONSOLE -> "A glanceable 8-line screen. Patch any OUT (data) jack into IN,\nor dial TAP to stderr/all to hear every device unpatched — errors glow red.";
            case TERMINAL -> "5,000 lines of selectable scrollback. FOLLOW tails the output.\nPatch the OUT of anything chatty in here.";
            case BENCH -> "FIRE hammers the URL with autocannon; req/s on the meter.\nPatch SURGE URL → URL and READY → RUN to bench the second it serves.";
            case DEBUG -> "LAUNCH starts your runtime in debug-server mode; the attach\nendpoint (chrome://inspect, debugpy, dlv…) lands on the LCD.";
            case GIT -> "STATUS/PULL/COMMIT/PUSH with the branch on the LCD and a DIRTY light.\nAmber buttons mutate - the law of the rack.";
            case AUDIT -> "SCAN runs the security audit; severity ladders fill per class.\nSECURE lights green when the tree is clean.";
            case DEPLOY -> "Flip ARM, then LAUNCH deploys to the dialed target.\nUnarmed pads ignore even patched triggers - by design.";
            case ENV -> "Sets NODE_ENV/CI/custom vars for every command the rack runs.\nWhat the knob says is what every device gets.";
            case ROSETTA -> "Mixed repo? Pin every AUTO knob to one toolchain with the dial.\nAUTO follows detection; KIND out reports the choice.";
            case DOCKER -> "The ENGINE LED tracks the daemon; LCDs show containers up, images held, disk reclaimable.\nPANEL opens the full control room — containers, images, volumes, networks, and one-click Dockerize.";
            case BLACKBOX -> "The rack's session memory: every launch, exit, duration, and error, timestamped.\nVIEW scrolls the timeline; the health line warns when a build quietly slows past its average.";
            case SONAR -> "SWEEP maps every listening port to its owning process — docker containers labeled.\nVIEW opens the field: BROWSE any port, KILL any squatter. EADDRINUSE, solved.";
            case PREFLIGHT -> "CHECK runs the readiness list — git clean, tests, build, lint, audit — one LED per item.\nPatch OK → LAUNCHPAD RUN and unverified code physically cannot deploy.";
            case DATABASE -> "Pings SQL databases (PostgreSQL/MySQL/SQLite/MariaDB).\nDial DB TYPE to select database URL or profile; ping fires OK on success.";
            case CMD -> "Runs exactly what you type - make seed-db, ./scripts/fixtures.sh - argv only, no shell.\nPatch VERITAS OK → RUN to chain custom steps; SOLDER exports to CI like any device.";
            case TAIL -> "tail -f as a device: dial a file (relative to the project), flip FOLLOW.\nPatch OUT → PHOSPHOR and your server's logs/app.log scrolls beside its stdout.";
            case VITALS -> "Lighthouse headless against the dialed URL - PERF/A11Y/BEST/SEO on the meters.\nPatch SURGE URL → URL and READY → RUN; dial MIN and slow pages close the gate (FAIL, not OK).";
        };
    }

    public RackDevice create() {
        return factory.get();
    }

    public static DeviceType byId(String id) {
        for (DeviceType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
