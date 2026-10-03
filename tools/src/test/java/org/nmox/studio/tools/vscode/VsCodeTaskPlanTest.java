package org.nmox.studio.tools.vscode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Checked;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Outcome;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Plan;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Prepared;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Ready;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Refusal;
import org.nmox.studio.tools.vscode.VsCodeTaskPlan.Stages;
import org.nmox.studio.tools.vscode.VsCodeTasks.EditorContext;
import org.nmox.studio.tools.vscode.VsCodeTasks.Host;
import org.nmox.studio.tools.vscode.VsCodeTasks.InputDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.Launch;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;
import org.nmox.studio.tools.vscode.VsCodeTasks.Reason;
import org.nmox.studio.tools.vscode.VsCodeTasks.Refused;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;
import org.nmox.studio.tools.vscode.VsCodeTasks.TasksFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What one Enter on a task runs, and in what order: {@code dependsOn} and
 * {@code dependsOrder} as VS Code runs them, decided as stages before
 * anything starts — and the whole run refused, by name, when any part of
 * it cannot be honoured.
 */
class VsCodeTaskPlanTest {

    @TempDir
    Path project;

    private static final Host LINUX = new Host(Os.LINUX, name -> null, f -> false, name -> null);

    /** A file of {@code process} tasks: {@code "label"}, {@code "label>dep,dep"} (parallel) or {@code "label>>dep,dep"} (sequence). */
    private static TasksFile file(String... specs) {
        StringBuilder json = new StringBuilder("{\"tasks\":[");
        for (int i = 0; i < specs.length; i++) {
            String spec = specs[i];
            boolean sequence = spec.contains(">>");
            String[] halves = spec.split(">+", 2);
            json.append(i == 0 ? "" : ",").append("{\"label\":\"").append(halves[0])
                    .append("\",\"command\":\"run-").append(halves[0]).append('"');
            if (halves.length == 2) {
                json.append(",\"dependsOn\":[");
                String[] deps = halves[1].split(",");
                for (int d = 0; d < deps.length; d++) {
                    json.append(d == 0 ? "" : ",").append('"').append(deps[d]).append('"');
                }
                json.append(']');
                if (sequence) {
                    json.append(",\"dependsOrder\":\"sequence\"");
                }
            }
            json.append('}');
        }
        return VsCodeTasks.parseFile(json.append("]}").toString(), Os.LINUX);
    }

    private static TaskDef task(TasksFile file, String label) {
        return file.tasks().stream().filter(t -> t.label().equals(label)).findFirst().orElseThrow();
    }

    /** The stages of running the FIRST task of the file, as labels. */
    private static List<List<String>> stages(String... specs) {
        TasksFile file = file(specs);
        Plan plan = VsCodeTaskPlan.plan(file.tasks().get(0), file.tasks());
        assertThat(plan).isInstanceOf(Stages.class);
        List<List<String>> out = new ArrayList<>();
        for (List<TaskDef> stage : ((Stages) plan).stages()) {
            out.add(stage.stream().map(TaskDef::label).toList());
        }
        return out;
    }

    private static Refusal refusal(String... specs) {
        TasksFile file = file(specs);
        Plan plan = VsCodeTaskPlan.plan(file.tasks().get(0), file.tasks());
        assertThat(plan).isInstanceOf(Refusal.class);
        return (Refusal) plan;
    }

    @Test
    @DisplayName("a task with no dependsOn is one stage; parallel dependencies share a stage; sequence gives each its own")
    void parallelAndSequence() {
        assertThat(stages("build")).containsExactly(List.of("build"));
        assertThat(stages("release>build,test", "build", "test"))
                .as("parallel is VS Code's default: both start together, then the task")
                .containsExactly(List.of("build", "test"), List.of("release"));
        assertThat(stages("release>>build,test", "build", "test"))
                .as("sequence: in the order the file lists them")
                .containsExactly(List.of("build"), List.of("test"), List.of("release"));
        assertThat(stages("release>>test,build", "build", "test"))
                .containsExactly(List.of("test"), List.of("build"), List.of("release"));
    }

    @Test
    @DisplayName("a diamond: the dependency two tasks share runs once, before both")
    void sharedDependencyRunsOnce() {
        List<List<String>> stages = stages("all>left,right", "left>base", "right>base", "base");
        assertThat(stages).containsExactly(List.of("base"), List.of("left", "right"), List.of("all"));
        assertThat(stages.stream().flatMap(List::stream).filter("base"::equals).count())
                .as("VS Code runs a task it already met once per run").isEqualTo(1);
        // listed twice by one task, or met again further down, it is still one run
        assertThat(stages("all>base,base,left", "left>base", "base"))
                .containsExactly(List.of("base"), List.of("left"), List.of("all"));
        assertThat(stages("all>>left,right", "left>base", "right>base", "base"))
                .as("in a sequence the second branch finds the shared task already run")
                .containsExactly(List.of("base"), List.of("left"), List.of("right"), List.of("all"));
    }

    @Test
    @DisplayName("sequence holds back the next dependency AND what it depends on; each task keeps its own order")
    void nestedOrders() {
        assertThat(stages("all>>a,b", "a", "b>c", "c"))
                .as("b's own dependency does not start while a is still running")
                .containsExactly(List.of("a"), List.of("c"), List.of("b"), List.of("all"));
        assertThat(stages("all>a,b", "a>>x,y", "b>z", "x", "y", "z"))
                .as("a's sequence is a's; b's branch starts beside it")
                .containsExactly(List.of("x", "z"), List.of("y", "b"), List.of("a"), List.of("all"));
        assertThat(stages("all>p,q", "p>>x,y", "q>>y,x", "x", "y"))
                .as("two tasks that want opposite orders: the file's first wins, as in VS Code, and nothing runs twice")
                .containsExactly(List.of("x"), List.of("y"), List.of("p", "q"), List.of("all"));
    }

    @Test
    @DisplayName("every stage's tasks have every dependency in an earlier stage, on a graph with every shape at once")
    void dependenciesAlwaysComeFirst() {
        TasksFile file = file("root>>a,b,c", "a>d,e", "b>e,f", "c>>f,a", "d>g", "e>g", "f", "g");
        Stages plan = (Stages) VsCodeTaskPlan.plan(file.tasks().get(0), file.tasks());
        List<String> seen = new ArrayList<>();
        for (List<TaskDef> stage : plan.stages()) {
            for (TaskDef task : stage) {
                assertThat(seen).as(task.label() + " starts after " + task.dependsOn()).containsAll(task.dependsOn());
            }
            stage.forEach(t -> seen.add(t.label()));
        }
        assertThat(seen).as("each task once").doesNotHaveDuplicates().hasSize(8);
        assertThat(seen.get(seen.size() - 1)).as("the task Enter was pressed on is last").isEqualTo("root");
        assertThat(plan.stages().get(plan.stages().size() - 1)).as("and alone").hasSize(1);
    }

    @Test
    @DisplayName("a cycle refuses the whole run, naming the loop — however long, and a task that depends on itself")
    void cycleIsRefused() {
        assertThat(refusal("a>b", "b>a")).isEqualTo(
                new Refusal("a", new Refused(Reason.DEPENDENCY_CYCLE, "a, b, a")));
        assertThat(refusal("a>a")).isEqualTo(new Refusal("a", new Refused(Reason.DEPENDENCY_CYCLE, "a, a")));
        assertThat(refusal("start>ok,a", "ok", "a>b", "b>c", "c>a"))
                .as("the loop is named from where it closes, not from the task Enter was pressed on")
                .isEqualTo(new Refusal("a", new Refused(Reason.DEPENDENCY_CYCLE, "a, b, c, a")));
    }

    @Test
    @DisplayName("a label the file does not define, or defines twice, refuses naming the task that asked and the label")
    void missingAndAmbiguousLabels() {
        assertThat(refusal("release>build,npm: test", "build")).isEqualTo(
                new Refusal("release", new Refused(Reason.DEPENDENCY_MISSING, "npm: test")));
        assertThat(refusal("release>build", "build>lint")).as("one level down: the task that names it")
                .isEqualTo(new Refusal("build", new Refused(Reason.DEPENDENCY_MISSING, "lint")));
        assertThat(refusal("release>build", "build", "build")).isEqualTo(
                new Refusal("release", new Refused(Reason.DEPENDENCY_AMBIGUOUS, "build")));
    }

    @Test
    @DisplayName("the task-identifier object form and a background dependency refuse by name; a background task itself may wait for others")
    void objectFormAndBackground() {
        TasksFile object = VsCodeTasks.parseFile("""
                {"tasks":[{"label":"all","command":"x","dependsOn":["build",{"type":"npm","script":"test"}]},
                          {"label":"build","command":"make"}]}""", Os.LINUX);
        assertThat(VsCodeTaskPlan.plan(object.tasks().get(0), object.tasks())).isEqualTo(new Refusal("all",
                new Refused(Reason.DEPENDENCY_OBJECT, "{\"script\": \"test\", \"type\": \"npm\"}")));

        TasksFile watch = VsCodeTasks.parseFile("""
                {"tasks":[{"label":"serve","command":"x","isBackground":true,"dependsOn":["watch"]},
                          {"label":"watch","command":"tsc -w","isBackground":true}]}""", Os.LINUX);
        assertThat(VsCodeTaskPlan.plan(watch.tasks().get(0), watch.tasks()))
                .as("the background task is named, and the task that would wait for it")
                .isEqualTo(new Refusal("watch", new Refused(Reason.DEPENDENCY_BACKGROUND, "serve")));

        TasksFile fine = VsCodeTasks.parseFile("""
                {"tasks":[{"label":"serve","command":"x","isBackground":true,"dependsOn":["build"]},
                          {"label":"build","command":"make"}]}""", Os.LINUX);
        assertThat(VsCodeTaskPlan.plan(fine.tasks().get(0), fine.tasks())).isInstanceOf(Stages.class);
    }

    @Test
    @DisplayName("a run is at most MAX_TASKS tasks: one more refuses, and a chain that deep does not overflow the stack")
    void chainLengthIsBounded() {
        String[] atLimit = new String[VsCodeTaskPlan.MAX_TASKS];
        for (int i = 0; i < atLimit.length; i++) {
            atLimit[i] = i == atLimit.length - 1 ? "t" + i : "t" + i + ">t" + (i + 1);
        }
        assertThat(stages(atLimit)).hasSize(VsCodeTaskPlan.MAX_TASKS);

        String[] over = new String[5_000];
        for (int i = 0; i < over.length; i++) {
            over[i] = i == over.length - 1 ? "t" + i : "t" + i + ">t" + (i + 1);
        }
        assertThat(refusal(over)).isEqualTo(new Refusal("t0", new Refused(Reason.CHAIN_TOO_LONG, "")));

        String[] wide = new String[VsCodeTaskPlan.MAX_TASKS + 1];
        StringBuilder deps = new StringBuilder();
        for (int i = 1; i < wide.length; i++) {
            wide[i] = "w" + i;
            deps.append(i == 1 ? "" : ",").append("w").append(i);
        }
        wide[0] = "all>" + deps;
        assertThat(refusal(wide)).as("wide counts as long does")
                .isEqualTo(new Refusal("all", new Refused(Reason.CHAIN_TOO_LONG, "")));
    }

    /* -------------------------------------------------------- check and finish */

    private Outcome check(TasksFile file, String label, EditorContext editor) {
        return VsCodeTaskPlan.check(task(file, label), file, project.toFile(), LINUX, editor, "/home/x");
    }

    @Test
    @DisplayName("check: a dependency that would itself be refused refuses the whole run, naming that task and why")
    void aRefusedDependencyRefusesTheRun() {
        TasksFile file = VsCodeTasks.parseFile("""
                {"tasks":[
                  {"label":"release","command":"make","dependsOn":["build","lint","docs","pack","open"]},
                  {"label":"build","type":"gulp"},
                  {"label":"lint","command":"eslint","args":["${config:eslint.path}"]},
                  {"label":"docs","command":"make","options":{"cwd":".."}},
                  {"label":"pack","type":"shell"},
                  {"label":"open","command":"edit","args":["${file}"]},
                  {"label":"only-lint","command":"x","dependsOn":"lint"},
                  {"label":"only-docs","command":"x","dependsOn":"docs"},
                  {"label":"only-pack","command":"x","dependsOn":"pack"},
                  {"label":"only-open","command":"x","dependsOn":"open"}
                ]}""", Os.LINUX);
        assertThat(check(file, "release", EditorContext.NONE)).as("variables are judged first, across the whole run")
                .isEqualTo(new Refusal("lint", new Refused(Reason.VARIABLE, "${config:eslint.path}")));
        assertThat(check(file, "only-lint", EditorContext.NONE))
                .isEqualTo(new Refusal("lint", new Refused(Reason.VARIABLE, "${config:eslint.path}")));
        assertThat(check(file, "only-docs", EditorContext.NONE))
                .isEqualTo(new Refusal("docs", new Refused(Reason.CWD_OUTSIDE, "..")));
        assertThat(check(file, "only-pack", EditorContext.NONE))
                .isEqualTo(new Refusal("pack", new Refused(Reason.NO_COMMAND, "")));
        assertThat(check(file, "only-open", EditorContext.NONE))
                .as("no file open: the dependency that needs one is named, and nothing would run")
                .isEqualTo(new Refusal("open", new Refused(Reason.NEEDS_FILE, "${file}")));
        assertThat(check(file, "only-open", new EditorContext(project.resolve("a.js"), 1, 1, null)))
                .isInstanceOf(Checked.class);
    }

    private static final String ASKING = """
            {"tasks":[
              {"label":"deploy","type":"process","command":"deploy","args":["${input:env}","${input:token}","${input:env}"],
               "dependsOn":["build"],"options":{"env":{"REGION":"${input:region}"}}},
              {"label":"build","type":"process","command":"make","args":["${input:target}","${input:env}"]},
              {"label":"in","type":"process","command":"make","options":{"cwd":"${input:dir}"}},
              {"label":"in-static","type":"process","command":"make","args":["${input:env}"],"options":{"cwd":"nowhere"}},
              {"label":"cmd","command":"x","args":["${input:pick}"]},
              {"label":"odd","command":"x","args":["${input:odd}"]},
              {"label":"bare","command":"x","args":["${input:bare}"]},
              {"label":"ghost","command":"x","args":["${input:ghost}"]}
            ],
            "inputs":[
              {"id":"env","type":"pickString","description":"Where to?","options":["dev",{"label":"Production","value":"prod"}],"default":"prod"},
              {"id":"token","type":"promptString","description":"Token","password":true},
              {"id":"region","type":"promptString","description":"Region","default":"eu"},
              {"id":"target","type":"promptString","description":"Target"},
              {"id":"dir","type":"promptString","description":"Folder"},
              {"id":"pick","type":"command","command":"extension.pick"},
              {"id":"odd","type":"chooseFile","description":"x"},
              {"id":"bare","type":"promptString"}
            ]}""";

    @Test
    @DisplayName("check: the questions are each input once, in the order the run first uses it — dependencies first")
    void questionsInOrderOfFirstUse() {
        TasksFile file = VsCodeTasks.parseFile(ASKING, Os.LINUX);
        Checked checked = (Checked) check(file, "deploy", EditorContext.NONE);
        assertThat(checked.questions()).extracting(InputDef::id)
                .as("build runs first, so its inputs are asked first; env is asked once though used four times")
                .containsExactly("target", "env", "token", "region");
        assertThat(((Checked) check(file, "build", EditorContext.NONE)).questions())
                .extracting(InputDef::id).containsExactly("target", "env");
    }

    @Test
    @DisplayName("check: an input the file does not define, a command input, an unknown type and a missing description refuse by name")
    void inputsThatCannotBeAsked() {
        TasksFile file = VsCodeTasks.parseFile(ASKING, Os.LINUX);
        assertThat(check(file, "ghost", EditorContext.NONE))
                .isEqualTo(new Refusal("ghost", new Refused(Reason.INPUT_UNDEFINED, "ghost")));
        assertThat(check(file, "cmd", EditorContext.NONE))
                .isEqualTo(new Refusal("cmd", new Refused(Reason.INPUT_TYPE, "pick", "command")));
        assertThat(check(file, "odd", EditorContext.NONE))
                .isEqualTo(new Refusal("odd", new Refused(Reason.INPUT_TYPE, "odd", "chooseFile")));
        assertThat(check(file, "bare", EditorContext.NONE))
                .as("VS Code: an input of type promptString must include description")
                .isEqualTo(new Refusal("bare", new Refused(Reason.INPUT_INCOMPLETE, "bare", "description")));
    }

    @Test
    @DisplayName("check asks nothing of a refusal no answer could change; a folder written with an input waits for its answer")
    void whatWaitsForAnAnswer() throws Exception {
        TasksFile file = VsCodeTasks.parseFile(ASKING, Os.LINUX);
        assertThat(check(file, "in-static", EditorContext.NONE))
                .as("the folder is wrong whatever env is: refused before a question is put")
                .isEqualTo(new Refusal("in-static", new Refused(Reason.CWD_MISSING, "nowhere")));

        Outcome waits = check(file, "in", EditorContext.NONE);
        assertThat(waits).as("the folder IS the answer: decided after it").isInstanceOf(Checked.class);
        Files.createDirectories(project.resolve("web"));
        Prepared inside = VsCodeTaskPlan.finish((Checked) waits, Map.of("dir", "web"));
        assertThat(inside).isInstanceOf(Ready.class);
        Launch launch = (Launch) ((Ready) inside).stages().get(0).get(0).resolved();
        assertThat(launch.dir().getCanonicalFile()).isEqualTo(project.resolve("web").toFile().getCanonicalFile());
        assertThat(VsCodeTaskPlan.finish((Checked) waits, Map.of("dir", "../..")))
                .as("an answer cannot take the task out of the project")
                .isEqualTo(new Refusal("in", new Refused(Reason.CWD_OUTSIDE, "../..")));
        assertThat(VsCodeTaskPlan.finish((Checked) waits, Map.of("dir", "missing")))
                .isEqualTo(new Refusal("in", new Refused(Reason.CWD_MISSING, "missing")));
    }

    @Test
    @DisplayName("finish: every answer lands where the file put its variable; a missing answer is a cancelled question")
    void answersAreSubstituted() {
        TasksFile file = VsCodeTasks.parseFile(ASKING, Os.LINUX);
        Checked checked = (Checked) check(file, "deploy", EditorContext.NONE);
        Map<String, String> answers = Map.of("target", "all", "env", "prod", "token", "s3cret", "region", "");
        Ready ready = (Ready) VsCodeTaskPlan.finish(checked, answers);
        assertThat(ready.stages()).hasSize(2);
        assertThat(ready.running()).isEqualTo(2);
        Launch build = (Launch) ready.stages().get(0).get(0).resolved();
        Launch deploy = (Launch) ready.stages().get(1).get(0).resolved();
        assertThat(build.argv()).containsExactly("make", "all", "prod");
        assertThat(deploy.argv()).containsExactly("deploy", "prod", "s3cret", "prod");
        assertThat(deploy.env()).as("an empty answer is an answer: the user's own").containsEntry("REGION", "");

        assertThat(VsCodeTaskPlan.finish(checked, Map.of("target", "all", "env", "prod")))
                .as("Cancel at the third question: nothing is ready, and the question is named")
                .isEqualTo(new Refusal("deploy", new Refused(Reason.INPUT_UNANSWERED, "token")));
        assertThat(VsCodeTaskPlan.finish(checked, Map.of()))
                .isEqualTo(new Refusal("deploy", new Refused(Reason.INPUT_UNANSWERED, "target")));
        assertThat(VsCodeTaskPlan.finish(checked,
                Map.of("target", "all", "env", "staging", "token", "t", "region", "eu")))
                .as("a pickString answers with one of its options or not at all")
                .isEqualTo(new Refusal("deploy", new Refused(Reason.INPUT_UNANSWERED, "env")));
    }

    @Test
    @DisplayName("a group — dependsOn and no command — is a step that starts nothing, and its run counts only what starts")
    void aggregateRun() {
        TasksFile file = VsCodeTasks.parseFile("""
                {"tasks":[{"label":"all","dependsOn":["a","b"],"dependsOrder":"sequence"},
                          {"label":"a","command":"one"},{"label":"b","command":"two"}]}""", Os.LINUX);
        Ready ready = (Ready) VsCodeTaskPlan.finish((Checked) check(file, "all", EditorContext.NONE), Map.of());
        assertThat(ready.stages()).hasSize(3);
        assertThat(ready.stages().get(2).get(0).runs()).isFalse();
        assertThat(ready.running()).isEqualTo(2);
    }
}
