package org.nmox.studio.editor.diagnostics;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.netbeans.spi.tasklist.PushTaskScanner;
import org.netbeans.spi.tasklist.Task;
import org.netbeans.spi.tasklist.TaskScanningScope;
import org.nmox.studio.rack.engine.DiagnosticsBus;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * The Action Items half of the rack-diagnostics bridge (tech-debt #32):
 * problems published by PURITY (eslint) and TYPEGUARD (tsc/phpstan) become
 * rows in the standard Task List window — listable, severity-grouped, and
 * click-to-navigate — including for files no editor has open, which the
 * squiggle half ({@link RackSquiggler}) can never show.
 *
 * <p>Registered in layer.xml under {@code TaskList/Scanners}, so the Task
 * List framework instantiates it lazily on first scan — this class costs
 * boot nothing. The bus replays current batches to late subscribers, so a
 * scanner born after a lint run still knows everything.
 *
 * <p>Like RackSquiggler, the bus subscription is registered once for the
 * session and never removed: the framework creates exactly one scanner and
 * reuses it across scope changes ({@code setScope(null, null)} deactivates,
 * it does not destroy), so the singleton-lifetime listener is the symmetric
 * shape here. Publishes arrive on device worker threads and stay there —
 * no EDT work anywhere on this path.
 */
public final class RackFindingsTaskScanner extends PushTaskScanner {

    private final RackFindings findings = new RackFindings();
    private volatile TaskScanningScope scope;
    private volatile Callback callback;

    private RackFindingsTaskScanner() {
        super("Rack tool findings",
                "Problems reported by rack quality tools (eslint, tsc, phpstan)",
                null);
        DiagnosticsBus.addListener((tool, problems)
                -> push(findings.publish(tool, problems)));
    }

    /** Layer factory ({@code TaskList/Scanners/RackFindingsScanner.instance}). */
    public static RackFindingsTaskScanner create() {
        return new RackFindingsTaskScanner();
    }

    @Override
    public void setScope(TaskScanningScope newScope, Callback newCallback) {
        this.scope = newScope;
        this.callback = newCallback;
        if (newScope == null || newCallback == null) {
            return; // framework says stop reporting
        }
        newCallback.started();
        push(findings.snapshot());
        newCallback.finished();
    }

    /** Delivers per-file rows (empty list = clear) to the active scope. */
    private void push(Map<File, List<RackFindings.Finding>> delta) {
        Callback cb = this.callback;
        TaskScanningScope sc = this.scope;
        if (cb == null || sc == null) {
            return;
        }
        for (Map.Entry<File, List<RackFindings.Finding>> e : delta.entrySet()) {
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(e.getKey()));
            if (fo == null || !sc.isInScope(fo)) {
                continue; // vanished file, or outside what the window shows
            }
            List<Task> tasks = new ArrayList<>();
            for (RackFindings.Finding f : e.getValue()) {
                tasks.add(Task.create(fo, f.group(), f.text(), f.line()));
            }
            cb.setTasks(fo, tasks);
        }
    }
}
