package org.nmox.studio.tools.build;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The build-configuration builder: development-friendly defaults,
 * production-mode hardening that overrides explicit choices, and
 * faithful accumulation of environment and custom options.
 */
class BuildConfigurationTest {

    @Test
    @DisplayName("A bare builder yields development defaults: source maps on, minify off, dist on port 3000")
    void bareBuilderDefaults() {
        BuildConfiguration config = BuildConfiguration.builder().build();

        assertThat(config.getMode()).isEqualTo(BuildConfiguration.BuildMode.DEVELOPMENT);
        assertThat(config.isWatch()).isFalse();
        assertThat(config.isSourceMaps()).isTrue();
        assertThat(config.isMinify()).isFalse();
        assertThat(config.getOutputDir()).isEqualTo("dist");
        assertThat(config.getPublicPath()).isEqualTo("/");
        assertThat(config.getPort()).isEqualTo(3000);
        assertThat(config.isOpen()).isTrue();
        assertThat(config.getEnvironment()).isEmpty();
        assertThat(config.getCustomOptions()).isEmpty();
    }

    @Test
    @DisplayName("Production mode force-enables minify and strips source maps, even against explicit choices")
    void productionOverridesExplicitChoices() {
        BuildConfiguration config = BuildConfiguration.builder()
                .mode(BuildConfiguration.BuildMode.PRODUCTION)
                .minify(false)
                .sourceMaps(true)
                .build();

        assertThat(config.isMinify()).as("production always minifies").isTrue();
        assertThat(config.isSourceMaps()).as("production never ships source maps").isFalse();
    }

    @Test
    @DisplayName("Test and development modes leave minify and source maps exactly as configured")
    void nonProductionModesRespectChoices() {
        BuildConfiguration test = BuildConfiguration.builder()
                .mode(BuildConfiguration.BuildMode.TEST)
                .minify(false)
                .sourceMaps(true)
                .build();
        assertThat(test.isMinify()).isFalse();
        assertThat(test.isSourceMaps()).isTrue();

        BuildConfiguration dev = BuildConfiguration.builder()
                .minify(true)
                .sourceMaps(false)
                .build();
        assertThat(dev.isMinify()).isTrue();
        assertThat(dev.isSourceMaps()).isFalse();
    }

    @Test
    @DisplayName("Environment entries accumulate key by key; a later add wins over an earlier one")
    void environmentAccumulates() {
        BuildConfiguration config = BuildConfiguration.builder()
                .addEnvironment("API_URL", "http://localhost")
                .addEnvironment("DEBUG", "1")
                .addEnvironment("API_URL", "https://prod")
                .build();

        assertThat(config.getEnvironment())
                .hasSize(2)
                .containsEntry("API_URL", "https://prod")
                .containsEntry("DEBUG", "1");
    }

    @Test
    @DisplayName("A wholesale environment map replaces earlier per-key additions rather than merging")
    void environmentMapReplaces() {
        Map<String, String> replacement = new HashMap<>();
        replacement.put("ONLY", "me");

        BuildConfiguration config = BuildConfiguration.builder()
                .addEnvironment("OLD", "gone")
                .environment(replacement)
                .build();

        assertThat(config.getEnvironment()).containsOnlyKeys("ONLY");
    }

    @Test
    @DisplayName("Custom options accumulate and keep their value types")
    void customOptionsAccumulate() {
        BuildConfiguration config = BuildConfiguration.builder()
                .customOption("analyze", true)
                .customOption("target", "es2020")
                .build();

        assertThat(config.getCustomOptions())
                .hasSize(2)
                .containsEntry("analyze", true)
                .containsEntry("target", "es2020");
    }

    @Test
    @DisplayName("Every build mode maps to the lowercase value NODE_ENV expects")
    void modeValues() {
        assertThat(BuildConfiguration.BuildMode.DEVELOPMENT.getValue()).isEqualTo("development");
        assertThat(BuildConfiguration.BuildMode.PRODUCTION.getValue()).isEqualTo("production");
        assertThat(BuildConfiguration.BuildMode.TEST.getValue()).isEqualTo("test");
    }

    @Test
    @DisplayName("Watch, port, paths and open all carry through the builder unchanged")
    void settersCarryThrough() {
        BuildConfiguration config = BuildConfiguration.builder()
                .watch(true)
                .port(8080)
                .outputDir("build")
                .publicPath("/app/")
                .open(false)
                .build();

        assertThat(config.isWatch()).isTrue();
        assertThat(config.getPort()).isEqualTo(8080);
        assertThat(config.getOutputDir()).isEqualTo("build");
        assertThat(config.getPublicPath()).isEqualTo("/app/");
        assertThat(config.isOpen()).isFalse();
    }
}
