package org.nmox.studio.editor.typescript;

import javax.swing.text.EditorKit;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.modules.editor.NbEditorKit;

/**
 * The missing editor kit for TypeScript panes (the Angular-top arc,
 * 2026-08-11). JS/TS ride the custom lexer pipeline and — unlike the
 * ~60 CSL languages whose {@code @LanguageRegistration} generates a
 * kit — never registered one, so their panes fell back to a kit that
 * cannot resolve mime-registered actions in its KEYMAP: every chord
 * (⌘/ toggle-comment, ⌥⌘E Emmet) was dead on .ts files while the same
 * actions worked from the popup, which reads the document mime. Proven
 * live by differential before this class existed. A real NbEditorKit
 * whose content type IS the mime makes the keymap load this mime's
 * keybindings and resolve this mime's actions — the platform's normal
 * arrangement, restored.
 */
public class TypeScriptEditorKit extends NbEditorKit {

    @MimeRegistration(mimeType = "text/typescript", service = EditorKit.class)
    public static TypeScriptEditorKit create() {
        return new TypeScriptEditorKit();
    }

    @Override
    public String getContentType() {
        return "text/typescript";
    }
}
