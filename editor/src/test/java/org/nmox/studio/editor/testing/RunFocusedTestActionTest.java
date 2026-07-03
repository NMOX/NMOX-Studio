package org.nmox.studio.editor.testing;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.editor.testing.RunFocusedTestAction.Focused;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Run Focused Test" scans up from the caret for the nearest test
 * declaration and builds the exact one-test command for the file's
 * language and project. These pin the per-language declaration patterns,
 * the nearest-match scan, and the command each runner produces.
 */
class RunFocusedTestActionTest {

    private static PlainDocument doc(String text) throws BadLocationException {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        return d;
    }

    // ---- per-language declaration patterns ---------------------------------

    @Test
    @DisplayName("Each supported mime maps to its declaration pattern; others map to null")
    void patternFor() {
        assertThat(RunFocusedTestAction.patternFor("text/javascript")).isNotNull();
        assertThat(RunFocusedTestAction.patternFor("text/typescript")).isNotNull();
        assertThat(RunFocusedTestAction.patternFor("text/x-python")).isNotNull();
        assertThat(RunFocusedTestAction.patternFor("text/x-go")).isNotNull();
        assertThat(RunFocusedTestAction.patternFor("text/x-rust")).isNotNull();
        assertThat(RunFocusedTestAction.patternFor("text/x-elixir")).isNotNull();
        assertThat(RunFocusedTestAction.patternFor("text/x-ruby")).isNull();
    }

    // ---- nearest-match scan ------------------------------------------------

    @Test
    @DisplayName("JS: the last it/test/describe title within the caret's lookahead window wins")
    void jsNearestMatch() throws BadLocationException {
        String src = "describe('suite', () => {\n"
                + "  it('adds numbers', () => {});\n"
                + "  it('subtracts numbers', () => {});\n"
                + "});\n";
        Pattern p = RunFocusedTestAction.patternFor("text/javascript");
        // the scan reads up to caret+200 chars and keeps the last match — with a
        // short file every declaration is in range, so the final it() title wins
        int caret = src.indexOf("subtracts");
        assertThat(RunFocusedTestAction.nearestMatch(doc(src), caret, p))
                .isEqualTo("subtracts numbers");

        // when only the describe is within the window, that title is returned
        String longSrc = "describe('suite', () => {\n"
                + "  // " + "x".repeat(300) + "\n"
                + "  it('deep test', () => {});\n"
                + "});\n";
        assertThat(RunFocusedTestAction.nearestMatch(doc(longSrc), 0, p)).isEqualTo("suite");
    }

    @Test
    @DisplayName("Python/Go: the def/func test name nearest the caret is captured")
    void pythonAndGoNearestMatch() throws BadLocationException {
        String py = "def test_alpha():\n    pass\n\ndef test_beta():\n    pass\n";
        Pattern pyPat = RunFocusedTestAction.patternFor("text/x-python");
        assertThat(RunFocusedTestAction.nearestMatch(doc(py), py.indexOf("test_beta"), pyPat))
                .isEqualTo("test_beta");

        String go = "func TestOne(t *testing.T) {}\nfunc TestTwo(t *testing.T) {}\n";
        Pattern goPat = RunFocusedTestAction.patternFor("text/x-go");
        assertThat(RunFocusedTestAction.nearestMatch(doc(go), go.indexOf("TestTwo"), goPat))
                .isEqualTo("TestTwo");
    }

    @Test
    @DisplayName("No declaration above the caret yields null")
    void noMatchIsNull() throws BadLocationException {
        Pattern p = RunFocusedTestAction.patternFor("text/x-python");
        assertThat(RunFocusedTestAction.nearestMatch(doc("x = 1\n"), 5, p)).isNull();
        // a null pattern (unsupported mime) also yields null
        assertThat(RunFocusedTestAction.nearestMatch(doc("anything"), 0, null)).isNull();
    }

    // ---- command building --------------------------------------------------

    @Test
    @DisplayName("JS with no vitest dependency builds a jest -t command rooted at the manifest")
    void jsJestCommand(@TempDir File project) throws Exception {
        Files.writeString(new File(project, "package.json").toPath(),
                "{\"name\":\"app\"}", StandardCharsets.UTF_8);
        File testFile = new File(project, "adds.test.js");
        Files.writeString(testFile.toPath(), "it('x', () => {})", StandardCharsets.UTF_8);

        Focused f = RunFocusedTestAction.commandFor("text/javascript", testFile, "adds numbers", 1);
        assertThat(f).isNotNull();
        assertThat(f.command()).containsExactly("npx", "jest",
                testFile.getAbsolutePath(), "-t", "adds numbers");
        assertThat(f.dir()).isEqualTo(project);
    }

    @Test
    @DisplayName("JS with vitest in devDependencies builds a vitest run command")
    void jsVitestCommand(@TempDir File project) throws Exception {
        Files.writeString(new File(project, "package.json").toPath(),
                "{\"name\":\"app\",\"devDependencies\":{\"vitest\":\"^1.0.0\"}}",
                StandardCharsets.UTF_8);
        File testFile = new File(project, "adds.test.ts");
        Files.writeString(testFile.toPath(), "it('x', () => {})", StandardCharsets.UTF_8);

        Focused f = RunFocusedTestAction.commandFor("text/typescript", testFile, "adds numbers", 1);
        assertThat(f.command()).containsExactly("npx", "vitest", "run",
                "-t", "adds numbers", testFile.getAbsolutePath());
    }

    @Test
    @DisplayName("A JS file with no focused test name yields no command")
    void jsNoNameIsNull(@TempDir File project) throws Exception {
        Files.writeString(new File(project, "package.json").toPath(),
                "{\"name\":\"app\"}", StandardCharsets.UTF_8);
        File testFile = new File(project, "empty.test.js");
        Files.writeString(testFile.toPath(), "", StandardCharsets.UTF_8);
        assertThat(RunFocusedTestAction.commandFor("text/javascript", testFile, null, 1)).isNull();
    }

    @Test
    @DisplayName("Python builds a pytest node id, rooted at the package.json/manifest dir")
    void pythonCommand(@TempDir File project) throws Exception {
        // a python project rooted by a pyproject-like manifest: use go.mod-free node manifest
        Files.writeString(new File(project, "package.json").toPath(), "{}", StandardCharsets.UTF_8);
        File testFile = new File(project, "test_it.py");
        Files.writeString(testFile.toPath(), "def test_alpha(): pass", StandardCharsets.UTF_8);

        Focused f = RunFocusedTestAction.commandFor("text/x-python", testFile, "test_alpha", 1);
        assertThat(f.command()).containsExactly("python3", "-m", "pytest",
                testFile.getAbsolutePath() + "::test_alpha", "-v");
    }

    @Test
    @DisplayName("Go builds a go test -run anchored to the exact test name")
    void goCommand(@TempDir File project) throws Exception {
        Files.writeString(new File(project, "go.mod").toPath(), "module app\n", StandardCharsets.UTF_8);
        File testFile = new File(project, "main_test.go");
        Files.writeString(testFile.toPath(), "func TestOne(t *testing.T){}", StandardCharsets.UTF_8);

        Focused f = RunFocusedTestAction.commandFor("text/x-go", testFile, "TestOne", 1);
        assertThat(f.command()).containsExactly("go", "test", "-run", "^TestOne$", "./...");
        assertThat(f.dir()).isEqualTo(project);
    }

    @Test
    @DisplayName("Rust builds a cargo test for the named test")
    void rustCommand(@TempDir File project) throws Exception {
        Files.writeString(new File(project, "Cargo.toml").toPath(),
                "[package]\nname=\"app\"\n", StandardCharsets.UTF_8);
        File testFile = new File(project, "lib.rs");
        Files.writeString(testFile.toPath(), "fn it_works(){}", StandardCharsets.UTF_8);

        Focused f = RunFocusedTestAction.commandFor("text/x-rust", testFile, "it_works", 1);
        assertThat(f.command()).containsExactly("cargo", "test", "it_works");
    }

    @Test
    @DisplayName("Elixir runs mix test at file:line — it needs no test name")
    void elixirCommand(@TempDir File project) throws Exception {
        Files.writeString(new File(project, "mix.exs").toPath(),
                "defmodule App.MixProject do\nend\n", StandardCharsets.UTF_8);
        File testFile = new File(project, "app_test.exs");
        Files.writeString(testFile.toPath(), "test \"works\" do\nend", StandardCharsets.UTF_8);

        Focused f = RunFocusedTestAction.commandFor("text/x-elixir", testFile, null, 42);
        assertThat(f.command()).containsExactly("mix", "test",
                testFile.getAbsolutePath() + ":42");
    }

    @Test
    @DisplayName("An unsupported mime builds no command")
    void unsupportedMimeIsNull(@TempDir File project) throws Exception {
        File f = new File(project, "x.rb");
        Files.writeString(f.toPath(), "", StandardCharsets.UTF_8);
        assertThat(RunFocusedTestAction.commandFor("text/x-ruby", f, "foo", 1)).isNull();
    }
}
