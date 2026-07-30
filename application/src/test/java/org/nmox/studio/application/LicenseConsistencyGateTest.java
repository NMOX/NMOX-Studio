package org.nmox.studio.application;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One project, one license story, everywhere it is told. The v1.195.1
 * smoke test caught LICENSE and the docs disagreeing; v1.207.0
 * relicensed the project to Apache-2.0 (David's call — the app builds
 * on the Apache NetBeans Platform and now shares its license). This
 * gate pins every public statement of the license to Apache-2.0, and
 * — the lesson of the second mismatch, found in the user guide — the
 * DOCS are part of the public statement too. Third-party vendored
 * components keep their own licenses in their own NOTICE files; that
 * is out of scope here.
 */
class LicenseConsistencyGateTest {

    private static String read(String first, String... more) throws Exception {
        return Files.readString(Path.of(first, more));
    }

    @Test
    @DisplayName("LICENSE is the Apache License 2.0 and NOTICE names the project")
    void licenseAndNotice() throws Exception {
        String license = read("..", "LICENSE");
        assertThat(license).contains("Apache License");
        assertThat(license).contains("Version 2.0, January 2004");
        assertThat(license)
                .as("the canonical text, not a summary")
                .contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION");
        String notice = read("..", "NOTICE");
        assertThat(notice).contains("NMOX Studio");
        assertThat(notice)
                .as("Apache convention: the NOTICE carries the copyright line")
                .contains("Copyright");
        assertThat(notice)
                .as("the platform this builds on is attributed")
                .contains("Apache NetBeans Platform");
    }

    @Test
    @DisplayName("README badge and license section say Apache 2.0")
    void readme() throws Exception {
        String readme = read("..", "README.md");
        assertThat(readme).contains("License-Apache_2.0");
        assertThat(readme).contains("Apache License, Version 2.0");
        assertThat(readme)
                .as("no stale MIT claim survives")
                .doesNotContain("MIT License");
    }

    @Test
    @DisplayName("NBM metadata (root pom licenseName) says Apache 2.0")
    void nbmMetadata() throws Exception {
        assertThat(read("..", "pom.xml"))
                .contains("<licenseName>Apache License, Version 2.0</licenseName>");
    }

    @Test
    @DisplayName("the SBOM the release publishes says Apache-2.0")
    void sbom() throws Exception {
        assertThat(read("..", ".github", "workflows", "release.yml"))
                .contains("\"id\": \"Apache-2.0\"");
    }

    @Test
    @DisplayName("the docs tell the same story — the user-guide drift class stays dead")
    void docs() throws Exception {
        String guide = read("..", "docs", "user-guide.md");
        assertThat(guide).contains("Apache 2.0");
        assertThat(guide)
                .as("a doc claiming a different license is how this gate was born")
                .doesNotContain("(MIT)");
    }
}
