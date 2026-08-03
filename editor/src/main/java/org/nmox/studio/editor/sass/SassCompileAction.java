package org.nmox.studio.editor.sass;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Compile to CSS (v1.230.0, the Senior Web Designer pass): the SCSS
 * editor's right-click gesture that compiles THIS stylesheet to its
 * sibling .css — and, on the gesture, ARMS recompile-on-save for the
 * same file (the v1.212.0 law: a capability arms on the gesture that
 * proves the user wants it). From then on every ⌘S recompiles, the
 * fresh .css lands on disk, and the Browser's save-to-reload
 * (v1.228.0) repaints the served page — the designer's whole
 * scss → css → pixels loop with no terminal.
 *
 * <p>The watch is per-file and per-session; nothing persists, nothing
 * fires for files never compiled by hand. Partials refuse honestly.
 */
@ActionID(category = "Source", id = "org.nmox.studio.editor.sass.SassCompileAction")
@ActionRegistration(displayName = "#CTL_SassCompile", lazy = true)
@org.openide.awt.ActionReferences({
    // text/scss is what css-prep resolves real .scss files to; the
    // x-scss path covers indented .sass (v1.230.0 mime find)
    @ActionReference(path = "Editors/text/scss/Popup", position = 1855),
    @ActionReference(path = "Editors/text/x-scss/Popup", position = 1855)
})
@Messages("CTL_SassCompile=Compile to CSS")
public final class SassCompileAction implements ActionListener {

    /** One lane: compiles queue rather than pile up. */
    private static final RequestProcessor RP = new RequestProcessor("nmox-sass-compile", 1);

    /** Absolute path → armed listener; the session's watch list. */
    private static final Map<String, FileChangeListener> ARMED = new ConcurrentHashMap<>();

    private final DataObject context;

    public SassCompileAction(DataObject context) {
        this.context = context;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        FileObject fo = context.getPrimaryFile();
        File file = FileUtil.toFile(fo);
        if (file == null) {
            status("Not a local file.");
            return;
        }
        // the save (an editor-buffer write to disk) and the compile both
        // ride the RP — actionPerformed runs on the EDT and a slow or
        // network volume must not stall the paint thread (the v1.108.0
        // Load-Patch law; v1.234.0 review). CloneableEditorSupport saves
        // are thread-safe.
        org.openide.cookies.SaveCookie save =
                context.getLookup().lookup(org.openide.cookies.SaveCookie.class);
        RP.post(() -> {
            if (save != null) {
                try {
                    save.save();
                } catch (java.io.IOException ex) {
                    EventQueue.invokeLater(() -> status("Could not save before compiling."));
                    return;
                }
            }
            SassCompiler.Result result = new SassCompiler().compile(file);
            // the directory refresh stats the disk — do it here, not in
            // the EDT report (the review's other half of this finding)
            if (result.outcome() == SassCompiler.Outcome.COMPILED) {
                FileUtil.refreshFor(file.getParentFile());
            }
            EventQueue.invokeLater(() -> report(fo, file, result, true));
        });
    }

    private static void report(FileObject fo, File file, SassCompiler.Result result,
            boolean armAfter) {
        switch (result.outcome()) {
            case COMPILED -> {
                boolean newlyArmed = armAfter && arm(fo, file);
                status("Compiled " + result.output().getName()
                        + (newlyArmed ? " — recompiling on save." : "."));
            }
            case PARTIAL -> status("Partials (_" + stripUnderscore(file.getName())
                    + ") are imports, not entry points — compile the stylesheet that uses it.");
            case NO_SASS -> status("sass not found — install it in the project "
                    + "(npm i -D sass) or globally on PATH.");
            case FAILED -> status("Sass error: " + result.error());
        }
    }

    private static String stripUnderscore(String name) {
        return name.startsWith("_") ? name.substring(1) : name;
    }

    /** Arms the on-save recompile; true when this call newly armed it. */
    private static boolean arm(FileObject fo, File file) {
        String key = file.getAbsolutePath();
        if (ARMED.containsKey(key)) {
            return false;
        }
        FileChangeListener listener = new RecompileOnSave(file);
        ARMED.put(key, listener);
        fo.addFileChangeListener(listener);
        return true;
    }

    private static void status(String message) {
        StatusDisplayer.getDefault().setStatusText(message);
    }

    /** The armed half: every save of the watched file recompiles it. */
    private static final class RecompileOnSave implements FileChangeListener {

        private final File file;

        RecompileOnSave(File file) {
            this.file = file;
        }

        @Override
        public void fileChanged(FileEvent fe) {
            RP.post(() -> {
                SassCompiler.Result result = new SassCompiler().compile(file);
                if (result.outcome() == SassCompiler.Outcome.COMPILED) {
                    FileUtil.refreshFor(file.getParentFile()); // RP, not EDT
                }
                EventQueue.invokeLater(()
                        -> report(fe.getFile(), file, result, false));
            });
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            FileChangeListener self = ARMED.remove(file.getAbsolutePath());
            if (self != null) {
                fe.getFile().removeFileChangeListener(self);
            }
        }

        @Override
        public void fileRenamed(FileRenameEvent fe) {
            fileDeleted(fe);
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
        }

        @Override
        public void fileAttributeChanged(FileAttributeEvent fe) {
        }
    }
}
