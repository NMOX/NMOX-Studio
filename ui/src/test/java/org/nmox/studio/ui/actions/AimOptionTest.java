package org.nmox.studio.ui.actions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.api.sendopts.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code --aim <folder>} (3.1.0): the terminal door into Open Folder…,
 * driven through the platform's real sendopts parser so the option's
 * name, its argument and the caller's directory are all the real ones.
 */
class AimOptionTest {

    @TempDir
    Path tmp;

    private final List<File> aimed = new ArrayList<>();
    private Consumer<File> saved;

    @BeforeEach
    void seam() {
        saved = AimOption.aimer;
        AimOption.aimer = aimed::add;
    }

    @AfterEach
    void restore() {
        AimOption.aimer = saved;
    }

    private void run(File cwd, String... args) throws CommandException {
        CommandLine.create(AimOption.class).process(args,
                new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(), new ByteArrayOutputStream(), cwd);
    }

    @Test
    @DisplayName("`--aim .` aims the directory the command was typed in, as an absolute path")
    void dotIsTheCallersDirectory() throws Exception {
        Path app = Files.createDirectory(tmp.resolve("app"));
        run(app.toFile(), "--aim", ".");
        assertThat(aimed).containsExactly(app.toFile().getAbsoluteFile());
    }

    @Test
    @DisplayName("a folder with no manifest is aimed like any other - the case --open gets wrong")
    void manifestlessFolderIsAimed() throws Exception {
        Path notes = Files.createDirectory(tmp.resolve("notes"));
        Files.writeString(notes.resolve("todo.txt"), "hello");
        run(tmp.toFile(), "--aim", "notes");
        assertThat(aimed).containsExactly(notes.toFile());
    }

    @Test
    @DisplayName("relative paths resolve against the caller, and .. folds")
    void relativeResolvesAgainstCaller() throws Exception {
        Path a = Files.createDirectories(tmp.resolve("a/b"));
        Path c = Files.createDirectories(tmp.resolve("c"));
        run(a.toFile(), "--aim", "../../c");
        assertThat(aimed).containsExactly(c.toFile());
    }

    @Test
    @DisplayName("a trailing separator-dot folds away: Explorer's verb passes %V\\. so a drive root never ends in \\\"")
    void trailingDotFolds() throws Exception {
        Path c = Files.createDirectories(tmp.resolve("c"));
        run(new File("/"), "--aim", c.toAbsolutePath() + File.separator + ".");
        assertThat(aimed).containsExactly(c.toFile());
        File root = c.toAbsolutePath().getRoot().toFile();
        assertThat(AimOption.resolve(null, root.getPath() + File.separator + "."))
                .as("a root plus separator-dot is still the root").isEqualTo(root);
    }

    @Test
    @DisplayName("an absolute path ignores the caller's directory")
    void absoluteWins() throws Exception {
        Path c = Files.createDirectories(tmp.resolve("c"));
        run(new File("/"), "--aim", c.toString());
        assertThat(aimed).containsExactly(c.toFile());
    }

    @Test
    @DisplayName("a file, a missing path or a blank argument is refused with exit 2 and a sentence naming it")
    void nonFoldersAreRefusedOutLoud() throws Exception {
        Path file = Files.writeString(tmp.resolve("index.html"), "<p>");
        for (String bad : new String[] {"index.html", "nope", " "}) {
            assertThatThrownBy(() -> run(tmp.toFile(), "--aim", bad))
                    .isInstanceOf(CommandException.class)
                    .satisfies(e -> {
                        CommandException ce = (CommandException) e;
                        assertThat(ce.getExitCode()).isEqualTo(AimOption.EXIT_NOT_A_DIRECTORY);
                        assertThat(ce.getLocalizedMessage()).contains("--aim").contains(bad);
                    });
        }
        assertThat(file).exists();
        assertThat(aimed).as("nothing was aimed").isEmpty();
    }

    @Test
    @DisplayName("the option is registered with the platform, so a forwarded command line reaches it")
    void registeredAsAnOptionProcessor() throws Exception {
        String services = new String(AimOption.class.getClassLoader()
                .getResourceAsStream("META-INF/services/org.netbeans.spi.sendopts.OptionProcessor")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(services).contains(AimOption.class.getName());
    }
}
