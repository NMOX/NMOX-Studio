package org.nmox.studio.editor.grammars;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.tm4e.core.internal.oniguruma.OnigRegExp;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.grammars.GrammarDependenciesLoadGateTest.Registered;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every pattern of every registered grammar compiles in the engine the
 * editor runs (3.5.4).
 *
 * <p>TextMate grammars are written for Oniguruma. The editor runs TM4E over
 * joni, a Java port that compiles a look-behind only when each alternative
 * at its top level has a fixed length; recent Oniguruma takes more, and
 * upstream grammars use it. joni refuses such a pattern when the rule is
 * first needed and the platform's lexer does not catch the exception. For
 * Elixir and Haxe the pattern is among the first rules: in the published
 * 3.5.3 an Elixir file opened as an empty tab, with no text in it. A Svelte
 * file showed its text and lost its colour at the first {@code {#if}}.
 * Eighteen patterns in nine grammars, found by the test beside this one
 * tokenizing a single line of each grammar, and photographed in the product.
 *
 * <p>{@code scripts/rewrite-grammar-lookbehinds.py} holds the rewrites. This
 * gate compiles all of them, the fifteen thousand that never needed one
 * included, so a vendored grammar added or bumped fails here by name.
 */
class GrammarRegexesCompileGateTest {

    private record Pattern(String grammar, String where, String key, String source) {
    }

    private static void patterns(Object node, String grammar, String path, List<Pattern> out) {
        if (node instanceof JSONObject o) {
            for (String key : o.keySet()) {
                Object value = o.get(key);
                boolean regex = key.equals("match") || key.equals("begin") || key.equals("end") || key.equals("while");
                if (regex && value instanceof String source) {
                    out.add(new Pattern(grammar, path, key, source));
                } else {
                    patterns(value, grammar, path + "/" + key, out);
                }
            }
        } else if (node instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) {
                patterns(a.get(i), grammar, path + "[" + i + "]", out);
            }
        }
    }

    /**
     * An end or while pattern may refer to what its begin captured
     * ({@code \1}); the engine puts the captured text in before compiling.
     */
    private static String asCompiled(Pattern p) {
        boolean referring = p.key().equals("end") || p.key().equals("while");
        return referring ? p.source().replaceAll("\\\\(\\d+)", "x") : p.source();
    }

    private static List<String> refused(List<Pattern> all) {
        List<String> refused = new ArrayList<>();
        for (Pattern p : all) {
            try {
                new OnigRegExp(asCompiled(p));
            } catch (RuntimeException e) {
                String why = String.valueOf(e.getMessage());
                int at = why.lastIndexOf("failed with");
                refused.add(p.grammar() + " " + p.where() + "/" + p.key() + ": " + p.source()
                        + " — " + (at < 0 ? why : why.substring(at)));
            }
        }
        return refused;
    }

    @Test
    @DisplayName("the gate reads match, begin, end and while, wherever a rule can be")
    void theGateReadsEveryPattern() {
        String unbounded = "(?<=a\\\\s*)b";
        JSONObject grammar = new JSONObject("""
                {"scopeName": "fixture",
                 "patterns": [{"match": "%1$s"}, {"begin": "%1$s", "end": "ok"}],
                 "injections": {"L:x": {"patterns": [{"begin": "ok", "end": "%1$s"}]}},
                 "repository": {"r": {"begin": "ok", "while": "%1$s",
                     "beginCaptures": {"1": {"patterns": [{"match": "%1$s"}]}}},
                   "refers": {"begin": "(a)", "end": "\\\\1"}}}
                """.formatted(unbounded));
        List<Pattern> all = new ArrayList<>();
        patterns(grammar, "fixture.json", "", all);

        List<String> refused = refused(all);
        assertThat(refused).as("one under each key, and one inside a capture").hasSize(5)
                .allMatch(line -> line.contains("invalid pattern in look-behind"));
        assertThat(refused).anyMatch(line -> line.contains("/match:"))
                .anyMatch(line -> line.contains("/begin:"))
                .anyMatch(line -> line.contains("/end:"))
                .anyMatch(line -> line.contains("/while:"))
                .anyMatch(line -> line.contains("beginCaptures"));
        assertThat(refused).as("an end that refers to its begin's capture is not a refusal")
                .noneMatch(line -> line.contains("refers"));
    }

    @Test
    @DisplayName("every pattern of every grammar this module registers compiles")
    void everyPatternCompiles() throws Exception {
        Registered set = GrammarDependenciesLoadGateTest.registered();
        List<Pattern> all = new ArrayList<>();
        int grammars = 0;
        for (var registration : set.resources().entrySet()) {
            if (!registration.getValue().startsWith("/org/nmox/")) {
                continue; // the platform's Markdown grammar is the platform's to keep
            }
            grammars++;
            try (InputStream in = set.open(registration.getKey())) {
                JSONObject grammar = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                String file = registration.getValue().substring(registration.getValue().lastIndexOf('/') + 1);
                patterns(grammar, file, "", all);
            }
        }
        assertThat(grammars).as("grammars read").isGreaterThan(120);
        assertThat(all.size()).as("patterns read").isGreaterThan(10_000);

        assertThat(refused(all))
                .as("patterns the editor's regex engine refuses; the editor throws where one is needed "
                        + "(scripts/rewrite-grammar-lookbehinds.py holds the rewrites)")
                .isEmpty();
    }

    private static String scopesOf(List<String> tokens, String text) {
        return tokens.stream().filter(t -> t.startsWith(text + " → ")).findFirst()
                .orElseThrow(() -> new AssertionError("no token '" + text + "' in " + tokens));
    }

    @Test
    @DisplayName("an Elixir file is coloured, and a function piped into without parentheses is a call")
    void elixirPipes() throws Exception {
        Registered set = GrammarDependenciesLoadGateTest.registered();
        List<String> tokens = GrammarDependenciesLoadGateTest.tokenize(set, "source.elixir",
                "list", "|> inspect", "|>    count", "|>      far");
        assertThat(scopesOf(tokens, "inspect")).contains("entity.name.function.call.local.pipe.elixir");
        assertThat(scopesOf(tokens, "count")).as("up to four characters of whitespace behind the name")
                .contains("entity.name.function.call.local.pipe.elixir");
        assertThat(scopesOf(tokens, "far")).as("past the bound it is a name like any other")
                .contains("variable.other.readwrite.elixir");
    }

    @Test
    @DisplayName("a Haxe file is coloured, and a method's return type is still a type")
    void haxeReturnTypes() throws Exception {
        Registered set = GrammarDependenciesLoadGateTest.registered();
        List<String> tokens = GrammarDependenciesLoadGateTest.tokenize(set, "source.hx",
                "class Main {", "  static function main():Void {", "  }", "}");
        assertThat(scopesOf(tokens, "main")).contains("entity.name.function.hx");
        // the fallback rule is switched off; the method rule's own return rule does this
        assertThat(scopesOf(tokens, ":")).contains("keyword.operator.type.annotation.hx");
        assertThat(scopesOf(tokens, "Void")).contains("support.class.builtin.hx");
    }

    @Test
    @DisplayName("a Svelte file's blocks hold TypeScript, and a directive's name is a function")
    void svelteBlocks() throws Exception {
        Registered set = GrammarDependenciesLoadGateTest.registered();
        List<String> tokens = GrammarDependenciesLoadGateTest.tokenize(set, "source.svelte",
                "{#if count > 0}", "  <p use:tooltip class:active={on}>x</p>", "{/if}",
                "{#each items as item}", "{@const double = item * 2}", "{/each}");
        assertThat(scopesOf(tokens, "count")).contains("meta.special.if.svelte")
                .contains("meta.embedded.expression.svelte").contains("variable.other.readwrite.ts");
        assertThat(scopesOf(tokens, "tooltip")).contains("variable.function.svelte");
        assertThat(scopesOf(tokens, "active")).contains("entity.other.attribute-name.class.svelte");
        assertThat(scopesOf(tokens, "items")).contains("meta.special.each.svelte");
        assertThat(scopesOf(tokens, "double")).contains("meta.special.const.svelte");
    }

    @Test
    @DisplayName("PureScript's data constructors, Haskell's foreign names and a using declaration")
    void theOtherThree() throws Exception {
        Registered set = GrammarDependenciesLoadGateTest.registered();
        List<String> purs = GrammarDependenciesLoadGateTest.tokenize(set, "source.purescript",
                "data Color = Red | Green", "data Shape", "  = Circle Number", "  | Square Number");
        for (String constructor : List.of("Red", "Green", "Circle", "Square")) {
            assertThat(scopesOf(purs, constructor)).contains("entity.name.tag.purescript");
        }

        List<String> hs = GrammarDependenciesLoadGateTest.tokenize(set, "source.haskell",
                "foreign import ccall \"math.h sin\" c_sin :: Double -> Double",
                "foreign import ccall unsafe", "    \"math.h cos\" c_cos :: Double -> Double");
        assertThat(scopesOf(hs, "c_sin")).contains("entity.name.function.haskell");
        assertThat(scopesOf(hs, "c_cos")).as("on a continuation line, behind the string")
                .contains("entity.name.function.haskell");
        assertThat(scopesOf(hs, "math.h sin")).contains("entity.name.foreign.haskell");

        for (String scope : List.of("source.ts", "source.tsx", "source.js", "source.js.jsx")) {
            String suffix = scope.substring(scope.indexOf('.') + 1);
            List<String> ts = GrammarDependenciesLoadGateTest.tokenize(set, scope,
                    "await using res = open();", "using file = open();", "const x = 1;");
            assertThat(scopesOf(ts, "await using")).as(scope).contains("storage.type." + suffix);
            assertThat(scopesOf(ts, "using")).as(scope).contains("storage.type." + suffix);
            assertThat(scopesOf(ts, "x")).as("the declaration ended where it should: " + scope)
                    .contains("variable.other.constant." + suffix);
        }
    }
}
