package org.nmox.studio.apiclient.ui;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorRegistry;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * Test in API Studio (v2.34.0): right-click an Express/Fastify/Koa
 * route registration and API Studio opens with a ready draft — the
 * verb and path lifted from the line ({@link
 * org.nmox.studio.apiclient.api.RouteLine}, the v1.292.0 rule), the
 * url spelled {@code {{base_url}}<path>} so the active environment
 * aims it. The v2.31.0 fetch→route jump walks client to server; this
 * walks server to the request that exercises it. Caret identity per
 * the v2.30.1 split-editor law.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.apiclient.ui.TestInApiStudioAction")
@ActionRegistration(displayName = "#CTL_TestInApiStudio", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/text/javascript/Popup", position = 1875),
    @ActionReference(path = "Editors/text/typescript/Popup", position = 1875)
})
@Messages("CTL_TestInApiStudio=Test in API Studio")
public final class TestInApiStudioAction implements ActionListener {

    private final DataObject context;

    public TestInApiStudioAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
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
            status("Place the caret on a route line first.");
            return;
        }
        String line;
        try {
            Document doc = comp.getDocument();
            String text = doc.getText(0, doc.getLength());
            int caret = comp.getCaretPosition();
            int start = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
            int end = text.indexOf('\n', caret);
            line = text.substring(start, end < 0 ? text.length() : end);
        } catch (javax.swing.text.BadLocationException ex) {
            return;
        }
        String[] route = org.nmox.studio.apiclient.api.RouteLine.parse(line);
        if (route == null) {
            status("Place the caret on a route line — app.get('/path', …) and friends.");
            return;
        }
        ApiClientTopComponent.openWithDraft(route[0], route[1]);
        status(route[0] + " " + route[1] + " drafted in API Studio.");
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
            StatusDisplayer.getDefault().setStatusText(message);
        } else {
            EventQueue.invokeLater(
                    () -> StatusDisplayer.getDefault().setStatusText(message));
        }
    }
}
