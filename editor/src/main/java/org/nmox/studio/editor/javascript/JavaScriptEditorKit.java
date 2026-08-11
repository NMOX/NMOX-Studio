package org.nmox.studio.editor.javascript;

import javax.swing.text.EditorKit;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.editor.NbEditorKit;

/**
 * The missing editor kit for JavaScript panes — see {@link
 * org.nmox.studio.editor.typescript.TypeScriptEditorKit} for the full
 * story: without a registered kit the pane's keymap cannot resolve
 * mime-registered actions, so chords like ⌘/ and ⌥⌘E were dead on the
 * exact files a web IDE opens most.
 */
public class JavaScriptEditorKit extends NbEditorKit {

    @MimeRegistration(mimeType = "text/javascript", service = EditorKit.class)
    public static JavaScriptEditorKit create() {
        return new JavaScriptEditorKit();
    }

    @Override
    public String getContentType() {
        return "text/javascript";
    }
}
