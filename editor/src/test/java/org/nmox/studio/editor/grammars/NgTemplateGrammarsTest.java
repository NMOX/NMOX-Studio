package org.nmox.studio.editor.grammars;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Angular template injection grammars (v1.217.0), pinned:
 * byte-identity to what was vendored (the full-file tripwire idiom —
 * a partial check let a wrong Alpine version through once, v1.92.1),
 * the injection wiring the platform actually reads, and the scope
 * contract between the five files.
 */
class NgTemplateGrammarsTest {

    private static final Path DIR =
            Path.of("src/main/resources/org/nmox/studio/editor/grammars");

    /** sha256 of each vendored file at vendor time (upstream: angular/vscode-ng-language-service main). */
    private static final Map<String, String> PINS = Map.of(
            "ng-template.tmLanguage.json",
            "123875ebd14c7057aa9e5228e6ce9d173c4abc449a884e0e1d8993a881ded901",
            "ng-expression.tmLanguage.json",
            "ea3d34fe734715305fc5a04e4f2bc0f6188871f13b84aff0f9f8fc149f7d8c3e",
            "ng-template-blocks.tmLanguage.json",
            "69d05ab37f883d7c265a0149044f3ea949cbfe336a7c48a74cf720f7ad90ce1e",
            "ng-let-declaration.tmLanguage.json",
            "97d6d32d9d2b2f41d514c4f0069c7fe43f024586b66001212aff789fe1588187",
            "ng-template-tag.tmLanguage.json",
            "b862cfed046f9ad88aa0b39ad1d6204dac22c5f1919f6fe070433b054c9d8d57");

    @Test
    @DisplayName("every vendored file is byte-identical to its pin")
    void vendoredBytesPinned() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (var e : PINS.entrySet()) {
            byte[] bytes = Files.readAllBytes(DIR.resolve(e.getKey()));
            assertThat(HexFormat.of().formatHex(md.digest(bytes)))
                    .as(e.getKey()).isEqualTo(e.getValue());
        }
    }

    @Test
    @DisplayName("each grammar declares the injection selector it was designed with")
    void injectionSelectorsIntact() throws IOException {
        for (String name : PINS.keySet()) {
            JSONObject g = new JSONObject(Files.readString(DIR.resolve(name)));
            assertThat(g.optString("injectionSelector"))
                    .as(name + " is an injection grammar")
                    .startsWith("L:text.html");
        }
    }

    @Test
    @DisplayName("every scope a grammar includes resolves to a registered grammar")
    void includedScopesResolve() throws IOException {
        // the scopes our registry can serve: each grammar file in the dir
        // declares one scopeName
        var known = new java.util.HashSet<String>();
        try (var files = Files.list(DIR)) {
            for (Path p : files.filter(f -> f.getFileName().toString()
                    .endsWith(".json")).toList()) {
                String scope = new JSONObject(Files.readString(p)).optString("scopeName");
                if (!scope.isEmpty()) {
                    known.add(scope);
                }
            }
        }
        for (String name : PINS.keySet()) {
            String src = Files.readString(DIR.resolve(name));
            var m = java.util.regex.Pattern
                    .compile("\"include\"\\s*:\\s*\"([^#\"][^\"]*)\"").matcher(src);
            while (m.find()) {
                String scope = m.group(1).split("#")[0];
                assertThat(known)
                        .as(name + " includes " + scope
                                + " — an unresolvable scope silently deadens the "
                                + "patterns that ride it (the html-inside-@if case)")
                        .contains(scope);
            }
        }
    }

    @Test
    @DisplayName("four injections into our html scope; expression.ng embed-only; the mime has a driver")
    void registrationsWired() throws IOException {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/grammars/NgTemplateGrammars.java"),
                StandardCharsets.UTF_8);
        for (String name : PINS.keySet()) {
            assertThat(src).contains(name);
        }
        // absolute classpath paths: GrammarInjectionRegistration validates
        // the path VERBATIM (decompiled), unlike GrammarRegistration's
        // package-relative resolution — a bare filename fails the build
        assertThat(src).contains("org/nmox/studio/editor/grammars/ng-template.tmLanguage.json");
        // FOUR, not five: upstream injects template/blocks/let/tag and
        // leaves expression.ng include-only — injecting it stomped host
        // HTML (a live <h1> tokenized as a TS relational operator)
        assertThat(src.split("injectTo = \\{\"text.html.basic\"\\}", -1).length - 1)
                .as("four injections, all into text.html.basic").isEqualTo(4);
        assertThat(src)
                .as("expression.ng is embed-only — reachable by include, never injected")
                .contains("mimeType = \"text/x-nmox-embed-ng-expression\"");
        // the mime's driver grammar: without it the TextMate lexer has
        // nothing to serve for text/x-ng-template
        assertThat(src).contains("mimeType = \"text/x-ng-template\"");
    }

    @Test
    @DisplayName("NOTICE carries all five with their pins")
    void noticeCarriesPins() throws IOException {
        String notice = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/grammars/NOTICE-grammars.md"));
        for (var e : PINS.entrySet()) {
            assertThat(notice).contains(e.getKey());
            assertThat(notice).contains(e.getValue());
        }
    }
}
