package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The project speaks ONE license. The 1.195.0 smoke test found the
 * README badge and License section claiming Apache-2.0 while LICENSE
 * and the NBM metadata (root pom licenseName) say MIT — a public
 * contradiction a contributor or packager has to resolve by guessing.
 * This gate pins all three public statements to MIT. Third-party
 * mentions of Apache (NetBeans Platform badge, vendored grammar
 * provenance, Apache httpd configs) are out of scope and untouched.
 */
class LicenseConsistencyGateTest {

    @Test
    @DisplayName("LICENSE, README badge, README license section, and NBM metadata all say MIT")
    void oneLicenseEverywhere() throws Exception {
        String license = Files.readString(Path.of("..", "LICENSE"));
        assertThat(license).startsWith("MIT License");

        String readme = Files.readString(Path.of("..", "README.md"));
        assertThat(readme)
                .as("README badge")
                .contains("License-MIT");
        int section = readme.indexOf("## License");
        assertThat(section).as("README has a License section").isGreaterThan(-1);
        String sectionText = readme.substring(section, Math.min(readme.length(), section + 400));
        assertThat(sectionText).contains("MIT License");
        assertThat(sectionText).doesNotContain("Apache License 2.0");

        String rootPom = Files.readString(Path.of("..", "pom.xml"));
        assertThat(rootPom)
                .as("NBM license metadata")
                .contains("<licenseName>MIT License</licenseName>");
    }
}
