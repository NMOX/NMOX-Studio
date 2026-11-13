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

    /**
     * The one license id {@code release.yml} writes by hand is the row it
     * appends for the vendored js-debug adapter, and that row states
     * js-debug's license, which is MIT ({@code js-debug/LICENSE}). The
     * v1.207.0 relicense swept that row to Apache-2.0 and this test then
     * pinned the literal as "the SBOM says Apache-2.0": the SBOM told
     * consumers a Microsoft MIT component was Apache-licensed, and the
     * gate held the mistake in place. The product's own license reaches
     * the SBOM from Maven's metadata, never from this file.
     */
    @Test
    @DisplayName("the SBOM row for the vendored js-debug adapter states js-debug's own license, MIT")
    void sbomVendoredAdapterKeepsItsOwnLicense() throws Exception {
        assertThat(read("..", "editor", "src", "main", "release", "jsdebug", "js-debug", "LICENSE"))
                .as("what the adapter's own license file says")
                .contains("MIT License");
        String workflow = read("..", ".github", "workflows", "release.yml").replace("\r\n", "\n");
        int row = workflow.indexOf("\"name\": \"vscode-js-debug\"");
        assertThat(row).as("the vendored adapter's SBOM row").isPositive();
        String component = workflow.substring(row, workflow.indexOf("}]'", row));
        assertThat(component)
                .as("a third-party component keeps its own license in the SBOM")
                .contains("\"licenses\": [{\"license\": {\"id\": \"MIT\"}}]")
                .doesNotContain("Apache-2.0");
    }

    /**
     * The row is three facts with two homes (the NOTICE beside the adapter
     * and the workflow): version, purl and sha256 agree, or a bump that
     * touched one file ships an SBOM naming an adapter that is not in the
     * box.
     */
    @Test
    @DisplayName("the SBOM row names the adapter version and sha256 its NOTICE records")
    void sbomRowAgreesWithTheVendoredNotice() throws Exception {
        String notice = read("..", "editor", "src", "main", "release", "jsdebug", "NOTICE");
        java.util.regex.Matcher version = java.util.regex.Pattern
                .compile("(?m)^\\s*Version:\\s+v(\\d+\\.\\d+\\.\\d+)\\s*$").matcher(notice);
        java.util.regex.Matcher sha = java.util.regex.Pattern
                .compile("(?m)^\\s*SHA-256:\\s+([0-9a-f]{64})\\s*$").matcher(notice);
        assertThat(version.find()).as("NOTICE states the version").isTrue();
        assertThat(sha.find()).as("NOTICE states the sha256").isTrue();
        String workflow = read("..", ".github", "workflows", "release.yml").replace("\r\n", "\n");
        int row = workflow.indexOf("\"name\": \"vscode-js-debug\"");
        String component = workflow.substring(row, workflow.indexOf("}]'", row));
        assertThat(component)
                .contains("\"version\": \"" + version.group(1) + "\"")
                .contains("pkg:github/microsoft/vscode-js-debug@v" + version.group(1) + "\"")
                .contains("\"content\": \"" + sha.group(1) + "\"");
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
