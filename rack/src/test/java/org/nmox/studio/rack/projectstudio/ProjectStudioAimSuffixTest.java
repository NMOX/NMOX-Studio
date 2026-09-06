package org.nmox.studio.rack.projectstudio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The footer names the toolchain it detected, not the one manifest it
 * used to measure everything by (v2.85.0 — "(no package.json)" beside a
 * learning space on the walk).
 */
class ProjectStudioAimSuffixTest {

    @Test
    @DisplayName("Node stays bare; the detected manifest names itself; the last resorts speak plainly")
    void suffixSpeaksTheKind() {
        assertThat(ProjectStudioTopComponent.aimSuffix(ProjectKind.NODE)).isEmpty();
        assertThat(ProjectStudioTopComponent.aimSuffix(ProjectKind.RUST)).isEqualTo("  (Cargo.toml)");
        assertThat(ProjectStudioTopComponent.aimSuffix(ProjectKind.GO)).isEqualTo("  (go.mod)");
        assertThat(ProjectStudioTopComponent.aimSuffix(ProjectKind.DOTNET))
                .as("a glob-detected kind has no manifest name — the kind speaks").isEqualTo("  (dotnet)");
        assertThat(ProjectStudioTopComponent.aimSuffix(ProjectKind.LEARN)).isEqualTo("  (learning space)");
        assertThat(ProjectStudioTopComponent.aimSuffix(ProjectKind.STATIC)).isEqualTo("  (static site)");
        assertThat(ProjectStudioTopComponent.aimSuffix(ProjectKind.NONE)).isEqualTo("  (no manifest yet)");
        for (ProjectKind k : ProjectKind.values()) {
            assertThat(ProjectStudioTopComponent.aimSuffix(k))
                    .as("%s never says package.json about a non-Node aim", k)
                    .doesNotContain("package.json");
        }
    }
}
