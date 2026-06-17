package org.nmox.studio.tools.npm;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.spi.project.ActionProvider;
import org.nmox.studio.rack.devices.ProjectInspector;
import org.nmox.studio.rack.devices.ProjectInspector.ProjectKind;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.nmox.studio.rack.service.RackService;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Makes the platform's native Run / Build / Test / Clean actions
 * (toolbar, F6/F11, project menu) drive a web project the way its
 * toolchain expects. Each run aims the rack at the project, streams to
 * the same output bus the rack devices use, and shows in the status-bar
 * progress widget with a Cancel that actually kills the process — so the
 * IDE-native gestures and the rack are two faces of one mechanism, not
 * two parallel worlds.
 */
final class WebProjectActionProvider implements ActionProvider {

    private static final String[] SUPPORTED = {
        COMMAND_RUN, COMMAND_BUILD, COMMAND_TEST, COMMAND_CLEAN
    };

    private final WebProject project;

    WebProjectActionProvider(WebProject project) {
        this.project = project;
    }

    @Override
    public String[] getSupportedActions() {
        return SUPPORTED.clone();
    }

    @Override
    public boolean isActionEnabled(String command, Lookup context) {
        return resolve(command) != null;
    }

    @Override
    public void invokeAction(String command, Lookup context) {
        File dir = FileUtil.toFile(project.getProjectDirectory());
        List<String> cmd = resolve(command);
        if (dir == null || cmd == null) {
            return;
        }

        // the action and the rack are one mechanism: aim the rack so the
        // monitor, explorer and recent list all follow the same project.
        try {
            RackService.getDefault().openProject(dir);
        } catch (RuntimeException | LinkageError ignore) {
            // rack unavailable; the command still runs and shows output
        }

        String label = labelFor(command) + " — " + project.getName();
        AtomicReference<CommandExecutor.Handle> proc = new AtomicReference<>();
        ProgressHandle ph = ProgressHandle.createHandle(label, () -> {
            CommandExecutor.Handle h = proc.get();
            if (h != null) {
                h.kill();
            }
            return true;
        });
        ph.start();

        CommandExecutor.Handle handle = CommandExecutor.run(
                label, dir, Map.of(), cmd, line -> {
                }, exit -> ph.finish());
        proc.set(handle);
        CommandExecutor.showOutput(label);
    }

    private List<String> resolve(String command) {
        File dir = FileUtil.toFile(project.getProjectDirectory());
        if (dir == null) {
            return null;
        }
        ProjectKind kind = ProjectInspector.detectKind(dir);
        return WebProjectCommands.commandFor(dir, kind, command);
    }

    private static String labelFor(String command) {
        switch (command) {
            case COMMAND_RUN:
                return "Run";
            case COMMAND_BUILD:
                return "Build";
            case COMMAND_TEST:
                return "Test";
            case COMMAND_CLEAN:
                return "Clean";
            default:
                return command;
        }
    }
}
