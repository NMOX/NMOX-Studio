package org.nmox.studio.editor.grammars;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.grammar.IStateStack;
import org.eclipse.tm4e.core.grammar.IToken;
import org.eclipse.tm4e.core.grammar.ITokenizeLineResult;
import org.eclipse.tm4e.core.registry.IGrammarSource;
import org.eclipse.tm4e.core.registry.IRegistryOptions;
import org.eclipse.tm4e.core.registry.Registry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two git grammars run through TM4E — the engine the product's
 * TextMate lexer is built on — not through a JSON parser. A grammar can be
 * valid JSON and still be refused by TM4E (the v2.00.1 capture-shape
 * class), and a scope it includes that nothing registers prunes the
 * including rule; this test resolves every include the way the product's
 * registry would (source.diff to the in-house stub, source.shell to the
 * vendored shell grammar) and reads the scopes a real message gets.
 */
class GitGrammarsTokenizeTest {

    /** scope → the resource the product registers for it. */
    private static final Map<String, String> SOURCES = Map.of(
            "text.git-commit", "gitcommit.tmLanguage.json",
            "text.git-rebase", "gitrebase.tmLanguage.json",
            "source.diff", "stub-source.diff.json",
            "source.shell", "shell.tmLanguage.json");

    private static IGrammar grammar(String scope) {
        Registry registry = new Registry(new IRegistryOptions() {
            @Override
            public IGrammarSource getGrammarSource(String scopeName) {
                String resource = SOURCES.get(scopeName);
                return resource == null ? null
                        : IGrammarSource.fromResource(GitGrammarsTokenizeTest.class, resource);
            }
        });
        IGrammar g = registry.loadGrammar(scope);
        assertThat(g).as("TM4E loads " + scope).isNotNull();
        return g;
    }

    /** One line's tokens as "text → scopes", with the state carried across lines. */
    private static List<List<String>> tokenize(IGrammar g, String... lines) {
        List<List<String>> out = new ArrayList<>();
        IStateStack state = null;
        for (String line : lines) {
            ITokenizeLineResult<IToken[]> r = g.tokenizeLine(line, state, Duration.ofSeconds(5));
            List<String> row = new ArrayList<>();
            for (IToken t : r.getTokens()) {
                int end = Math.min(t.getEndIndex(), line.length());
                if (t.getStartIndex() >= end) {
                    continue;
                }
                row.add(line.substring(t.getStartIndex(), end) + " → " + t.getScopes());
            }
            out.add(row);
            state = r.getRuleStack();
        }
        return out;
    }

    private static String scopesOf(List<String> row, String text) {
        return row.stream().filter(s -> s.startsWith(text + " → ")).findFirst()
                .orElseThrow(() -> new AssertionError("no token '" + text + "' in " + row));
    }

    @Test
    @DisplayName("a commit message: git's # template lines are comments, the summary is the message")
    void commitMessageScopes() {
        List<List<String>> rows = tokenize(grammar("text.git-commit"),
                "Fix the parser",
                "",
                "# Please enter the commit message for your changes. Lines starting",
                "#\tmodified:   src/Parser.java");

        assertThat(String.join(" ", rows.get(0)))
                .as("the summary line")
                .contains("meta.scope.message.git-commit")
                .doesNotContain("comment");
        assertThat(String.join(" ", rows.get(2)))
                .as("git's own instruction line")
                .contains("comment.line.number-sign.git-commit");
        assertThat(String.join(" ", rows.get(3)))
                .as("the status block names the change")
                .contains("markup.changed.git-commit");
    }

    @Test
    @DisplayName("a commit -v diff resolves through the source.diff stub instead of pruning the rule")
    void verboseDiffEmbeds() {
        List<List<String>> rows = tokenize(grammar("text.git-commit"),
                "Fix it",
                "# ------------------------ >8 ------------------------",
                "diff --git a/x b/x",
                "+added");
        assertThat(String.join(" ", rows.get(3)))
                .as("the diff region survives as its own embedded scope")
                .contains("meta.embedded.diff.git-commit");
    }

    @Test
    @DisplayName("a rebase todo: pick is the command, the hash a constant, # a comment")
    void rebaseTodoScopes() {
        List<List<String>> rows = tokenize(grammar("text.git-rebase"),
                "pick abc123 Fix the parser",
                "# Rebase 1a2b3c..4d5e6f onto 1a2b3c (1 command)",
                "exec make test");

        assertThat(scopesOf(rows.get(0), "pick")).contains("support.function.git-rebase");
        assertThat(scopesOf(rows.get(0), "abc123")).contains("constant.sha.git-rebase");
        assertThat(String.join(" ", rows.get(1)))
                .contains("comment.line.number-sign.git-rebase");
        assertThat(scopesOf(rows.get(2), "exec")).contains("support.function.git-rebase");
    }

    @Test
    @DisplayName("spellcheck reads the grammar's REAL scopes: the message is prose, the template and diff are not")
    void spellcheckAgreesWithTheGrammar() {
        IGrammar g = grammar("text.git-commit");
        String[] lines = {
            "Fix the parser",
            "# Please enter the commit message for your changes.",
            "diff --git a/x b/x"};
        IStateStack state = null;
        List<List<List<String>>> stacks = new ArrayList<>();
        for (String line : lines) {
            ITokenizeLineResult<IToken[]> r = g.tokenizeLine(line, state, Duration.ofSeconds(5));
            List<List<String>> row = new ArrayList<>();
            for (IToken t : r.getTokens()) {
                row.add(t.getScopes());
            }
            stacks.add(row);
            state = r.getRuleStack();
        }
        assertThat(stacks.get(0)).as("every token of the summary is prose")
                .allMatch(org.nmox.studio.editor.spell.GitMessageSpellTokenListProvider::isMessageScope);
        assertThat(stacks.get(1)).as("no token of git's template is prose")
                .noneMatch(org.nmox.studio.editor.spell.GitMessageSpellTokenListProvider::isMessageScope);
        assertThat(stacks.get(2)).as("no token of a commit -v diff is prose")
                .noneMatch(org.nmox.studio.editor.spell.GitMessageSpellTokenListProvider::isMessageScope);
    }

    @Test
    @DisplayName("the registered grammars carry the scopes the mimes are named for")
    void registeredScopes() {
        assertThat(grammar("text.git-commit").getScopeName()).isEqualTo("text.git-commit");
        assertThat(grammar("text.git-rebase").getScopeName()).isEqualTo("text.git-rebase");
    }
}
