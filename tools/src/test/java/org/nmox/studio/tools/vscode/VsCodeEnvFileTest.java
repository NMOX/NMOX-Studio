package org.nmox.studio.tools.vscode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nmox.studio.tools.vscode.VsCodeEnvFile.Loaded;
import org.nmox.studio.tools.vscode.VsCodeEnvFile.Unplain;
import org.nmox.studio.tools.vscode.VsCodeEnvFile.Unreadable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A launch configuration's {@code envFile}: the variables come from the
 * product's one dotenv reader, and a line that VS Code's own readers would
 * take differently stops the launch instead of nearly matching it.
 */
class VsCodeEnvFileTest {

    private static final boolean NODE = false;
    private static final boolean PYTHON = true;

    @Test
    @DisplayName("the plain lines every .env is mostly made of are read, by the rack's reader")
    void readsThePlainLines(@TempDir Path dir) throws Exception {
        Path env = Files.writeString(dir.resolve(".env"), """
                \uFEFF# a comment, then a blank

                PORT=3000
                DATABASE_URL=postgres://user:pw@localhost:5432/dev?sslmode=disable
                GREETING=hello world
                JSON={"a":1}
                WIN_PATH=C:\\Users\\dev
                EMPTY=
                QUOTED="two words"
                SINGLE='it is "quoted"'
                _PRIVATE = spaced
                export EXPORTED=yes
                """);
        VsCodeEnvFile.Result r = VsCodeEnvFile.read(env.toFile(), NODE);
        assertThat(r).isInstanceOf(Loaded.class);
        Map<String, String> vars = ((Loaded) r).vars();
        assertThat(vars).containsEntry("PORT", "3000")
                .containsEntry("DATABASE_URL", "postgres://user:pw@localhost:5432/dev?sslmode=disable")
                .containsEntry("GREETING", "hello world")
                .containsEntry("JSON", "{\"a\":1}")
                .containsEntry("WIN_PATH", "C:\\Users\\dev")
                .containsEntry("EMPTY", "")
                .containsEntry("QUOTED", "two words")
                .containsEntry("SINGLE", "it is \"quoted\"")
                .containsEntry("_PRIVATE", "spaced")
                .containsEntry("EXPORTED", "yes")
                .hasSize(10);
        assertThat(r.toString()).as("names, never values").contains("DATABASE_URL").doesNotContain("pw@");
    }

    @Test
    @DisplayName("a line VS Code's Node reader (dotenv) takes differently is not plain for Node")
    void unplainForNode() {
        for (String line : new String[] {
            "URL=http://localhost/#frag",       // dotenv ends an unquoted value at #
            "KEY=value # trailing comment",
            "MULTI=\"line one\\nline two\"",     // dotenv turns \n into a newline
            "ESC=\"a \\\" b\"",
            "TICK=`backticks quote in dotenv`",
            "OPEN=\"no closing quote",
            "TWO='a' 'b'",
            "KEY: value",                         // dotenv takes a colon; the rack's reader skips the line
            "NOT A KEY=x",
            "1ST=x",
            "DOTTED.NAME=x",
            "=x",
            "just text"}) {
            assertThat(VsCodeEnvFile.plain(line, NODE)).as(line).isFalse();
        }
        assertThat(VsCodeEnvFile.plain("PW=p@$$w0rd${X}", NODE))
                .as("dotenv does not expand ${…}; neither does the rack's reader").isTrue();
        assertThat(VsCodeEnvFile.plain("QUOTED=\"a # b\"", NODE)).as("# inside quotes is the value").isTrue();
    }

    @Test
    @DisplayName("VS Code's Python reader differs: no export, ${NAME} is replaced, a # stays in the value")
    void unplainForPython() {
        assertThat(VsCodeEnvFile.plain("export A=1", PYTHON)).isFalse();
        assertThat(VsCodeEnvFile.plain("BIN=${HOME}/bin", PYTHON)).isFalse();
        assertThat(VsCodeEnvFile.plain("BIN='${HOME}/bin'", PYTHON)).isFalse();
        assertThat(VsCodeEnvFile.plain("URL=http://localhost/#frag", PYTHON)).isTrue();
        assertThat(VsCodeEnvFile.plain("A=1", PYTHON)).isTrue();
        assertThat(VsCodeEnvFile.plain("NL=\"a\\nb\"", PYTHON)).isFalse();
    }

    @Test
    @DisplayName("the first unplain line is named by its number, counted from one, blank and comment lines included")
    void namesTheLine(@TempDir Path dir) throws Exception {
        assertThat(VsCodeEnvFile.firstUnplainLine(List.of("A=1", "", "# c", "B=x # y", "C=`z`"), NODE)).isEqualTo(4);
        assertThat(VsCodeEnvFile.firstUnplainLine(List.of("A=1", "B=2"), NODE)).isZero();
        Path env = Files.writeString(dir.resolve("crlf.env"), "A=1\r\nB=\"x\\ty\"\r\n");
        assertThat(VsCodeEnvFile.read(env.toFile(), NODE)).isEqualTo(new Unplain(2));
    }

    @Test
    @DisplayName("a file over the cap, or not there to read, is Unreadable — never an empty environment")
    void unreadable(@TempDir Path dir) throws Exception {
        Path big = Files.writeString(dir.resolve("big.env"), "A=" + "x".repeat((int) VsCodeEnvFile.MAX_BYTES));
        assertThat(VsCodeEnvFile.read(big.toFile(), NODE)).isInstanceOf(Unreadable.class);
        assertThat(VsCodeEnvFile.read(dir.resolve("absent.env").toFile(), NODE)).isInstanceOf(Unreadable.class);
        assertThat(VsCodeEnvFile.read(dir.toFile(), NODE)).as("a folder").isInstanceOf(Unreadable.class);
        Path empty = Files.writeString(dir.resolve("empty.env"), "");
        assertThat(VsCodeEnvFile.read(empty.toFile(), NODE)).isEqualTo(new Loaded(Map.of()));
    }
}
