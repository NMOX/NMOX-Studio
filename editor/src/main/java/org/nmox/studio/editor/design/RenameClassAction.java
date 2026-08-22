package org.nmox.studio.editor.design;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

import org.netbeans.api.editor.EditorRegistry;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Rename Class… (v2.29.0): the capstone of the class-intelligence arc —
 * both navigation directions exist (usage → rule, rule → usage), so the
 * product knows every place a class lives, and this action turns that
 * knowledge into one safe cross-file edit. Whole-token spans only, from
 * the same pure core the jumps ride.
 *
 * <p>The refusal ladder, every rung with its reason on the status line:
 * caret not on a class; an invalid new name; a rename onto a class that
 * already exists (silently merging rules — the v1.284.0 law); a file
 * census that hit the walk cap (a PARTIAL rename is corruption, so an
 * over-large project refuses whole); and any affected file open with
 * UNSAVED changes (the buffer would overwrite the rename on save).
 * Writes go through {@link FileObject} streams so open, unmodified
 * editors reload the renamed text themselves.
 */
@ActionID(category = "Source", id = "org.nmox.studio.editor.design.RenameClassAction")
@ActionRegistration(displayName = "#CTL_RenameClass", lazy = true)
@ActionReferences({
    @ActionReference(path = "Editors/text/css/Popup", position = 1860),
    @ActionReference(path = "Editors/text/scss/Popup", position = 1860),
    @ActionReference(path = "Editors/text/less/Popup", position = 1860),
    @ActionReference(path = "Editors/text/x-scss/Popup", position = 1860),
    @ActionReference(path = "Editors/text/x-less/Popup", position = 1860),
    @ActionReference(path = "Editors/text/x-sass/Popup", position = 1860),
    @ActionReference(path = "Editors/text/html/Popup", position = 1860),
    @ActionReference(path = "Editors/text/x-vue/Popup", position = 1860),
    @ActionReference(path = "Editors/text/x-svelte/Popup", position = 1860),
    @ActionReference(path = "Editors/text/x-ng-template/Popup", position = 1860)
})
@Messages("CTL_RenameClass=Rename Class…")
public final class RenameClassAction implements ActionListener {

    private static final RequestProcessor RP =
            new RequestProcessor("nmox-css-class-rename", 1);

    private final DataObject context;

    public RenameClassAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // the caret must come from THIS action's file (v2.30.1 review):
        // lastFocusedComponent can belong to the OTHER half of a split
        // editor while the popup's context names this one — a rename
        // computed on the wrong file's token. Identity-checked, with
        // the component list as the fallback for unfocused popups.
        JTextComponent comp = EditorRegistry.lastFocusedComponent();
        if (comp == null || !documentBelongsTo(comp.getDocument(), context)) {
            comp = null;
            for (JTextComponent candidate : EditorRegistry.componentList()) {
                if (documentBelongsTo(candidate.getDocument(), context)) {
                    comp = candidate;
                    break;
                }
            }
        }
        if (comp == null) {
            status("Place the caret on a class name first.");
            return;
        }
        Document doc = comp.getDocument();
        String text;
        try {
            text = doc.getText(0, doc.getLength());
        } catch (javax.swing.text.BadLocationException ex) {
            return;
        }
        int caret = comp.getCaretPosition();
        boolean markup = HtmlStyleRegions.isMarkup(doc.getProperty("mimeType"));
        int[] span = markup
                ? spanInMarkup(text, caret)
                : CssClasses.selectorSpanAt(text, caret);
        if (span == null) {
            status(markup
                    ? "Place the caret on a class name inside class=\"…\" or a <style> selector."
                    : "Place the caret on a .class selector.");
            return;
        }
        String oldName = text.substring(span[0], span[1]);
        NotifyDescriptor.InputLine input = new NotifyDescriptor.InputLine(
                "New name for ." + oldName + ":", "Rename Class");
        input.setInputText(oldName);
        if (DialogDisplayer.getDefault().notify(input)
                != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String newName = input.getInputText().strip();
        if (newName.equals(oldName)) {
            return;
        }
        if (!CssClasses.validClassName(newName)) {
            status("\"" + newName + "\" is not a valid class name.");
            return;
        }
        // modified-editor census on the EDT, where the registry lives
        Set<String> dirty = new HashSet<>();
        for (DataObject d : DataObject.getRegistry().getModifiedSet()) {
            File f = FileUtil.toFile(d.getPrimaryFile());
            if (f != null) {
                dirty.add(f.getAbsolutePath());
            }
        }
        File root = projectDirOf();
        RP.post(() -> rename(root, oldName, newName, dirty));
    }


    /**
     * True when {@code doc} is the open document OF {@code context}'s
     * file — the identity rule that keeps a split editor's OTHER half
     * from supplying the caret (pure given its inputs; the document
     * carries its DataObject as the stream description).
     */
    static boolean documentBelongsTo(Document doc, DataObject context) {
        if (doc == null || context == null) {
            return false;
        }
        Object sd = doc.getProperty(Document.StreamDescriptionProperty);
        return sd == context
                || (sd instanceof DataObject d
                        && d.getPrimaryFile().equals(context.getPrimaryFile()));
    }

    /** Markup caret: a class-attribute token or a style-region selector. */
    private static int[] spanInMarkup(String text, int caret) {
        int[] attr = CssClasses.attrNameSpanAt(text, caret);
        if (attr != null) {
            return attr;
        }
        for (HtmlStyleRegions.Region r : HtmlStyleRegions.find(text)) {
            if (caret >= r.start() && caret <= r.end()) {
                int[] s = CssClasses.selectorSpanAt(
                        text.substring(r.start(), r.end()), caret - r.start());
                if (s != null) {
                    return new int[] {s[0] + r.start(), s[1] + r.start()};
                }
            }
        }
        return null;
    }

    /** The survey → refusal ladder → apply, off the EDT. */
    private static void rename(File root, String oldName, String newName,
            Set<String> dirty) {
        CssClasses.RenameSurvey survey =
                CssClasses.surveyRename(root, oldName, newName);
        if (!survey.censusComplete()) {
            status("Rename refused: the project has more stylesheets than the "
                    + "bounded census reads — a partial rename would corrupt it.");
            return;
        }
        if (survey.collision()) {
            status("Rename refused: ." + newName + " already exists — renaming "
                    + "onto it would merge the two classes' rules.");
            return;
        }
        if (survey.spanCount() == 0) {
            status("." + oldName + " appears nowhere in this project.");
            return;
        }
        List<String> dirtyHits = new ArrayList<>();
        for (File f : survey.files()) {
            if (dirty.contains(f.getAbsolutePath())) {
                dirtyHits.add(f.getName());
            }
        }
        if (!dirtyHits.isEmpty()) {
            status("Rename refused: unsaved changes in "
                    + String.join(", ", dirtyHits) + " — save first.");
            return;
        }
        int files = 0;
        for (File f : survey.files()) {
            try {
                String before = Files.readString(f.toPath());
                String after = CssClasses.renameInText(before,
                        CssClasses.isMarkupFile(f.getName()), oldName, newName);
                if (!after.equals(before)) {
                    write(f, after);
                    files++;
                }
            } catch (IOException ex) {
                status("Rename stopped at " + f.getName() + ": " + ex.getMessage());
                return;
            }
        }
        int spans = survey.spanCount();
        int fileCount = files;
        status("Renamed ." + oldName + " → ." + newName + " — "
                + spans + (spans == 1 ? " span in " : " spans in ")
                + fileCount + (fileCount == 1 ? " file" : " files"));
    }

    /** FileObject-stream write so open, unmodified editors reload. */
    private static void write(File f, String content) throws IOException {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(f));
        if (fo == null) {
            throw new IOException("not in the filesystem view");
        }
        FileLock lock = fo.lock();
        try (OutputStream out = fo.getOutputStream(lock)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            lock.releaseLock();
        }
    }

    private File projectDirOf() {
        File f = FileUtil.toFile(context.getPrimaryFile());
        if (f == null) {
            return null;
        }
        File dir = f.getParentFile();
        File cursor = dir;
        for (int up = 0; cursor != null && up < 6; up++, cursor = cursor.getParentFile()) {
            if (new File(cursor, "package.json").isFile()
                    || new File(cursor, "angular.json").isFile()
                    || new File(cursor, ".git").exists()) {
                return cursor;
            }
        }
        return dir;
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
