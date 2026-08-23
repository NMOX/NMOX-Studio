package org.nmox.studio.apiclient.ui;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * Copy TS Types on a .json FILE (v2.34.0): the response-pane export
 * generalized — any JSON document in the editor becomes TypeScript
 * interfaces on the clipboard, named after the file. Same pure
 * {@link org.nmox.studio.apiclient.api.JsonTypes} codec, same honest
 * refusal for non-object JSON.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.apiclient.ui.CopyTsTypesAction")
@ActionRegistration(displayName = "#CTL_CopyTsTypes", lazy = true)
@ActionReference(path = "Editors/text/x-json/Popup", position = 1876)
@Messages("CTL_CopyTsTypes=Copy TS Types")
public final class CopyTsTypesAction implements ActionListener {

    private final DataObject context;

    public CopyTsTypesAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String text;
        try {
            text = context.getPrimaryFile().asText();
        } catch (java.io.IOException ex) {
            status("Could not read " + context.getPrimaryFile().getNameExt() + ".");
            return;
        }
        String stem = context.getPrimaryFile().getName().replaceAll("[^A-Za-z0-9_ -]", "");
        String types = org.nmox.studio.apiclient.api.JsonTypes.interfacesFor(
                text, stem.isBlank() ? "Root" : stem);
        if (types == null) {
            status("Not a JSON object — nothing to type.");
            return;
        }
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(types), null);
        status("TypeScript interfaces copied.");
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
