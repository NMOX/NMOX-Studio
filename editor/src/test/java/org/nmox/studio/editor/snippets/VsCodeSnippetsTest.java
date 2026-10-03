package org.nmox.studio.editor.snippets;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Malformed;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Parsed;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Snippet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A {@code .code-snippets} file's text: what is read, what is left out, and that leaving out is said. */
class VsCodeSnippetsTest {

    private static final String TEAM = """
            {
              // the team's snippets
              "Log to console": {
                "prefix": ["log", "cl"],
                "body": ["console.log('$1');", "$0"],
                "description": "Log output to console",
                "scope": "javascript,typescript",
              },
              "Header": {
                "prefix": "hdr",
                "body": "/* $TM_FILENAME */"
              },
              /* a group, one level deep */
              "testing": {
                "Vitest it": { "prefix": "it", "body": "it('$1', () => {\\n\\t$0\\n});", "scope": "typescript" }
              }
            }
            """;

    private static Snippet named(Parsed parsed, String name) {
        return parsed.snippets().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("prefix as a string or a list, body as a string or lines, comments and trailing commas allowed")
    void readsTheDocumentedShape() throws Malformed {
        Parsed parsed = VsCodeSnippets.parse(TEAM, "team.code-snippets");
        assertThat(parsed.snippets()).extracting(Snippet::name)
                .containsExactly("Header", "Log to console", "Vitest it");
        Snippet log = named(parsed, "Log to console");
        assertThat(log.prefixes()).containsExactly("log", "cl");
        assertThat(log.description()).isEqualTo("Log output to console");
        assertThat(log.scopes()).containsExactlyInAnyOrder("javascript", "typescript");
        assertThat(log.firstLine()).isEqualTo("console.log('$1');");
        assertThat(log.source()).isEqualTo("team.code-snippets");
        assertThat(log.body().nodes()).hasSize(4);
        assertThat(named(parsed, "Header").prefixes()).containsExactly("hdr");
        assertThat(named(parsed, "Header").description()).isEmpty();
        assertThat(parsed.notes()).isEmpty();
    }

    @Test
    @DisplayName("a snippet with a scope is offered only in those languages; one without, in every language")
    void scopeDecidesWhere() throws Malformed {
        Parsed parsed = VsCodeSnippets.parse(TEAM, "team.code-snippets");
        Snippet log = named(parsed, "Log to console");
        assertThat(log.appliesTo("javascript")).isTrue();
        assertThat(log.appliesTo("typescript")).isTrue();
        assertThat(log.appliesTo("python")).isFalse();
        assertThat(log.appliesTo("javascriptreact")).as("VS Code's ids are exact: a .jsx file is not javascript").isFalse();
        assertThat(log.appliesTo(null)).as("a file this product has no language id for").isFalse();
        Snippet header = named(parsed, "Header");
        assertThat(header.appliesTo("python")).isTrue();
        assertThat(header.appliesTo(null)).isTrue();
        assertThat(named(parsed, "Vitest it").appliesTo("javascript")).isFalse();
    }

    @Test
    @DisplayName("scope ids are trimmed, and an empty scope is no scope")
    void scopeParsing() throws Malformed {
        Parsed parsed = VsCodeSnippets.parse("""
                {"a": {"prefix": "a", "body": "x", "scope": " html , vue ,"},
                 "b": {"prefix": "b", "body": "x", "scope": ""},
                 "c": {"prefix": "c", "body": "x", "scope": 7}}
                """, "f");
        assertThat(named(parsed, "a").scopes()).containsExactlyInAnyOrder("html", "vue");
        assertThat(named(parsed, "b").scopes()).isEmpty();
        assertThat(named(parsed, "c").scopes()).isEmpty();
    }

    @Test
    @DisplayName("what cannot be honoured is left out by name, and the rest of the file still loads")
    void leavesOutByName() throws Malformed {
        Parsed parsed = VsCodeSnippets.parse("""
                {
                  "good": {"prefix": "g", "body": "fine $1"},
                  "hostile regex": {"prefix": "h", "body": "${TM_FILENAME/(a+)+$/x/}"},
                  "only transformed": {"prefix": "o", "body": "${1/(.*)/${1:/upcase}/}"},
                  "bottomless": {"prefix": "d", "body": "%s"},
                  "no body": {"prefix": "n"},
                  "odd body": {"prefix": "n", "body": 42},
                  "no prefix": {"body": "reachable only by a command"},
                  "blank prefix": {"prefix": ["", "  "], "body": "x"},
                  "not an object": "text"
                }
                """.formatted("${1:".repeat(40)), "team.code-snippets");
        assertThat(parsed.snippets()).extracting(Snippet::name).containsExactly("good");
        assertThat(parsed.notes()).anySatisfy(n -> assertThat(n)
                .contains("\"hostile regex\" is left out because").contains("repeats a group that itself repeats"));
        assertThat(parsed.notes()).anySatisfy(n -> assertThat(n)
                .contains("\"only transformed\" is left out because").contains("only written with a transform"));
        assertThat(parsed.notes()).anySatisfy(n -> assertThat(n)
                .contains("\"bottomless\" is left out because").contains("nested deeper"));
        assertThat(parsed.notes()).anySatisfy(n -> assertThat(n).contains("\"no body\" has no body"));
        assertThat(parsed.notes()).anySatisfy(n -> assertThat(n).contains("\"odd body\"").contains("neither a string"));
        assertThat(parsed.notes()).anySatisfy(n -> assertThat(n).contains("\"not an object\" is not an object"));
        assertThat(parsed.notes()).anySatisfy(n -> assertThat(n).contains("2 snippets have no prefix"));
    }

    @Test
    @DisplayName("the trials of one file share one budget: past it the rest are kept untried, and that is said")
    void trialsShareOneBudget() throws Malformed {
        String json = """
                {"a only transformed": {"prefix": "a", "body": "${1/(.*)/${1:/upcase}/}"},
                 "b fine": {"prefix": "b", "body": "fine"}}
                """;
        Parsed tried = VsCodeSnippets.parse(json, "f");
        assertThat(tried.snippets()).extracting(Snippet::name).containsExactly("b fine");
        assertThat(tried.notes()).hasSize(1);

        Parsed spent = VsCodeSnippets.parse(json, "f", 0);
        assertThat(spent.snippets()).extracting(Snippet::name).containsExactly("a only transformed", "b fine");
        assertThat(spent.notes()).containsExactly(
                "2 snippets were kept without being tried: the file used its 0 ms for that on the ones before");
    }

    @Test
    @DisplayName("a body past the size bound is left out whole, never cut")
    void oversizeBodyIsLeftOut() throws Malformed {
        String big = "x".repeat(SnippetBody.MAX_CHARS + 1);
        Parsed parsed = VsCodeSnippets.parse("{\"big\": {\"prefix\": \"b\", \"body\": \"" + big + "\"},"
                + "\"lines\": {\"prefix\": \"l\", \"body\": [\"" + "y".repeat(9000) + "\", \"" + "y".repeat(9000)
                + "\", \"tail\"]}}", "f");
        assertThat(parsed.snippets()).isEmpty();
        assertThat(parsed.notes()).hasSize(2).allSatisfy(n -> assertThat(n).contains("over the 16384 limit"));
    }

    @Test
    @DisplayName("past the cap, snippets are counted and said, in name order so every machine keeps the same ones")
    void capIsCountedAndDeterministic() throws Malformed {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < VsCodeSnippets.MAX_SNIPPETS + 7; i++) {
            json.append(i == 0 ? "" : ",").append("\"s%04d\": {\"prefix\": \"p%d\", \"body\": \"b\"}".formatted(i, i));
        }
        Parsed parsed = VsCodeSnippets.parse(json.append('}').toString(), "f");
        assertThat(parsed.snippets()).hasSize(VsCodeSnippets.MAX_SNIPPETS);
        assertThat(parsed.snippets().get(0).name()).isEqualTo("s0000");
        assertThat(parsed.snippets().get(VsCodeSnippets.MAX_SNIPPETS - 1).name()).isEqualTo("s0499");
        assertThat(parsed.notes()).containsExactly("7 more past the first 500 are not read");
    }

    @Test
    @DisplayName("many refusals are listed up to a point and counted after it")
    void notesAreBounded() throws Malformed {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < 40; i++) {
            json.append(i == 0 ? "" : ",").append("\"bad%02d\": {\"prefix\": \"p\", \"body\": 1}".formatted(i));
        }
        Parsed parsed = VsCodeSnippets.parse(json.append('}').toString(), "f");
        assertThat(parsed.notes()).hasSize(VsCodeSnippets.MAX_NOTES + 1);
        assertThat(parsed.notes().get(VsCodeSnippets.MAX_NOTES)).isEqualTo("and 28 more left out");
    }

    @Test
    @DisplayName("a name written twice keeps the later entry, as VS Code does")
    void duplicateNamesKeepTheLast() throws Malformed {
        Parsed parsed = VsCodeSnippets.parse(
                "{\"x\": {\"prefix\": \"one\", \"body\": \"1\"}, \"x\": {\"prefix\": \"two\", \"body\": \"2\"}}", "f");
        assertThat(parsed.snippets()).hasSize(1);
        assertThat(parsed.snippets().get(0).prefixes()).containsExactly("two");
    }

    @Test
    @DisplayName("Windows line ends in a body are line ends")
    void lineEndsAreNormalized() throws Malformed {
        Parsed parsed = VsCodeSnippets.parse("{\"x\": {\"prefix\": \"x\", \"body\": \"a\\r\\nb\\rc\"}}", "f");
        assertThat(parsed.snippets().get(0).body().nodes())
                .containsExactly(new SnippetBody.Text("a\nb\nc"));
        assertThat(parsed.snippets().get(0).firstLine()).isEqualTo("a");
    }

    @Test
    @DisplayName("text that is not a JSON object is malformed, and says why")
    void malformed() {
        for (String bad : List.of("", "[]", "{\"a\": ", "not json", "{" + "\"a\":{".repeat(2000))) {
            assertThatThrownBy(() -> VsCodeSnippets.parse(bad, "f")).as(bad.length() > 20 ? "deep" : bad)
                    .isInstanceOf(Malformed.class).hasMessageMatching("(?s).+");
        }
    }
}
