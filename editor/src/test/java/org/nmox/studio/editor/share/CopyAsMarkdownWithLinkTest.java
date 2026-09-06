package org.nmox.studio.editor.share;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CopyAsMarkdownWithLinkTest {

    @Test
    @DisplayName("a selection's line range is 1-based inclusive; a selection ending on a newline does not claim the next line")
    void lineRange() throws Exception {
        PlainDocument doc = new PlainDocument();
        doc.insertString(0, "a\nbb\nccc\ndddd\n", null);
        assertThat(CopyAsMarkdown.lineRange(doc, 0, 0)).containsExactly(0, 0);           // nothing selected: whole file
        assertThat(CopyAsMarkdown.lineRange(doc, 2, 4)).containsExactly(2, 2);           // "bb"
        assertThat(CopyAsMarkdown.lineRange(doc, 2, 5)).containsExactly(2, 2);           // "bb\n" — the newline is line 2's
        assertThat(CopyAsMarkdown.lineRange(doc, 2, 6)).containsExactly(2, 3);           // "bb\nc"
        assertThat(CopyAsMarkdown.lineRange(doc, 0, doc.getLength())).containsExactly(1, 4);
    }

    @Test
    @DisplayName("the resolver walks root → origin → GitHub → HEAD and links the repo-relative path with forward slashes")
    void resolvesALink(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.writeString(repo.resolve(".git/config"), "[remote \"origin\"]\n\turl = git@github.com:NMOX/demo.git\n");
        Path file = repo.resolve("src/App.jsx");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x\n");
        CopyAsMarkdownWithLinkAction.Outcome out = CopyAsMarkdownWithLinkAction.resolve(file.toFile(), 3, 14);
        assertThat(out.refusal()).isNull();
        assertThat(out.url()).isEqualTo("https://github.com/NMOX/demo/blob/main/src/App.jsx#L3-L14");
        assertThat(out.relPath()).isEqualTo("src/App.jsx");
        assertThat(out.slug()).isEqualTo("NMOX/demo");
        assertThat(out.ref()).isEqualTo("main");
    }

    @Test
    @DisplayName("every rung that cannot vouch for a link refuses with its own reason")
    void refusalsSpeak(@TempDir Path tmp) throws Exception {
        Path loose = tmp.resolve("loose/f.js");
        Files.createDirectories(loose.getParent());
        Files.writeString(loose, "x");
        assertThat(CopyAsMarkdownWithLinkAction.resolve(loose.toFile(), 1, 1).refusal()).contains("not inside a git repository");

        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Path file = repo.resolve("f.js");
        Files.writeString(file, "x");
        Files.writeString(repo.resolve(".git/config"), "[core]\n");
        assertThat(CopyAsMarkdownWithLinkAction.resolve(file.toFile(), 1, 1).refusal()).contains("no origin remote");

        Files.writeString(repo.resolve(".git/config"), "[remote \"origin\"]\n\turl = git@gitlab.com:o/r.git\n");
        assertThat(CopyAsMarkdownWithLinkAction.resolve(file.toFile(), 1, 1).refusal())
                .contains("not a GitHub remote").contains("gitlab.com");

        Files.writeString(repo.resolve(".git/config"), "[remote \"origin\"]\n\turl = git@github.com:o/r.git\n");
        Files.writeString(repo.resolve(".git/HEAD"), "garbage\n");
        assertThat(CopyAsMarkdownWithLinkAction.resolve(file.toFile(), 1, 1).refusal()).contains("HEAD could not be read");
    }

    @Test
    @DisplayName("a detached HEAD links by its short sha — the only ref a reader can open")
    void detachedHeadLinksBySha(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve(".git/HEAD"), "0123456789abcdef0123456789abcdef01234567\n");
        Files.writeString(repo.resolve(".git/config"), "[remote \"origin\"]\n\turl = https://github.com/o/r\n");
        Path file = repo.resolve("f.js");
        Files.writeString(file, "x");
        assertThat(CopyAsMarkdownWithLinkAction.resolve(file.toFile(), 0, 0).url())
                .isEqualTo("https://github.com/o/r/blob/0123456/f.js");
    }

    @Test
    @DisplayName("the git reads ride the RP and the clipboard write follows them; the action is on the popup AND the Edit menu")
    void wiring() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/editor/share/CopyAsMarkdownWithLinkAction.java"));
        assertThat(src).contains("path = \"Editors/Popup\"").contains("path = \"Menu/Edit\"").contains("RP.post(");
        assertThat(src.indexOf("resolve(file")).isGreaterThan(src.indexOf("RP.post("));
        assertThat(src.indexOf("setContents(")).isGreaterThan(src.indexOf("resolve(file"));
        assertThat(src).doesNotContain("ProcessBuilder").doesNotContain("Runtime.getRuntime");
    }
}
