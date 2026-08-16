package org.nmox.studio.editor.grammars;

import javax.swing.text.EditorKit;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.editor.NbEditorKit;

/**
 * The editor kit for {@code .svelte} components (v2.14.0): same cure
 * as {@link VueEditorKit} — {@code text/x-svelte} carried a grammar,
 * typing interceptors, completion, and a toggle-comment action, but
 * no KIT, so keyboard chords could not resolve any of them. See the
 * Angular-top arc's JS/TS finding for the mechanism.
 */
public class SvelteEditorKit extends NbEditorKit {

    @MimeRegistration(mimeType = "text/x-svelte", service = EditorKit.class)
    public static SvelteEditorKit create() {
        return new SvelteEditorKit();
    }

    @Override
    public String getContentType() {
        return "text/x-svelte";
    }
}
