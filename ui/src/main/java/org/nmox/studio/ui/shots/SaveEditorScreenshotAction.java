package org.nmox.studio.ui.shots;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Tools ▸ Save Editor Screenshot… (v2.87.0, the developer evangelist's
 * second shot): a slide wants the CODE, not the whole IDE — so this paints
 * only the editor area's selected tab at 2x (editor toolbar, gutter, the
 * text, its sidebars) and names the file after the document it shows,
 * {@code App.jsx-2026-09-06-081530.png}. Which tab: the activated window
 * when it is an editor tab, else the editor mode's selection — the tab you
 * are looking at even when focus sits in the Navigator or a tool window
 * (the v2.83.0 {@code editor_state} rule). Nothing open in the editor
 * area is a spoken refusal, not a blank image.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.ui.shots.SaveEditorScreenshotAction")
@ActionRegistration(displayName = "#CTL_SaveEditorScreenshot", lazy = true)
@ActionReference(path = "Menu/Tools", position = 101)
@Messages("CTL_SaveEditorScreenshot=Save Editor Screenshot…")
public final class SaveEditorScreenshotAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        WindowManager wm = WindowManager.getDefault();
        TopComponent activated = TopComponent.getRegistry().getActivated();
        Mode editorMode = wm.findMode("editor");
        TopComponent tab = selectedEditor(activated,
                activated != null && wm.isOpenedEditorTopComponent(activated),
                editorMode == null ? null : editorMode.getSelectedTopComponent());
        if (tab == null) {
            StatusDisplayer.getDefault().setStatusText("Not saved — nothing is open in the editor area");
            return;
        }
        ShotSaver.save(tab, "Save Editor Screenshot",
                Screenshot.editorFileName(documentName(tab), LocalDateTime.now()), "editor screenshot");
    }

    /** The tab to paint: the activated window when it is an editor tab, else the editor area's selection. */
    static TopComponent selectedEditor(TopComponent activated, boolean activatedIsEditor,
            TopComponent selectedInEditorMode) {
        if (activated != null && activatedIsEditor) {
            return activated;
        }
        return selectedInEditorMode;
    }

    /** The document's file name when the tab carries one, else the tab's own name (a studio tab). */
    static String documentName(TopComponent tab) {
        DataObject dob = tab.getLookup().lookup(DataObject.class);
        if (dob != null) {
            return dob.getPrimaryFile().getNameExt();
        }
        String name = tab.getName();
        return name == null ? "" : name;
    }
}
