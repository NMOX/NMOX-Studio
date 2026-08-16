package org.nmox.studio.editor.grammars;

import javax.swing.text.EditorKit;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.editor.NbEditorKit;

/**
 * The editor kit for {@code .vue} single-file components (v2.14.0 —
 * the JS/TS lesson from the Angular-top arc, found again one mime
 * over): {@code text/x-vue} was a grammar-plus-resolver mime with NO
 * registered kit, so the pane fell back to a kit whose action map
 * knows none of our mime-registered actions — ⌘/ toggle comment,
 * ⌥⌘E Emmet, every chord DEAD on the exact file a Vue developer
 * lives in, while the same actions worked from the right-click popup
 * (the popup reads the document mime; the keymap resolves against
 * the KIT). An {@link NbEditorKit} whose {@code getContentType()}
 * names the mime is the whole cure.
 */
public class VueEditorKit extends NbEditorKit {

    @MimeRegistration(mimeType = "text/x-vue", service = EditorKit.class)
    public static VueEditorKit create() {
        return new VueEditorKit();
    }

    @Override
    public String getContentType() {
        return "text/x-vue";
    }
}
