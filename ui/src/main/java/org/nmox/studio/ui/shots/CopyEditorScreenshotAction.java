package org.nmox.studio.ui.shots;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Tools ▸ Copy Editor Screenshot (v2.87.0): the editor area's selected
 * tab painted at 2x straight onto the clipboard — the shot the developer
 * evangelist pastes into a chat, an issue or a slide a dozen times a day
 * without ever wanting a file. Same tab rule as
 * {@link SaveEditorScreenshotAction}: the activated window when it is an
 * editor tab, else the editor mode's selection. Nothing open in the
 * editor area is a spoken refusal; the clipboard is left as it was.
 */
@ActionID(category = "Tools", id = "org.nmox.studio.ui.shots.CopyEditorScreenshotAction")
@ActionRegistration(displayName = "#CTL_CopyEditorScreenshot", lazy = true)
@ActionReference(path = "Menu/Tools", position = 102)
@Messages("CTL_CopyEditorScreenshot=Copy Editor Screenshot")
public final class CopyEditorScreenshotAction implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
        WindowManager wm = WindowManager.getDefault();
        TopComponent activated = TopComponent.getRegistry().getActivated();
        Mode editorMode = wm.findMode("editor");
        TopComponent tab = SaveEditorScreenshotAction.selectedEditor(activated,
                activated != null && wm.isOpenedEditorTopComponent(activated),
                editorMode == null ? null : editorMode.getSelectedTopComponent());
        if (tab == null) {
            StatusDisplayer.getDefault().setStatusText("Not copied — nothing is open in the editor area");
            return;
        }
        ShotSaver.copy(tab, "editor screenshot");
    }
}
