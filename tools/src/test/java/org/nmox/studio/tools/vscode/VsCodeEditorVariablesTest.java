package org.nmox.studio.tools.vscode;

import java.io.File;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The variables VS Code fills from its active editor, filled from the file
 * the caller names: each has VS Code's own definition, a value from the
 * editor is never read again for variables, and no file means no value.
 */
class VsCodeEditorVariablesTest {

    @TempDir
    Path project;

    private static final UnaryOperator<String> ENV = name -> "HOME".equals(name) ? "/home/dev" : null;

    private String sub(String s, Path file) {
        return VsCodeEditorVariables.substitute(s, project.toFile(), ENV, file);
    }

    @Test
    @DisplayName("each variable is VS Code's: the path, its name, its name without the last extension, its folder, its path from the workspace")
    void definitions() {
        Path file = project.resolve("src/app/main.test.ts");
        String abs = file.toAbsolutePath().normalize().toString();
        assertThat(sub("${file}", file)).isEqualTo(abs);
        assertThat(sub("${fileBasename}", file)).isEqualTo("main.test.ts");
        assertThat(sub("${fileBasenameNoExtension}", file)).isEqualTo("main.test");
        assertThat(sub("${fileDirname}", file)).isEqualTo(file.getParent().toAbsolutePath().normalize().toString());
        assertThat(sub("${relativeFile}", file)).isEqualTo("src" + File.separator + "app" + File.separator + "main.test.ts");

        assertThat(sub("${fileBasenameNoExtension}", project.resolve(".bashrc")))
                .as("a dotfile has no extension to drop").isEqualTo(".bashrc");
        assertThat(sub("${fileBasenameNoExtension}", project.resolve("Makefile"))).isEqualTo("Makefile");
        assertThat(sub("${relativeFile}", project.resolve("../elsewhere/x.js")))
                .as("a file outside the workspace is a path that climbs out of it")
                .isEqualTo(".." + File.separator + "elsewhere" + File.separator + "x.js");
        assertThat(sub("${file}", project.resolve("a/../b.js"))).as("normalized").endsWith(File.separator + "b.js")
                .doesNotContain("..");
    }

    @Test
    @DisplayName("the other variables are VsCodeTasks's, in the same pass, and the text between is left alone")
    void mixesWithTheWorkspaceVariables() {
        Path file = project.resolve("server.js");
        assertThat(sub("--entry=${fileBasename} --root=${workspaceFolder} --home=${env:HOME} ${env:UNSET}$ {x}", file))
                .isEqualTo("--entry=server.js --root=" + project.toFile().getAbsolutePath() + " --home=/home/dev $ {x}");
        assertThat(sub("${workspaceFolderBasename}/${fileBasename}", file))
                .isEqualTo(project.toFile().getName() + "/server.js");
    }

    @Test
    @DisplayName("a value that came from the editor is never read again for variables")
    void onePass() {
        // a legal file name on every OS (no colon), and a variable VsCodeTasks would fill
        Path odd = project.resolve("${workspaceFolderBasename}.js");
        assertThat(sub("${fileBasename}", odd)).isEqualTo("${workspaceFolderBasename}.js");
        assertThat(sub("${file} ${workspaceFolderBasename}", odd))
                .isEqualTo(odd.toAbsolutePath().normalize() + " " + project.toFile().getName());
    }

    @Test
    @DisplayName("first names the editor variable a value uses; unsupported leaves them out and asks VsCodeTasks about the rest")
    void questions() {
        assertThat(VsCodeEditorVariables.first("run ${workspaceFolder} ${relativeFile} ${file}")).isEqualTo("${relativeFile}");
        assertThat(VsCodeEditorVariables.first("${workspaceFolder}/server.js")).isNull();
        assertThat(VsCodeEditorVariables.first("${fileExtname}")).as("a task's file variables are a launch's too")
                .isEqualTo("${fileExtname}");
        assertThat(VsCodeEditorVariables.first("${lineNumber}")).as("the caret is not a file").isNull();

        assertThat(VsCodeEditorVariables.unsupported("${file} ${workspaceFolder} ${env:X}")).isNull();
        assertThat(VsCodeEditorVariables.unsupported("${file} ${input:port}")).isEqualTo("${input:port}");
        assertThat(VsCodeEditorVariables.unsupported("${fileExtname}")).isNull();
        assertThat(VsCodeEditorVariables.unsupported("${lineNumber}")).isEqualTo("${lineNumber}");
        assertThat(VsCodeEditorVariables.NAMES).as("one list, the task resolver's").isSameAs(VsCodeTasks.FILE_VARIABLES);
        assertThat(VsCodeEditorVariables.unsupported("${file} ${oops")).isEqualTo("${oops");
        assertThat(VsCodeEditorVariables.unsupported("$${file}{input:x}"))
                .as("what stands either side of an editor variable does not close up into a new one").isNull();
    }

    @Test
    @DisplayName("an editor variable with no file is never filled with a blank: substitute throws, naming it")
    void noFileNoGuess() {
        assertThatThrownBy(() -> sub("node ${file}", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("${file}");
        assertThat(sub("${workspaceFolder}", null)).as("no editor variable, no file needed")
                .isEqualTo(project.toFile().getAbsolutePath());
    }
}
