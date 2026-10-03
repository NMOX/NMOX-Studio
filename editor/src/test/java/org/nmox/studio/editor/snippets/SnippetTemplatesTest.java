package org.nmox.studio.editor.snippets;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;
import org.nmox.studio.editor.snippets.SnippetTemplates.CodeTemplateText;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The translation into the code-template language, pinned as strings:
 * first the rules one at a time, then real snippets a team would
 * commit, each expanded for a stated file. {@code SnippetEngineTest}
 * proves the engine reads these strings as intended; this proves the
 * strings.
 */
class SnippetTemplatesTest {

    private static final String P = SnippetTemplates.NO_INDENT;

    private static final ZonedDateTime FRIDAY =
            ZonedDateTime.of(2026, 10, 2, 9, 5, 7, 0, ZoneOffset.UTC);

    /** A TypeScript file in a project called shop, two-space indentation, caret on an unindented line. */
    private static SnippetContext ctx() {
        return SnippetContext.at(FRIDAY, new Random(7))
                .withFile("/work/shop/src/app/user-card.component.ts", "/work/shop")
                .withComments("//", "/*", "*/")
                .withIndent("", "  ");
    }

    private static CodeTemplateText translate(String body) throws Refused {
        return SnippetTemplates.toCodeTemplate(SnippetBody.parse(body), ctx());
    }

    private static CodeTemplateText translate(String body, SnippetContext ctx) throws Refused {
        return SnippetTemplates.toCodeTemplate(SnippetBody.parse(body), ctx);
    }

    // ---- the rules ---------------------------------------------------

    @Test
    @DisplayName("a tab stop is a parameter named for its number, ordered by it; $0 is the cursor")
    void tabStopsBecomeParameters() throws Refused {
        CodeTemplateText t = translate("log(${1:value});$0");
        assertThat(t.text()).isEqualTo(P + "log(${t1 default=\"value\" ordering=1});${cursor}");
        assertThat(t.plain()).isEqualTo("log(value);");
        assertThat(t.plainCaret()).isEqualTo("log(value);".length());
        assertThat(t.values()).isEmpty();
        assertThat(t.lives()).isEmpty();
    }

    @Test
    @DisplayName("the same number again is the same parameter name again, wherever it is written first")
    void mirrorsShareAName() throws Refused {
        assertThat(translate("$2 $1 $2 ${1:x} $1").text()).isEqualTo(
                P + "${t2 default=\"\" ordering=2} ${t1 default=\"x\" ordering=1} ${t2} ${t1} ${t1}");
        assertThat(translate("$2 $1 $2 ${1:x} $1").plain()).isEqualTo(" x  x x");
    }

    @Test
    @DisplayName("the first occurrence that has a default gives it to every occurrence")
    void firstDefaultWins() throws Refused {
        assertThat(translate("${1:a} ${1:b}").text()).isEqualTo(P + "${t1 default=\"a\" ordering=1} ${t1}");
        assertThat(translate("${1:a} ${1:b}").plain()).isEqualTo("a a");
    }

    @Test
    @DisplayName("a placeholder inside a default is flattened into it, and is a stop of its own only where it also stands alone")
    void nestedPlaceholdersFlatten() throws Refused {
        assertThat(translate("${1:foo ${2:bar}}").text()).isEqualTo(P + "${t1 default=\"foo bar\" ordering=1}");
        assertThat(translate("${1:foo ${2:bar}} $2").text()).isEqualTo(
                P + "${t1 default=\"foo bar\" ordering=1} ${t2 default=\"bar\" ordering=2}");
        assertThat(translate("${1:a ${1} b}").text())
                .as("a stop inside its own default reads as nothing rather than forever")
                .isEqualTo(P + "${t1 default=\"a  b\" ordering=1}");
    }

    @Test
    @DisplayName("a choice starts as its first option")
    void choicesTakeTheFirst() throws Refused {
        CodeTemplateText t = translate("${1|it,test|}('${2:name}')");
        assertThat(t.text()).isEqualTo(P + "${t1 default=\"it\" ordering=1}('${t2 default=\"name\" ordering=2}')");
        assertThat(t.plain()).isEqualTo("it('name')");
    }

    @Test
    @DisplayName("without $0 there is no cursor parameter and the caret ends after the body")
    void implicitFinalCaret() throws Refused {
        CodeTemplateText t = translate("a ${1:b} c");
        assertThat(t.text()).doesNotContain("${cursor}");
        assertThat(t.plainCaret()).isEqualTo("a b c".length());
    }

    @Test
    @DisplayName("${0:text} inserts the text with the caret after it, and a second $0 is nothing")
    void finalStopWithText() throws Refused {
        CodeTemplateText t = translate("{ ${0:pass} } $0");
        assertThat(t.text()).isEqualTo(P + "{ pass${cursor} } ");
        assertThat(t.plainCaret()).isEqualTo("{ pass".length());
    }

    @Test
    @DisplayName("every dollar of literal text is doubled, so text that reads like a parameter stays text")
    void literalDollarsAreDoubled() throws Refused {
        CodeTemplateText t = translate("\\${cursor} \\${name default=\"x\"} ${no-indent} $$ cost: \\$5 $");
        assertThat(t.text()).isEqualTo(
                P + "$${cursor} $${name default=\"x\"} $${no-indent} $$$$ cost: $$5 $$");
        assertThat(t.plain()).isEqualTo("${cursor} ${name default=\"x\"} ${no-indent} $$ cost: $5 $");
    }

    @Test
    @DisplayName("a default is written in place when the grammar carries it: dollars, braces and line breaks included")
    void defaultsInPlace() throws Refused {
        assertThat(translate("${1:a $ b { c \\} d}").text())
                .as("inside a quoted hint a dollar is an ordinary character and is NOT doubled")
                .isEqualTo(P + "${t1 default=\"a $ b { c } d\" ordering=1}");
        assertThat(translate("${1:one\ntwo}").text()).isEqualTo(P + "${t1 default=\"one\ntwo\" ordering=1}");
    }

    @Test
    @DisplayName("a default holding a quote or a backslash is NOT written into the template; it travels beside it")
    void unsafeDefaultsTravelBeside() throws Refused {
        CodeTemplateText quoted = translate("print(${1:\"hello\"})");
        assertThat(quoted.text()).isEqualTo(P + "print(${t1 ordering=1})");
        assertThat(quoted.values()).isEqualTo(Map.of("t1", "\"hello\""));
        assertThat(quoted.plain()).isEqualTo("print(\"hello\")");

        CodeTemplateText slashed = translate("cd ${1:C:\\\\tools} $1");
        assertThat(slashed.text()).isEqualTo(P + "cd ${t1 ordering=1} ${t1}");
        assertThat(slashed.values()).isEqualTo(Map.of("t1", "C:\\tools"));

        assertThat(SnippetTemplates.grammarCarries("a $ } { \n b")).isTrue();
        assertThat(SnippetTemplates.grammarCarries("a\"b")).isFalse();
        assertThat(SnippetTemplates.grammarCarries("a\\b")).isFalse();
    }

    @Test
    @DisplayName("a variable is its value; one that is not set is its default, placeholders and all, or nothing")
    void variables() throws Refused {
        assertThat(translate("$TM_FILENAME in $WORKSPACE_NAME").text())
                .isEqualTo(P + "user-card.component.ts in shop");
        assertThat(translate("[${TM_SELECTED_TEXT:${1:nothing selected}}]").text())
                .isEqualTo(P + "[${t1 default=\"nothing selected\" ordering=1}]");
        assertThat(translate("[${TM_SELECTED_TEXT:${1:nothing selected}}]",
                ctx().withLine("x", 0, null, "picked")).text()).isEqualTo(P + "[picked]");
        assertThat(translate("[$TM_SELECTED_TEXT]").text()).isEqualTo(P + "[]");
        assertThat(translate("$TM_FILEPATH", ctx().withFile("/a/$b.ts", null)).text())
                .as("a dollar in a VALUE is literal text too").isEqualTo(P + "/a/$$b.ts");
    }

    @Test
    @DisplayName("a name VS Code does not know becomes a placeholder holding the name, numbered after the body's own")
    void unknownVariablesBecomePlaceholders() throws Refused {
        CodeTemplateText t = translate("${foo} $1 $foo ${bar:fallback} ${cursor}");
        assertThat(t.text()).isEqualTo(P + "${t2 default=\"foo\" ordering=2} ${t1 default=\"\" ordering=1} ${t2} "
                + "fallback ${t3 default=\"cursor\" ordering=3}");
        assertThat(t.plain()).isEqualTo("foo  foo fallback cursor");
    }

    @Test
    @DisplayName("a variable's transform is applied once, when the snippet is translated")
    void variableTransforms() throws Refused {
        assertThat(translate("${TM_FILENAME/(.*)\\..+$/$1/}").text()).isEqualTo(P + "user-card.component");
        assertThat(translate("${TM_FILENAME_BASE/(.*)\\.component/${1:/pascalcase}/}").text())
                .isEqualTo(P + "UserCard");
        assertThat(translate("${TM_SELECTED_TEXT/^$/none/}").text())
                .as("not set is transformed as the empty text, as in VS Code").isEqualTo(P + "none");
    }

    @Test
    @DisplayName("a transformed tab stop is a parameter nobody types into, tied to its source")
    void transformedMirrors() throws Refused {
        CodeTemplateText t = translate("${1:count} set${1/(.*)/${1:/capitalize}/} ${1/(.*)/${1:/upcase}/}");
        assertThat(t.text()).isEqualTo(P + "${t1 default=\"count\" ordering=1} "
                + "set${t1x1 default=\"Count\" editable=false} ${t1x2 default=\"COUNT\" editable=false}");
        assertThat(t.plain()).isEqualTo("count setCount COUNT");
        assertThat(t.lives()).extracting(SnippetTemplates.Live::source).containsExactly("t1", "t1");
        assertThat(t.lives()).extracting(SnippetTemplates.Live::target).containsExactly("t1x1", "t1x2");
    }

    @Test
    @DisplayName("a tab stop that appears only transformed has nothing to type into: refused")
    void onlyTransformedIsRefused() {
        assertThatThrownBy(() -> translate("x ${1/(.*)/${1:/upcase}/} y"))
                .isInstanceOf(Refused.class).hasMessageContaining("tab stop 1 is only written with a transform");
        assertThatThrownBy(() -> translate("${2:outer ${1:inner}} ${1/(.*)/$1/}"))
                .as("its only plain occurrence was flattened into another stop's default")
                .isInstanceOf(Refused.class);
    }

    @Test
    @DisplayName("a transform that runs past its clock on this file's values refuses the translation")
    void transformOverrunRefuses() {
        SnippetContext longLine = ctx().withLine("a".repeat(400), 0, null, null);
        assertThatThrownBy(() -> translate("${TM_CURRENT_LINE/(.*)(.*)(.*)(.*)(.*)(.*)!!/x/}", longLine))
                .isInstanceOf(Refused.class).hasMessageContaining("did not finish");
    }

    @Test
    @DisplayName("the transforms of one translation share one budget: with none left the translation is refused, and a body without transforms never asks")
    void transformsShareOneBudget() throws Refused {
        SnippetBody transformed = SnippetBody.parse("${TM_FILENAME/(.*)\\..+$/$1/} ${1:x}");
        assertThatThrownBy(() -> SnippetTemplates.toCodeTemplate(transformed, ctx(), 0))
                .isInstanceOf(Refused.class).hasMessageContaining("did not finish within 0 ms between them");
        assertThat(SnippetTemplates.toCodeTemplate(transformed, ctx()).plain()).isEqualTo("user-card.component x");
        assertThat(SnippetTemplates.toCodeTemplate(SnippetBody.parse("plain ${1:x} $TM_FILENAME"), ctx(), 0).plain())
                .isEqualTo("plain x user-card.component.ts");
    }

    @Test
    @DisplayName("lines after the first take the caret line's indentation; a leading tab becomes the indent unit")
    void indentation() throws Refused {
        SnippetContext indented = ctx().withIndent("    ", "  ");
        CodeTemplateText t = translate("if ($1) {\n\t$0\n\t\tdeep\tmid\n}", indented);
        assertThat(t.plain()).isEqualTo("if () {\n      \n        deep\tmid\n    }");
        assertThat(t.plainCaret()).isEqualTo("if () {\n      ".length());

        assertThat(translate("a\n\tb", ctx().withIndent("\t", "\t")).plain())
                .as("an editor that indents with tabs keeps them").isEqualTo("a\n\t\tb");
        assertThat(translate("\tfirst\n\tsecond", ctx().withIndent("  ", "    ")).plain())
                .as("the body's first line lands at the caret: its tab is a unit, with no line indentation added")
                .isEqualTo("    first\n      second");
    }

    @Test
    @DisplayName("a default's own lines are indented too; a variable's value is inserted as it is")
    void indentationInsideDefaultsAndValues() throws Refused {
        SnippetContext indented = ctx().withIndent("  ", "    ");
        assertThat(translate("${1:one\n\ttwo}", indented).plain()).isEqualTo("one\n      two");
        assertThat(translate("x\n$TM_SELECTED_TEXT\ny", indented.withLine("", 0, null, "sel\n\tsel2")).plain())
                .isEqualTo("x\n  sel\n\tsel2\n  y");
    }

    // ---- real snippets, expanded --------------------------------------

    @Test
    @DisplayName("real: console.log with a mirrored label")
    void realConsoleLog() throws Refused {
        CodeTemplateText t = translate("console.log('${1:value}:', $1);$0");
        assertThat(t.text()).isEqualTo(
                P + "console.log('${t1 default=\"value\" ordering=1}:', ${t1});${cursor}");
        assertThat(t.plain()).isEqualTo("console.log('value:', value);");
    }

    @Test
    @DisplayName("real: an Angular component named from its file")
    void realAngularComponent() throws Refused {
        String body = String.join("\n",
                "@Component({",
                "\tselector: '${1:app-${TM_FILENAME_BASE/(.*)\\.component/$1/}}',",
                "\tstandalone: true,",
                "\ttemplateUrl: './${TM_FILENAME_BASE}.html',",
                "})",
                "export class ${2:${TM_FILENAME_BASE/(.*)\\.component/${1:/pascalcase}/}Component} {",
                "\t$0",
                "}");
        CodeTemplateText t = translate(body);
        assertThat(t.plain()).isEqualTo(String.join("\n",
                "@Component({",
                "  selector: 'app-user-card',",
                "  standalone: true,",
                "  templateUrl: './user-card.component.html',",
                "})",
                "export class UserCardComponent {",
                "  ",
                "}"));
        assertThat(t.text()).isEqualTo(P + String.join("\n",
                "@Component({",
                "  selector: '${t1 default=\"app-user-card\" ordering=1}',",
                "  standalone: true,",
                "  templateUrl: './user-card.component.html',",
                "})",
                "export class ${t2 default=\"UserCardComponent\" ordering=2} {",
                "  ${cursor}",
                "}"));
        assertThat(t.plain().substring(0, t.plainCaret())).endsWith("export class UserCardComponent {\n  ");
    }

    @Test
    @DisplayName("real: an Express route with a verb to choose, on an indented line")
    void realExpressRoute() throws Refused {
        String body = "${1:router}.${2|get,post,put,delete|}('${3:/path}', async (req, res) => {\n"
                + "\t${0:res.sendStatus(204);}\n"
                + "});";
        CodeTemplateText t = translate(body, ctx().withIndent("  ", "  "));
        assertThat(t.plain()).isEqualTo(
                "router.get('/path', async (req, res) => {\n    res.sendStatus(204);\n  });");
        assertThat(t.text()).isEqualTo(P + "${t1 default=\"router\" ordering=1}.${t2 default=\"get\" ordering=2}"
                + "('${t3 default=\"/path\" ordering=3}', async (req, res) => {\n"
                + "    res.sendStatus(204);${cursor}\n  });");
    }

    @Test
    @DisplayName("real: a Vitest test with two choices")
    void realVitestTest() throws Refused {
        String body = "${1|it,test|}('${2:does something}', () => {\n"
                + "\texpect(${3:actual}).${4|toBe,toEqual,toMatchObject|}(${5:expected});$0\n"
                + "});";
        CodeTemplateText t = translate(body);
        assertThat(t.plain()).isEqualTo("it('does something', () => {\n  expect(actual).toBe(expected);\n});");
        assertThat(t.text()).contains("${t4 default=\"toBe\" ordering=4}").contains("${cursor}\n});");
    }

    @Test
    @DisplayName("real: a file header with the year, the file's name in capitals and the language's own comment marks")
    void realFileHeader() throws Refused {
        String body = String.join("\n",
                "${BLOCK_COMMENT_START}",
                " * ${TM_FILENAME_BASE/(.*)/${1:/upcase}/} — ${1:what this file is for}",
                " * Copyright (c) $CURRENT_YEAR ${2:${WORKSPACE_NAME}}. $CURRENT_MONTH_NAME $CURRENT_DATE.",
                " ${BLOCK_COMMENT_END}",
                "$0");
        CodeTemplateText t = translate(body);
        assertThat(t.plain()).isEqualTo(String.join("\n",
                "/*",
                " * USER-CARD.COMPONENT — what this file is for",
                " * Copyright (c) 2026 shop. October 02.",
                " */",
                ""));
        assertThat(t.text()).contains("${t1 default=\"what this file is for\" ordering=1}")
                .contains("${t2 default=\"shop\" ordering=2}");

        CodeTemplateText python = translate(body, ctx().withComments("#", null, null));
        assertThat(python.plain())
                .as("a language with no block comment: the variable is not set, and nothing is invented")
                .startsWith("\n * USER-CARD");
    }

    @Test
    @DisplayName("real: a Python function whose docstring names the function, with a nested optional argument")
    void realPythonFunction() throws Refused {
        String body = "def ${1:name}(${2:self${3:, arg}}):\n\t\"\"\"${4:Describe $1.}\"\"\"\n\t${0:pass}";
        CodeTemplateText t = translate(body, ctx().withIndent("", "    "));
        assertThat(t.plain()).isEqualTo("def name(self, arg):\n    \"\"\"Describe name.\"\"\"\n    pass");
        assertThat(t.text()).isEqualTo(P + "def ${t1 default=\"name\" ordering=1}"
                + "(${t2 default=\"self, arg\" ordering=2}):\n"
                + "    \"\"\"${t4 default=\"Describe name.\" ordering=4}\"\"\"\n    pass${cursor}");
    }

    @Test
    @DisplayName("real: an Angular signal with a setter named from it")
    void realSignalWithSetter() throws Refused {
        String body = "private readonly ${1:count} = signal(${2:0});\n"
                + "readonly set${1/(.*)/${1:/capitalize}/} = (v: ${3:number}) => this.$1.set(v);";
        CodeTemplateText t = translate(body);
        assertThat(t.plain()).isEqualTo("private readonly count = signal(0);\n"
                + "readonly setCount = (v: number) => this.count.set(v);");
        assertThat(t.lives()).hasSize(1);
        assertThat(t.text()).contains("set${t1x1 default=\"Count\" editable=false} = ");
    }

    @Test
    @DisplayName("real: a shell line with literal dollars and a quoted default")
    void realShellLine() throws Refused {
        CodeTemplateText t = translate("echo ${1:\"\\$HOME is $WORKSPACE_NAME\"} >> \\$LOG; exit \\$?");
        assertThat(t.plain()).isEqualTo("echo \"$HOME is shop\" >> $LOG; exit $?");
        assertThat(t.text()).isEqualTo(P + "echo ${t1 ordering=1} >> $$LOG; exit $$?");
        assertThat(t.values()).isEqualTo(Map.of("t1", "\"$HOME is shop\""));
    }
}
