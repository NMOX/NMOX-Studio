package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;

import static org.nmox.studio.rack.projectstudio.RackPresets.add;

/**
 * The rack a project starts with, decided by what the project IS (v2.176.0).
 *
 * <p>Two doors mount a starter rack and they must agree. The New Project wizard
 * has written a pre-wired {@code .nmoxrack.json} beside every template since
 * v1.0 — a Rust crate gets IGNITION/VERITAS/INSPECTOR on REFLEX, a Vite site gets
 * SURGE feeding SCOPE with CRATE→FORGE→VERITAS behind MASTER. A project opened
 * from disk with no patch got none of that: since v1.278.0 it reset to the bare
 * starter (one MONITOR), the same as a first launch, because the alternative —
 * inheriting the PREVIOUS project's devices — was a lie the persona walk caught.
 * The bare rack was the honest choice between two; this class adds the third:
 * the patchless project gets the rack its kind would have been born with.
 *
 * <p>So the wirings live HERE, once, and both doors read them: the wizard
 * serializes {@code buildPatch()} into the new project, and
 * {@code RackService.resetToStarterRack} mounts {@link #forProject} for an
 * opened one. A template rack and a detected-kind rack cannot drift apart,
 * because they are the same object (the v2.131.0 second-home law).
 *
 * <p>It is a STARTER, not a preset: nothing is saved until the user presses
 * Save Patch, exactly as before. Two kinds keep the bare rack on purpose
 * ({@link #BARE}): a learning space writes its own driver patch on creation, and
 * a directory with no manifest at all has no toolchain to wire.
 */
public final class StarterRacks {

    /** A named wiring, so the log can say which starter a project received. */
    public record Starter(String id, Consumer<Rack> wiring) {
        /** Serializes the wiring to patch JSON, loadable via {@code RackIO}. */
        public JSONObject buildPatch() {
            return RackPresets.buildPatchFrom(wiring);
        }
    }

    /**
     * Kinds that deliberately keep the bare starter. LEARN: the learning-space
     * driver writes the space's own patch, and a patchless learning directory is
     * one someone emptied on purpose. NONE: no manifest, no toolchain to wire —
     * and this is also every fresh {@code ~/NMOX} workspace, so the first thing a
     * new user sees stays the one honest MONITOR.
     */
    static final Set<ProjectKind> BARE = EnumSet.of(ProjectKind.LEARN, ProjectKind.NONE);

    /**
     * Run / debug / test on a save loop, for any toolchain IGNITION, INSPECTOR
     * and VERITAS speak (which since v1.163.0 is every kind with a run or test
     * verb). A device whose lane has nothing for this kind greys honestly rather
     * than lying — INSPECTOR reads "NO DEBUGGER FOR <KIND>" (v1.77.1).
     */
    static final Consumer<Rack> POLYGLOT = rack -> {
        RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "false", "filter", "1"));
        RackDevice run = add(rack, DeviceType.RUN, null);
        RackDevice debug = add(rack, DeviceType.DEBUG, null);
        RackDevice test = add(rack, DeviceType.TEST, null);
        RackDevice console = add(rack, DeviceType.CONSOLE, null);
        wire(rack, reflex.getPort("changed"), test.getPort("run"));
        wire(rack, test.getPort("out"), console.getPort("in"));
        wire(rack, run.getPort("out"), console.getPort("in"));
        wire(rack, debug.getPort("out"), console.getPort("in"));
    };

    /** The Vite family: SURGE serves into SCOPE, MASTER runs install→build→test, REFLEX re-tests on save. */
    static final Consumer<Rack> VITE = rack -> {
        RackDevice master = add(rack, DeviceType.MASTER, null);
        RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "false", "filter", "1"));
        RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
        RackDevice server = add(rack, DeviceType.DEV_SERVER, Map.of("server", "1", "port", "2"));
        RackDevice browser = add(rack, DeviceType.BROWSER, Map.of("url", "http://localhost:5173"));
        RackDevice build = add(rack, DeviceType.BUILD, Map.of("tool", "1"));
        RackDevice test = add(rack, DeviceType.TEST, Map.of("framework", "2"));
        RackDevice console = add(rack, DeviceType.CONSOLE, null);
        wire(rack, master.getPort("trig1"), deps.getPort("run"));
        wire(rack, deps.getPort("ok"), build.getPort("run"));
        wire(rack, build.getPort("ok"), test.getPort("run"));
        wire(rack, test.getPort("out"), console.getPort("in"));
        wire(rack, server.getPort("url"), browser.getPort("url"));
        wire(rack, server.getPort("ready"), browser.getPort("open"));
        wire(rack, reflex.getPort("changed"), test.getPort("run"));
    };

    /** Angular: HALO serves and opens SCOPE, CRATE installs, VERITAS re-runs on save. */
    static final Consumer<Rack> ANGULAR = rack -> {
        RackDevice halo = add(rack, DeviceType.ANGULAR, Map.of("prod", "true"));
        RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
        RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "false", "filter", "1"));
        RackDevice test = add(rack, DeviceType.TEST, null);
        RackDevice browser = add(rack, DeviceType.BROWSER, null);
        RackDevice console = add(rack, DeviceType.CONSOLE, null);
        wire(rack, halo.getPort("url"), browser.getPort("url"));
        wire(rack, halo.getPort("ready"), browser.getPort("open"));
        wire(rack, reflex.getPort("changed"), test.getPort("run"));
        wire(rack, test.getPort("out"), console.getPort("in"));
        wire(rack, halo.getPort("out"), console.getPort("in"));
    };

    /** A Node HTTP service: the dev server's READY pings its own health route. */
    static final Consumer<Rack> EXPRESS = rack -> {
        RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
        RackDevice server = add(rack, DeviceType.DEV_SERVER, Map.of("server", "0", "port", "0"));
        RackDevice ping = add(rack, DeviceType.HTTP,
                Map.of("url", "http://localhost:3000/health", "method", "0"));
        RackDevice console = add(rack, DeviceType.CONSOLE, null);
        wire(rack, server.getPort("ready"), ping.getPort("send"));
        wire(rack, ping.getPort("body"), console.getPort("in"));
        wire(rack, server.getPort("out"), console.getPort("in"));
        wire(rack, deps.getPort("out"), console.getPort("in"));
    };

    /**
     * Any other Node project: install, then a script of the user's choosing —
     * NPM-9000's SCRIPT knob lists exactly what package.json declares, which is
     * the only honest lane for a project whose server nobody here has seen.
     */
    static final Consumer<Rack> NODE = rack -> {
        RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
        RackDevice script = add(rack, DeviceType.NPM_SCRIPT, null);
        RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "false", "filter", "1"));
        RackDevice test = add(rack, DeviceType.TEST, null);
        RackDevice console = add(rack, DeviceType.CONSOLE, null);
        wire(rack, reflex.getPort("changed"), test.getPort("run"));
        wire(rack, deps.getPort("out"), console.getPort("in"));
        wire(rack, script.getPort("out"), console.getPort("in"));
        wire(rack, test.getPort("out"), console.getPort("in"));
    };

    /** A static site: serve it and open it, nothing to build. */
    static final Consumer<Rack> STATIC = rack -> {
        RackDevice server = add(rack, DeviceType.DEV_SERVER, Map.of("server", "2", "port", "4"));
        RackDevice browser = add(rack, DeviceType.BROWSER, Map.of("url", "http://localhost:8080"));
        RackDevice console = add(rack, DeviceType.CONSOLE, null);
        wire(rack, server.getPort("url"), browser.getPort("url"));
        wire(rack, server.getPort("ready"), browser.getPort("open"));
        wire(rack, server.getPort("out"), console.getPort("in"));
    };

    /** BEAM: deps first (mix deps.get), then the polyglot loop. */
    static final Consumer<Rack> ELIXIR = rack -> {
        RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
        RackDevice run = add(rack, DeviceType.RUN, null);
        RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "false", "filter", "1"));
        RackDevice test = add(rack, DeviceType.TEST, null);
        RackDevice console = add(rack, DeviceType.CONSOLE, null);
        wire(rack, deps.getPort("ok"), test.getPort("run"));
        wire(rack, reflex.getPort("changed"), test.getPort("run"));
        wire(rack, test.getPort("out"), console.getPort("in"));
        wire(rack, run.getPort("out"), console.getPort("in"));
    };

    /** Foundry: ANVIL is the loop's heart; VERITAS runs forge test on save. */
    static final Consumer<Rack> FOUNDRY = rack -> {
        RackDevice anvil = add(rack, DeviceType.LOCAL_CHAIN, null);
        RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "false", "filter", "1"));
        RackDevice test = add(rack, DeviceType.TEST, null);
        RackDevice console = add(rack, DeviceType.CONSOLE, null);
        wire(rack, reflex.getPort("changed"), test.getPort("run"));
        wire(rack, test.getPort("out"), console.getPort("in"));
        wire(rack, anvil.getPort("url"), console.getPort("in"));
    };

    /**
     * Connects two jacks or throws — a starter that names a jack a device does not
     * have, or wires two that cannot meet, is a defect in THIS file, and
     * {@code Rack.connect} answering null would otherwise mount the rack one cable
     * short with nothing said (the first mutant of {@code StarterRacksTest} did
     * exactly that and lived).
     */
    private static void wire(Rack rack, Port from, Port to) {
        if (from == null || to == null || rack.connect(from, to) == null) {
            throw new IllegalStateException("starter rack cannot cable "
                    + (from == null ? "<missing>" : from.getDevice().getTypeId() + "." + from.getId()) + " -> "
                    + (to == null ? "<missing>" : to.getDevice().getTypeId() + "." + to.getId()));
        }
    }

    private StarterRacks() {
    }

    /** The starter for the project at {@code projectDir}, or empty for a kind in {@link #BARE}. */
    public static Optional<Starter> forProject(File projectDir) {
        return forKind(ProjectInspector.detectKind(projectDir), projectDir);
    }

    /**
     * The starter for a kind. Every {@link ProjectKind} answers here or sits in
     * {@link #BARE} with its reason — {@code StarterRacksTest} fails the build on a
     * kind that does neither, so a toolchain added tomorrow cannot fall through to
     * the bare rack by accident (the v1.233.0 completeness-gate shape).
     */
    static Optional<Starter> forKind(ProjectKind kind, File projectDir) {
        if (BARE.contains(kind)) {
            return Optional.empty();
        }
        return Optional.of(switch (kind) {
            case NODE -> nodeStarter(projectDir);
            case PHP -> new Starter("lamp", RackPresets.LAMP_BENCH::wire);
            case STATIC, BOWER, GRUNT, GULP, WEBPACK -> new Starter("classic", RackPresets.CLASSIC_WEB::wire);
            case ELIXIR, ERLANG, GLEAM -> new Starter("beam", ELIXIR);
            case FOUNDRY -> new Starter("foundry", FOUNDRY);
            default -> new Starter("polyglot", POLYGLOT);
        });
    }

    /**
     * A Node project is one of four things, read from what it declares:
     * Angular by its {@code angular.json}, a Vite site by the dependency, an
     * HTTP service by its framework, else a package with scripts.
     */
    private static Starter nodeStarter(File projectDir) {
        if (ProjectInspector.hasAngular(projectDir)) {
            return new Starter("angular", ANGULAR);
        }
        if (ProjectInspector.firstDependency(projectDir, "vite") != null) {
            return new Starter("vite", VITE);
        }
        if (ProjectInspector.firstDependency(projectDir, "express", "fastify", "koa", "hono", "@hapi/hapi") != null) {
            return new Starter("express", EXPRESS);
        }
        return new Starter("node", NODE);
    }
}
