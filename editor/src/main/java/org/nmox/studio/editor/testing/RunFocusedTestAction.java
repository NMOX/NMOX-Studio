package org.nmox.studio.editor.testing;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.editor.EditorActionRegistrations;
import org.netbeans.editor.BaseAction;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 * Run the test under the cursor - the developer's actual inner loop.
 * Scans upward from the caret for the nearest test declaration and
 * runs exactly that one through the project's own runner, with output
 * (and clickable failure locations) in a "Focused Test" tab.
 */
@EditorActionRegistrations({
    @EditorActionRegistration(name = "nmox-run-focused-test", mimeType = "text/javascript",
            popupText = "Run Focused Test", popupPath = "", popupPosition = 7900),
    @EditorActionRegistration(name = "nmox-run-focused-test", mimeType = "text/typescript",
            popupText = "Run Focused Test", popupPath = "", popupPosition = 7900),
    @EditorActionRegistration(name = "nmox-run-focused-test", mimeType = "text/x-python",
            popupText = "Run Focused Test", popupPath = "", popupPosition = 7900),
    @EditorActionRegistration(name = "nmox-run-focused-test", mimeType = "text/x-go",
            popupText = "Run Focused Test", popupPath = "", popupPosition = 7900),
    @EditorActionRegistration(name = "nmox-run-focused-test", mimeType = "text/x-rust",
            popupText = "Run Focused Test", popupPath = "", popupPosition = 7900),
    @EditorActionRegistration(name = "nmox-run-focused-test", mimeType = "text/x-elixir",
            popupText = "Run Focused Test", popupPath = "", popupPosition = 7900)
})
public class RunFocusedTestAction extends BaseAction {

    private static final Pattern JS_TEST = Pattern.compile(
            "(?:it|test|describe)(?:\\.\\w+)?\\(\\s*['\"`](.+?)['\"`]");
    private static final Pattern PY_TEST = Pattern.compile("def\\s+(test_\\w+)");
    private static final Pattern GO_TEST = Pattern.compile("func\\s+(Test\\w+)");
    private static final Pattern RS_TEST = Pattern.compile("fn\\s+(\\w+)\\s*\\(");
    private static final Pattern EX_TEST = Pattern.compile("test\\s+\"(.+?)\"");

    public RunFocusedTestAction() {
        super("nmox-run-focused-test");
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target == null) {
            return;
        }
        Document doc = target.getDocument();
        FileObject fo = fileOf(doc);
        if (fo == null) {
            return;
        }
        File file = org.openide.filesystems.FileUtil.toFile(fo);
        String mime = (String) doc.getProperty("mimeType");
        int caretLine = lineOf(doc, target.getCaretPosition());
        String name = nearestMatch(doc, target.getCaretPosition(), patternFor(mime));

        Focused focused = commandFor(mime, file, name, caretLine);
        if (focused == null) {
            StatusDisplayer.getDefault().setStatusText(
                    "No test found above the caret" + (name == null ? "" : " for " + name));
            return;
        }
        StatusDisplayer.getDefault().setStatusText("Focused test: "
                + (name != null ? name : "line " + caretLine));
        CommandExecutor.showOutput("Focused Test");
        CommandExecutor.run("Focused Test", focused.dir(), Map.of(),
                focused.command(), line -> { }, code -> StatusDisplayer.getDefault()
                        .setStatusText(code == 0 ? "Focused test PASSED" : "Focused test FAILED [" + code + "]"));
    }

    private record Focused(List<String> command, File dir) {
    }

    private static Focused commandFor(String mime, File file, String name, int line) {
        File root = file.getParentFile();
        // walk up to the relevant manifest so runners resolve correctly
        for (File d = root; d != null; d = d.getParentFile()) {
            if (ProjectInspector.hasProjectManifest(d)) {
                root = d;
                break;
            }
        }
        String path = file.getAbsolutePath();
        return switch (mime) {
            case "text/javascript", "text/typescript" -> {
                if (name == null) {
                    yield null;
                }
                boolean vitest = ProjectInspector.firstDependency(root, "vitest") != null;
                yield new Focused(vitest
                        ? List.of("npx", "vitest", "run", "-t", name, path)
                        : List.of("npx", "jest", path, "-t", name), root);
            }
            case "text/x-python" -> name == null ? null
                    : new Focused(List.of("python3", "-m", "pytest", path + "::" + name, "-v"), root);
            case "text/x-go" -> name == null ? null
                    : new Focused(List.of("go", "test", "-run", "^" + name + "$", "./..."), root);
            case "text/x-rust" -> name == null ? null
                    : new Focused(List.of("cargo", "test", name), root);
            case "text/x-elixir" ->
                new Focused(List.of("mix", "test", path + ":" + line), root);
            default -> null;
        };
    }

    private static Pattern patternFor(String mime) {
        return switch (mime) {
            case "text/javascript", "text/typescript" -> JS_TEST;
            case "text/x-python" -> PY_TEST;
            case "text/x-go" -> GO_TEST;
            case "text/x-rust" -> RS_TEST;
            case "text/x-elixir" -> EX_TEST;
            default -> null;
        };
    }

    /** Nearest declaration at or above the caret. */
    private static String nearestMatch(Document doc, int caret, Pattern pattern) {
        if (pattern == null) {
            return null;
        }
        try {
            String upToCaret = doc.getText(0, Math.min(doc.getLength(),
                    caret + 200 > doc.getLength() ? doc.getLength() : caret + 200));
            Matcher m = pattern.matcher(upToCaret);
            String best = null;
            while (m.find()) {
                if (m.start() > caret + 200) {
                    break;
                }
                best = m.group(1);
            }
            return best;
        } catch (BadLocationException ex) {
            return null;
        }
    }

    private static int lineOf(Document doc, int offset) {
        Element root = doc.getDefaultRootElement();
        return root.getElementIndex(offset) + 1;
    }

    private static FileObject fileOf(Document doc) {
        Object sdp = doc.getProperty(Document.StreamDescriptionProperty);
        if (sdp instanceof DataObject dataObject) {
            return dataObject.getPrimaryFile();
        }
        return sdp instanceof FileObject fo ? fo : null;
    }
}
