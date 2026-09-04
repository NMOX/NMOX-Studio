package org.nmox.studio.tools.npm;

import java.util.function.Supplier;
import org.netbeans.spi.project.ui.support.BuildExecutionSupport;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.openide.filesystems.FileObject;

/**
 * One IDE run as the platform sees it (v2.69.10): registered with
 * {@link BuildExecutionSupport} on spawn so Run ▸ Stop Build/Run can stop
 * it and Repeat can run it again; {@link #finished()} on exit. The
 * process handle arrives after construction (the run is registered as
 * soon as it is spawned), hence the supplier.
 */
final class IdeRunItem implements BuildExecutionSupport.ActionItem {

    private final String command;
    private final FileObject projectDir;
    private final String label;
    private final Supplier<CommandExecutor.Handle> process;
    private final Runnable rerun;
    private volatile boolean running = true;

    IdeRunItem(String command, FileObject projectDir, String label,
            Supplier<CommandExecutor.Handle> process, Runnable rerun) {
        this.command = command;
        this.projectDir = projectDir;
        this.label = label;
        this.process = process;
        this.rerun = rerun;
    }

    /** The exit handler's word: the item is done, Repeat stays available. */
    void finished() {
        running = false;
    }

    @Override
    public String getAction() {
        return command;
    }

    @Override
    public FileObject getProjectDirectory() {
        return projectDir;
    }

    @Override
    public String getDisplayName() {
        return label;
    }

    @Override
    public void repeatExecution() {
        rerun.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void stopRunning() {
        CommandExecutor.Handle h = process.get();
        if (h != null) {
            h.kill();
        }
    }
}
