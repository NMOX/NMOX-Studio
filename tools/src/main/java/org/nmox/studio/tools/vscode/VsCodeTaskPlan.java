package org.nmox.studio.tools.vscode;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.nmox.studio.tools.vscode.VsCodeTasks.Aggregate;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.Host;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputOption;
import org.nmox.studio.tools.vscode.VsCodeTasks.Reason;
import org.nmox.studio.tools.vscode.VsCodeTasks.Refused;
import org.nmox.studio.tools.vscode.VsCodeTasks.Resolved;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.TasksFile;
import org.nmox.studio.tools.vscode.VsCodeTasks.Vars;

/**
 * What one Enter on a VS Code task RUNS, and in what order: the task, the
 * tasks its {@code dependsOn} names, theirs, and the questions its
 * {@code ${input:…}} variables ask. Pure, like {@link VsCodeTasks}: it
 * spawns nothing and shows nothing; the provider asks the questions and
 * runs the stages.
 *
 * <p><b>The order</b> is VS Code's ({@code terminalTaskSystem.ts}): a
 * task's dependencies run before it; {@code "dependsOrder": "parallel"}
 * (the default) starts them together, {@code "sequence"} starts each one
 * — and whatever IT depends on — only when the one before it has
 * finished; a task two others depend on runs ONCE, where the file's
 * order meets it first. {@link #plan} turns that into <em>stages</em>:
 * every task of a stage may start together, and a stage starts when the
 * one before it has finished with every exit code zero. Stages keep
 * every order the file declares; they are stricter than VS Code in one
 * way, said here rather than found later: VS Code starts a task the
 * moment its own dependencies finish, while a stage waits for the whole
 * stage before it, so two unrelated branches advance in step.
 *
 * <p><b>Everything is decided before anything runs.</b> {@link #check}
 * refuses the WHOLE run, naming the task and the reason, when the graph
 * names a label the file does not define (or defines twice), loops back
 * on itself, holds VS Code's task-identifier object form, waits for a
 * background task (it never "finishes", and the problem matcher that
 * tells VS Code it is ready is not implemented here), grows past
 * {@link #MAX_TASKS}, or holds any task that would itself be refused.
 * Running the tasks before a refused one would leave the project in a
 * state the file's author never asked for. What needs an answer the user
 * has not given yet — a working folder or a shell written with an
 * {@code ${input:…}} — is decided by {@link #finish}, after the
 * questions and still before the first spawn.
 */
final class VsCodeTaskPlan {

    /**
     * The most tasks one Enter may run. A stage starts all its tasks at
     * once, and the file is a cloned repository's; a real chain is a
     * handful.
     */
    static final int MAX_TASKS = 64;

    private VsCodeTaskPlan() {
    }

    /** The order of a run, or why there is none. */
    sealed interface Plan permits Stages, Refusal {
    }

    /** Stage by stage, the tasks to run; the task Enter was pressed on is the last stage, alone. */
    record Stages(List<List<TaskDef>> stages) implements Plan {
    }

    /** What {@link #check} answers. */
    sealed interface Outcome permits Checked, Refusal {
    }

    /** What {@link #finish} answers. */
    sealed interface Prepared permits Ready, Refusal {
    }

    /**
     * Nothing runs: {@code task} is the label of the task the reason is
     * about — the one Enter was pressed on, or one it depends on.
     */
    record Refusal(String task, Refused why) implements Plan, Outcome, Prepared {
    }

    /**
     * A run every part of which can be decided without asking: its
     * stages, and the {@code questions} to put to the user — each input
     * once, in the order the run first uses it.
     */
    record Checked(TaskDef root, List<List<TaskDef>> stages, List<InputDef> questions,
            TasksFile file, File project, Host host, EditorContext editor, String userHome) implements Outcome {
    }

    /** One task of a run and what it resolved to: a launch, an npm hand-off, or a group with nothing of its own. */
    record Step(TaskDef task, Resolved resolved) {

        /** Whether the step starts anything. */
        boolean runs() {
            return !(resolved instanceof Aggregate);
        }
    }

    /** The run, resolved: stage by stage, what to start. */
    record Ready(List<List<Step>> stages) implements Prepared {

        /** How many steps start something. */
        long running() {
            return stages.stream().flatMap(List::stream).filter(Step::runs).count();
        }
    }

    /* -------------------------------------------------------------- the order */

    /**
     * The stages of running {@code root} among {@code all} the file's
     * tasks, or the refusal. Tasks are told apart by identity: {@code
     * root} is the object Enter was pressed on, a dependency is the one
     * task of the file with that label.
     */
    static Plan plan(TaskDef root, List<TaskDef> all) {
        Scheduler scheduler = new Scheduler(root, all);
        if (scheduler.schedule(root, 0) < 0) {
            return scheduler.refusal;
        }
        Map<Integer, List<TaskDef>> byStage = new TreeMap<>();
        for (TaskDef task : scheduler.order) {
            byStage.computeIfAbsent(scheduler.stage.get(task), k -> new ArrayList<>()).add(task);
        }
        List<List<TaskDef>> stages = new ArrayList<>();
        byStage.values().forEach(stage -> stages.add(List.copyOf(stage)));
        return new Stages(List.copyOf(stages));
    }

    /** The depth-first walk VS Code's own executor makes, recording a stage where it would start a process. */
    private static final class Scheduler {

        private final TaskDef root;
        private final Map<String, List<TaskDef>> byLabel = new LinkedHashMap<>();
        /** The stage each task that is already placed runs in: placed once, met again, it is not run again. */
        private final Map<TaskDef, Integer> stage = new IdentityHashMap<>();
        /** The placed tasks, dependencies before the tasks that wait for them, in the file's order. */
        private final List<TaskDef> order = new ArrayList<>();
        /** The tasks being walked right now, outermost first: meeting one of them again is a loop. */
        private final List<TaskDef> path = new ArrayList<>();
        private Refusal refusal;

        Scheduler(TaskDef root, List<TaskDef> all) {
            this.root = root;
            for (TaskDef task : all) {
                byLabel.computeIfAbsent(task.label(), k -> new ArrayList<>()).add(task);
            }
        }

        /**
         * Places {@code task} and everything it depends on, none of it
         * before stage {@code after}; answers the task's stage, or -1
         * with {@link #refusal} set.
         */
        int schedule(TaskDef task, int after) {
            Integer placed = stage.get(task);
            if (placed != null) {
                return placed;
            }
            if (stage.size() + path.size() >= MAX_TASKS) {
                return refuse(root.label(), new Refused(Reason.CHAIN_TOO_LONG, ""));
            }
            if (task.foreignDependency() != null) {
                return refuse(task.label(), new Refused(Reason.DEPENDENCY_OBJECT, task.foreignDependency()));
            }
            path.add(task);
            int earliest = after;
            int latest = -1;
            for (String label : task.dependsOn()) {
                List<TaskDef> named = byLabel.getOrDefault(label, List.of());
                if (named.isEmpty()) {
                    return refuse(task.label(), new Refused(Reason.DEPENDENCY_MISSING, label));
                }
                if (named.size() > 1) {
                    return refuse(task.label(), new Refused(Reason.DEPENDENCY_AMBIGUOUS, label));
                }
                TaskDef dependency = named.get(0);
                int loop = indexOnPath(dependency);
                if (loop >= 0) {
                    List<String> labels = new ArrayList<>();
                    path.subList(loop, path.size()).forEach(t -> labels.add(t.label()));
                    labels.add(dependency.label());
                    return refuse(dependency.label(),
                            new Refused(Reason.DEPENDENCY_CYCLE, String.join(", ", labels)));
                }
                if (dependency.background()) {
                    return refuse(dependency.label(),
                            new Refused(Reason.DEPENDENCY_BACKGROUND, task.label()));
                }
                int at = schedule(dependency, earliest);
                if (at < 0) {
                    return -1;
                }
                latest = Math.max(latest, at);
                if (task.sequence()) {
                    // the next one, and whatever it depends on, waits for this one
                    earliest = Math.max(earliest, at + 1);
                }
            }
            path.remove(path.size() - 1);
            int mine = Math.max(after, latest + 1);
            stage.put(task, mine);
            order.add(task);
            return mine;
        }

        private int indexOnPath(TaskDef task) {
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i) == task) {
                    return i;
                }
            }
            return -1;
        }

        private int refuse(String task, Refused why) {
            refusal = new Refusal(task, why);
            return -1;
        }
    }

    /* ------------------------------------------------------- deciding the run */

    /**
     * Whether an answer could still change {@code reason} for {@code
     * task}: its folder, or its shell, is written with an
     * {@code ${input:…}}. Every other refusal is as true before the
     * questions as after them.
     */
    static boolean waitsForAnAnswer(TaskDef task, Reason reason) {
        List<String> written = new ArrayList<>();
        if (reason == Reason.CWD_OUTSIDE || reason == Reason.CWD_MISSING) {
            written.add(task.cwd());
            written.add(task.path());
        } else if (reason == Reason.SHELL_MISSING || reason == Reason.SHELL_UNSUPPORTED) {
            if (task.shell() != null) {
                written.add(task.shell().executable());
                if (task.shell().args() != null) {
                    written.addAll(task.shell().args());
                }
            }
        }
        return written.stream().anyMatch(s -> s != null && !VsCodeTasks.inputIds(s).isEmpty());
    }

    /**
     * Everything about running {@code root} that can be decided before a
     * question is asked: the order, every variable of every task, and —
     * for a task that asks nothing — exactly what it would run. Two
     * filesystem questions per task (its working folder), so off the EDT.
     */
    static Outcome check(TaskDef root, TasksFile file, File project, Host host,
            EditorContext editor, String userHome) {
        Plan plan = plan(root, file.tasks());
        if (plan instanceof Refusal refusal) {
            return refusal;
        }
        List<List<TaskDef>> stages = ((Stages) plan).stages();
        Vars unasked = new Vars(editor, userHome, file.inputs(), null);
        Map<String, InputDef> questions = new LinkedHashMap<>();
        Map<String, String> blanks = new LinkedHashMap<>();
        List<TaskDef> order = stages.stream().flatMap(List::stream).toList();
        for (TaskDef task : order) {
            for (String used : VsCodeTasks.usedStrings(task)) {
                Refused problem = VsCodeTasks.variableProblem(used, unasked);
                if (problem != null) {
                    return new Refusal(task.label(), problem);
                }
                for (String id : VsCodeTasks.inputIds(used)) {
                    questions.putIfAbsent(id, file.inputs().get(id));
                    blanks.put(id, "");
                }
            }
        }
        // with every question answered blank, what is left to refuse does
        // not depend on the answers — except a folder or a shell, which wait
        Vars blank = new Vars(editor, userHome, file.inputs(), blanks);
        for (TaskDef task : order) {
            if (VsCodeTasks.resolve(task, project, host, blank) instanceof Refused refused
                    && !waitsForAnAnswer(task, refused.reason())) {
                return new Refusal(task.label(), refused);
            }
        }
        return new Checked(root, stages, List.copyOf(questions.values()), file, project, host, editor, userHome);
    }

    /**
     * The run with its questions answered: every task resolved, or the
     * refusal. An input with no answer in {@code answers} — the user
     * pressed Cancel — refuses naming the input, and so does a
     * {@code pickString} answer that is not one of its options (a list
     * cannot give one; a seam could).
     */
    static Prepared finish(Checked checked, Map<String, String> answers) {
        for (InputDef question : checked.questions()) {
            String answer = answers.get(question.id());
            if (answer == null || (question.pick()
                    && question.options().stream().map(InputOption::value).noneMatch(answer::equals))) {
                return new Refusal(checked.root().label(), new Refused(Reason.INPUT_UNANSWERED, question.id()));
            }
        }
        Map<String, String> given = new LinkedHashMap<>();
        checked.questions().forEach(q -> given.put(q.id(), answers.get(q.id())));
        Vars vars = new Vars(checked.editor(), checked.userHome(), checked.file().inputs(), given);
        List<List<Step>> stages = new ArrayList<>();
        for (List<TaskDef> stage : checked.stages()) {
            List<Step> steps = new ArrayList<>();
            for (TaskDef task : stage) {
                Resolved resolved = VsCodeTasks.resolve(task, checked.project(), checked.host(), vars);
                if (resolved instanceof Refused refused) {
                    return new Refusal(task.label(), refused);
                }
                steps.add(new Step(task, resolved));
            }
            stages.add(List.copyOf(steps));
        }
        return new Ready(List.copyOf(stages));
    }
}
