package org.nmox.studio.tools.vscode;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import org.nmox.studio.tools.npm.search.NpmScriptSearchProvider;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

/**
 * Run ▸ Run Task… — the tasks of the aimed project's {@code
 * .vscode/tasks.json} in a list; the chosen one runs as Enter on it in
 * Quick Search runs it ({@link VsCodeTaskSearchProvider#run}). VS Code's
 * door of the same name is <i>Terminal ▸ Run Task…</i>. See {@link
 * VsCodeMenuDoor} for the order of a press.
 */
@ActionID(category = "Project", id = "org.nmox.studio.tools.vscode.RunTaskAction")
@ActionRegistration(displayName = "#CTL_RunTaskAction", lazy = true)
@ActionReference(path = "Menu/BuildProject", position = 30)
public final class RunTaskAction implements ActionListener {

    static final VsCodeMenuDoor<TaskDef> DOOR = new VsCodeMenuDoor<>(new VsCodeMenuDoor.Source<>() {
        @Override
        public List<TaskDef> read(File project) {
            return VsCodeTasks.read(project);
        }

        @Override
        public String row(TaskDef task) {
            return RunTaskAction.row(task);
        }

        @Override
        public void start(File project, TaskDef task, EditorContext editor) {
            VsCodeTaskSearchProvider.run(project, task, editor);
        }
    }, () -> new VsCodeMenuDoor.Words(
            message("RunTaskAction_title"), message("RunTaskAction_run"),
            message("RunTaskAction_noProject"), message("RunTaskAction_nothing"),
            message("RunTaskAction_aimMoved")),
            s -> VsCodeTaskSearchProvider.statusSink.accept(s));

    /** {@code build — make all}: the task's label, then its command as the file wrote it, on one line. */
    static String row(TaskDef task) {
        String label = NpmScriptSearchProvider.oneLine(task.label());
        String command = NpmScriptSearchProvider.clip(
                NpmScriptSearchProvider.oneLine(VsCodeTasks.display(task)), VsCodeTaskSearchProvider.MAX_COMMAND);
        return command.isEmpty() ? label : label + " — " + command;
    }

    private static String message(String key) {
        return NbBundle.getMessage(RunTaskAction.class, key);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DOOR.press();
    }
}
