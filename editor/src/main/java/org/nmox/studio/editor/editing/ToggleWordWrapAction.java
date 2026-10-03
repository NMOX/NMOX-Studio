package org.nmox.studio.editor.editing;

import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.nmox.studio.core.util.PlainStatus;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;

/**
 * View ▸ Word Wrap (⌥Z, VS Code's chord): switches soft line wrap for the
 * language of the editor you were last typing in. The scope is the
 * platform's and is said on the status line: every editor of that
 * language follows, and the choice is saved with the editor's other
 * settings ({@link WordWrap} has the mechanism).
 *
 * <p>The checkbox reads the setting each time the menu shows, from the
 * document the editor is showing, so it reports what that editor does
 * rather than what this action last wrote. The chord is an editor
 * keybinding on {@link ToggleWordWrapKeyAction}, which runs the same
 * {@link #press}.
 */
@ActionID(category = "View", id = "org.nmox.studio.editor.editing.ToggleWordWrapAction")
@ActionRegistration(displayName = "#CTL_ToggleWordWrap", lazy = false)
@ActionReference(path = "Menu/View", position = 1155)
public final class ToggleWordWrapAction extends AbstractAction implements Presenter.Menu {

    /** The preferences of a mime type (the empty one is all languages); a seam for tests. */
    static Function<String, Preferences> prefs =
            mime -> MimeLookup.getLookup(MimePath.parse(mime)).lookup(Preferences.class);

    /** The open editors, to be told the setting changed; a seam for tests. */
    static Supplier<List<? extends JTextComponent>> editors = EditorRegistry::componentList;

    /** Where the outcome is said; a seam for tests. */
    static Consumer<String> status = text -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(text));

    public ToggleWordWrapAction() {
        super(NbBundle.getMessage(ToggleWordWrapAction.class, "CTL_ToggleWordWrap"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        press(EditorRegistry.lastFocusedComponent());
    }

    /** One press, for the editor {@code target} (null when there is none). */
    static void press(JTextComponent target) {
        Document doc = target == null ? null : target.getDocument();
        String mime = doc == null ? null : ToggleBlockCommentAction.mimeOf(doc);
        if (doc == null || mime == null || doc.getProperty(WordWrap.KEY) == null) {
            // no editor, or not a code editor's document: nothing reads the setting here
            status.accept(NbBundle.getMessage(ToggleWordWrapAction.class, "WordWrap_noEditor"));
            return;
        }
        WordWrap.Result result = WordWrap.toggle(mime, () -> doc.getProperty(WordWrap.KEY), prefs,
                () -> refreshEditors(mime));
        String key = !result.took() ? "WordWrap_unchanged" : result.on() ? "WordWrap_on" : "WordWrap_off";
        status.accept(NbBundle.getMessage(ToggleWordWrapAction.class, key, languageName(mime)));
    }

    /**
     * Tells every open editor to read the setting again, the way the
     * Options dialog does after Apply: the view listens to its document's
     * {@code text-line-wrap} property, not to the preference behind it.
     */
    public static void refreshEditors() {
        refreshEditors(null);
    }

    /**
     * Tells the open editors that read {@code mime}'s setting to read it
     * again, and no others: a poke makes an editor lay its text out again,
     * on the event thread, so a toggle for TypeScript leaves the Markdown
     * and CSS editors alone. Null tells every editor (a change to the
     * all-languages setting).
     */
    static void refreshEditors(String mime) {
        for (JTextComponent editor : editors.get()) {
            Document doc = editor.getDocument();
            if (doc != null && doc.getProperty(WordWrap.KEY) != null
                    && (mime == null || readsFrom(ToggleBlockCommentAction.mimeOf(doc), mime))) {
                doc.putProperty(WordWrap.KEY, "");
            }
        }
    }

    /**
     * Whether a document of {@code docMime} reads its wrap from
     * {@code mime}'s preferences: its own, or one its mime path inherits
     * from ({@code text/x-ant+xml} reads {@code text/xml}'s). A document
     * whose language cannot be told is told anyway: a needless relayout
     * costs less than an editor that shows the old setting.
     */
    static boolean readsFrom(String docMime, String mime) {
        if (docMime == null || docMime.equals(mime)) {
            return true;
        }
        try {
            for (MimePath included : MimePath.parse(docMime).getIncludedPaths()) {
                if (included.getPath().equals(mime)) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException notAMimePath) {
            return true;
        }
    }

    /** Whether the editor last typed in wraps now. */
    static boolean wrapsNow(JTextComponent target) {
        Document doc = target == null ? null : target.getDocument();
        return doc != null && WordWrap.isOn(doc.getProperty(WordWrap.KEY));
    }

    @Override
    public JMenuItem getMenuPresenter() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(this) {
            @Override
            public void addNotify() {
                super.addNotify();
                setSelected(wrapsNow(EditorRegistry.lastFocusedComponent()));
            }
        };
        item.setToolTipText(NbBundle.getMessage(ToggleWordWrapAction.class, "WordWrap_tip"));
        item.setSelected(wrapsNow(EditorRegistry.lastFocusedComponent()));
        return item;
    }

    /**
     * The language as the status line names it: the id VS Code gives it
     * ({@code typescript}), which is the word a switcher's settings use,
     * and the MIME type only for a language with no such id.
     */
    static String languageName(String mime) {
        String id = org.nmox.studio.editor.lsp.LspLanguageIds.forMime(mime);
        return id == null || id.isBlank() ? mime : id;
    }
}
