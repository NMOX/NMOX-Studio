package org.nmox.studio.editor.snippets;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Malformed;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Snippet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the completion list offers: which snippets, under which prefix,
 * replacing how much, and what a row says. The list's rows are painted
 * as markup, so what a repository wrote is shown as characters.
 */
class ProjectSnippetCompletionTest {

    private static final File WORKSPACE = new File("/work/shop");

    private static List<Snippet> team() throws Malformed {
        return VsCodeSnippets.parse("""
                {
                  "Log to console": {"prefix": ["log", "cl"], "body": "console.log($1);",
                                     "description": "Log output", "scope": "javascript,typescript"},
                  "Python print": {"prefix": "log", "body": "print($1)", "scope": "python"},
                  "License header": {"prefix": "lic", "body": "// Copyright $CURRENT_YEAR"},
                  "Dot log": {"prefix": ".log", "body": "console.log($TM_CURRENT_WORD)", "scope": "typescript"}
                }
                """, "team.code-snippets").snippets();
    }

    private static List<String> offered(String languageId, String before) throws Malformed {
        return ProjectSnippetCompletionProvider.items(team(), languageId, before, WORKSPACE, 100).stream()
                .map(i -> i.getInsertPrefix().toString() + "@" + i.startOffset())
                .toList();
    }

    @Test
    @DisplayName("scope: a typed prefix offers the snippets of this file's language and the unscoped ones, no others")
    void scopeFilters() throws Malformed {
        assertThat(offered("typescript", "  lo")).containsExactly("log@98");
        assertThat(offered("python", "  lo")).containsExactly("log@98");
        assertThat(ProjectSnippetCompletionProvider.items(team(), "python", "  lo", WORKSPACE, 100))
                .extracting(i -> ProjectSnippetCompletionItem.label(i.snippet(), "log"))
                .containsExactly("log — Python print (print($1))");
        assertThat(offered("ruby", "  lo")).as("scoped to other languages").isEmpty();
        assertThat(offered("ruby", "  li")).containsExactly("lic@98");
        assertThat(offered(null, "li")).as("a file with no language id still gets the unscoped ones")
                .containsExactly("lic@98");
    }

    @Test
    @DisplayName("a snippet is one row, under whichever of its prefixes matches the most of what was typed")
    void bestPrefixWins() throws Malformed {
        assertThat(offered("javascript", "c")).containsExactly("cl@99");
        assertThat(offered("javascript", "l")).containsExactlyInAnyOrder("log@99", "lic@99");
        assertThat(offered("javascript", "LO")).as("letters match whatever their case").containsExactly("log@98");
        assertThat(offered("typescript", "items.lo")).containsExactlyInAnyOrder("log@98", ".log@97");
    }

    @Test
    @DisplayName("at a fresh spot every applicable snippet is offered, replacing nothing")
    void freshSpotOffersAll() throws Malformed {
        assertThat(offered("typescript", "")).containsExactlyInAnyOrder("log@100", "lic@100", ".log@100");
        assertThat(offered("typescript", "foo(")).hasSize(3);
        assertThat(offered("typescript", "xlo")).as("inside another word nothing is offered").isEmpty();
        assertThat(offered("typescript", "zzz")).isEmpty();
    }

    @Test
    @DisplayName("a row is: prefix — name (description, or the body's first line), and the file on the right")
    void label() throws Malformed {
        Snippet log = team().stream().filter(s -> s.name().equals("Log to console")).findFirst().orElseThrow();
        assertThat(ProjectSnippetCompletionItem.label(log, "log")).isEqualTo("log — Log to console (Log output)");
        assertThat(ProjectSnippetCompletionItem.provenance(log)).isEqualTo("· team.code-snippets");
        Snippet lic = team().stream().filter(s -> s.name().equals("License header")).findFirst().orElseThrow();
        assertThat(ProjectSnippetCompletionItem.label(lic, "lic"))
                .isEqualTo("lic — License header (// Copyright $CURRENT_YEAR)");
    }

    @Test
    @DisplayName("names, prefixes, descriptions and file names from the repository are shown as characters, never as markup")
    void rowsArePlain() throws Malformed {
        Snippet hostile = VsCodeSnippets.parse("""
                {"<html><img src='http://evil.example/x.png'>": {
                    "prefix": "<b>p",
                    "body": "x",
                    "description": "<font color='red'>&amp; \\"quoted\\"</font>"}}
                """, "<i>team</i>.code-snippets").snippets().get(0);
        String label = ProjectSnippetCompletionItem.label(hostile, "<b>p");
        assertThat(label).doesNotContain("<").doesNotContain(">")
                .contains("&lt;html&gt;&lt;img src=").contains("&lt;b&gt;p").contains("&amp;amp;");
        assertThat(ProjectSnippetCompletionItem.provenance(hostile))
                .doesNotContain("<").contains("&lt;i&gt;team&lt;/i&gt;.code-snippets");
    }

    @Test
    @DisplayName("a long description is cut on a code point with an ellipsis, a description equal to the name is not repeated")
    void labelClipsAndDedupes() throws Malformed {
        Snippet longOne = VsCodeSnippets.parse("{\"n\": {\"prefix\": \"p\", \"body\": \"x\", \"description\": \""
                + "😀".repeat(80) + "\"}}", "f").snippets().get(0);
        String label = ProjectSnippetCompletionItem.label(longOne, "p");
        assertThat(label).endsWith("…)");
        assertThat(label.codePointCount(0, label.length()))
                .isEqualTo("p — n (".length() + ProjectSnippetCompletionItem.HINT_CHARS + 1);
        assertThat(label).doesNotContain("�");
        Snippet same = VsCodeSnippets.parse(
                "{\"Same\": {\"prefix\": \"p\", \"body\": \"x\", \"description\": \"Same\"}}", "f").snippets().get(0);
        assertThat(ProjectSnippetCompletionItem.label(same, "p")).isEqualTo("p — Same");
    }

    @Test
    @DisplayName("the language id is the editor's own, with .jsx and .tsx told apart as VS Code tells them")
    void languageId() throws Exception {
        PlainDocument ts = new PlainDocument();
        ts.putProperty("mimeType", "text/typescript");
        assertThat(ProjectSnippetCompletionProvider.languageId(ts, new File("/p/a.ts"))).isEqualTo("typescript");
        assertThat(ProjectSnippetCompletionProvider.languageId(ts, new File("/p/a.tsx"))).isEqualTo("typescriptreact");
        PlainDocument js = new PlainDocument();
        js.putProperty("mimeType", "text/javascript");
        assertThat(ProjectSnippetCompletionProvider.languageId(js, new File("/p/A.JSX"))).isEqualTo("javascriptreact");
        PlainDocument template = new PlainDocument();
        template.putProperty("mimeType", "text/x-ng-template");
        assertThat(ProjectSnippetCompletionProvider.languageId(template, new File("/p/a.component.html")))
                .as("the vocabulary a language server is told: an Angular template is html").isEqualTo("html");
        assertThat(ProjectSnippetCompletionProvider.languageId(new PlainDocument(), null)).isNull();
    }

    /** A source file with Unix line ends, whatever the checkout wrote (the Windows lane checks out CRLF). */
    private static String source(Path file) throws java.io.IOException {
        return Files.readString(file).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("the provider reads through the bounded lane and the item inserts through the engine: the call sites exist")
    void wiring() throws Exception {
        Path src = Path.of("src/main/java/org/nmox/studio/editor/snippets");
        String provider = source(src.resolve("ProjectSnippetCompletionProvider.java"));
        assertThat(provider).contains("ProjectSnippets.within(file, WAIT)")
                .contains("items(found.snippets(), languageId(doc, file), before,")
                .contains("return 0;");
        assertThat(provider).as("disk is never read on the calling thread here").doesNotContain("ProjectSnippets.read(");
        String item = source(src.resolve("ProjectSnippetCompletionItem.java"));
        assertThat(item).contains("SnippetInsertion.insert(component, snippet, workspace, startOffset, typed)")
                .contains("        Completion.get().hideAll();\n        insertInto(component);");
        String insertion = source(src.resolve("SnippetInsertion.java"));
        assertThat(insertion).contains("CodeTemplateManager.get(doc).createTemporary(text.text()).insert(component)")
                .contains("base.runAtomicAsUser(() -> {")
                .contains("engine.insert(doc, component, text);")
                .contains("component.putClientProperty(SnippetTemplateProcessor.PENDING, text)");
    }
}
