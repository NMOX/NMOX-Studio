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
            popupText = "Run Focused Test", popupPath = "", popupPosition = 7900),
    @EditorActionRegistration(name = "nmox-run-focused-test", mimeType = "text/x-php5",
            popupText = "Run Focused Test", popupPath = "", popupPosition = 7900)
})
public class RunFocusedTestAction extends BaseAction {

    private static final Pattern JS_TEST = Pattern.compile(
            "(?:it|test|describe)(?:\\.\\w+)?\\(\\s*['\"`](.+?)['\"`]");
    private static final Pattern PY_TEST = Pattern.compile("def\\s+(test_\\w+)");
    private static final Pattern GO_TEST = Pattern.compile("func\\s+(Test\\w+)");
    // A bare `fn` is NOT a test: `cargo test <helper-fn>` matches nothing,
    // exits ZERO ("0 passed; 0 failed", measured on cargo 1.95), and the
    // action would report "Focused test PASSED" for code that never ran —
    // the v1.257.0 false-green class. Only a fn under a test attribute
    // counts: #[test], #[tokio::test], #[rstest], #[test_case(...)], with
    // optional further attributes (#[ignore]) between it and the fn.
    private static final Pattern RS_TEST = Pattern.compile(
            "#\\[[^\\]]*test[^\\]]*\\]\\s*(?:#\\[[^\\]]*\\]\\s*)*(?:pub\\s+)?(?:async\\s+)?fn\\s+(\\w+)");
    private static final Pattern EX_TEST = Pattern.compile("test\\s+\"(.+?)\"");
    // PHPUnit's two declaration shapes: the classic test-prefixed method
    // and the PHP 8 #[Test] attribute on an arbitrarily named method.
    // The name lands in group 1 either way.
    private static final Pattern PHP_TEST = Pattern.compile(
            "(?:#\\[Test\\]\\s*public\\s+function|public\\s+function(?=\\s+test))\\s+(\\w+)\\s*\\(");

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
        // A focused test EXECUTES the project's committed code (the spec,
        // its imports, the runner's own config) — the same inward flow the
        // debug action gates. Missed by the v1.103.0 sweep; found by the
        // v1.223.0 Angular pass (the DapDebugAction idiom, prompt-once).
        if (!org.nmox.studio.rack.service.WorkspaceTrust.requestTrust(focused.dir())) {
            StatusDisplayer.getDefault().setStatusText(
                    "Focused test not run — workspace not trusted");
            return;
        }
        StatusDisplayer.getDefault().setStatusText("Focused test: "
                + (name != null ? name : "line " + caretLine));
        CommandExecutor.showOutput("Focused Test");
        CommandExecutor.run("Focused Test", focused.dir(), Map.of(),
                focused.command(), line -> { }, code -> StatusDisplayer.getDefault()
                        .setStatusText(code == 0 ? "Focused test PASSED" : "Focused test FAILED [" + code + "]"));
    }

    record Focused(List<String> command, File dir) {
    }

    static Focused commandFor(String mime, File file, String name, int line) {
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
                // Deno workspaces test with the runtime's own runner. The
                // --filter argument is a SUBSTRING match unless wrapped in
                // /slashes/ as a regex (the v1.257.0 lesson: metacharacters
                // in real test names must be escaped or the filter silently
                // matches nothing and reports a false green).
                File denoRoot = null;
                for (File d = file.getParentFile(); d != null; d = d.getParentFile()) {
                    if (new File(d, "deno.json").isFile()
                            || new File(d, "deno.jsonc").isFile()) {
                        denoRoot = d;
                        break;
                    }
                }
                if (denoRoot != null) {
                    // "/" additionally escaped: it would terminate deno's
                    // /.../ regex wrapper (JS regex accepts \/; the shared
                    // escaper must NOT learn it — go's RE2 rejects it)
                    String pattern = name == null ? null
                            : org.nmox.studio.rack.devices.TestDevice
                                    .regexLiteral(name).replace("/", "\\/");
                    yield pattern == null
                            ? new Focused(List.of("deno", "test", path), denoRoot)
                            : new Focused(List.of("deno", "test", "--filter",
                                    "/^" + pattern + "$/", path), denoRoot);
                }
                // The Angular workspace root is found by walking up for
                // angular.json ITSELF — the generic manifest walk above
                // stops at src/ because Angular's src/index.html is a
                // STATIC-kind manifest (v1.34.0), which both mislocates
                // the root and hides angular.json (found LIVE: the trust
                // prompt named ngdemo/src, and the branch fell to jest).
                File ngRoot = null;
                for (File d = file.getParentFile(); d != null; d = d.getParentFile()) {
                    if (new File(d, "angular.json").isFile()) {
                        ngRoot = d;
                        break;
                    }
                }
                File depRoot = ngRoot != null ? ngRoot : root;
                boolean vitest = ProjectInspector.firstDependency(depRoot, "vitest") != null;
                boolean jest = ProjectInspector.firstDependency(depRoot, "jest") != null;
                // An Angular workspace with neither runner declared tests
                // through the CLI's own runner (Karma by default) — the old
                // blind `npx jest` fallback failed for every Angular dev.
                // Karma has no test-NAME filter, so file-level focus via
                // --include is the honest ceiling; --watch=false makes it
                // one-shot, ChromeHeadless keeps it windowless.
                if (!vitest && !jest && ngRoot != null) {
                    if (!file.getName().endsWith(".spec.ts")) {
                        yield null; // only a spec file can be focused via ng test
                    }
                    File localNg = new File(ngRoot, "node_modules/.bin/ng");
                    String ng = localNg.isFile() ? localNg.getAbsolutePath() : "ng";
                    String rel = ngRoot.toPath().relativize(file.toPath())
                            .toString().replace('\\', '/');
                    yield new Focused(List.of(ng, "test", "--watch=false",
                            "--browsers=ChromeHeadless", "--include=" + rel), ngRoot);
                }
                if (name == null) {
                    yield null;
                }
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
            case "text/x-php5" -> {
                if (name == null) {
                    yield null;
                }
                // like the jest/vitest split: prefer the project's own
                // runner (composer's vendor/bin) over a global install
                File local = new File(new File(root, "vendor"), "bin/phpunit");
                String phpunit = local.isFile() ? local.getAbsolutePath() : "phpunit";
                yield new Focused(List.of(phpunit, "--filter", name,
                        root.toPath().relativize(file.toPath()).toString()), root);
            }
            default -> null;
        };
    }

    static Pattern patternFor(String mime) {
        return switch (mime) {
            case "text/javascript", "text/typescript" -> JS_TEST;
            case "text/x-python" -> PY_TEST;
            case "text/x-go" -> GO_TEST;
            case "text/x-rust" -> RS_TEST;
            case "text/x-elixir" -> EX_TEST;
            case "text/x-php5" -> PHP_TEST;
            default -> null;
        };
    }

    /** Nearest declaration at or above the caret. */
    static String nearestMatch(Document doc, int caret, Pattern pattern) {
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
