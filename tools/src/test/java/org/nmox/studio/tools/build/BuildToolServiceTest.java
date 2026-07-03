package org.nmox.studio.tools.build;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nmox.studio.tools.build.BuildToolService.BuildToolType;

/**
 * The build-tool catalog: every concrete tool names itself and claims
 * the config files that betray its presence, with no two tools
 * claiming the same file.
 */
class BuildToolServiceTest {

    @Test
    @DisplayName("Every concrete tool declares at least one config file; only UNKNOWN declares none")
    void concreteToolsDeclareConfigFiles() {
        for (BuildToolType type : BuildToolType.values()) {
            if (type == BuildToolType.UNKNOWN) {
                assertThat(type.getConfigFiles()).as("UNKNOWN has nothing to detect").isNull();
            } else {
                assertThat(type.getConfigFiles()).as(type + " config files").isNotEmpty();
                assertThat(type.getConfigFiles()).as(type + " config files are real names")
                        .allMatch(f -> f != null && !f.isBlank());
            }
        }
    }

    @Test
    @DisplayName("No two tools claim the same config file, so detection can never be ambiguous")
    void configFilesAreUniqueAcrossTools() {
        Set<String> seen = new HashSet<>();
        for (BuildToolType type : BuildToolType.values()) {
            if (type.getConfigFiles() == null) {
                continue;
            }
            for (String file : type.getConfigFiles()) {
                assertThat(seen.add(file))
                        .as("config file '" + file + "' claimed twice")
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("Display names are human labels, not shouting enum constants")
    void displayNamesAreHumanLabels() {
        assertThat(BuildToolType.WEBPACK.getDisplayName()).isEqualTo("Webpack");
        assertThat(BuildToolType.VITE.getDisplayName()).isEqualTo("Vite");
        assertThat(BuildToolType.PARCEL.getDisplayName()).isEqualTo("Parcel");
        assertThat(BuildToolType.NPM_SCRIPTS.getDisplayName()).isEqualTo("NPM Scripts");
        assertThat(BuildToolType.UNKNOWN.getDisplayName()).isEqualTo("Unknown");
        for (BuildToolType type : BuildToolType.values()) {
            assertThat(type.getDisplayName()).as(type + " display name").isNotBlank();
        }
    }

    @Test
    @DisplayName("Vite is detectable by either its JS or its TS config flavor")
    void viteHasBothConfigFlavors() {
        assertThat(BuildToolType.VITE.getConfigFiles())
                .containsExactly("vite.config.js", "vite.config.ts");
    }

    @Test
    @DisplayName("NPM scripts fall back on package.json, the manifest every JS project has")
    void npmScriptsClaimPackageJson() {
        assertThat(BuildToolType.NPM_SCRIPTS.getConfigFiles()).containsExactly("package.json");
    }
}
