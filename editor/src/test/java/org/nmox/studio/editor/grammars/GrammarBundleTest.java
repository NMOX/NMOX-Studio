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
        "handlebars", "liquid", "nginx", "apache", "makefile", "proto", "prisma",
        "solidity", "vyper", "coffeescript", "gleam", "nim", "d", "racket", "elm", "rescript", "purescript", "vlang", "fortran",
        "smalltalk", "prolog", "tcl", "scheme", "ada", "pascal", "odin", "cobol", "haxe", "janet", "http"})
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
        "text/x-nginx-conf", "text/x-apache-conf", "text/x-makefile",
        "text/x-protobuf", "text/x-prisma", "text/x-solidity", "text/x-vyper", "text/coffeescript", "text/x-gleam",
        "text/x-nim", "text/x-d", "text/x-racket", "text/x-elm", "text/x-rescript", "text/x-purescript",
        "text/x-vlang", "text/x-fortran",
        // text/x-smalltalk deliberately absent: no line comment exists
        "text/x-prolog", "text/x-tcl", "text/x-scheme",
        "text/x-ada", "text/x-pascal", "text/x-odin", "text/x-cobol",
        "text/x-haxe", "text/x-janet", "text/x-http-request",
        "text/x-yaml", "text/x-toml", "text/x-dockerfile", "text/x-sql"})
    @DisplayName("Every code language has comment-toggle syntax")
    void commentSyntaxCovered(String mime) {
        assertThat(LanguageComments.lineCommentFor(mime)).as(mime).isNotNull();
    }

    /**
     * Every grammar the build REGISTERED must ship beside it, saying the
     * same scope name it was registered under.
     *
     * <p><b>Why the layer and not the sources.</b> Until v2.186.0 this
     * read {@code src/main/java/…/*Grammar.java} by FILENAME, so
     * {@link EmbeddedScopeGrammars} (49 registrations) and
     * {@link NgTemplateGrammars} (6, every Angular-template grammar)
     * were invisible — 55 of 131 outside a gate whose stated purpose
     * covers them. The generated layer states the real population:
     * every {@code @GrammarRegistration} and
     * {@code @GrammarInjectionRegistration} becomes one {@code <file>}
     * carrying a {@code textmate-grammar} attr, whatever class declared
     * it, so a new registration class is covered on the commit that
     * adds it. The derivation carries a non-empty floor because a
     * derivation that returns nothing makes every assertion below
     * vacuous.
     *
     * <p><b>What this can and cannot catch.</b> A typo'd resource PATH
     * cannot reach here: the platform's {@code CreateRegistrationProcessor}
     * calls {@code LayerBuilder.validateResource} and then reads the
     * grammar to extract its scope name, so a path that names nothing
     * fails javac with a {@code LayerGenerationException} — measured,
     * not assumed (v2.186.0). What is NOT compile-checked is whether
     * the bytes the processor read at compile time are the bytes that
     * SHIP: the processor resolves against the source path, the product
     * loads {@code nbresloc:} from the module jar. So the law here is
     * the packaged one — the registered resource is present in the
     * build output, parses, and its {@code scopeName} is the one the
     * layer promises TM4E it will find.
     */
    @org.junit.jupiter.api.Test
    @DisplayName("Every registered grammar ships, parses, and owns its declared scope")
    void registeredGrammarsShipUnderTheirScope() throws Exception {
        java.nio.file.Path layer = java.nio.file.Path.of(
                "target/classes/META-INF/generated-layer.xml");
        assertThat(layer).as("the generated layer this module built").exists();
        javax.xml.parsers.DocumentBuilderFactory dbf =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(layer.toFile());

        int registrations = 0;
        java.util.Map<String, String> scopeByResource = new java.util.LinkedHashMap<>();
        org.w3c.dom.NodeList files = doc.getElementsByTagName("file");
        for (int i = 0; i < files.getLength(); i++) {
            org.w3c.dom.Element file = (org.w3c.dom.Element) files.item(i);
            String scope = attr(file, "textmate-grammar");
            if (scope == null) {
                continue;
            }
            registrations++;
            String url = file.getAttribute("url");
            assertThat(url).as(file.getAttribute("name") + " declares a resource url")
                    .startsWith("nbresloc:/");
            scopeByResource.put(url.substring("nbresloc:".length()), scope);
        }

        // 131 registrations across 88 distinct grammar files today (76
        // from the per-language classes, 49 embed-only scopes, 6
        // Angular); the floor keeps a derivation that finds nothing —
        // a renamed attr, a moved layer — from passing vacuously
        assertThat(registrations)
                .as("grammar registrations derived from the generated layer")
                .isGreaterThan(110);
        assertThat(scopeByResource)
                .as("distinct grammar files behind those registrations")
                .hasSizeGreaterThan(70);

        for (java.util.Map.Entry<String, String> e : scopeByResource.entrySet()) {
            String resource = e.getKey();
            try (InputStream in = GrammarBundleTest.class.getResourceAsStream(resource)) {
                assertThat(in).as(resource + " ships beside its registration").isNotNull();
                String text = new String(in.readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                org.json.JSONObject json = new org.json.JSONObject(text);
                assertThat(json.optString("scopeName"))
                        .as(resource + " scopeName as the layer registered it")
                        .isEqualTo(e.getValue());
            }
        }
    }

    /** The {@code stringvalue} of a child {@code <attr name=…>}, or null. */
    private static String attr(org.w3c.dom.Element file, String name) {
        org.w3c.dom.NodeList attrs = file.getElementsByTagName("attr");
        for (int i = 0; i < attrs.getLength(); i++) {
            org.w3c.dom.Element a = (org.w3c.dom.Element) attrs.item(i);
            if (name.equals(a.getAttribute("name"))) {
                return a.getAttribute("stringvalue");
            }
        }
        return null;
    }
}
