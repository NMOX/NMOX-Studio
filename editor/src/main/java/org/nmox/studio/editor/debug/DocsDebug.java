package org.nmox.studio.editor.debug;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.text.StyledDocument;
import org.netbeans.api.debugger.DebuggerManager;
import org.nmox.studio.core.spi.DocsScene;
import org.nmox.studio.core.util.DocsFixtures;
import org.openide.awt.Actions;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;

/**
 * Stages the breakpoint picture (v2.164.0): a real Node script paused on
 * a real breakpoint, its variables live in the debugger's own views.
 *
 * <p>The script is the shop's nightly stock report, one catalogue item per
 * line so the breakpoint's line is the same in every language while the
 * item names, the comment and the printed labels are the reader's. The
 * breakpoint is set the way a user sets one — the platform's own Toggle
 * Line Breakpoint action with the caret on the line — and the run starts
 * through the same launch the editor's Debug File row uses.
 *
 * <p>That launch asks Workspace Trust first, and a dialog would stall the
 * forge. The scene grants trust in the trust store's SCRATCH node (the one
 * the test suites use, emptied first), never in the developer's real
 * grants: a throwaway forge fixture must not stay trusted on their machine
 * after the run (the DocsStaging law, v2.162.0).
 */
@ServiceProvider(service = DocsScene.class)
public final class DocsDebug implements DocsScene {

    /** The scene's name, as its picture is named. */
    public static final String ID = "debug-javascript";

    /** The script's name in the demo shop. */
    public static final String SCRIPT = "inventory.js";

    /** The 1-based line the run pauses on: {@code totalValue += value;}. */
    public static final int BREAKPOINT_LINE = 18;

    /** SKU, price and stock per catalogue line — numbers are the same everywhere. */
    private static final String[][] CATALOGUE = {
        {"KTL-06", "49.00", "41"},
        {"GRD-40", "129.00", "17"},
        {"FLT-01", "6.95", "88"},
        {"CRF-60", "24.50", "12"},
    };

    /** Holds after the session appears, so the adapter has paused and the views filled. */
    static final long PAUSE_HOLD_MS = 6_000;

    private File script;
    private int phase;
    private long phaseAt;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public File stage(File home, String fixtures, String lang) throws IOException {
        File dir = DocsFixtures.projectDir(home);
        Files.createDirectories(dir.toPath());
        script = new File(dir, SCRIPT);
        Files.writeString(script.toPath(), source(
                DocsFixtures.text(fixtures, lang, "inventory", "comment"),
                DocsFixtures.strings(fixtures, lang, "inventory", "items"),
                DocsFixtures.text(fixtures, lang, "inventory", "value"),
                DocsFixtures.text(fixtures, lang, "inventory", "restock"),
                DocsFixtures.text(fixtures, lang, "inventory", "shelf")), StandardCharsets.UTF_8);
        phase = 0;
        return dir;
    }

    /**
     * The script. Its shape is fixed so {@link #BREAKPOINT_LINE} holds in
     * every language; only quoted text varies, escaped for its quotes.
     */
    static String source(String comment, List<String> items, String value, String restock, String shelf) {
        StringBuilder s = new StringBuilder();
        s.append("// ").append(comment.replace('\n', ' ')).append('\n');
        s.append("const catalog = [\n");
        for (int i = 0; i < CATALOGUE.length; i++) {
            String name = i < items.size() ? items.get(i) : CATALOGUE[i][0];
            s.append("  { sku: '").append(CATALOGUE[i][0]).append("', name: '").append(quoted(name))
                    .append("', price: ").append(CATALOGUE[i][1]).append(", stock: ").append(CATALOGUE[i][2])
                    .append(" },\n");
        }
        s.append("];\n\n");
        s.append("function restockCost(item, target) {\n");
        s.append("  const missing = Math.max(0, target - item.stock);\n");
        s.append("  return missing * item.price;\n");
        s.append("}\n\n");
        s.append("let totalValue = 0;\n");
        s.append("let restockBudget = 0;\n");
        s.append("for (const item of catalog) {\n");
        s.append("  const value = item.price * item.stock;\n");
        s.append("  totalValue += value;\n");
        s.append("  restockBudget += restockCost(item, 50);\n");
        s.append("  console.log(`${item.sku}  ${item.name}: ${value.toFixed(2)} ").append(template(shelf)).append("`);\n");
        s.append("}\n\n");
        s.append("console.log(`").append(template(value)).append(": ${totalValue.toFixed(2)}`);\n");
        s.append("console.log(`").append(template(restock)).append(": ${restockBudget.toFixed(2)}`);\n");
        return s.toString();
    }

    private static String quoted(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
    }

    private static String template(String text) {
        return text.replace("\\", "\\\\").replace("`", "\\`").replace("${", "\\${").replace("\n", " ");
    }

    @Override
    public void arrange() {
        phase = 0;
        phaseAt = System.currentTimeMillis();
        if (script != null) {
            org.nmox.studio.rack.service.DocsStaging.openFile(script);
        }
    }

    /**
     * Polled on the EDT. Each step waits for the one before it to land:
     * the editor showing, then the breakpoint set with the caret on its
     * line, then the session up, then a hold while the adapter pauses.
     */
    @Override
    public boolean ready() {
        if (script == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        switch (phase) {
            case 0 -> {
                JEditorPane pane = pane();
                if (pane == null || !pane.isShowing()) {
                    return false;
                }
                StyledDocument doc = (StyledDocument) pane.getDocument();
                pane.setCaretPosition(NbDocument.findLineOffset(doc, BREAKPOINT_LINE - 1));
                TopComponent tc = (TopComponent) javax.swing.SwingUtilities.getAncestorOfClass(TopComponent.class, pane);
                if (tc != null) {
                    tc.requestActive();
                }
                pane.requestFocusInWindow();
                advance(now);
            }
            case 1 -> {
                if (now - phaseAt < 1_000) {
                    return false; // the editor context follows focus on its own time
                }
                if (DebuggerManager.getDebuggerManager().getBreakpoints().length == 0) {
                    Action toggle = Actions.forID("Debug", "org.netbeans.modules.debugger.ui.actions.ToggleBreakpointAction");
                    if (toggle != null) {
                        toggle.actionPerformed(new ActionEvent(pane(), ActionEvent.ACTION_PERFORMED, "docs-shot"));
                    }
                }
                advance(now);
            }
            case 2 -> {
                if (DebuggerManager.getDebuggerManager().getBreakpoints().length == 0) {
                    return false;
                }
                File root = DapDebugAction.projectRoot(script);
                org.nmox.studio.rack.service.WorkspaceTrust.clearForTest(); // the scratch store, emptied
                org.nmox.studio.rack.service.WorkspaceTrust.trust(root);
                DapDebugAction.launch(script, "text/javascript");
                advance(now);
            }
            case 3 -> {
                if (DebuggerManager.getDebuggerManager().getCurrentSession() == null) {
                    return false;
                }
                advance(now);
            }
            default -> {
                return now - phaseAt >= PAUSE_HOLD_MS;
            }
        }
        return false;
    }

    private void advance(long now) {
        phase++;
        phaseAt = now;
    }

    private JEditorPane pane() {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(script));
        if (fo == null) {
            return null;
        }
        try {
            EditorCookie editor = DataObject.find(fo).getLookup().lookup(EditorCookie.class);
            JEditorPane[] panes = editor == null ? null : editor.getOpenedPanes();
            return panes == null || panes.length == 0 ? null : panes[0];
        } catch (IOException ex) {
            return null;
        }
    }
}
