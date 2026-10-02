package org.nmox.studio.editor.grammars;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.grammar.IStateStack;
import org.eclipse.tm4e.core.grammar.IToken;
import org.eclipse.tm4e.core.grammar.ITokenizeLineResult;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IRegistryOptions;
import org.eclipse.tm4e.core.registry.Registry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No shipped grammar keeps a repository inside a rule, because the engine
 * the editor runs cannot see one.
 *
 * <p>A TextMate rule may carry a {@code repository} of its own. VS Code's
 * engine resolves an {@code #include} against the nearest; TM4E looks only
 * at the grammar's top level, logs {@code CANNOT find rule for scopeName
 * [#parens]} and removes the rule that asked. The vendored Ruby grammar
 * keeps every percent literal's delimiters that way, so {@code %w(a b)},
 * {@code %i[x y]}, {@code %q{…}} and {@code %r<…>} lost their rules, and
 * the braces inside a string interpolation with them. Nobody had seen it
 * in the editor; it was found as 109 warnings in the log of a walk (3.5).
 *
 * <p>{@code scripts/hoist-grammar-repositories.py} moves each local
 * definition to the top level under a name of its own and rewrites the
 * includes that could see it. This gate reads every shipped grammar for
 * the shape, and tokenizes Ruby through TM4E for the outcome.
 */
class RuleLocalRepositoriesGateTest {

    private static final Path GRAMMARS = Path.of("src/main/resources/org/nmox/studio/editor/grammars");

    /** Paths of rules, other than the grammar itself, that carry a repository. */
    private static void localRepositories(Object node, String path, boolean root, List<String> found) {
        if (node instanceof JSONObject o) {
            if (!root && o.has("repository")) {
                found.add(path);
            }
            for (String key : o.keySet()) {
                localRepositories(o.get(key), path + "/" + key, false, found);
            }
        } else if (node instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) {
                localRepositories(a.get(i), path + "[" + i + "]", false, found);
            }
        }
    }

    @Test
    @DisplayName("no shipped grammar carries a rule-local repository")
    void everyRepositoryIsAtTheTop() throws IOException {
        List<String> offenders = new ArrayList<>();
        int grammars = 0;
        try (Stream<Path> files = Files.list(GRAMMARS)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                grammars++;
                List<String> found = new ArrayList<>();
                // the top-level repository's own entries are rules like any other
                localRepositories(new JSONObject(Files.readString(p, StandardCharsets.UTF_8)), "", true, found);
                found.removeIf(path -> path.equals("/repository"));
                for (String path : found) {
                    offenders.add(p.getFileName() + " " + path);
                }
            }
        }
        assertThat(grammars).as("grammar files read").isGreaterThan(80);
        assertThat(offenders)
                .as("run scripts/hoist-grammar-repositories.py on the grammar: TM4E cannot resolve these")
                .isEmpty();
    }

    private static List<String> tokenize(String scope, String resource, String... lines) {
        Registry registry = new Registry(new IRegistryOptions() {
            @Override
            public IGrammarSource getGrammarSource(String scopeName) {
                return scope.equals(scopeName)
                        ? IGrammarSource.fromFile(GRAMMARS.resolve(resource)) : null;
            }
        });
        IGrammar g = registry.loadGrammar(scope);
        assertThat(g).as("TM4E loads " + scope).isNotNull();
        List<String> out = new ArrayList<>();
        IStateStack state = null;
        for (String line : lines) {
            ITokenizeLineResult<IToken[]> r = g.tokenizeLine(line, state, Duration.ofSeconds(5));
            for (IToken t : r.getTokens()) {
                int end = Math.min(t.getEndIndex(), line.length());
                if (t.getStartIndex() < end) {
                    out.add(line.substring(t.getStartIndex(), end) + " → " + t.getScopes());
                }
            }
            state = r.getRuleStack();
        }
        return out;
    }

    /** The scopes of the first token whose text, trailing blanks aside, is {@code text}. */
    private static String scopesOf(List<String> tokens, String text) {
        return tokens.stream().filter(s -> s.substring(0, s.indexOf(" → ")).strip().equals(text)).findFirst()
                .orElseThrow(() -> new AssertionError("no token '" + text + "' in " + tokens));
    }

    @Test
    @DisplayName("Ruby's %w( ) is an array of strings again, and the code after it is code")
    void rubyPercentLiteralsTokenize() {
        List<String> tokens = tokenize("source.ruby", "ruby.tmLanguage.json",
                "names = %w(alpha beta)",
                "puts names");
        assertThat(scopesOf(tokens, "alpha")).as("a word inside %w( )")
                .contains("meta.array.string.ruby").contains("string.other.ruby");
        assertThat(scopesOf(tokens, "puts")).as("the next line is not swallowed by the literal")
                .doesNotContain("meta.array").doesNotContain("string.");
    }

    @Test
    @DisplayName("a %w literal with nested parentheses ends at ITS parenthesis")
    void rubyNestedDelimitersBalance() {
        List<String> tokens = tokenize("source.ruby", "ruby.tmLanguage.json",
                "x = %w(a (b) c)",
                "y = 1");
        assertThat(scopesOf(tokens, "y")).as("after a literal whose words contain parentheses")
                .doesNotContain("meta.array").doesNotContain("string.");
    }
}
