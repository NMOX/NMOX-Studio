package org.nmox.studio.editor.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tools ▸ Check Markdown Links… (3.2.0): the rules, each on a real project
 * folder, read the way GitHub renders it.
 */
class MarkdownLinksTest {

    @TempDir
    Path root;

    private Path write(String name, String text) throws IOException {
        Path p = root.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
        return p;
    }

    private MarkdownLinks.Report check(Path... docs) {
        return MarkdownLinks.check(root, List.of(docs), p -> {
            try {
                return Files.isRegularFile(p) ? Files.readString(p) : null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    @DisplayName("a link to a file that exists lands; one to a file that does not goes nowhere, on its line")
    void missingFile() throws Exception {
        write("docs/guide.md", "# Guide\n");
        Path readme = write("README.md", "intro\n\nsee [the guide](docs/guide.md)\nand [old](docs/gone.md)\n");
        MarkdownLinks.Report r = check(readme);
        assertThat(r.links()).isEqualTo(2);
        assertThat(r.findings()).containsExactly(
                new MarkdownLinks.Finding(readme, 4, "docs/gone.md", MarkdownLinks.Kind.MISSING_FILE));
        assertThat(r.findings().get(0).error()).isTrue();
    }

    @Test
    @DisplayName("an image that is not there is a dead link too")
    void brokenImage() throws Exception {
        write("logo.png", "png");
        Path readme = write("README.md", "![logo](logo.png)\n![shot](docs/shot.png)\n");
        assertThat(check(readme).findings()).extracting(MarkdownLinks.Finding::target).containsExactly("docs/shot.png");
    }

    @Test
    @DisplayName("#anchors follow GitHub's rule, repeats numbered, code and link text stripped from headings")
    void anchors() throws Exception {
        write("guide.md", "# 2. First launch\n\n## `nmox` from a terminal\n\n## Notes\n\n## Notes\n\n"
                + "## See [the site](https://x)\n\nSetext Title\n============\n\n<a id=\"custom\"></a>\n");
        Path readme = write("README.md", String.join("\n",
                "[a](guide.md#2-first-launch)",
                "[b](guide.md#nmox-from-a-terminal)",
                "[c](guide.md#notes-1)",
                "[d](guide.md#see-the-site)",
                "[e](guide.md#setext-title)",
                "[f](guide.md#custom)",
                "[g](guide.md#2-First-Launch)",
                "[h](guide.md#no-such-heading)",
                "[i](guide.md#notes-2)", ""));
        assertThat(check(readme).findings()).extracting(MarkdownLinks.Finding::target)
                .containsExactly("guide.md#no-such-heading", "guide.md#notes-2");
    }

    @Test
    @DisplayName("an anchor-only link checks the same file")
    void sameFileAnchor() throws Exception {
        Path readme = write("README.md", "# Install\n\n[up](#install) [gone](#usage)\n");
        assertThat(check(readme).findings()).extracting(MarkdownLinks.Finding::target, MarkdownLinks.Finding::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("#usage", MarkdownLinks.Kind.MISSING_HEADING));
    }

    @Test
    @DisplayName("links with a scheme, and anything inside code, are left alone")
    void externalAndCode() throws Exception {
        Path readme = write("README.md", String.join("\n",
                "[site](https://example.com/missing.md) [mail](mailto:a@b.c) [proto](//cdn.x/y.js)",
                "```md",
                "[in a fence](nowhere.md)",
                "```",
                "~~~",
                "[in a tilde fence](nowhere2.md)",
                "~~~",
                "inline `[code](nowhere3.md)` span", ""));
        MarkdownLinks.Report r = check(readme);
        assertThat(r.findings()).isEmpty();
        assertThat(r.links()).isZero();
    }

    @Test
    @DisplayName("reference definitions, angle brackets, titles and escapes are read")
    void forms() throws Exception {
        write("a b.md", "# Hi\n");
        Path readme = write("README.md", String.join("\n",
                "[x](<a b.md> \"title\")",
                "[y](a%20b.md#hi 'title')",
                "[ref]: ./gone.md",
                "  [ref2]: <a b.md#nope>", ""));
        assertThat(check(readme).findings()).extracting(MarkdownLinks.Finding::target, MarkdownLinks.Finding::line)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("./gone.md", 3),
                        org.assertj.core.groups.Tuple.tuple("a b.md#nope", 4));
    }

    @Test
    @DisplayName("a root-relative link is the repository root's; a folder is a link that lands")
    void rootRelativeAndFolders() throws Exception {
        Files.createDirectories(root.resolve("src"));
        Path doc = write("docs/deep/page.md", "[root](/README.md) [src](/src) [gone](/missing)\n");
        write("README.md", "hi\n");
        assertThat(check(doc).findings()).extracting(MarkdownLinks.Finding::target).containsExactly("/missing");
    }

    @Test
    @DisplayName("a link that climbs out of the project is reported and never followed")
    void outsideTheProject() throws Exception {
        Path readme = write("README.md", "[secret](../../etc/passwd.md#x)\n");
        assertThat(check(readme).findings()).extracting(MarkdownLinks.Finding::kind)
                .containsExactly(MarkdownLinks.Kind.OUTSIDE_PROJECT);
        assertThat(check(readme).findings().get(0).error()).isFalse();
    }

    @Test
    @DisplayName("a fragment into a file that is not Markdown is GitHub's line anchor, not a heading")
    void lineAnchorsInCode() throws Exception {
        write("src/app.js", "x");
        Path readme = write("README.md", "[line](src/app.js#L10)\n");
        assertThat(check(readme).findings()).isEmpty();
    }

    @Test
    @DisplayName("the status sentence counts in the user's language, and a clean run says so")
    void sentences() throws Exception {
        write("docs/g.md", "# G\n");
        Path readme = write("README.md", "[a](docs/g.md) [b](docs/x.md) [c](docs/g.md#zz)\n");
        String s = CheckMarkdownLinksAction.sentence(check(readme), true, 0);
        assertThat(s).isEqualTo("Markdown links: 1 file, 3 links, 1 goes nowhere, 1 names a missing heading");
        Path clean = write("CLEAN.md", "[a](docs/g.md)\n");
        assertThat(CheckMarkdownLinksAction.sentence(check(clean), false, 0))
                .isEqualTo("Markdown links: 1 file, 1 link, every link lands, the walk stopped at "
                        + CheckMarkdownLinksAction.MAX_FILES + " files");
        assertThat(CheckMarkdownLinksAction.sentence(check(clean), true, 1))
                .as("a file left out is said, not passed over as 'every link lands'")
                .isEqualTo("Markdown links: 1 file, 1 link, every link lands, 1 file over 2 MiB not read");
    }

    @Test
    @DisplayName("hostile text stays linear: a long heading line and a run of \"](\" cost milliseconds, not minutes")
    void linearOnHostileText() throws Exception {
        // the review's measurements of the first cut: 31 s and 21 s
        String heading = "# a" + " ".repeat(2000) + "b\n";
        Path target = write("t.md", heading.repeat(20));
        Path readme = write("README.md", "[x](t.md#nope)\n" + "](".repeat(20000) + "\n");
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
                () -> check(readme, target));
        assertThat(MarkdownLinks.trimClosing("Title ##  ")).isEqualTo("Title");
        assertThat(MarkdownLinks.trimClosing("C#")).as("a # that is part of a word stays").isEqualTo("C#");
        assertThat(MarkdownLinks.trimClosing("##")).isEmpty();
    }

    @Test
    @DisplayName("a target no file can have is a dead link, not a crash that loses the whole run; a ?query is the same file")
    void impossibleNamesAndQueries() throws Exception {
        write("a.md", "# A\n");
        Path readme = write("README.md", "[nul](docs%00x.md)\n[plain](a.md?plain=1#a)\n[good](a.md)\n");
        MarkdownLinks.Report r = check(readme);
        assertThat(r.links()).isEqualTo(3);
        assertThat(r.findings()).extracting(MarkdownLinks.Finding::target, MarkdownLinks.Finding::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("docs%00x.md", MarkdownLinks.Kind.MISSING_FILE));
    }

    @Test
    @DisplayName("a link inside the project that leads out through a symlink is refused, never read")
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void symlinkOut(@TempDir Path outside) throws Exception {
        Files.writeString(outside.resolve("secret.md"), "# Secret\n");
        Files.createSymbolicLink(root.resolve("elsewhere"), outside);
        Path readme = write("README.md", "[s](elsewhere/secret.md#secret)\n");
        java.util.List<Path> read = new java.util.ArrayList<>();
        MarkdownLinks.Report r = MarkdownLinks.check(root, List.of(readme), p -> {
            read.add(p);
            try {
                return Files.readString(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        assertThat(r.findings()).extracting(MarkdownLinks.Finding::kind)
                .containsExactly(MarkdownLinks.Kind.OUTSIDE_PROJECT);
        assertThat(read).as("only the README was read").containsExactly(readme.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("the walk reads regular files only, never follows a link out, and counts what is too large")
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void theWalk(@TempDir Path outside) throws Exception {
        write("README.md", "hi\n");
        write("docs/guide.md", "hi\n");
        write("node_modules/pkg/README.md", "hi\n");
        write(".github/notes.md", "hi\n");
        Files.writeString(outside.resolve("far.md"), "x");
        Files.createSymbolicLink(root.resolve("docs/out"), outside);
        Files.createSymbolicLink(root.resolve("zero.md"), Path.of("/dev/zero"));
        Path big = root.resolve("BIG.md");
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(big.toFile(), "rw")) {
            f.setLength(CheckMarkdownLinksAction.MAX_BYTES + 1);
        }
        CheckMarkdownLinksAction.Walk w = CheckMarkdownLinksAction.walk(root);
        assertThat(w.docs()).extracting(p -> root.relativize(p).toString().replace('\\', '/'))
                .containsExactlyInAnyOrder("README.md", "docs/guide.md");
        assertThat(w.tooLarge()).isEqualTo(1);
        assertThat(w.capped()).isFalse();
    }
}
