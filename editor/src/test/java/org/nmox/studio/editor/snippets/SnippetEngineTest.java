package org.nmox.studio.editor.snippets;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.Caret;
import javax.swing.text.DefaultCaret;
import javax.swing.undo.UndoManager;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseTextUI;
import org.netbeans.modules.editor.NbEditorKit;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Snippet;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A project snippet put through the platform's REAL code-template
 * engine, headless: what is in the document afterwards, where the
 * caret is, what typing into a tab stop does, and what one undo takes
 * back. The translation's own tests pin the string; this pins that the
 * engine reads the string the way the translation believes it does,
 * which is the half a platform upgrade can change.
 *
 * <p>The pane is a real {@link NbEditorKit} pane (the engine drives tab
 * stops only on the platform's own text UI) with a plain caret, because
 * the platform's caret asks for the system selection and there is none
 * without a screen. The engine finds the product's processor factory
 * through {@link SnippetEngineMimeData}.
 */
class SnippetEngineTest {

    private static final String MIME = SnippetEngineMimeData.MIME;

    /** An editor: document, pane and an undo manager listening. */
    private static final class Editor {
        BaseDocument doc;
        JEditorPane pane;
        UndoManager undo;

        String text() throws Exception {
            return doc.getText(0, doc.getLength());
        }

        String selection() throws Exception {
            String t = text();
            return t.substring(pane.getSelectionStart(), pane.getSelectionEnd());
        }
    }

    private static <T> T onEdt(Callable<T> body) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                out.set(body.call());
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        if (thrown.get() instanceof Exception e) {
            throw e;
        }
        if (thrown.get() instanceof Error e) {
            throw e;
        }
        return out.get();
    }

    private static Editor editor(String text, int caret, boolean platformUi) throws Exception {
        Editor e = new Editor();
        e.doc = new BaseDocument(false, MIME);
        e.doc.putProperty("mimeType", MIME);
        e.doc.insertString(0, text, null);
        e.undo = new UndoManager();
        e.doc.addUndoableEditListener(e.undo);
        e.pane = new JEditorPane();
        if (platformUi) {
            e.pane.setEditorKit(new NbEditorKit() {
                @Override
                public Caret createCaret() {
                    return new DefaultCaret();
                }

                @Override
                public String getContentType() {
                    return MIME;
                }
            });
        }
        e.pane.setDocument(e.doc);
        e.pane.setCaretPosition(caret);
        return e;
    }

    private static Snippet snippet(String body) throws Exception {
        return new Snippet("probe", List.of("lo"), "", Set.of(), SnippetBody.parse(body),
                body, "team.code-snippets");
    }

    /** Accepts {@code body} at the end of {@code "  lo"} with {@code lo} typed, as the completion item does. */
    private static Editor accept(String body) throws Exception {
        Editor e = editor("  lo\nnext", 4, true);
        assertThat(SnippetInsertion.insert(e.pane, snippet(body), null, 2, 2)).isTrue();
        return e;
    }

    @Test
    @DisplayName("the pane is one the engine drives: the platform's text UI")
    void thePaneIsThePlatforms() throws Exception {
        onEdt(() -> {
            assertThat(editor("x", 0, true).pane.getUI()).isInstanceOf(BaseTextUI.class);
            return null;
        });
    }

    @Test
    @DisplayName("a body lands with its first tab stop selected, and typing there is typed into its mirror")
    void tabStopsAndMirrors() throws Exception {
        onEdt(() -> {
            Editor e = accept("console.log('${1:label}', $1);$0");
            assertThat(e.text()).isEqualTo("  console.log('label', label);\nnext");
            assertThat(e.selection()).isEqualTo("label");
            assertThat(e.pane.getSelectionStart()).isEqualTo("  console.log('".length());
            e.pane.replaceSelection("user");
            assertThat(e.text()).isEqualTo("  console.log('user', user);\nnext");
            return null;
        });
    }

    @Test
    @DisplayName("the lower-numbered stop is first even when it is written second")
    void tabOrderIsTheNumbers() throws Exception {
        onEdt(() -> {
            Editor e = accept("${2:second} ${1:first}");
            assertThat(e.text()).isEqualTo("  second first\nnext");
            assertThat(e.selection()).isEqualTo("first");
            return null;
        });
    }

    @Test
    @DisplayName("with no tab stop the caret is at $0, inside the indented body")
    void caretLandsAtZero() throws Exception {
        onEdt(() -> {
            Editor e = accept("try {\n\t$0\n} finally {\n}");
            String expected = "  try {\n      \n  } finally {\n  }\nnext";
            assertThat(e.text()).isEqualTo(expected);
            assertThat(e.pane.getCaretPosition()).isEqualTo("  try {\n      ".length());
            assertThat(e.pane.getSelectionStart()).isEqualTo(e.pane.getSelectionEnd());
            return null;
        });
    }

    /**
     * Presses a key the engine took over for the life of a template. The
     * engine puts its own action where the pane's input map sends the
     * key (read from {@code TextRegionManager$OverrideAction}: the
     * binding the input map names for the keystroke, else
     * {@code insert-tab} / {@code insert-break}), so the action is looked
     * up the way Swing would on a real keypress and invoked.
     */
    private static void press(Editor e, int key, String fallbackName) {
        Object binding = e.pane.getInputMap().get(KeyStroke.getKeyStroke(key, 0));
        Action action = e.pane.getActionMap().get(binding != null ? binding : fallbackName);
        assertThat(action.getClass().getName())
                .as("the key is the engine's while a template is being filled in")
                .contains("TextRegionManager");
        action.actionPerformed(new ActionEvent(e.pane, ActionEvent.ACTION_PERFORMED, fallbackName));
    }

    @Test
    @DisplayName("Tab walks the stops in number order and comes round again; Enter walks them and ends at $0")
    void tabAndEnter() throws Exception {
        onEdt(() -> {
            Editor e = accept("if (${2:b} && ${1:a}) {\n\t$0\n}");
            String text = "  if (b && a) {\n      \n  }\nnext";
            assertThat(e.text()).isEqualTo(text);
            assertThat(e.selection()).isEqualTo("a");
            press(e, KeyEvent.VK_TAB, "insert-tab");
            assertThat(e.selection()).isEqualTo("b");
            assertThat(e.pane.getSelectionStart()).isEqualTo("  if (".length());
            // the engine's keys, not VS Code's: Tab after the last stop returns to
            // the first; Enter goes forward too, and after the last stop leaves
            // the snippet with the caret at $0
            press(e, KeyEvent.VK_TAB, "insert-tab");
            assertThat(e.selection()).isEqualTo("a");
            press(e, KeyEvent.VK_ENTER, "insert-break");
            assertThat(e.selection()).isEqualTo("b");
            press(e, KeyEvent.VK_ENTER, "insert-break");
            assertThat(e.text()).isEqualTo(text);
            assertThat(e.pane.getSelectionStart()).isEqualTo("  if (b && a) {\n      ".length());
            assertThat(e.pane.getSelectionEnd()).isEqualTo(e.pane.getSelectionStart());
            return null;
        });
    }

    @Test
    @DisplayName("text that reads like the engine's own syntax arrives as text")
    void hostileBodiesAreText() throws Exception {
        onEdt(() -> {
            // \${cursor} is a literal in VS Code; unescaped it would be the engine's caret parameter
            Editor a = accept("a \\${cursor} b ${no-indent} c $$ d ${ e");
            assertThat(a.text()).isEqualTo("  a ${cursor} b ${no-indent} c $$ d ${ e\nnext");
            assertThat(a.pane.getCaretPosition())
                    .as("the caret is at the end, not where a swallowed ${cursor} would have put it")
                    .isEqualTo("  a ${cursor} b ${no-indent} c $$ d ${ e".length());

            Editor b = accept("x = ${1:a $ b ${y} } \" \\\\ ;");
            assertThat(b.text()).isEqualTo("  x = a $ b y  \" \\ ;\nnext");
            assertThat(b.selection()).isEqualTo("a $ b y ");
            return null;
        });
    }

    @Test
    @DisplayName("a default written into the template arrives whole: braces, a dollar, a line break, even the engine's own syntax")
    void defaultsInPlaceArriveWhole() throws Exception {
        onEdt(() -> {
            Editor e = accept("f(${1:{ a: \\$b \\}\nnext}) $1");
            String d = "{ a: $b }\n  next";
            assertThat(e.text()).isEqualTo("  f(" + d + ") " + d + "\nnext");
            assertThat(e.selection()).isEqualTo(d);

            Editor looksLikeAParameter = accept("v = ${1:\\${cursor\\} \\${x default=y\\}}; $0");
            assertThat(looksLikeAParameter.text()).isEqualTo("  v = ${cursor} ${x default=y}; \nnext");
            assertThat(looksLikeAParameter.selection()).isEqualTo("${cursor} ${x default=y}");
            return null;
        });
    }

    @Test
    @DisplayName("a default with a quote or a backslash arrives whole, through the processor")
    void quotedDefaultsArriveWhole() throws Exception {
        onEdt(() -> {
            Editor e = accept("say(${1:\"he said \\\\\"hi\\\\\" C:\\\\dir\"}) $1");
            String d = "\"he said \\\"hi\\\" C:\\dir\"";
            assertThat(e.text()).isEqualTo("  say(" + d + ") " + d + "\nnext");
            assertThat(e.selection()).isEqualTo(d);
            return null;
        });
    }

    @Test
    @DisplayName("the engine's hint escapes really do drop a character, which is why such a default travels beside the template")
    void theEngineDefectIsStillThere() throws Exception {
        onEdt(() -> {
            Editor e = editor("", 0, true);
            org.netbeans.lib.editor.codetemplates.api.CodeTemplateManager.get(e.doc)
                    .createTemporary("${no-indent}${v default=\"a\\\"bcd\"}").insert(e.pane);
            assertThat(e.text())
                    .as("if this ever reads a\"bcd the platform fixed its escape, and "
                            + "SnippetTemplates.grammarCarries can carry quotes in place again")
                    .isEqualTo("a\"cd");
            return null;
        });
    }

    @Test
    @DisplayName("a template that is not the announced snippet is left alone, even with a parameter of the same name")
    void foreignTemplatesAreLeftAlone() throws Exception {
        onEdt(() -> {
            Editor e = editor("", 0, true);
            SnippetTemplates.CodeTemplateText stale = SnippetTemplates.toCodeTemplate(
                    SnippetBody.parse("${1:\"a quoted default the processor would set\"}"),
                    SnippetContext.at(java.time.ZonedDateTime.now(), new java.util.Random(1)));
            assertThat(stale.values()).containsKey("t1");
            e.pane.putClientProperty(SnippetTemplateProcessor.PENDING, stale);
            try {
                org.netbeans.lib.editor.codetemplates.api.CodeTemplateManager.get(e.doc)
                        .createTemporary("${no-indent}${t1 default=\"somebody else's\"}").insert(e.pane);
            } finally {
                e.pane.putClientProperty(SnippetTemplateProcessor.PENDING, null);
            }
            assertThat(e.text()).isEqualTo("somebody else's");
            return null;
        });
    }

    @Test
    @DisplayName("a transformed mirror follows its source as the source is typed")
    void liveTransform() throws Exception {
        onEdt(() -> {
            Editor e = accept("class ${1:name} { static tag = '${1/(.*)/${1:/upcase}/}'; }");
            assertThat(e.text()).isEqualTo("  class name { static tag = 'NAME'; }\nnext");
            assertThat(e.selection()).isEqualTo("name");
            e.pane.replaceSelection("card");
            assertThat(e.text()).isEqualTo("  class card { static tag = 'CARD'; }\nnext");
            return null;
        });
    }

    @Test
    @DisplayName("one undo takes back the body and gives the typed prefix back")
    void oneUndo() throws Exception {
        onEdt(() -> {
            Editor e = accept("for (const ${1:item} of ${2:items}) {\n\t$0\n}");
            assertThat(e.text()).startsWith("  for (const item of items) {");
            assertThat(e.undo.canUndo()).isTrue();
            e.undo.undo();
            assertThat(e.text()).isEqualTo("  lo\nnext");
            return null;
        });
    }

    @Test
    @DisplayName("accepted in the middle of a token, the rest of the token goes too")
    void midTokenFoldsTheTail() throws Exception {
        onEdt(() -> {
            Editor e = editor("  log.x\nnext", 4, true);
            assertThat(SnippetInsertion.insert(e.pane, snippet("print($0)"), null, 2, 2)).isTrue();
            assertThat(e.text()).isEqualTo("  print().x\nnext");
            assertThat(e.pane.getCaretPosition()).isEqualTo("  print(".length());

            Editor fresh = editor("  word\nnext", 2, true);
            assertThat(SnippetInsertion.insert(fresh.pane, snippet("print($0)"), null, 2, 0)).isTrue();
            assertThat(fresh.text())
                    .as("with nothing typed, the word after the caret is the user's and stays")
                    .isEqualTo("  print()word\nnext");

            Editor top = editor("word", 0, true);
            assertThat(SnippetInsertion.insert(top.pane, snippet("print($0)"), null, 0, 0))
                    .as("at the very start of a document there is no character before the caret to ask about")
                    .isTrue();
            assertThat(top.text()).isEqualTo("print()word");
            return null;
        });
    }

    @Test
    @DisplayName("an editor the engine cannot drive gets the text and the caret at $0")
    void plainEditorGetsPlainText() throws Exception {
        onEdt(() -> {
            Editor e = editor("  lo\nnext", 4, false);
            assertThat(e.pane.getUI()).isNotInstanceOf(BaseTextUI.class);
            java.util.List<java.util.logging.LogRecord> warnings = new java.util.ArrayList<>();
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(SnippetInsertion.class.getName());
            java.util.logging.Handler handler = new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord r) {
                    if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                        warnings.add(r);
                    }
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            };
            logger.addHandler(handler);
            try {
                assertThat(SnippetInsertion.insert(e.pane, snippet("if (${1:ok}) {\n\t$0\n}"), null, 2, 2)).isTrue();
            } finally {
                logger.removeHandler(handler);
            }
            assertThat(e.text()).isEqualTo("  if (ok) {\n      \n  }\nnext");
            assertThat(e.pane.getCaretPosition()).isEqualTo("  if (ok) {\n      ".length());
            assertThat(warnings)
                    .as("the engine is not even tried on an editor it cannot drive: no failure to recover from")
                    .isEmpty();
            return null;
        });
    }

    @Test
    @DisplayName("the context is read off the editor: the caret's line with the prefix still in it, its word, its indentation, the language's comments")
    void contextReadsTheEditor() throws Exception {
        onEdt(() -> {
            String text = "function f() {\n\t  const total = lo\n}\n";
            int caret = text.indexOf("lo\n}") + 2;
            Editor e = editor(text, caret, true);
            e.doc.putProperty("mimeType", "text/typescript");
            SnippetContext ctx = SnippetInsertion.context(e.pane, e.doc, caret, new java.io.File("/work/shop"));
            assertThat(ctx.currentLine()).isEqualTo("\t  const total = lo");
            assertThat(ctx.currentWord()).as("VS Code's word at the caret is the prefix being typed").isEqualTo("lo");
            assertThat(ctx.lineIndex()).isEqualTo(1);
            assertThat(ctx.lineIndent()).isEqualTo("\t  ");
            assertThat(ctx.indentUnit()).matches("\t| +");
            assertThat(ctx.selectedText()).isNull();
            assertThat(ctx.lineComment()).isEqualTo("//");
            assertThat(ctx.blockCommentStart()).isEqualTo("/*");
            assertThat(ctx.blockCommentEnd()).isEqualTo("*/");
            assertThat(ctx.workspaceFolder()).endsWith("shop");
            assertThat(ctx.filePath()).as("a document with no file").isNull();
            assertThat(ctx.clipboard().get()).as("no clipboard without a screen: not set, not an exception").isNull();
            assertThat(SnippetVariables.resolve("TM_LINE_NUMBER", ctx)).isEqualTo("2");
            return null;
        });
    }

    @Test
    @DisplayName("a variable is answered for the editor it is inserted into")
    void variablesSeeTheEditor() throws Exception {
        onEdt(() -> {
            Editor e = editor("  lo\nnext", 4, true);
            assertThat(SnippetInsertion.insert(e.pane, snippet("// line $TM_LINE_NUMBER: [$TM_CURRENT_LINE] $1"),
                    null, 2, 2)).isTrue();
            assertThat(e.text()).isEqualTo("  // line 1: [  lo] \nnext");
            return null;
        });
    }

    @Test
    @DisplayName("end to end: a file a team committed, read from disk, offered for what was typed, accepted into the editor")
    void fromTheFileToTheEditor(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path project = java.nio.file.Files.createDirectories(tmp.resolve("shop"));
        java.nio.file.Files.writeString(project.resolve("package.json"), "{}");
        java.nio.file.Files.createDirectories(project.resolve(".vscode"));
        // as it sits in the repository: JSON's own escapes around the snippet's
        java.nio.file.Files.writeString(project.resolve(".vscode/team.code-snippets"), """
                {
                  // the team's route
                  "Express route": {
                    "prefix": ["route", "rt"],
                    "scope": "javascript,typescript",
                    "description": "A route with a handler",
                    "body": [
                      "${1:router}.${2|get,post|}('/${3:path}', (req, res) => {",
                      "\\tres.json({ in: '$WORKSPACE_NAME', cost: '\\\\$5' });$0",
                      "});"
                    ]
                  },
                  "Python only": { "prefix": "route", "scope": "python", "body": "nope" }
                }
                """);
        java.io.File edited = project.resolve("src/server.js").toFile();
        java.nio.file.Files.createDirectories(edited.toPath().getParent());
        java.nio.file.Files.writeString(edited.toPath(), "");
        ProjectSnippets.Found found = ProjectSnippets.read(edited);

        onEdt(() -> {
            Editor e = editor("app.use(x);\n  rou\n", "app.use(x);\n  rou".length(), true);
            java.util.List<ProjectSnippetCompletionItem> rows = ProjectSnippetCompletionProvider.items(
                    found.snippets(), "javascript", "  rou", found.workspace(), e.pane.getCaretPosition());
            assertThat(rows).hasSize(1);
            assertThat(ProjectSnippetCompletionItem.label(rows.get(0).snippet(), "route"))
                    .isEqualTo("route — Express route (A route with a handler)");
            assertThat(rows.get(0).insertInto(e.pane)).isTrue();
            assertThat(e.text()).isEqualTo("app.use(x);\n"
                    + "  router.get('/path', (req, res) => {\n"
                    + "      res.json({ in: 'shop', cost: '$5' });\n"
                    + "  });\n");
            assertThat(e.selection()).isEqualTo("router");
            return null;
        });
    }

    @Test
    @DisplayName("when the engine throws, what it did is taken back and the snippet arrives as text, as one undo")
    void engineFailureFallsBackToText() throws Exception {
        onEdt(() -> {
            Editor e = editor("  lo\nnext", 4, true);
            boolean inserted = SnippetInsertion.insert(e.pane, snippet("if (${1:ok}) {\n\t$0\n}"), null, 2, 2,
                    (doc, component, text) -> {
                        try {
                            doc.insertString(0, "half an insertion", null);
                        } catch (javax.swing.text.BadLocationException ex) {
                            throw new AssertionError(ex);
                        }
                        throw new IllegalStateException("the engine broke");
                    });
            assertThat(inserted).isTrue();
            assertThat(e.text()).isEqualTo("  if (ok) {\n      \n  }\nnext");
            assertThat(e.pane.getCaretPosition()).isEqualTo("  if (ok) {\n      ".length());
            e.undo.undo();
            assertThat(e.text()).isEqualTo("  lo\nnext");
            return null;
        });
    }

    @Test
    @DisplayName("a transform that runs past its clock on this line inserts nothing at all")
    void refusedInsertionLeavesTheDocument() throws Exception {
        onEdt(() -> {
            String line = "a".repeat(300) + " lo";
            Editor e = editor(line + "\nnext", line.length(), true);
            // six greedy runs and no match: about 300^6 / 720 ways to cut the line,
            // and nothing in it that the load-time check can call a repeated repeat
            Snippet hostile = snippet("// ${TM_CURRENT_LINE/(.*)(.*)(.*)(.*)(.*)(.*)!!/x/} $1");
            assertThat(SnippetInsertion.insert(e.pane, hostile, null, line.length() - 2, 2)).isFalse();
            assertThat(e.text()).isEqualTo(line + "\nnext");
            assertThat(e.undo.canUndo()).isFalse();
            return null;
        });
    }

    @Test
    @DisplayName("the provider and the processor factory are registered for every language, the provider with a position")
    void registeredAtTheRoot() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Element root = dbf.newDocumentBuilder()
                .parse(Path.of("target/classes/META-INF/generated-layer.xml").toFile()).getDocumentElement();
        Element editors = child(root, "folder", "Editors");
        Element provider = child(child(editors, "folder", "CompletionProviders"), "file",
                "org-nmox-studio-editor-snippets-ProjectSnippetCompletionProvider.instance");
        assertThat(provider).as("Editors/CompletionProviders holds the provider: the root, every mime's parent")
                .isNotNull();
        assertThat(attr(provider, "position")).isEqualTo("590");
        assertThat(child(child(editors, "folder", "CodeTemplateProcessorFactories"), "file",
                "org-nmox-studio-editor-snippets-SnippetTemplateProcessor$Factory.instance"))
                .as("Editors/CodeTemplateProcessorFactories holds the factory the engine asks")
                .isNotNull();
    }

    private static Element child(Element parent, String tag, String name) {
        if (parent == null) {
            return null;
        }
        NodeList kids = parent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element e && tag.equals(e.getTagName()) && name.equals(e.getAttribute("name"))) {
                return e;
            }
        }
        return null;
    }

    private static String attr(Element file, String name) {
        NodeList kids = file.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element e && "attr".equals(e.getTagName()) && name.equals(e.getAttribute("name"))) {
                return e.getAttribute("intvalue");
            }
        }
        return null;
    }
}
