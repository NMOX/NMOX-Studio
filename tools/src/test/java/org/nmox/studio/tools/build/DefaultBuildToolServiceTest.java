package org.nmox.studio.tools.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.nmox.studio.tools.build.BuildToolService.BuildToolType;

/**
 * Build-tool detection from what is actually on disk: a tool's config
 * file marks the project, package.json alone means NPM scripts, and an
 * empty directory is honestly UNKNOWN.
 */
class DefaultBuildToolServiceTest {

    private final DefaultBuildToolService service = new DefaultBuildToolService();

    private static void touch(File dir, String name) throws IOException {
        touch(dir, name, "// config");
    }

    private static void touch(File dir, String name, String content) throws IOException {
        Files.writeString(new File(dir, name).toPath(), content);
    }

    @Test
    @DisplayName("A webpack.config.js marks the project as Webpack")
    void detectsWebpack(@TempDir File dir) throws IOException {
        touch(dir, "webpack.config.js");
        assertThat(service.detectBuildTool(dir)).isEqualTo(BuildToolType.WEBPACK);
    }

    @Test
    @DisplayName("Vite is detected through either its JS or its TS config flavor")
    void detectsViteBothFlavors(@TempDir File js, @TempDir File ts) throws IOException {
        touch(js, "vite.config.js");
        assertThat(service.detectBuildTool(js)).isEqualTo(BuildToolType.VITE);

        touch(ts, "vite.config.ts");
        assertThat(service.detectBuildTool(ts)).isEqualTo(BuildToolType.VITE);
    }

    @Test
    @DisplayName("A .parcelrc marks the project as Parcel")
    void detectsParcel(@TempDir File dir) throws IOException {
        touch(dir, ".parcelrc");
        assertThat(service.detectBuildTool(dir)).isEqualTo(BuildToolType.PARCEL);
    }

    @Test
    @DisplayName("package.json alone means NPM scripts, even when it depends on vite")
    void packageJsonAloneIsNpmScripts(@TempDir File dir) throws IOException {
        touch(dir, "package.json",
                "{\"name\":\"app\",\"devDependencies\":{\"vite\":\"^5.0.0\"}}");
        assertThat(service.detectBuildTool(dir))
                .as("a dedicated config file, not a dependency entry, names the tool")
                .isEqualTo(BuildToolType.NPM_SCRIPTS);
    }

    @Test
    @DisplayName("A config file outranks package.json when both are present")
    void configFileOutranksManifest(@TempDir File dir) throws IOException {
        touch(dir, "package.json", "{\"name\":\"app\"}");
        touch(dir, "vite.config.js");
        assertThat(service.detectBuildTool(dir)).isEqualTo(BuildToolType.VITE);
    }

    @Test
    @DisplayName("With rival config files present, webpack outranks vite by catalog order")
    void catalogOrderBreaksTies(@TempDir File dir) throws IOException {
        touch(dir, "vite.config.js");
        touch(dir, "webpack.config.js");
        assertThat(service.detectBuildTool(dir)).isEqualTo(BuildToolType.WEBPACK);
    }

    @Test
    @DisplayName("An empty directory is honestly UNKNOWN, not defaulted to npm")
    void emptyDirectoryIsUnknown(@TempDir File dir) {
        assertThat(service.detectBuildTool(dir)).isEqualTo(BuildToolType.UNKNOWN);
    }

    @Test
    @DisplayName("Scripts are read from package.json's scripts block in declaration order")
    void scriptsAreParsedInOrder(@TempDir File dir) throws IOException {
        touch(dir, "package.json",
                "{\"name\":\"app\",\"scripts\":{"
                + "\"dev\":\"vite\",\"build\":\"vite build\",\"test\":\"vitest\"}}");
        assertThat(service.getAvailableScripts(dir))
                .containsExactly("dev", "build", "test");
    }

    @Test
    @DisplayName("No package.json, or one without scripts, yields an empty list rather than an error")
    void missingScriptsYieldEmptyList(@TempDir File bare, @TempDir File noScripts)
            throws IOException {
        assertThat(service.getAvailableScripts(bare)).isEmpty();

        touch(noScripts, "package.json", "{\"name\":\"app\"}");
        assertThat(service.getAvailableScripts(noScripts)).isEmpty();
    }

    @Test
    @DisplayName("A bundler declared only as a dependency does not override the package.json verdict")
    void bundlerDependencyDoesNotOverrideNpmScripts(@TempDir File webpack,
            @TempDir File vite, @TempDir File parcel) throws IOException {
        // Detection reads config files on disk, never package.json contents:
        // NPM_SCRIPTS claims package.json as its config file, so any
        // package.json-bearing project resolves to NPM_SCRIPTS. Only a
        // dedicated bundler config file (tested elsewhere) names the tool.
        touch(webpack, "package.json",
                "{\"name\":\"app\",\"devDependencies\":{\"webpack\":\"^5.0.0\"}}");
        assertThat(service.detectBuildTool(webpack)).isEqualTo(BuildToolType.NPM_SCRIPTS);

        touch(vite, "package.json",
                "{\"name\":\"app\",\"dependencies\":{\"vite\":\"^5.0.0\"}}");
        assertThat(service.detectBuildTool(vite)).isEqualTo(BuildToolType.NPM_SCRIPTS);

        touch(parcel, "package.json",
                "{\"name\":\"app\",\"devDependencies\":{\"parcel\":\"^2.0.0\"}}");
        assertThat(service.detectBuildTool(parcel)).isEqualTo(BuildToolType.NPM_SCRIPTS);
    }

    @Test
    @DisplayName("A package.json naming no known bundler is plain NPM scripts")
    void packageJsonWithoutBundlerIsNpmScripts(@TempDir File dir) throws IOException {
        touch(dir, "package.json",
                "{\"name\":\"app\",\"dependencies\":{\"react\":\"^18.0.0\"}}");
        assertThat(service.detectBuildTool(dir)).isEqualTo(BuildToolType.NPM_SCRIPTS);
    }

    @Test
    @DisplayName("Scripts with extra whitespace and mixed quoting are still parsed by name")
    void scriptsParsedDespiteWhitespace(@TempDir File dir) throws IOException {
        touch(dir, "package.json",
                "{ \"scripts\" : {  \"lint\" :\"eslint .\" ,  \"format\": \"prettier -w .\" } }");
        assertThat(service.getAvailableScripts(dir)).containsExactly("lint", "format");
    }

    @Test
    @DisplayName("stopAll is safe to call when nothing is running")
    void stopAllIsSafeWhenIdle() {
        // no processes were ever started; this must not throw
        service.stopAll();
        service.stopAll();
    }
}
