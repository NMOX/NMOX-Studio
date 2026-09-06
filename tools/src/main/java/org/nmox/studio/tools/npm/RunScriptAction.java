package org.nmox.studio.tools.npm;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Run Script (v2.33.0, granting the v2.31.0 recorded wish): right-click
 * a line inside package.json's {@code "scripts"} object and run that
 * script — the editor gesture for the thing every full-stack day does
 * ten times. Resolution is the pure {@link NpmScripts} rule (a
 * dependency named like a script never runs); the spawn rides {@link
 * NpmService#runScript}, which carries the v1.103.0 Workspace Trust
 * gate and the project's own package manager (corepack pin, then
 * lockfile). Caret identity follows the v2.30.1 split-editor law:
 * the component must belong to THIS action's file.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.tools.npm.RunScriptAction")
@ActionRegistration(displayName = "#CTL_RunScript", lazy = true)
@ActionReference(path = "Editors/text/x-json/Popup", position = 1870)
@Messages("CTL_RunScript=Run Script")
public final class RunScriptAction implements ActionListener {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-run-script", 1);

    private final DataObject context;

    public RunScriptAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!"package.json".equals(context.getPrimaryFile().getNameExt())) {
            status("Run Script lives in package.json.");
            return;
        }
        JTextComponent comp = EditorRegistry.lastFocusedComponent();
        if (comp == null || !belongsToContext(comp.getDocument())) {
            comp = null;
            for (JTextComponent candidate : EditorRegistry.componentList()) {
                if (belongsToContext(candidate.getDocument())) {
                    comp = candidate;
                    break;
                }
            }
        }
        if (comp == null) {
            status("Place the caret on a script line first.");
            return;
        }
        String text;
        try {
            text = comp.getDocument().getText(0, comp.getDocument().getLength());
        } catch (javax.swing.text.BadLocationException ex) {
            return;
        }
        String script = NpmScripts.scriptAt(text, comp.getCaretPosition());
        if (script == null) {
            status("Place the caret on a line inside \"scripts\" to run it.");
            return;
        }
        File dir = FileUtil.toFile(context.getPrimaryFile().getParent());
        if (dir == null) {
            status("Not a local project.");
            return;
        }
        status("Running \"" + script + "\"…");
        RP.post(() -> {
            NpmService npm = NpmService.getDefault();
            npm.runScript(dir, script, npm.detectPackageManager(dir));
        });
    }

    private boolean belongsToContext(Document doc) {
        if (doc == null) {
            return false;
        }
        Object sd = doc.getProperty(Document.StreamDescriptionProperty);
        return sd == context
                || (sd instanceof DataObject d
                        && d.getPrimaryFile().equals(context.getPrimaryFile()));
    }

    private static void status(String message) {
        if (EventQueue.isDispatchThread()) {
            StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(message));
        } else {
            EventQueue.invokeLater(
                    () -> StatusDisplayer.getDefault().setStatusText(org.nmox.studio.core.util.PlainStatus.text(message)));
        }
    }
}
