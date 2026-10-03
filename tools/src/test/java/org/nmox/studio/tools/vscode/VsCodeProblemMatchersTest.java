package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Applied;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Declared;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Finding;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Report;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Session;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Severity;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Signal;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Skipped;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Unusable;
import org.nmox.studio.tools.vscode.VsCodeProblemMatchers.Why;
import org.nmox.studio.tools.vscode.VsCodeTasks.Os;
import org.nmox.studio.tools.vscode.VsCodeTasks.TaskDef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A task's {@code problemMatcher}, from the file's text to findings: the
 * built-in matchers against the output their tools really print, inline
 * matchers as VS Code reads them, the line machine, and what a hostile
 * or merely unlucky regular expression is allowed to cost.
 */
class VsCodeProblemMatchersTest {

    private static final char ESC = (char) 27;

    @TempDir
    Path project;

    /* ------------------------------------------------------------------ help */

    /** The matchers of a task whose {@code problemMatcher} is {@code written}, read through the real parser. */
    private Applied applied(Object written, boolean background) {
        JSONObject task = new JSONObject().put("label", "t").put("type", "process").put("command", "x")
                .put("isBackground", background).put("problemMatcher", written);
        TaskDef def = VsCodeTasks.parse(new JSONObject().put("tasks", new JSONArray().put(task)).toString(),
                Os.LINUX).get(0);
        File dir = project.toFile();
        return VsCodeProblemMatchers.apply(def.problemMatchers(), background, dir,
                s -> VsCodeTasks.substitute(s, dir, name -> null));
    }

    private Applied applied(Object written) {
        return applied(written, false);
    }

    private static Report feed(Session session, String output) {
        for (String line : output.split("\n", -1)) {
            session.line(line);
        }
        return session.report();
    }

    private List<Finding> found(Object written, String output) {
        Applied applied = applied(written);
        assertThat(applied.skipped()).as("every matcher is applied").isEmpty();
        return feed(new Session(applied, f -> false), output).findings();
    }

    private File inProject(String relative) {
        return new File(project.toFile(), relative).toPath().normalize().toFile();
    }

    private static File asWritten(String path) {
        return new File(path).toPath().normalize().toFile();
    }

    private static JSONObject pattern(String regexp) {
        return new JSONObject().put("regexp", regexp);
    }

    private static JSONObject matcher(Object pattern) {
        return new JSONObject().put("owner", "custom").put("pattern", pattern);
    }

    private static String only(List<Skipped> skipped) {
        assertThat(skipped).hasSize(1);
        return skipped.get(0).name() + " " + skipped.get(0).why() + " " + skipped.get(0).detail();
    }

    /* ------------------------------------------------- the built-ins by name */

    @Test
    @DisplayName("the built-in matchers are exactly these names")
    void builtInNames() {
        assertThat(VsCodeProblemMatchers.builtIns().keySet()).containsExactlyInAnyOrder(
                "msCompile", "lessCompile", "gulp-tsc", "jshint", "jshint-stylish", "eslint-compact",
                "eslint-stylish", "go", "tsc", "tsc-watch", "tsgo-watch", "lessc", "gcc", "rustc");
    }

    @Test
    @DisplayName("$tsc reads tsc's plain and pretty lines: file under the workspace, line, column, severity, code, source")
    void tsc() {
        List<Finding> found = found("$tsc", """
                src/index.ts(3,7): error TS2322: Type 'string' is not assignable to type 'number'.
                src/lib/util.ts:10:1 - warning TS6133: 'unused' is declared but its value is never read.

                10 const unused = 1;
                   ~~~~~~

                Found 2 errors in 2 files.
                """);
        assertThat(found).containsExactly(
                new Finding(inProject("src/index.ts"), 3, 7, 0, 0, Severity.ERROR,
                        "Type 'string' is not assignable to type 'number'.", "2322", "ts"),
                new Finding(inProject("src/lib/util.ts"), 10, 1, 0, 0, Severity.WARNING,
                        "'unused' is declared but its value is never read.", "6133", "ts"));
    }

    @Test
    @DisplayName("colour and cursor escape sequences are stripped before a line is matched")
    void ansiIsStripped() {
        String coloured = ESC + "[96msrc/index.ts" + ESC + "[0m:" + ESC + "[93m3" + ESC + "[0m:" + ESC + "[93m7"
                + ESC + "[0m - " + ESC + "[91merror" + ESC + "[0m" + ESC + "[90m TS2322: " + ESC
                + "[0mType 'string' is not assignable to type 'number'.";
        assertThat(VsCodeProblemMatchers.stripAnsi(coloured))
                .isEqualTo("src/index.ts:3:7 - error TS2322: Type 'string' is not assignable to type 'number'.");
        assertThat(found("$tsc", coloured)).extracting(Finding::line, Finding::column, Finding::code)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3, 7, "2322"));
        assertThat(VsCodeProblemMatchers.stripAnsi(ESC + "c" + ESC + "]0;title" + (char) 7 + "plain"))
                .as("a terminal reset and a title sequence go too").isEqualTo("plain");
        assertThat(VsCodeProblemMatchers.stripAnsi("no escapes")).isEqualTo("no escapes");
    }

    @Test
    @DisplayName("$eslint-stylish: a file line, then its problems — the loop — and the next file starts a new match")
    void eslintStylish() {
        List<Finding> found = found("$eslint-stylish", """

                /Users/dev/shop/src/cart.js
                   3:10  error    'total' is assigned a value but never used  no-unused-vars
                  12:5   warning  Unexpected console statement                no-console

                /Users/dev/shop/src/api.js
                  7:1  error  'fetchAll' is not defined  no-undef

                \u2716 3 problems (2 errors, 1 warning)
                """);
        File cart = asWritten("/Users/dev/shop/src/cart.js");
        File api = asWritten("/Users/dev/shop/src/api.js");
        assertThat(found).containsExactly(
                new Finding(cart, 3, 10, 0, 0, Severity.ERROR, "'total' is assigned a value but never used",
                        "no-unused-vars", "eslint"),
                new Finding(cart, 12, 5, 0, 0, Severity.WARNING, "Unexpected console statement", "no-console",
                        "eslint"),
                new Finding(api, 7, 1, 0, 0, Severity.ERROR, "'fetchAll' is not defined", "no-undef", "eslint"));
    }

    @Test
    @DisplayName("$eslint-compact reads one problem per line, severity from the tool's own word")
    void eslintCompact() {
        List<Finding> found = found("$eslint-compact", """
                /Users/dev/shop/src/cart.js: line 3, col 10, Error - 'total' is assigned a value but never used. (no-unused-vars)
                /Users/dev/shop/src/cart.js: line 12, col 5, Warning - Unexpected console statement. (no-console)

                2 problems
                """);
        File cart = asWritten("/Users/dev/shop/src/cart.js");
        assertThat(found).containsExactly(
                new Finding(cart, 3, 10, 0, 0, Severity.ERROR, "'total' is assigned a value but never used.",
                        "no-unused-vars", "eslint"),
                new Finding(cart, 12, 5, 0, 0, Severity.WARNING, "Unexpected console statement.", "no-console",
                        "eslint"));
    }

    @Test
    @DisplayName("$gcc reads errors, warnings and fatal errors; notes, context lines and the source excerpt are no problems")
    void gcc() {
        Applied applied = applied("$gcc");
        Predicate<File> exists = f -> f.equals(inProject("src/main.c"));
        Report report = feed(new Session(applied, exists), """
                src/main.c: In function 'main':
                src/main.c:12:5: error: 'x' undeclared (first use in this function)
                   12 |     x = 1;
                      |     ^
                src/main.c:12:5: note: each undeclared identifier is reported only once for each function it appears in
                src/main.c:20:9: warning: unused variable 'y' [-Wunused-variable]
                In file included from src/util.c:1:
                /usr/include/stdio.h:27:10: fatal error: bits/libc-header-start.h: No such file or directory
                compilation terminated.
                """);
        assertThat(report.findings()).containsExactly(
                new Finding(inProject("src/main.c"), 12, 5, 0, 0, Severity.ERROR,
                        "'x' undeclared (first use in this function)", null, "gcc"),
                new Finding(inProject("src/main.c"), 20, 9, 0, 0, Severity.WARNING,
                        "unused variable 'y' [-Wunused-variable]", null, "gcc"),
                new Finding(asWritten("/usr/include/stdio.h"), 27, 10, 0, 0, Severity.ERROR,
                        "bits/libc-header-start.h: No such file or directory", null, "gcc"));
    }

    @Test
    @DisplayName("$go, $gulp-tsc, $lessCompile and $lessc read their tools' lines")
    void goAndTheSmallOnes() {
        assertThat(found("$go", """
                # example.com/shop
                ./main.go:12:5: undefined: total
                cart/cart.go:7: syntax error: unexpected newline
                """)).containsExactly(
                new Finding(inProject("main.go"), 12, 5, 0, 0, Severity.ERROR, "undefined: total", null, "go"),
                new Finding(inProject("cart/cart.go"), 7, 0, 0, 0, Severity.ERROR,
                        "syntax error: unexpected newline", null, "go"));
        assertThat(found("$gulp-tsc", "src/app.ts(3,7): 2322 Type 'string' is not assignable to type 'number'."))
                .containsExactly(new Finding(inProject("src/app.ts"), 3, 7, 0, 0, Severity.ERROR,
                        "Type 'string' is not assignable to type 'number'.", "2322", "ts"));
        assertThat(found("$lessCompile", "  Unrecognised input in file /Users/dev/shop/site.less line no. 3"))
                .containsExactly(new Finding(asWritten("/Users/dev/shop/site.less"), 3, 0, 0, 0, Severity.ERROR,
                        "Unrecognised input", null, "less"));
        assertThat(found("$lessc", "ParseError: Unrecognised input. Possibly missing something in "
                + "/Users/dev/shop/styles/site.less on line 3, column 1:"))
                .containsExactly(new Finding(asWritten("/Users/dev/shop/styles/site.less"), 3, 1, 0, 0,
                        Severity.ERROR, "ParseError: Unrecognised input. Possibly missing something", null, "less"));
    }

    @Test
    @DisplayName("$msCompile reads cl and MSBuild lines, a location of one number or two; a linker line with no place is none")
    void msCompile() {
        List<Finding> found = found("$msCompile",
                "C:\\src\\shop\\main.cpp(12,5): error C2065: 'total': undeclared identifier\n"
                + "  1>C:\\src\\shop\\util.cpp(7): warning C4101: 'y': unreferenced local variable\n"
                + "LINK : fatal error LNK1104: cannot open file 'kernel32.lib'\n");
        assertThat(found).extracting(f -> f.file().getName().replace('\\', '/').replaceAll(".*/", ""),
                Finding::line, Finding::column, Finding::severity, Finding::code, Finding::message)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("main.cpp", 12, 5, Severity.ERROR, "C2065",
                                "'total': undeclared identifier"),
                        org.assertj.core.groups.Tuple.tuple("util.cpp", 7, 0, Severity.WARNING, "C4101",
                                "'y': unreferenced local variable"));
    }

    @Test
    @DisplayName("$msCompile as VS Code reads it today: a category word before the severity, and a code that may be absent")
    void msCompileCategoryAndNoCode() {
        List<Finding> found = found("$msCompile",
                "C:\\src\\shop\\app.ts(3,7): Build error TS1005: ';' expected.\n"
                + "C:\\src\\shop\\main.cpp(9): error : the build stopped\n");
        assertThat(found).extracting(Finding::line, Finding::column, Finding::severity, Finding::code, Finding::message)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(3, 7, Severity.ERROR, "TS1005", "';' expected."),
                        org.assertj.core.groups.Tuple.tuple(9, 0, Severity.ERROR, null, "the build stopped"));
    }

    @Test
    @DisplayName("$tsgo-watch: TypeScript 7's watch mode begins and ends its cycles on its own two lines")
    void tsgoWatchCycles() {
        Session session = new Session(applied("$tsgo-watch", true));
        assertThat(signals(session, "build starting at 10:40:00\n"
                + "src/app.ts(2,1): error TS2304: Cannot find name 'x'.\n"
                + "build finished in 120ms\n")).containsExactly(Signal.ENDED);
        assertThat(session.report().findings()).extracting(Finding::code).containsExactly("2304");
        assertThat(signals(session, "build starting at 10:40:05\nbuild finished in 90ms\n"))
                .containsExactly(Signal.BEGAN, Signal.ENDED);
        assertThat(session.report().findings()).as("a clean cycle clears").isEmpty();
    }

    @Test
    @DisplayName("$jshint and $jshint-stylish: the letter before the code is the severity")
    void jshint() {
        assertThat(found("$jshint", """
                /w/src/app.js: line 3, col 10, 'total' is defined but never used. (W098)
                /w/src/app.js: line 9, col 1, Missing semicolon. (E033)

                2 errors
                """)).containsExactly(
                new Finding(asWritten("/w/src/app.js"), 3, 10, 0, 0, Severity.WARNING,
                        "'total' is defined but never used.", "098", "jshint"),
                new Finding(asWritten("/w/src/app.js"), 9, 1, 0, 0, Severity.ERROR, "Missing semicolon.", "033",
                        "jshint"));
        assertThat(found("$jshint-stylish", """
                /w/src/app.js
                  line 3  col 10  'total' is defined but never used.  (W098)
                  line 9  col 1   Missing semicolon.  (E033)

                  2 problems
                """)).extracting(Finding::line, Finding::severity, Finding::code).containsExactly(
                org.assertj.core.groups.Tuple.tuple(3, Severity.WARNING, "098"),
                org.assertj.core.groups.Tuple.tuple(9, Severity.ERROR, "033"));
    }

    @Test
    @DisplayName("$rustc: the message line, then the place on the line after it — two lines make one problem")
    void rustc() {
        Applied applied = applied("$rustc");
        Report report = feed(new Session(applied, f -> f.equals(inProject("src/main.rs"))), """
                warning: unused variable: `x`
                 --> src/main.rs:2:9
                  |
                2 |     let x = 5;
                  |         ^ help: if this is intentional, prefix it with an underscore: `_x`
                  |
                  = note: `#[warn(unused_variables)]` on by default

                error[E0425]: cannot find value `y` in this scope
                 --> src/main.rs:3:20
                  |
                3 |     println!("{}", y);
                  |                    ^ help: a local variable with a similar name exists: `x`

                error: aborting due to 1 previous error; 1 warning emitted
                """);
        assertThat(report.findings()).containsExactly(
                new Finding(inProject("src/main.rs"), 2, 9, 0, 0, Severity.WARNING, "unused variable: `x`", null,
                        "rustc"),
                new Finding(inProject("src/main.rs"), 3, 20, 0, 0, Severity.ERROR,
                        "cannot find value `y` in this scope", "E0425", "rustc"));
    }

    /* ----------------------------------------------------------- inline ones */

    @Test
    @DisplayName("an inline pattern with no groups named takes VS Code's defaults: file 1, line 2, column 3, the whole match as message")
    void inlineDefaults() {
        List<Finding> found = found(matcher(pattern("^(\\S+):(\\d+):(\\d+): bad$")), "lib/a.txt:4:2: bad");
        assertThat(found).containsExactly(new Finding(inProject("lib/a.txt"), 4, 2, 0, 0, Severity.ERROR,
                "lib/a.txt:4:2: bad", null, "custom"));
    }

    @Test
    @DisplayName("a location group is read as line, line,column or line,column,endLine,endColumn; endLine and endColumn as groups too")
    void locations() {
        JSONObject located = pattern("^(\\S+)\\(([\\d,]+)\\): (.*)$").put("file", 1).put("location", 2)
                .put("message", 3);
        assertThat(found(matcher(located), "a.c(7): one\na.c(7,3): two\na.c(7,3,8,9): four"))
                .extracting(Finding::line, Finding::column, Finding::endLine, Finding::endColumn)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(7, 0, 0, 0),
                        org.assertj.core.groups.Tuple.tuple(7, 3, 0, 0),
                        org.assertj.core.groups.Tuple.tuple(7, 3, 8, 9));
        JSONObject ranged = pattern("^(\\S+) (\\d+):(\\d+)-(\\d+):(\\d+) (.*)$").put("file", 1).put("line", 2)
                .put("column", 3).put("endLine", 4).put("endColumn", 5).put("message", 6);
        assertThat(found(matcher(ranged), "a.c 1:2-3:4 range")).containsExactly(
                new Finding(inProject("a.c"), 1, 2, 3, 4, Severity.ERROR, "range", null, "custom"));
    }

    @Test
    @DisplayName("\"kind\": \"file\" needs no line: the problem is about the whole file")
    void fileKind() {
        JSONObject whole = pattern("^FAIL (\\S+): (.*)$").put("kind", "file").put("file", 1).put("message", 2);
        assertThat(found(matcher(whole), "FAIL test/a.spec.js: 2 of 9 failed")).containsExactly(
                new Finding(inProject("test/a.spec.js"), 0, 0, 0, 0, Severity.ERROR, "2 of 9 failed", null,
                        "custom"));
    }

    @Test
    @DisplayName("a line with no file, no place or no message is no problem — and neither is a line number that is not a number")
    void lessThanAProblemIsNone() {
        JSONObject p = pattern("^(\\S*):(\\S*): ?(.*)$").put("file", 1).put("line", 2).put("message", 3);
        assertThat(found(matcher(p), ":3: no file\na.c:: no line\na.c:3:\na.c:x: not a number\na.c:3: fine"))
                .extracting(Finding::message).containsExactly("fine");
    }

    @Test
    @DisplayName("a multi-line matcher: each pattern takes the next line, a message on two lines is joined, a miss on any line is no match")
    void multiLine() {
        JSONArray patterns = new JSONArray()
                .put(pattern("^Error in (\\S+)$").put("file", 1))
                .put(pattern("^  at line (\\d+), column (\\d+)$").put("line", 1).put("column", 2))
                .put(pattern("^  (\\w+): (.*)$").put("severity", 1).put("message", 2))
                .put(pattern("^  hint: (.*)$").put("message", 1));
        List<Finding> found = found(matcher(patterns), """
                Error in src/a.tpl
                  at line 4, column 9
                  warning: unclosed tag
                  hint: add </div>
                Error in src/b.tpl
                  at line 1, column 1
                  something else entirely
                  hint: never read
                Error in src/c.tpl
                  at line 2, column 3
                  error: bad attribute
                  hint: remove it
                """);
        assertThat(found).containsExactly(
                new Finding(inProject("src/a.tpl"), 4, 9, 0, 0, Severity.WARNING, "unclosed tag\nadd </div>", null,
                        "custom"),
                new Finding(inProject("src/c.tpl"), 2, 3, 0, 0, Severity.ERROR, "bad attribute\nremove it", null,
                        "custom"));
    }

    @Test
    @DisplayName("loop: the last pattern keeps matching the lines that follow, each a problem in the same file; the line that ends it may start the next")
    void loop() {
        JSONArray patterns = new JSONArray()
                .put(pattern("^== (\\S+) ==$").put("file", 1))
                .put(pattern("^(\\d+): (.*)$").put("line", 1).put("message", 2).put("loop", true));
        List<Finding> found = found(matcher(patterns), """
                == a.txt ==
                1: first
                2: second
                == b.txt ==
                9: third
                not a problem
                10: after the loop ended: no file, so no problem
                """);
        assertThat(found).extracting(f -> f.file().getName(), Finding::line, Finding::message).containsExactly(
                org.assertj.core.groups.Tuple.tuple("a.txt", 1, "first"),
                org.assertj.core.groups.Tuple.tuple("a.txt", 2, "second"),
                org.assertj.core.groups.Tuple.tuple("b.txt", 9, "third"));
    }

    @Test
    @DisplayName("the lines of a match are used up: the last line of one problem is never the first line of the next")
    void aMatchedLineIsNotMatchedAgain() {
        JSONArray patterns = new JSONArray()
                .put(pattern("^(\\S+)$").put("kind", "file").put("file", 1))
                .put(pattern("^(\\S+)$").put("message", 1));
        assertThat(found(matcher(patterns), "a\nb\nc\nd\ne")).extracting(f -> f.file().getName(), Finding::message)
                .as("a+b, then c+d: not a+b, b+c, c+d, d+e").containsExactly(
                        org.assertj.core.groups.Tuple.tuple("a", "b"), org.assertj.core.groups.Tuple.tuple("c", "d"));
    }

    @Test
    @DisplayName("two matchers on one task: each line goes to the first that takes it, and a multi-line one is tried before a single-line one")
    void severalMatchers() {
        JSONArray both = new JSONArray().put("$eslint-stylish").put("$tsc");
        List<Finding> found = found(both, """
                src/index.ts(3,7): error TS2322: Type 'string' is not assignable to type 'number'.
                /w/src/cart.js
                  12:5  warning  Unexpected console statement  no-console
                """);
        assertThat(found).extracting(Finding::source, Finding::line).containsExactly(
                org.assertj.core.groups.Tuple.tuple("ts", 3), org.assertj.core.groups.Tuple.tuple("eslint", 12));
    }

    /* ------------------------------------------------------------- severity */

    @Test
    @DisplayName("severity: the captured word as VS Code reads it, then E / W / I, hint and note; anything else is the matcher's own, else error")
    void severityIsReadAsVsCodeReadsIt() {
        JSONObject p = pattern("^(\\S+):(\\d+): \\[([^\\]]*)\\] (.*)$").put("file", 1).put("line", 2)
                .put("severity", 3).put("message", 4);
        String output = String.join("\n", "a:1: [error] x", "a:2: [WARNING] x", "a:3: [warn] x", "a:4: [Info] x",
                "a:5: [E] x", "a:6: [W] x", "a:7: [I] x", "a:8: [Hint] x", "a:9: [note] x", "a:10: [fatal] x",
                "a:11: [] x", "a:12: [e] x");
        assertThat(found(matcher(p), output)).extracting(Finding::severity).containsExactly(
                Severity.ERROR, Severity.WARNING, Severity.WARNING, Severity.INFO,
                Severity.ERROR, Severity.WARNING, Severity.INFO, Severity.INFO, Severity.INFO,
                Severity.ERROR, Severity.ERROR, Severity.ERROR);
        assertThat(found(matcher(p).put("severity", "warning"), "a:10: [fatal] x\na:11: [] x\na:1: [error] x"))
                .as("the matcher's own severity is the default, not an override")
                .extracting(Finding::severity).containsExactly(Severity.WARNING, Severity.WARNING, Severity.ERROR);
        assertThat(found(matcher(p).put("severity", "info"), "a:11: [] x")).extracting(Finding::severity)
                .containsExactly(Severity.INFO);
        assertThat(found(matcher(p).put("severity", "nonsense"), "a:11: [] x")).as("VS Code reads an unknown word as error")
                .extracting(Finding::severity).containsExactly(Severity.ERROR);
    }

    /* --------------------------------------------------------- fileLocation */

    @Test
    @DisplayName("fileLocation: absolute as written; relative to the workspace or to the folder named; never pulled into the project")
    void fileLocations() {
        JSONObject p = pattern("^(\\S+):(\\d+): (.*)$").put("file", 1).put("line", 2).put("message", 3);
        String output = "src/a.c:1: x\n../other/b.c:2: y\n/usr/include/c.h:3: z";

        assertThat(found(matcher(p), output)).as("no fileLocation: relative to the workspace folder")
                .extracting(Finding::file).containsExactly(inProject("src/a.c"), inProject("../other/b.c"),
                        inProject("usr/include/c.h"));
        assertThat(inProject("../other/b.c").toPath().startsWith(project))
                .as("a path that leaves the project is kept where it points").isFalse();

        assertThat(found(matcher(p).put("fileLocation", "absolute"), output)).as("absolute: the name itself")
                .extracting(Finding::file).containsExactly(asWritten(File.separator + "src/a.c"),
                        asWritten(File.separator + "../other/b.c"), asWritten("/usr/include/c.h"));

        assertThat(found(matcher(p).put("fileLocation", new JSONArray().put("relative")
                .put("${workspaceFolder}/packages/app")), "src/a.c:1: x"))
                .extracting(Finding::file).containsExactly(inProject("packages/app/src/a.c"));

        assertThat(found(matcher(p).put("fileLocation", new JSONArray().put("absolute")), "/x/a.c:1: x"))
                .extracting(Finding::file).containsExactly(asWritten("/x/a.c"));
    }

    @Test
    @DisplayName("autoDetect: relative when a file of that name is there, else the name as written")
    void autoDetect() {
        JSONObject p = pattern("^(\\S+):(\\d+): (.*)$").put("file", 1).put("line", 2).put("message", 3);
        Predicate<File> exists = f -> f.equals(inProject("src/a.c")) || f.equals(inProject("sub/b.c"));

        Applied plain = applied(matcher(p).put("fileLocation", "autoDetect"));
        assertThat(feed(new Session(plain, exists), "src/a.c:1: x\n/opt/sdk/c.h:2: y").findings())
                .extracting(Finding::file).containsExactly(inProject("src/a.c"), asWritten("/opt/sdk/c.h"));

        Applied under = applied(matcher(p).put("fileLocation",
                new JSONArray().put("autoDetect").put("${workspaceFolder}/sub")));
        assertThat(feed(new Session(under, exists), "b.c:1: x").findings())
                .extracting(Finding::file).containsExactly(inProject("sub/b.c"));
    }

    @Test
    @DisplayName("{\"base\": \"$tsc\"} keeps the built-in's pattern and takes the file's fileLocation, owner, source and severity")
    void base() {
        JSONObject extended = new JSONObject().put("base", "$tsc")
                .put("fileLocation", new JSONArray().put("relative").put("${workspaceFolder}/packages/app"))
                .put("source", "tsc (app)");
        assertThat(found(extended, "src/index.ts(3,7): error TS2322: nope")).containsExactly(
                new Finding(inProject("packages/app/src/index.ts"), 3, 7, 0, 0, Severity.ERROR, "nope", "2322",
                        "tsc (app)"));
        assertThat(found(new JSONObject().put("base", "$tsc"), "src/index.ts(3,7): error TS2322: nope"))
                .as("nothing overridden: the built-in").extracting(Finding::file, Finding::source)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(inProject("src/index.ts"), "ts"));
        assertThat(found(new JSONObject().put("base", "$tsc-watch").put("pattern", "$go"), "./main.go:1:2: x"))
                .as("a pattern named with its dollar").extracting(Finding::message).containsExactly("x");
    }

    /* ------------------------------------------------------- what is skipped */

    @Test
    @DisplayName("a matcher this IDE cannot apply is set aside by name with its reason; the ones beside it still apply")
    void skipped() {
        assertThat(only(applied("$rustc-watch").skipped())).isEqualTo("$rustc-watch UNKNOWN $rustc-watch");
        assertThat(only(applied("tsc").skipped())).as("a name is written with its dollar")
                .isEqualTo("tsc INVALID problemMatcher");
        assertThat(only(applied(new JSONObject().put("base", "$nope")).skipped())).isEqualTo("#1 UNKNOWN $nope");
        assertThat(only(applied(matcher("$nope")).skipped())).isEqualTo("#1 UNKNOWN $nope");
        assertThat(only(applied(new JSONObject().put("owner", "x")).skipped())).isEqualTo("#1 INVALID pattern");
        assertThat(only(applied(matcher(new JSONObject().put("file", 1))).skipped()))
                .isEqualTo("#1 INVALID regexp");
        assertThat(only(applied(matcher(pattern("^(.*)$")).put("fileLocation", "search")).skipped()))
                .isEqualTo("#1 FILE_LOCATION search");
        assertThat(only(applied(matcher(pattern("^(.*)$")).put("fileLocation", "sideways")).skipped()))
                .isEqualTo("#1 FILE_LOCATION sideways");
        assertThat(only(applied(matcher(pattern("^(.*)$")).put("fileLocation",
                new JSONArray().put("relative").put("${fileDirname}"))).skipped()))
                .as("a folder only the editor could fill").isEqualTo("#1 FILE_LOCATION ${fileDirname}");

        Applied mixed = applied(new JSONArray().put("$tsc").put("$from-an-extension")
                .put(matcher(pattern("(unclosed"))));
        assertThat(mixed.matchers()).extracting(b -> b.matcher().name()).containsExactly("$tsc");
        assertThat(mixed.skipped()).containsExactly(
                new Skipped("$from-an-extension", Why.UNKNOWN, "$from-an-extension"),
                new Skipped("#3", Why.REGEXP, "(unclosed"));
        assertThat(feed(new Session(mixed), "a.ts(1,1): error TS1: x").findings()).hasSize(1);
    }

    @Test
    @DisplayName("a multi-line pattern must name a file, a message and a place between its lines, and loop only on the last")
    void multiLineIsValidated() {
        JSONObject file = pattern("^(\\S+)$").put("file", 1);
        JSONObject place = pattern("^(\\d+)$").put("line", 1);
        JSONObject message = pattern("^(.*)$").put("message", 1);
        assertThat(only(applied(matcher(new JSONArray().put(place).put(message))).skipped()))
                .isEqualTo("#1 INVALID file");
        assertThat(only(applied(matcher(new JSONArray().put(file).put(place))).skipped()))
                .isEqualTo("#1 INVALID message");
        assertThat(only(applied(matcher(new JSONArray().put(file).put(message))).skipped()))
                .isEqualTo("#1 INVALID line");
        assertThat(only(applied(matcher(new JSONArray()
                .put(pattern("^(\\S+)$").put("file", 1).put("loop", true)).put(place).put(message))).skipped()))
                .isEqualTo("#1 INVALID loop");
        assertThat(only(applied(matcher(new JSONArray())).skipped())).isEqualTo("#1 INVALID pattern");
        assertThat(applied(matcher(new JSONArray().put(pattern("^(\\S+)$").put("file", 1).put("kind", "file"))
                .put(message))).skipped()).as("kind file needs no place").isEmpty();
    }

    @Test
    @DisplayName("more than the limit of matchers, patterns or regexp length is said, not silently cut")
    void limits() {
        JSONArray many = new JSONArray();
        for (int i = 0; i < VsCodeProblemMatchers.MAX_MATCHERS + 3; i++) {
            many.put("$tsc");
        }
        Applied applied = applied(many);
        assertThat(applied.matchers()).hasSize(VsCodeProblemMatchers.MAX_MATCHERS);
        assertThat(applied.skipped()).containsExactly(new Skipped("$tsc", Why.TOO_MANY, ""));

        JSONArray lines = new JSONArray();
        for (int i = 0; i <= VsCodeProblemMatchers.MAX_PATTERNS; i++) {
            lines.put(pattern("^(.*)$").put("file", 1).put("message", 1).put("line", 1));
        }
        assertThat(only(applied(matcher(lines)).skipped())).isEqualTo("#1 TOO_LARGE ");
        assertThat(only(applied(matcher(pattern("a".repeat(VsCodeProblemMatchers.MAX_REGEXP + 1)))).skipped()))
                .isEqualTo("#1 TOO_LARGE ");
    }

    /* ------------------------------------------- JavaScript's regexp in Java */

    private static boolean jsFinds(String regexp, String text) throws Unusable {
        return VsCodeProblemMatchers.compile(regexp).pattern().matcher(text).find();
    }

    @Test
    @DisplayName("a JavaScript regexp means in Java what it means in JavaScript: \\s, the dot, $, and a class's oddities")
    void javascriptMeaningIsKept() throws Exception {
        char nbsp = (char) 0xA0;
        char nel = (char) 0x85;
        char lineSeparator = (char) 0x2028;
        assertThat(jsFinds("^a\\sb$", "a" + nbsp + "b")).as("\\s is JavaScript's: a no-break space is one").isTrue();
        assertThat(jsFinds("^a[\\s]b$", "a" + nbsp + "b")).isTrue();
        assertThat(jsFinds("^a[^\\s]b$", "a" + nbsp + "b")).isFalse();
        assertThat(jsFinds("^a\\Sb$", "a" + nbsp + "b")).isFalse();
        assertThat(jsFinds("^a\\Sb$", "axb")).isTrue();
        assertThat(jsFinds("^a[\\S]b$", "axb")).isTrue();
        assertThat(jsFinds("^a.b$", "a" + nel + "b")).as("the dot stops at JavaScript's line ends only").isTrue();
        assertThat(jsFinds("^a.b$", "a" + lineSeparator + "b")).isFalse();
        assertThat(jsFinds("^ab$", "ab" + lineSeparator)).as("$ is the end, not the place before a last line end")
                .isFalse();
        assertThat(jsFinds("^[\\w-.]+$", "a-b.c")).as("a hyphen beside \\w is a hyphen").isTrue();
        assertThat(jsFinds("^[\\s->=]*x$", " -->= x")).isTrue();
        assertThat(jsFinds("^[a[]+$", "a[a")).as("[ inside a class is itself").isTrue();
        assertThat(jsFinds("^[a&&b]+$", "a&b")).as("&& inside a class is two ampersands").isTrue();
        assertThat(jsFinds("^{$", "{")).as("a brace that is no quantifier is a brace").isTrue();
        assertThat(jsFinds("^a{2}$", "aa")).isTrue();
        assertThat(jsFinds("^a{2,}b{1,2}$", "aaab")).isTrue();
        assertThat(jsFinds("^x{a}$", "x{a}")).isTrue();
        assertThat(jsFinds("^a\\cjb$", "a\nb")).as("\\cj is control-J whatever the letter's case").isTrue();
        assertThat(jsFinds("^a\\0b$", "a" + (char) 0 + "b")).isTrue();
        assertThat(jsFinds("^a[\\b]b$", "a\bb")).as("[\\b] is a backspace").isTrue();
        assertThat(jsFinds("\\bcat\\b", "a cat sat")).isTrue();
        assertThat(jsFinds("^a\\vb$", "a" + (char) 0x0B + "b")).isTrue();
        assertThat(jsFinds("^a\\vb$", "a\nb")).as("\\v is one character in JavaScript, a class in Java").isFalse();
        assertThat(jsFinds("^(?<file>\\S+):(?<line>\\d+)$", "a.c:3")).isTrue();
        assertThat(jsFinds("^(?:a|b)(?=c)(?!d)c(?<=c)(?<!x)$", "ac")).isTrue();
        assertThat(jsFinds("^a+?b*?c??$", "aab")).as("lazy quantifiers are the same in both").isTrue();
        assertThat(jsFinds("^\\u0041\\x42\\.\\/\\-$", "AB./-")).isTrue();
    }

    @Test
    @DisplayName("what cannot be said exactly in Java is refused naming the construct, never approximated")
    void dialectIsRefusedByName() {
        for (String[] each : new String[][] {
            {"^\\h+x", "\\h"}, {"a\\Rb", "\\R"}, {"\\Qa.b\\E", "\\Q"}, {"\\p{L}+", "\\p"}, {"\\Ax", "\\A"},
            {"x\\z", "\\z"}, {"a\\eb", "\\e"}, {"[]x", "[]"}, {"[^]x", "[^]"}, {"(?i)abc", "(?i"},
            {"(?>a)b", "(?>"}, {"a*+b", "*+"}, {"a++b", "++"}, {"a{2}+b", "}+"}, {"a\\07b", "\\07"},
            {"[\\1]", "[\\1]"}, {"[^\\S]", "[^\\S]"}, {"a\\kb", "\\k"}, {"\\xZZ", "\\x"}, {"\\u12", "\\u"}}) {
            assertThatThrownBy(() -> VsCodeProblemMatchers.compile(each[0])).as(each[0])
                    .isInstanceOfSatisfying(Unusable.class, refused -> {
                        assertThat(refused.why).isEqualTo(Why.DIALECT);
                        assertThat(refused.detail).isEqualTo(each[1]);
                    });
        }
        for (String broken : new String[] {"(unclosed", "a)", "[a-", "*a", "a\\", "(?<na_me>x)"}) {
            assertThatThrownBy(() -> VsCodeProblemMatchers.compile(broken)).as(broken)
                    .isInstanceOfSatisfying(Unusable.class, refused -> assertThat(refused.why).isEqualTo(Why.REGEXP));
        }
        assertThat(only(applied(matcher(pattern("^\\h*(\\S+):(\\d+):(\\d+)"))).skipped()))
                .isEqualTo("#1 DIALECT \\h");
    }

    /* ---------------------------------------------------------- hostile input */

    @Test
    @DisplayName("a regexp that backtracks without end is cut off after a fixed amount of reading, and its matcher switched off by name")
    void catastrophicBacktrackingIsCutOff() {
        Applied applied = applied(new JSONArray()
                .put(matcher(pattern("^(.*a){12}$").put("file", 1).put("message", 1).put("line", 1)))
                .put("$tsc"));
        Session session = new Session(applied);
        String evil = "a".repeat(40) + "!";
        for (int i = 0; i < VsCodeProblemMatchers.MAX_STRIKES - 1; i++) {
            session.line(evil);
        }
        assertThat(session.report().switchedOff()).as("not yet").isEmpty();
        session.line(evil);
        assertThat(session.report().switchedOff()).containsExactly("#1");
        // switched off, it costs nothing more — a thousand more such lines return at once
        for (int i = 0; i < 1_000; i++) {
            session.line(evil);
        }
        session.line("src/a.ts(1,1): error TS1: the matcher beside it still reads");
        assertThat(session.report().findings()).extracting(Finding::message)
                .containsExactly("the matcher beside it still reads");
    }

    @Test
    @DisplayName("an honest pattern on an ordinary long line stays far inside the budget")
    void honestPatternsFit() {
        Session session = new Session(applied(new JSONArray().put("$tsc").put("$eslint-stylish").put("$gcc")
                .put("$go").put("$lessc").put("$msCompile").put("$rustc").put("$tsc-watch"), true));
        String prose = ("the quick brown fox in a box: on line 3, column 4 of file.c:12:5 said (1,2): no "
                .repeat(40)).substring(0, VsCodeProblemMatchers.MAX_LINE);
        for (int i = 0; i < 50; i++) {
            session.line(prose);
        }
        assertThat(session.report().switchedOff()).isEmpty();
    }

    @Test
    @DisplayName("a line over the length limit is not matched, is counted, and ends a loop like any line that does not match")
    void longLinesAreCountedNotMatched() {
        Session session = new Session(applied(new JSONArray().put("$eslint-stylish").put("$tsc")));
        session.line("/w/a.js");
        session.line("  1:1  error  first  rule-a");
        session.line("  2:1  error  " + "x".repeat(VsCodeProblemMatchers.MAX_LINE) + "  rule-b");
        session.line("  3:1  error  after the long line the loop is over  rule-c");
        session.line("src/a.ts(1,1): error TS1: " + "y".repeat(VsCodeProblemMatchers.MAX_LINE));
        Report report = session.report();
        assertThat(report.findings()).extracting(Finding::message).containsExactly("first");
        assertThat(report.longLines()).isEqualTo(2);
    }

    @Test
    @DisplayName("at most MAX_FINDINGS are kept and the rest counted; the same problem twice is one")
    void findingsAreCappedAndTheOverflowCounted() {
        Session session = new Session(applied("$tsc"));
        for (int i = 1; i <= VsCodeProblemMatchers.MAX_FINDINGS + 7; i++) {
            session.line("src/a.ts(" + i + ",1): error TS1: x");
        }
        for (int i = 1; i <= 5; i++) {
            session.line("src/a.ts(" + i + ",1): error TS1: x"); // said again: the same problem, not another
        }
        Report report = session.report();
        assertThat(report.findings()).hasSize(VsCodeProblemMatchers.MAX_FINDINGS);
        assertThat(report.findings().get(VsCodeProblemMatchers.MAX_FINDINGS - 1).line())
                .as("the first ones are the ones kept").isEqualTo(VsCodeProblemMatchers.MAX_FINDINGS);
        assertThat(report.dropped()).isEqualTo(7);
    }

    @Test
    @DisplayName("a very long message is clipped; a line number too large to hold is held at the largest")
    void sizesAreHeld() {
        JSONObject p = pattern("^(\\S+):(\\d+): (.*)$").put("file", 1).put("line", 2).put("message", 3);
        List<Finding> found = found(matcher(p), "a:99999999999999999999: " + "m".repeat(1_500));
        assertThat(found.get(0).line()).isEqualTo(Integer.MAX_VALUE);
        assertThat(found.get(0).message()).hasSize(VsCodeProblemMatchers.MAX_MESSAGE + 1).endsWith("\u2026");
        assertThat(VsCodeProblemMatchers.parseInt(" 12abc")).isEqualTo(12);
        assertThat(VsCodeProblemMatchers.parseInt("abc")).isEqualTo(-1);
        assertThat(VsCodeProblemMatchers.parseInt("-3")).isEqualTo(-1);
        assertThat(VsCodeProblemMatchers.parseInt(null)).isEqualTo(-1);
    }

    /* ------------------------------------------------------------ background */

    private static final String TSC_WATCH = """
            [10:32:15 AM] Starting compilation in watch mode...

            src/index.ts(3,7): error TS2322: Type 'string' is not assignable to type 'number'.
            src/util.ts(1,1): error TS2304: Cannot find name 'foo'.

            [10:32:17 AM] Found 2 errors. Watching for file changes.
            """;

    private static List<Signal> signals(Session session, String output) {
        List<Signal> out = new ArrayList<>();
        for (String line : output.split("\n", -1)) {
            Signal signal = session.line(line);
            if (signal != Signal.NONE) {
                out.add(signal);
            }
        }
        return out;
    }

    @Test
    @DisplayName("$tsc-watch on a background task: ready at the first \"Watching for file changes\", and each cycle's findings are its own")
    void tscWatchCycles() {
        Session session = new Session(applied("$tsc-watch", true));
        assertThat(signals(session, TSC_WATCH)).as("active from the start: the begins line begins nothing new")
                .containsExactly(Signal.ENDED);
        assertThat(session.report().findings()).hasSize(2);
        assertThat(session.report().cycles()).isEqualTo(1);
        assertThat(session.lastCycle()).isEqualTo(session.report());

        assertThat(signals(session, "10:33:02 - File change detected. Starting incremental compilation...\n"))
                .containsExactly(Signal.BEGAN);
        assertThat(session.report().findings()).as("a new cycle starts from nothing").isEmpty();
        assertThat(session.lastCycle().findings()).as("what the ended cycle found is still its answer").hasSize(2);
        assertThat(signals(session, "src/util.ts(1,1): error TS2304: Cannot find name 'foo'.\n"
                + "\n10:33:03 - Found 1 error. Watching for file changes.\n")).containsExactly(Signal.ENDED);
        assertThat(session.report().findings()).extracting(Finding::code).containsExactly("2304");

        assertThat(signals(session, "[10:34:00 AM] File change detected. Starting incremental compilation...\n"
                + "\n[10:34:01 AM] Found 0 errors. Watching for file changes.\n"))
                .containsExactly(Signal.BEGAN, Signal.ENDED);
        assertThat(session.report().findings()).as("a clean cycle clears").isEmpty();
        assertThat(session.report().cycles()).isEqualTo(3);
        assertThat(signals(session, "message TS6042: Compilation complete. Watching for file changes."))
                .as("an end with no cycle going on means nothing").isEmpty();
    }

    @Test
    @DisplayName("activeOnStart false: an endsPattern before any beginsPattern is not ready; after one, it is")
    void inactiveUntilItBegins() {
        JSONObject watcher = matcher(pattern("^(\\S+):(\\d+): (.*)$").put("file", 1).put("line", 2).put("message", 3))
                .put("background", new JSONObject().put("beginsPattern", "^build started")
                        .put("endsPattern", new JSONObject().put("regexp", "^build finished")));
        Session session = new Session(applied(watcher, true));
        assertThat(signals(session, "build finished\na.c:1: early")).as("idle: the end means nothing").isEmpty();
        assertThat(signals(session, "build started\na.c:2: late\nbuild started\nbuild finished"))
                .as("a second begins while active begins nothing").containsExactly(Signal.BEGAN, Signal.ENDED);
        assertThat(session.report().findings()).extracting(Finding::message).containsExactly("late");

        Session active = new Session(applied(watcher.put("background",
                watcher.getJSONObject("background").put("activeOnStart", true)), true));
        assertThat(signals(active, "build finished")).containsExactly(Signal.ENDED);
    }

    @Test
    @DisplayName("a task that is not a background task does not use a matcher's background block")
    void notBackgroundNoCycles() {
        Session session = new Session(applied("$tsc-watch", false));
        assertThat(signals(session, TSC_WATCH)).isEmpty();
        assertThat(session.report().findings()).hasSize(2);
        assertThat(session.report().cycles()).isZero();
    }

    @Test
    @DisplayName("which matchers can say \"ready\": one with a background block, declared whole")
    void watching() {
        assertThat(VsCodeProblemMatchers.read(List.of("$tsc-watch")).watching()).isTrue();
        assertThat(VsCodeProblemMatchers.read(List.of("$tsc")).watching()).isFalse();
        assertThat(VsCodeProblemMatchers.read(List.of()).watching()).isFalse();
        assertThat(VsCodeProblemMatchers.read(List.of("$tsc", "$unknown")).watching()).isFalse();
        JSONObject both = new JSONObject().put("base", "$tsc").put("background",
                new JSONObject().put("beginsPattern", "^start").put("endsPattern", "^end"));
        assertThat(VsCodeProblemMatchers.read(List.of(both.toString())).watching()).isTrue();
        JSONObject legacy = new JSONObject().put("base", "$tsc").put("watching",
                new JSONObject().put("beginsPattern", "^start").put("endsPattern", "^end"));
        assertThat(VsCodeProblemMatchers.read(List.of(legacy.toString())).watching()).as("the older spelling").isTrue();

        Declared half = VsCodeProblemMatchers.read(List.of(new JSONObject().put("base", "$tsc")
                .put("background", new JSONObject().put("beginsPattern", "^start")).toString()));
        assertThat(half.watching()).isFalse();
        assertThat(half.skipped()).as("VS Code: both patterns, or neither")
                .containsExactly(new Skipped("#1", Why.INVALID, "endsPattern"));
        Declared broken = VsCodeProblemMatchers.read(List.of(new JSONObject().put("base", "$tsc")
                .put("background", new JSONObject().put("beginsPattern", "(").put("endsPattern", "^end")).toString()));
        assertThat(broken.watching()).isFalse();
        assertThat(broken.skipped()).containsExactly(new Skipped("#1", Why.REGEXP, "("));
        assertThat(applied("$tsc-watch", true).watches()).isTrue();
        assertThat(applied("$tsc-watch", false).watches()).as("only a background task has cycles").isFalse();
    }

    /* ------------------------------------------------------- the file's text */

    @Test
    @DisplayName("problemMatcher is read as a name, an object or an array of either, with the running OS's override")
    void readFromTheFile() {
        List<TaskDef> tasks = VsCodeTasks.parse("""
                {"tasks":[
                  {"label":"one","command":"x","problemMatcher":"$tsc"},
                  {"label":"many","command":"x","problemMatcher":["$tsc", {"base":"$go"}, 7, "{odd"]},
                  {"label":"none","command":"x"},
                  {"label":"empty","command":"x","problemMatcher":[]},
                  {"label":"per-os","command":"x","problemMatcher":"$tsc","linux":{"problemMatcher":"$gcc"}}
                ]}""", Os.LINUX);
        assertThat(tasks.get(0).problemMatchers()).containsExactly("$tsc");
        assertThat(tasks.get(1).problemMatchers()).containsExactly("$tsc", "{\"base\":\"$go\"}", "?7", "?{odd");
        assertThat(tasks.get(2).problemMatchers()).isEmpty();
        assertThat(tasks.get(3).problemMatchers()).isEmpty();
        assertThat(tasks.get(4).problemMatchers()).containsExactly("$gcc");
        Declared many = VsCodeProblemMatchers.read(tasks.get(1).problemMatchers());
        assertThat(many.matchers()).hasSize(2);
        assertThat(many.skipped()).extracting(Skipped::why).containsExactly(Why.INVALID, Why.INVALID);
    }

    @Test
    @DisplayName("a resolved task carries its matchers bound to their folders; a task with none carries NONE, background or not")
    void resolvedTasksCarryTheirMatchers() {
        File dir = project.toFile();
        List<TaskDef> tasks = VsCodeTasks.parse("""
                {"tasks":[
                  {"label":"watch","type":"process","command":"tsc","args":["-w"],"isBackground":true,
                   "problemMatcher":"$tsc-watch"},
                  {"label":"plain","type":"process","command":"tsc","isBackground":true},
                  {"label":"script","type":"npm","script":"watch","isBackground":true,"problemMatcher":"$tsc-watch"}
                ]}""", Os.LINUX);
        VsCodeTasks.Launch watch = (VsCodeTasks.Launch) VsCodeTasks.resolve(tasks.get(0), dir, Os.LINUX, n -> null);
        assertThat(watch.matching().watches()).isTrue();
        assertThat(watch.matching().matchers()).extracting(b -> b.base()).containsExactly(dir);
        assertThat(VsCodeTasks.resolve(tasks.get(0), dir, Os.LINUX, n -> null)).as("the same task resolves equal")
                .isEqualTo(watch);
        VsCodeTasks.Launch plain = (VsCodeTasks.Launch) VsCodeTasks.resolve(tasks.get(1), dir, Os.LINUX, n -> null);
        assertThat(plain.matching()).isSameAs(Applied.NONE);
        assertThat(plain).isEqualTo(new VsCodeTasks.Launch(List.of("tsc"), dir, java.util.Map.of()));
        VsCodeTasks.NpmLaunch script = (VsCodeTasks.NpmLaunch) VsCodeTasks.resolve(tasks.get(2), dir, Os.LINUX,
                n -> null);
        assertThat(script.matching().watches()).isTrue();
    }
}
