package org.nmox.studio.editor.format;

import java.awt.EventQueue;
import java.io.File;
import java.util.function.Consumer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.modules.editor.indent.spi.Context;
import org.netbeans.modules.editor.indent.spi.ExtraLock;
import org.netbeans.modules.editor.indent.spi.ReformatTask;
import org.nmox.studio.core.util.EditedFile;
import org.nmox.studio.core.util.PlainStatus;
import org.nmox.studio.editor.format.PrettierFormatter.OnDemand;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * Format, for the languages the platform has no formatter for (3.7.0).
 *
 * <p>Source ▸ Format — and VS Code's chord for it, and <i>Format
 * Document</i> in Quick Search — did NOTHING in a JavaScript or
 * TypeScript file: no formatter is registered for those languages, the
 * platform's action found nothing to run, and said nothing. Walked in
 * the assembled app (3.6.0): a line indented six spaces stayed indented
 * six spaces, under the chord and under the menu row alike, while an
 * HTML file beside it was formatted. In a web IDE that is the gesture a
 * person makes most.
 *
 * <p>The formatter those languages have is the project's own: Prettier.
 * So Format on a whole document of one of these languages, in a project
 * that configures Prettier, runs it — the same engine, the same
 * trust-gated binary and the same caret-preserving edit as <i>Format
 * with Prettier</i> ({@link FormatWithPrettierAction}) and format on
 * save. A project that does not configure Prettier is told so, with the
 * door that formats with Prettier's defaults named, instead of being
 * met with silence: a refusal speaks.
 *
 * <p><b>Whole documents only.</b> The platform calls a language's
 * reformat for more than the Format gesture: a code template that was
 * just inserted is reformatted over its own lines, and so is a paste
 * under some settings. Those are regions, and Prettier formats files;
 * so a region does nothing here, exactly as before this class. Format
 * with nothing selected is the whole document, and that is the gesture
 * this answers.
 *
 * <p><b>Never on the event thread.</b> The platform runs a reformat
 * with the document locked, on the thread that asked — the event thread
 * for a key press. This task takes the text there and nothing else:
 * whether the project opted in is a walk up the disk and Prettier is a
 * process, so both ride a lane, and the result is applied back on the
 * event thread only if the document still holds the text that was
 * formatted (typing during the run refuses quietly and says so).
 */
public final class PrettierReformat implements ReformatTask {

    /** One lane: a second Format queues behind the first rather than racing it. */
    private static final RequestProcessor RP = new RequestProcessor("nmox-prettier-format", 1, true);

    /** Where the outcome is said (a seam for tests). */
    static volatile Consumer<String> statusSink = PrettierReformat::status;

    /** Runs a job off the caller's thread (a seam: a test runs it inline). */
    static volatile Consumer<Runnable> lane = RP::post;

    /** Applies a result on the event thread (a seam: a test runs it inline). */
    static volatile Consumer<Runnable> onEventThread = EventQueue::invokeLater;

    /** Makes the formatter (a seam: a test supplies one whose process is a function). */
    static volatile java.util.function.Supplier<PrettierFormatter> formatter = PrettierFormatter::new;

    private final Context context;

    PrettierReformat(Context context) {
        this.context = context;
    }

    @Override
    public void reformat() throws BadLocationException {
        Document doc = context.document();
        if (!wholeDocument(context.startOffset(), context.endOffset(), doc.getLength())) {
            return;
        }
        FileObject fo = EditedFile.of(doc);
        File file = fo == null ? null : FileUtil.toFile(fo);
        if (file == null || file.getParentFile() == null) {
            return; // an in-memory document: no project to ask and no path for Prettier
        }
        String snapshot = doc.getText(0, doc.getLength());
        lane.accept(() -> run(doc, snapshot, file));
    }

    /** Whether a reformat of {@code start..end} is Format on the whole of a document {@code length} long. */
    static boolean wholeDocument(int start, int end, int length) {
        return start <= 0 && end >= length;
    }

    /** The lane's half: ask the project, run Prettier, hand the outcome to the event thread. */
    @Messages({
        "# {0} - the name of the Format with Prettier action, as the editor's right-click menu shows it",
        "PrettierReformat_notConfigured=Nothing was formatted: this project does not configure Prettier, and "
                + "NMOX Studio has no other formatter for this language. {0}, on the editor’s right-click menu, "
                + "formats the file with Prettier’s defaults."
    })
    static void run(Document doc, String snapshot, File file) {
        if (!PrettierFormatter.projectOptedIn(file.getParentFile())) {
            statusSink.accept(Bundle.PrettierReformat_notConfigured(Bundle.CTL_FormatWithPrettier()));
            return;
        }
        OnDemand result = formatter.get().formatOnDemand(snapshot, file);
        onEventThread.accept(() -> FormatWithPrettierAction.report(doc, snapshot, result, statusSink));
    }

    @Override
    public ExtraLock reformatLock() {
        return null;
    }

    private static void status(String message) {
        if (EventQueue.isDispatchThread()) {
            StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message));
        } else {
            EventQueue.invokeLater(() -> StatusDisplayer.getDefault().setStatusText(PlainStatus.text(message)));
        }
    }

    /**
     * The languages Format had nothing for. HTML, CSS, JSON, YAML and the
     * stylesheet dialects keep the platform's own formatters: registering
     * here too would run both.
     */
    @MimeRegistrations({
        @MimeRegistration(mimeType = "text/javascript", service = ReformatTask.Factory.class),
        @MimeRegistration(mimeType = "text/typescript", service = ReformatTask.Factory.class),
        @MimeRegistration(mimeType = "text/x-vue", service = ReformatTask.Factory.class),
        @MimeRegistration(mimeType = "text/x-svelte", service = ReformatTask.Factory.class),
        @MimeRegistration(mimeType = "text/x-astro", service = ReformatTask.Factory.class),
        @MimeRegistration(mimeType = "text/x-graphql", service = ReformatTask.Factory.class)
    })
    public static final class Factory implements ReformatTask.Factory {
        @Override
        public ReformatTask createTask(Context context) {
            return new PrettierReformat(context);
        }
    }
}
