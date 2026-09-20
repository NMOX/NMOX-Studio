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
     * <p><b>What the compiler already proves, and what it does not.</b>
     * A typo'd resource PATH cannot reach here, in the old population
     * or the new: the platform's {@code CreateRegistrationProcessor}
     * calls {@code LayerBuilder.validateResource} and then READS the
     * grammar to extract its scope name, so a path that names nothing
     * fails javac ("Cannot find resource …") — measured in v2.186.0 by
     * planting one, which means the sentence this gate carried for four
     * years ("a typo would silently kill that language's highlighting")
     * was never the thing it could catch. What the compiler does not
     * see is the three laws below, all true today and all unheld until
     * now, over a population that includes the 55 registrations the
     * filename filter could not reach:
     *
     * <ol>
     *   <li>the registered grammar is in the BUILD OUTPUT, parses, and
     *       carries the scope the layer promises TM4E it will find —
     *       the processor resolves at compile time, the product loads
     *       {@code nbresloc:} out of the module jar;</li>
     *   <li>no editor mime carries two grammars: a second one on the
     *       same mime does not merge, it shadows, and only the running
     *       lexer would say which won;</li>
     *   <li>an {@code x-nmox-embed-*} mime carries its grammar and
     *       NOTHING else. {@link EmbeddedScopeGrammars} says so in
     *       prose — "Do not add editor bindings (CSL, loaders) to these
     *       mimes" — because those mimes exist only to put a scope in
     *       TM4E's registry for cross-grammar includes; no file
     *       resolves to one, so anything else registered there is
     *       machinery nothing can ever reach.</li>
     * </ol>
     */
    @org.junit.jupiter.api.Test
    @DisplayName("Every registered grammar ships under its scope, alone on its mime")
    void registeredGrammarsShipUnderTheirScope() throws Exception {
        java.nio.file.Path layer = java.nio.file.Path.of(
                "target/classes/META-INF/generated-layer.xml");
        assertThat(layer).as("the generated layer this module built").exists();
        javax.xml.parsers.DocumentBuilderFactory dbf =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        org.w3c.dom.Document doc = dbf.newDocumentBuilder().parse(layer.toFile());

        java.util.Map<String, String> scopeByResource = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<String>> grammarsByMime =
                new java.util.LinkedHashMap<>();
        java.util.List<String> strayOnEmbedMime = new java.util.ArrayList<>();
        int[] registrations = {0};
        walk(doc.getDocumentElement(), "", scopeByResource, grammarsByMime,
                strayOnEmbedMime, registrations);

        // 131 registrations across 88 distinct grammar files today: 76
        // from the per-language classes, 50 on embed-only mimes, and 5
        // more from NgTemplateGrammars (four injections into
        // text.html.basic plus the html grammar on text/x-ng-template).
        // The floor keeps a derivation that finds nothing — a renamed
        // attr, a moved layer — from passing vacuously
        assertThat(registrations[0])
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

        assertThat(grammarsByMime.entrySet().stream()
                .filter(e -> e.getValue().size() > 1).toList())
                .as("a mime with two grammars has one that never tokenizes anything")
                .isEmpty();

        assertThat(strayOnEmbedMime)
                .as("an embed-only mime is a scope for TM4E's registry, not an editor")
                .isEmpty();
    }

    /** Collects the layer's grammar registrations and what shares their mimes. */
    private static void walk(org.w3c.dom.Element folder, String path,
            java.util.Map<String, String> scopeByResource,
            java.util.Map<String, java.util.List<String>> grammarsByMime,
            java.util.List<String> strayOnEmbedMime, int[] registrations) {
        org.w3c.dom.NodeList children = folder.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof org.w3c.dom.Element child)) {
                continue;
            }
            String here = path + "/" + child.getAttribute("name");
            if ("folder".equals(child.getTagName())) {
                if (path.contains("/x-nmox-embed-")) {
                    strayOnEmbedMime.add(here); // an editor folder on a scope-only mime
                }
                walk(child, here, scopeByResource, grammarsByMime,
                        strayOnEmbedMime, registrations);
                continue;
            }
            String scope = attr(child, "textmate-grammar");
            if (scope == null) {
                // an embed-only mime's folder holds its grammar and no
                // editor machinery — see law 3
                if (path.contains("/x-nmox-embed-")) {
                    strayOnEmbedMime.add(here);
                }
                continue;
            }
            registrations[0]++;
            String url = child.getAttribute("url");
            assertThat(url).as(here + " declares a resource url").startsWith("nbresloc:/");
            scopeByResource.put(url.substring("nbresloc:".length()), scope);
            if (attr(child, "inject-to") == null) {
                // an injection rides the host grammar's scope, not a mime;
                // only a mime-bound grammar competes to tokenize a file
                grammarsByMime.computeIfAbsent(path, m -> new java.util.ArrayList<>())
                        .add(child.getAttribute("name"));
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
