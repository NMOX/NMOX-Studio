package org.nmox.studio.tools.npm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.spi.project.ActionProvider;
import org.nmox.studio.rack.devices.PreflightPlan;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ship gate must never silently skip a toolchain the IDE lanes
 * speak. v1.161.0 proved the failure mode: when CLARITY began
 * outranking NODE, PreflightPlan's NODE case stopped matching Clarinet
 * repos and the whole npm ship plan vanished (fixed in v1.162.0). This
 * gate makes that class structural: for every kind where
 * WebProjectCommands defines a Test or Build command, PreflightPlan
 * must plan at least one check driven by the SAME tool (argv[0]).
 * Flags may differ — preflight release-hardens builds deliberately —
 * but the tool may not drift and the coverage may not vanish.
 */
class PreflightLaneParityTest {

    @TempDir
    Path root;

    /**
     * Kinds deliberately outside the gate, each with its reason:
     * task-runner/target-unknowable kinds have no honest ship checks
     * (a false RED gate is worse than no gate), TACT is npm-carried
     * (NODE is primary for every real Tact repo), and NONE is nothing.
     */
    private static final Set<ProjectKind> OUT_OF_GATE = Set.of(
            // LEARN (v2.58.0): a learning space's manifest is its marker
            // file and its toolchain is the rack's pre-wired driver — the
            // IDE has no Run/Build/Test verb for it by design
            ProjectKind.LEARN,
            ProjectKind.NONE, ProjectKind.STATIC, ProjectKind.MAKE,
            ProjectKind.CMAKE, ProjectKind.WEBPACK, ProjectKind.GRUNT,
            ProjectKind.GULP, ProjectKind.BOWER, ProjectKind.TACT);

    /**
     * Files that make a directory detect as the given kind — including
     * the conditioning files PreflightPlan probes before planning a
     * check (Rakefile, phpunit.xml, test/, main.rkt, src/Main.elm), so
     * the gate exercises the fullest honest plan each kind can have.
     */
    private static Map<String, String> fixtureFor(ProjectKind kind) {
        return switch (kind) {
            case NIM -> Map.of("app.nimble", "# glob-detected");
            case DOTNET -> Map.of("app.csproj", "<Project/>");
            case NODE -> Map.of("package.json",
                    "{\"scripts\":{\"test\":\"vitest run\",\"build\":\"vite build\"}}");
            case CLARITY -> Map.of(
                    "Clarinet.toml", "[project]\n",
                    "package.json", "{\"scripts\":{\"test\":\"vitest run\"}}");
            case RUBY -> Map.of("Gemfile", "source 'https://rubygems.org'",
                    "Rakefile", "task :test");
            case PHP -> Map.of("composer.json", "{}",
                    "phpunit.xml", "<phpunit/>");
            case DART -> Map.of("pubspec.yaml", "name: app",
                    "test/app_test.dart", "void main() {}");
            case RACKET -> Map.of("info.rkt", "#lang info",
                    "main.rkt", "#lang racket");
            case ELM -> Map.of("elm.json", "{}",
                    "src/Main.elm", "module Main exposing (..)");
            default -> Map.of(kind.manifest(), "# test manifest");
        };
    }

    @Test
    @DisplayName("Every lane-covered toolchain keeps a same-tool ship check (v1.163.0)")
    void preflightCoversEveryLaneToolchain() throws IOException {
        for (ProjectKind kind : ProjectKind.values()) {
            if (OUT_OF_GATE.contains(kind) || kind.manifest().isEmpty()
                    && kind != ProjectKind.NIM && kind != ProjectKind.DOTNET) {
                continue;
            }
            Path dir = Files.createDirectories(root.resolve(kind.name().toLowerCase()));
            for (var e : fixtureFor(kind).entrySet()) {
                Path f = dir.resolve(e.getKey());
                Files.createDirectories(f.getParent());
                Files.writeString(f, e.getValue());
            }
            File d = dir.toFile();
            Set<String> plannedTools = PreflightPlan.forProject(d).stream()
                    .map(c -> c.command().get(0))
                    .collect(Collectors.toSet());
            for (String action : new String[]{
                ActionProvider.COMMAND_TEST, ActionProvider.COMMAND_BUILD}) {
                List<String> lane = WebProjectCommands.commandFor(d, kind, action);
                if (lane == null || lane.isEmpty()) {
                    continue;
                }
                assertThat(plannedTools)
                        .as("kind %s: the IDE %s lane speaks '%s' but PREFLIGHT "
                                + "plans no check with that tool — the ship gate "
                                + "silently skips a toolchain (the v1.161.0 "
                                + "CLARITY-starvation class)",
                                kind, action, lane.get(0))
                        .contains(lane.get(0));
            }
        }
    }
}
