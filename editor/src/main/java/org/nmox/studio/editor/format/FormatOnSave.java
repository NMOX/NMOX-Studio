package org.nmox.studio.editor.format;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.spi.editor.document.OnSaveTask;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbPreferences;

/**
 * Format on save, the way every modern web editor does it: Ctrl+S runs
 * the project's own Prettier over the buffer before the bytes hit disk.
 * Strictly opt-in twice over — the IDE-wide toggle (Options → Editor →
 * Format on Save, default on) and the project's own Prettier config;
 * a project that never chose Prettier never gets rewritten.
 *
 * The replacement is the minimal edit between old and new text (common
 * prefix and suffix stripped), so the caret and the scroll position
 * survive the save instead of jumping to the top of the file.
 */
public final class FormatOnSave implements OnSaveTask {

    private static final Logger LOG = Logger.getLogger(FormatOnSave.class.getName());

    /** Preference key for the IDE-wide toggle; default is on. */
    static final String PREF_ENABLED = "formatOnSave";

    private final Document doc;
    private volatile boolean cancelled;

    FormatOnSave(Document doc) {
        this.doc = doc;
    }

    @Override
    public void performTask() {
        if (cancelled || !isEnabled()) {
            return;
        }
        FileObject fo = NbEditorUtilities.getFileObject(doc);
        File file = fo == null ? null : FileUtil.toFile(fo);
        if (file == null) {
            return; // in-memory or virtual documents save as-is
        }
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (BadLocationException ex) {
            return;
        }
        String formatted = new PrettierFormatter().format(text, file);
        if (formatted == null || cancelled) {
            return;
        }
        try {
            applyMinimalEdit(doc, text, formatted);
        } catch (BadLocationException ex) {
            LOG.log(Level.WARNING, "format-on-save could not apply edit", ex);
        }
    }

    @Override
    public void runLocked(Runnable run) {
        run.run();
    }

    @Override
    public boolean cancel() {
        cancelled = true;
        return true;
    }

    static boolean isEnabled() {
        return NbPreferences.forModule(FormatOnSave.class).getBoolean(PREF_ENABLED, true);
    }

    static void setEnabled(boolean enabled) {
        NbPreferences.forModule(FormatOnSave.class).putBoolean(PREF_ENABLED, enabled);
    }

    /**
     * Replaces only the span that actually changed: the longest common
     * prefix and suffix stay untouched, so document Positions outside
     * the span (carets, folds, annotations) keep their places.
     */
    static void applyMinimalEdit(Document doc, String before, String after)
            throws BadLocationException {
        if (before.equals(after)) {
            return;
        }
        int minLength = Math.min(before.length(), after.length());
        int prefix = 0;
        while (prefix < minLength && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < minLength - prefix
                && before.charAt(before.length() - 1 - suffix)
                == after.charAt(after.length() - 1 - suffix)) {
            suffix++;
        }
        doc.remove(prefix, before.length() - suffix - prefix);
        doc.insertString(prefix, after.substring(prefix, after.length() - suffix), null);
    }

    /**
     * One factory per Prettier-formattable mime. Svelte and Astro ride
     * along because their Prettier plugins are declared in the very
     * config that opts the project in; without the plugin Prettier
     * errors and the save proceeds untouched.
     */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/javascript", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/typescript", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/css", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-scss", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-less", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/html", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-json", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-yaml", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-markdown", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/markdown", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-vue", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-graphql", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-svelte", service = OnSaveTask.Factory.class, position = 400),
        @MimeRegistration(mimeType = "text/x-astro", service = OnSaveTask.Factory.class, position = 400)
    })
    public static final class FactoryImpl implements OnSaveTask.Factory {

        @Override
        public OnSaveTask createTask(Context context) {
            return new FormatOnSave(context.getDocument());
        }
    }
}
