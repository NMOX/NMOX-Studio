package org.nmox.studio.tools.npm;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every toolchain the rack can detect has a door into the platform.
 *
 * <p>A kind with no door is the quietest defect this factory has: the rack
 * reads the manifest, wires Run/Build/Test lanes for it, and the directory
 * still cannot be opened as a project — no {@code ActionProvider}, no F6, no
 * OpenProjects. <i>The lanes exist; the door doesn't.</i>
 *
 * <p>It has happened twice. v1.233.0 found it for {@code CMakeLists.txt} and
 * {@code Makefile} and fixed it by adding two names to a hand-written list of
 * sixty. The list then drifted again, and by this release {@code setup.py},
 * {@code Rakefile} and {@code bun.lockb} had no door — a Python repository
 * carrying only a {@code setup.py} could not be opened at all. The second fix
 * is not a longer list: {@code MANIFESTS} is derived from {@code ProjectKind},
 * and this gate keeps the derivation honest by asking the factory itself, not
 * by reading a copy of the answer.
 */
class ProjectKindDoorsTest {

    /**
     * The only kinds allowed to name no marker: two are found by file
     * extension rather than by a fixed name, and one is the absence of a kind.
     */
    private static final List<ProjectKind> MARKERLESS =
            List.of(ProjectKind.DOTNET, ProjectKind.NIM, ProjectKind.NONE);

    @Test
    @DisplayName("every marker a kind declares opens a project — a kind the rack can detect can be opened")
    void everyKindMarkerOpensAProject() {
        List<String> doorless = new ArrayList<>();
        for (ProjectKind kind : ProjectKind.values()) {
            if (MARKERLESS.contains(kind)) {
                continue;
            }
            for (String marker : kind.manifests()) {
                if (!WebProjectFactory.opensOn(marker)) {
                    doorless.add(kind + " is detected by " + marker
                            + ", which opens no project");
                }
            }
        }
        assertThat(doorless)
                .as("the rack wires lanes for these and the platform cannot open them")
                .isEmpty();
    }

    @Test
    @DisplayName("the markerless kinds really do declare no marker, so the exemption above stays honest")
    void theExemptedKindsAreStillMarkerless() {
        for (ProjectKind kind : MARKERLESS) {
            assertThat(kind.manifests())
                    .as("%s is exempt because it is glob-detected or absent; "
                            + "if it has gained a marker the exemption is now a hole", kind)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("the three markers that had no door are open now — setup.py, Rakefile, bun.lockb")
    void theThreeThatWereClosedAreOpen() {
        assertThat(WebProjectFactory.opensOn("setup.py")).isTrue();
        assertThat(WebProjectFactory.opensOn("Rakefile")).isTrue();
        assertThat(WebProjectFactory.opensOn("bun.lockb")).isTrue();
    }

    @Test
    @DisplayName("a last-resort marker is not walked up to — an index.html does not make a project of every folder holding one")
    void lastResortMarkersAreNotWalkedManifests() {
        assertThat(WebProjectFactory.walkedManifests())
                .as("walking ancestors for index.html would make a project of every directory with one")
                .doesNotContain("index.html", "index.htm", ".nmox-learn");
        // but they still open a project, through the root-only branch
        assertThat(WebProjectFactory.opensOn("index.html")).isTrue();
        assertThat(WebProjectFactory.opensOn(".nmox-learn")).isTrue();
    }

    @Test
    @DisplayName("the derived list is not empty and did not shrink — a derivation that returns nothing would make every gate above vacuous")
    void theDerivedListIsWholeAndNotEmpty() {
        assertThat(WebProjectFactory.walkedManifests())
                .as("the hand-written list this replaced held 60 names")
                .hasSizeGreaterThanOrEqualTo(60)
                .contains("package.json", "Cargo.toml", "go.mod", "CMakeLists.txt",
                        "angular.json", "Clarinet.toml");
    }
}
