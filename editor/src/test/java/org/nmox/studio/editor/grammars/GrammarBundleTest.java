package org.nmox.studio.editor.grammars;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nmox.studio.editor.polyglot.LanguageComments;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The TextMate grammars must ship inside the module and be valid JSON
 * with a scopeName - a missing or mangled grammar would silently kill
 * highlighting for that whole language.
 */
class GrammarBundleTest {

    @ParameterizedTest
    @ValueSource(strings = {"java", "c", "cpp", "python", "ruby", "rust", "php", "shell", "json",
        "html", "css", "scss", "less",
        // the config layer: what a web repo is actually full of
        "ini", "ignore", "graphql", "vue", "svelte", "astro", "pug",
        "handlebars", "liquid", "nginx", "makefile", "proto", "prisma"})
    @DisplayName("Grammar resource exists and parses with a scopeName")
    void grammarShipsAndParses(String language) throws IOException {
        String resource = language + ".tmLanguage.json";
        try (InputStream in = GrammarBundleTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource + " on classpath").isNotNull();
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONObject json = new org.json.JSONObject(text);
            assertThat(json.getString("scopeName")).as(resource + " scopeName").isNotBlank();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/x-java", "text/x-c", "text/x-cpp", "text/x-rust",
        "text/x-php5", "text/x-go", "text/x-python", "text/x-ruby", "text/sh",
        "text/x-ini", "text/x-ignore", "text/x-graphql", "text/x-pug",
        "text/x-nginx-conf", "text/x-makefile", "text/x-protobuf", "text/x-prisma",
        "text/x-yaml", "text/x-toml", "text/x-dockerfile", "text/x-sql"})
    @DisplayName("Every code language has comment-toggle syntax")
    void commentSyntaxCovered(String mime) {
        assertThat(LanguageComments.lineCommentFor(mime)).as(mime).isNotNull();
    }

    /**
     * Every grammar registration class must point at a real bundled
     * resource - a typo in the annotation would silently kill that
     * language's highlighting at runtime.
     */
    @org.junit.jupiter.api.Test
    @DisplayName("Every @GrammarRegistration references a bundled grammar file")
    void registrationsReferenceRealFiles() throws Exception {
        java.io.File dir = new java.io.File("src/main/java/org/nmox/studio/editor/grammars");
        org.junit.jupiter.api.Assumptions.assumeTrue(dir.isDirectory());
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("grammar = \"([^\"]+)\"");
        for (java.io.File src : dir.listFiles((d, n) -> n.endsWith("Grammar.java"))) {
            String code = java.nio.file.Files.readString(src.toPath());
            java.util.regex.Matcher m = p.matcher(code);
            while (m.find()) {
                assertThat(GrammarBundleTest.class.getResource(m.group(1)))
                        .as(src.getName() + " -> " + m.group(1)).isNotNull();
            }
        }
    }
}
