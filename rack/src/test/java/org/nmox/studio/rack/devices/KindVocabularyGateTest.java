package org.nmox.studio.rack.devices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
import org.nmox.studio.rack.model.Rack;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One vocabulary for "which toolchain runs and tests how" (debt ledger
 * 112). It used to live in four hand-kept mirrors — IGNITION's knob
 * array, its {@code ProjectKind -> String} switch and the inverse of
 * that switch, then the same shape again in VERITAS — which agreed only
 * by discipline, with both inverses answering {@code NODE} by default.
 * A missed arm therefore compiled, shipped, and ran a node command under
 * another toolchain's name instead of greying honestly; two tokens the
 * devices could already produce were missing from the knobs entirely.
 *
 * <p>The facts now live on {@link ProjectKind} beside {@code manifests()}
 * and the inverse is derived from {@code values()}. This gate holds four
 * laws over that arrangement:
 *
 * <ol>
 *   <li>the knob arrays keep their on-disk index order — a patch persists
 *       the knob POSITION, so a reorder silently re-dials saved racks;</li>
 *   <li>every {@code ProjectKind} either names a token or is written down
 *       here as naming none, with the reason — the population is derived
 *       from {@code values()}, so a new language fails the build until
 *       somebody decides;</li>
 *   <li>a kind that names no run target GREYS — it does not quietly run
 *       node;</li>
 *   <li>every token a device can produce is a token a user can dial, and
 *       every dialled token builds a command or is a blessed grey.</li>
 * </ol>
 */
class KindVocabularyGateTest {

    /**
     * IGNITION's TARGET knob, exactly as saved patches index it. The head
     * of this list is the v1.34-through-v2.185 order and MUST NOT move:
     * `.nmoxrack.json` stores the integer position, so inserting a
     * position re-dials every rack saved before it. New targets append.
     */
    private static final List<String> TARGET_ORDER = List.of(
            "auto", "node", "python", "go", "rust", "elixir", "erlang", "clojure",
            "swift", "dotnet", "dart", "scala", "haskell", "zig", "ocaml", "crystal",
            "maven", "gradle", "ruby", "php", "make", "bun", "deno", "static",
            "gleam", "julia", "nim", "dlang", "racket", "elm", "purescript", "vlang",
            "fortran", "ada", "cairo", "move", "aiken", "clarity", "tact",
            // appended v2.186.0
            "rescript", "webpack");

    /** VERITAS's RUNNER knob, exactly as saved patches index it. */
    private static final List<String> RUNNER_ORDER = List.of(
            "auto", "jest", "vitest", "mocha", "playwright", "cypress", "pytest",
            "cargo", "go", "mvn", "rspec", "phpunit", "mix", "rebar3", "clojure",
            "swift", "dotnet", "dart", "sbt", "stack", "zig", "dune", "crystal",
            "bun", "deno", "forge", "gleam", "julia", "nim", "dlang", "racket",
            "elm", "purescript", "vlang", "fortran", "ada", "cairo", "move",
            "aiken", "node",
            // appended v2.186.0
            "gradle", "rescript");

    /**
     * Kinds that name NO run target, each with the reason. A kind here
     * greys IGNITION; a kind missing from both this set and the
     * declarations fails the build.
     */
    private static final Map<ProjectKind, String> NO_RUN_TARGET = Map.of(
            ProjectKind.FOUNDRY, "a Foundry repo builds and tests contracts; "
                    + "there is no program to run",
            ProjectKind.LEARN, "a learning space's SOLDER driver is its toolchain; "
                    + "guessing `node index.js` for a COBOL space is the lie this "
                    + "vocabulary exists to stop telling",
            ProjectKind.NONE, "nothing is aimed at the rack — the refusal says so "
                    + "(CommandDevice.NO_MANIFEST) rather than naming a missing verb");

    /**
     * Kinds that name NO test runner of their own. Each falls through to
     * the project's own package.json harness, which is the honest answer
     * for a kind whose suite IS that harness — and for the classic-web
     * kinds, which have no suite at all.
     */
    private static final Set<ProjectKind> NO_TEST_RUNNER = EnumSet.of(
            ProjectKind.NODE,     // the runner is whatever package.json declares
            ProjectKind.CLARITY,  // vitest/simnet harness beside Clarinet.toml
            ProjectKind.TACT,     // jest/@ton/sandbox harness beside tact.config.json
            ProjectKind.CMAKE, ProjectKind.MAKE, // no test verb of their own
            ProjectKind.WEBPACK, ProjectKind.GRUNT, ProjectKind.GULP,
            ProjectKind.BOWER, ProjectKind.STATIC, // the classic web has no suite
            ProjectKind.LEARN, ProjectKind.NONE);

    /**
     * Runner tokens no kind names: the npm-resolved JavaScript runners a
     * Node project declares for itself, plus the AUTO sentinel. These are
     * the ONLY tokens allowed to fall back to the Node lane's directory.
     */
    private static final Set<String> NPM_FAMILY = Set.of(
            "jest", "vitest", "mocha", "playwright", "cypress", "node", "npm-script");

    /** Targets that build no command on purpose, with the reason. */
    private static final Map<String, String> GREY_TARGETS = Map.of(
            "tact", "Tact rides npm scripts and has no run verb",
            "rescript", "ReScript compiles but has no run entry point");

    /** Runners that build no command on purpose, with the reason. */
    private static final Map<String, String> GREY_RUNNERS = Map.of(
            "ada", "Alire has no universal test verb",
            "rescript", "ReScript has no standard test runner");

    @TempDir
    Path root;

    private int caseNo;

    private Path freshDir(String... files) throws IOException {
        Path dir = root.resolve("case-" + (caseNo++));
        Files.createDirectories(dir);
        for (String f : files) {
            Files.writeString(dir.resolve(f), "{}");
        }
        return dir;
    }

    // ---------------- law 1: the knob order is the on-disk contract ----------------

    @Test
    @DisplayName("The TARGET and RUNNER knobs keep their on-disk index order")
    void knobOrderIsTheOnDiskContract() {
        assertThat(RunDevice.targets())
                .as("a saved patch stores the TARGET knob's INDEX: reordering or "
                        + "inserting a position re-dials every rack saved before it")
                .containsExactlyElementsOf(TARGET_ORDER);
        assertThat(TestDevice.runners())
                .as("a saved patch stores the RUNNER knob's INDEX: reordering or "
                        + "inserting a position re-dials every rack saved before it")
                .containsExactlyElementsOf(RUNNER_ORDER);
    }

    // ---------------- law 2: every kind decides ----------------

    @Test
    @DisplayName("Every ProjectKind names a run target or is written down as naming none")
    void everyKindDecidesItsRunTarget() {
        List<ProjectKind> undecided = new ArrayList<>();
        for (ProjectKind kind : ProjectKind.values()) {
            boolean declared = kind.runTarget() != null;
            if (declared == NO_RUN_TARGET.containsKey(kind)) {
                undecided.add(kind);
            }
        }
        assertThat(undecided)
                .as("each of these either names an IGNITION target or belongs in "
                        + "NO_RUN_TARGET with a reason — adding a language is the "
                        + "most repeated change in this repository, and this is "
                        + "where it gets decided")
                .isEmpty();
    }

    @Test
    @DisplayName("Every ProjectKind names a test runner or is written down as naming none")
    void everyKindDecidesItsTestRunner() {
        List<ProjectKind> undecided = new ArrayList<>();
        for (ProjectKind kind : ProjectKind.values()) {
            boolean declared = kind.testRunner() != null;
            if (declared == NO_TEST_RUNNER.contains(kind)) {
                undecided.add(kind);
            }
        }
        assertThat(undecided)
                .as("each of these either names a VERITAS runner or belongs in "
                        + "NO_TEST_RUNNER, which means its tests ride the project's "
                        + "own npm harness")
                .isEmpty();
    }

    // ---------------- law 3: a kind with no target greys ----------------

    @Test
    @DisplayName("A kind that names no run target GREYS — it does not run a node command")
    void aKindWithNoRunTargetGreys() throws IOException {
        // FOUNDRY is the live instance: a foundry.toml with no package.json
        // resolved to "node" through the old default arm and IGNITE ran
        // `node index.js` in a Solidity repo.
        assertThat(runCommandOver("foundry.toml"))
                .as("a Foundry repo has no program to run — IGNITION greys instead "
                        + "of inventing a node command")
                .isNull();
        // an unaimed rack: NONE is not a toolchain with a missing verb
        assertThat(runCommandOver())
                .as("nothing detected, nothing to run").isNull();
        // and the refusal says the useful thing rather than "NO VERB FOR NONE"
        assertThat(refusalOver())
                .isEqualTo(CommandDevice.NO_MANIFEST);
    }

    @Test
    @DisplayName("Pressing IGNITE on a target-less kind refuses out loud — it does not throw")
    void greyingIsReachableFromTheButton() throws IOException {
        // the grey contract is a null command, and primaryAction used to
        // dereference it for its two lane flags: IGNITE on a Tact or
        // ReScript project threw a NullPointerException before it could
        // reach the refusal. Only AUTO could produce a null, so no test
        // had ever pressed the button on one.
        Rack rack = new Rack();
        rack.setProjectDir(freshDir("tact.config.json").toFile());
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.applyState(Map.of("target",
                    String.valueOf(RunDevice.targets().indexOf("tact"))));
            assertThat(run.buildCommand()).isNull();
            run.primaryAction(); // must refuse, not throw
            assertThat(run.isLive()).isFalse();
        } finally {
            rack.shutdown();
        }
    }

    @Test
    @DisplayName("Every kind's declared run target really is the one AUTO resolves")
    void autoResolvesEachKindToItsDeclaredTarget() throws IOException {
        // the forward direction, proven through the device rather than the
        // enum: a declaration nothing reads is not a vocabulary
        assertThat(runCommandOver("Cargo.toml")).startsWith("cargo");
        assertThat(runCommandOver("go.mod")).startsWith("go");
        assertThat(runCommandOver("fpm.toml")).startsWith("fpm");
        assertThat(testCommandOver("Cargo.toml")).startsWith("cargo");
        assertThat(testCommandOver("build.gradle")).startsWith("gradle");
        assertThat(testCommandOver("pom.xml")).startsWith("mvn");
    }

    // ---------------- law 4: the vocabulary is closed ----------------

    @Test
    @DisplayName("Every token a kind names is a position a user can dial")
    void everyProducibleTokenIsOnItsKnob() {
        for (ProjectKind kind : ProjectKind.values()) {
            if (kind.runTarget() != null) {
                assertThat(RunDevice.targets())
                        .as(kind + " resolves to '" + kind.runTarget()
                                + "', which nobody can pick by hand")
                        .contains(kind.runTarget());
            }
            if (kind.testRunner() != null) {
                assertThat(TestDevice.runners())
                        .as(kind + " resolves to '" + kind.testRunner()
                                + "', which nobody can pick by hand")
                        .contains(kind.testRunner());
            }
        }
    }

    @Test
    @DisplayName("Every RUNNER position is owned by a kind or is a declared npm-family runner")
    void everyRunnerIsOwnedOrNpmFamily() {
        for (String runner : TestDevice.runners()) {
            if ("auto".equals(runner)) {
                continue;
            }
            boolean owned = ProjectKind.forTestRunner(runner) != null;
            assertThat(owned || NPM_FAMILY.contains(runner))
                    .as("'" + runner + "' is owned by no kind and is not in the "
                            + "declared npm family, so commandDir's Node-lane "
                            + "fallback would adopt it silently")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Every TARGET position builds a command, or is a blessed grey")
    void everyTargetBuildsOrIsABlessedGrey() throws IOException {
        for (String target : RunDevice.targets()) {
            if ("auto".equals(target)) {
                continue;
            }
            List<String> cmd = runCommandForTarget(target);
            if (GREY_TARGETS.containsKey(target)) {
                assertThat(cmd).as(target + ": " + GREY_TARGETS.get(target)).isNull();
            } else {
                assertThat(cmd).as(target + " has a knob position and no command")
                        .isNotNull().isNotEmpty();
            }
        }
    }

    @Test
    @DisplayName("Every RUNNER position builds a command, or is a blessed grey")
    void everyRunnerBuildsOrIsABlessedGrey() throws IOException {
        for (String runner : TestDevice.runners()) {
            if ("auto".equals(runner)) {
                continue;
            }
            List<String> cmd = testCommandForRunner(runner);
            if (GREY_RUNNERS.containsKey(runner)) {
                assertThat(cmd).as(runner + ": " + GREY_RUNNERS.get(runner)).isEmpty();
            } else {
                assertThat(cmd).as(runner + " has a knob position and no command")
                        .isNotEmpty();
            }
        }
    }

    // ---------------- the derived inverse ----------------

    @Test
    @DisplayName("The inverse is derived, and the two shared tokens name their owners")
    void theInverseNamesTheGenericKind() {
        for (ProjectKind kind : ProjectKind.values()) {
            if (kind.runTarget() != null) {
                assertThat(ProjectKind.forRunTarget(kind.runTarget()).runTarget())
                        .isEqualTo(kind.runTarget());
            }
            if (kind.testRunner() != null) {
                assertThat(ProjectKind.forTestRunner(kind.testRunner()))
                        .as("no two kinds may name the same runner")
                        .isEqualTo(kind);
            }
        }
        // two run targets are named by several kinds; the owner is the
        // generic one, which precedence order already puts last
        assertThat(ProjectKind.forRunTarget("static"))
                .as("GRUNT/GULP/BOWER borrow `static`; serving a folder is STATIC's "
                        + "own verb, and a Makefile-less CMake tree must not steal "
                        + "the Gruntfile's directory")
                .isEqualTo(ProjectKind.STATIC);
        assertThat(ProjectKind.forRunTarget("make"))
                .as("CMAKE borrows `make`; the Makefile is MAKE's own")
                .isEqualTo(ProjectKind.MAKE);
        assertThat(ProjectKind.forRunTarget("nonesuch")).isNull();
        assertThat(ProjectKind.forRunTarget(null)).isNull();
        assertThat(ProjectKind.forTestRunner("jest"))
                .as("an npm-resolved runner is owned by no kind").isNull();
    }

    // ---------------- helpers ----------------

    private List<String> runCommandOver(String... files) throws IOException {
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

    private List<String> testCommandOver(String... files) throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir(files).toFile());
        try {
            TestDevice veritas = new TestDevice();
            rack.addDevice(veritas);
            return veritas.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    /** The LCD sentence a refused IGNITE speaks on an unaimed rack. */
    private String refusalOver() throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            return run.noCommandReason();
        } finally {
            rack.shutdown();
        }
    }

    private List<String> runCommandForTarget(String target) throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            RunDevice run = new RunDevice();
            rack.addDevice(run);
            run.applyState(Map.of("target",
                    String.valueOf(RunDevice.targets().indexOf(target))));
            return run.buildCommand();
        } finally {
            rack.shutdown();
        }
    }

    private List<String> testCommandForRunner(String runner) throws IOException {
        Rack rack = new Rack();
        rack.setProjectDir(freshDir().toFile());
        try {
            TestDevice veritas = new TestDevice();
            rack.addDevice(veritas);
            veritas.applyState(Map.of("framework",
                    String.valueOf(TestDevice.runners().indexOf(runner))));
            return veritas.buildCommand();
        } finally {
            rack.shutdown();
        }
    }
}
