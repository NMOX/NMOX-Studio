package org.nmox.studio.rack.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dotenv reader feeds every command the rack launches: it must
 * match what the ecosystem's own tooling does for the common cases and
 * fail silent-and-empty for the broken ones.
 */
class EnvFilesTest {

    @Test
    @DisplayName("Reads KEY=VALUE, skips comments and blanks, strips export and quotes")
    void parsesTheCommonShapes(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".env"), """
                # database
                DATABASE_URL=postgres://localhost:5432/dev

                export API_KEY="sk-123=abc"
                NAME='single quoted'
                EMPTY=
                NOT A KEY=nope
                =novalue
                justtext
                """);
        Map<String, String> env = EnvFiles.load(dir.toFile());
        assertThat(env).containsEntry("DATABASE_URL", "postgres://localhost:5432/dev")
                .containsEntry("API_KEY", "sk-123=abc") // = inside quoted value survives
                .containsEntry("NAME", "single quoted")
                .containsEntry("EMPTY", "")
                .doesNotContainKeys("NOT A KEY", "", "justtext");
    }

    @Test
    @DisplayName(".env.local wins over .env")
    void localOverridesBase(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".env"), "PORT=3000\nSHARED=base\n");
        Files.writeString(dir.resolve(".env.local"), "PORT=4000\n");
        Map<String, String> env = EnvFiles.load(dir.toFile());
        assertThat(env).containsEntry("PORT", "4000").containsEntry("SHARED", "base");
    }

    @Test
    @DisplayName("Missing files and null dirs load as empty, never throw")
    void missingIsEmpty(@TempDir Path dir) {
        assertThat(EnvFiles.load(dir.toFile())).isEmpty();
        assertThat(EnvFiles.load(null)).isEmpty();
    }

    @Test
    @DisplayName("A pathologically large .env is refused, not slurped")
    void oversizedIsSkipped(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".env"), "KEY=" + "x".repeat(300 * 1024));
        assertThat(EnvFiles.load(dir.toFile())).isEmpty();
    }

    @Test
    @DisplayName("parseLines is the same reader over lines somebody else read: what a file's line means has one home")
    void linesMeanWhatTheFileMeans(@TempDir Path dir) throws Exception {
        String text = "# c\nA=1\nexport B=\"two words\"\nC='x'\nNOT A KEY=nope\nD=a=b\nA=last wins\n";
        Files.writeString(dir.resolve(".env"), text);
        assertThat(EnvFiles.parseLines(text.lines().toList()))
                .isEqualTo(EnvFiles.load(dir.toFile()))
                .containsEntry("A", "last wins").containsEntry("B", "two words")
                .containsEntry("C", "x").containsEntry("D", "a=b").hasSize(4);
        assertThat(EnvFiles.parseLines(java.util.List.of())).isEmpty();
    }
}
