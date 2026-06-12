package org.nmox.studio.editor.diagnostics;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.OnStart;

/**
 * The bridge from rack pipelines to the code: problems published by
 * PURITY (eslint) and TYPEGUARD (tsc) become squiggles on the exact
 * lines, in whatever files they name. Run the lint lane, see the red
 * underline appear under the offending expression.
 */
@OnStart
public class RackSquiggler implements Runnable {

    /** Files we have annotated, so an all-clear run erases old marks. */
    private static final Set<File> TOUCHED = new HashSet<>();

    @Override
    public void run() {
        DiagnosticsBus.addListener((tool, problems) -> {
            String layer = "nmox-rack-" + tool;
            Set<File> files = new HashSet<>();
            for (DiagnosticsBus.Problem p : problems) {
                files.add(p.file());
            }
            synchronized (TOUCHED) {
                Set<File> stale = new HashSet<>(TOUCHED);
                stale.removeAll(files);
                for (File f : stale) {
                    setErrors(f, layer, "", List.of());
                }
                TOUCHED.clear();
                TOUCHED.addAll(files);
            }
            for (File f : files) {
                List<DiagnosticsBus.Problem> fileProblems = new ArrayList<>();
                for (DiagnosticsBus.Problem p : problems) {
                    if (p.file().equals(f)) {
                        fileProblems.add(p);
                    }
                }
                setErrors(f, layer, "[" + tool + "] ", fileProblems);
            }
        });
    }

    /** Squiggles attach to open documents; files opened later get them on open. */
    private static void setErrors(File file, String layer, String prefix,
            List<DiagnosticsBus.Problem> problems) {
        javax.swing.text.Document doc = openDocumentFor(file);
        if (doc != null) {
            HintsController.setErrors(doc, layer, describe(doc, prefix, problems));
        }
    }

    private static List<ErrorDescription> describe(javax.swing.text.Document doc,
            String prefix, List<DiagnosticsBus.Problem> problems) {
        List<ErrorDescription> descriptions = new ArrayList<>();
        for (DiagnosticsBus.Problem p : problems) {
            descriptions.add(ErrorDescriptionFactory.createErrorDescription(
                    p.error() ? Severity.ERROR : Severity.WARNING,
                    prefix + p.message(), doc, Math.max(1, p.line())));
        }
        return descriptions;
    }

    private static javax.swing.text.Document openDocumentFor(File file) {
        for (javax.swing.text.JTextComponent comp
                : org.netbeans.api.editor.EditorRegistry.componentList()) {
            javax.swing.text.Document doc = comp.getDocument();
            Object sdp = doc.getProperty(javax.swing.text.Document.StreamDescriptionProperty);
            FileObject fo = sdp instanceof org.openide.loaders.DataObject dataObject
                    ? dataObject.getPrimaryFile()
                    : sdp instanceof FileObject f ? f : null;
            if (fo != null && file.equals(FileUtil.toFile(fo))) {
                return doc;
            }
        }
        return null;
    }

    static {
        // newly opened files pick up whatever the bus already knows
        org.netbeans.api.editor.EditorRegistry.addPropertyChangeListener(evt -> {
            javax.swing.text.JTextComponent comp =
                    org.netbeans.api.editor.EditorRegistry.focusedComponent();
            if (comp == null) {
                return;
            }
            Object sdp = comp.getDocument()
                    .getProperty(javax.swing.text.Document.StreamDescriptionProperty);
            FileObject fo = sdp instanceof org.openide.loaders.DataObject dataObject
                    ? dataObject.getPrimaryFile()
                    : sdp instanceof FileObject f ? f : null;
            File file = fo == null ? null : FileUtil.toFile(fo);
            if (file == null) {
                return;
            }
            List<DiagnosticsBus.Problem> problems = DiagnosticsBus.problemsFor(file);
            HintsController.setErrors(comp.getDocument(), "nmox-rack-onopen",
                    describe(comp.getDocument(), "", problems));
        });
    }
}
