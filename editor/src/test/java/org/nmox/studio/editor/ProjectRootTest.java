package org.nmox.studio.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The project-root rule, pinned where every editor surface reads it.
 * The marker triple and the depth bound used to live in twelve private
 * copies; these are the properties those copies all had and none of
 * them asserted.
 */
class ProjectRootTest {

    @Test
    @DisplayName("each of the three markers names a root, and no other file does")
    void theMarkerTriple(@TempDir Path tmp) throws Exception {
        for (String marker : new String[]{"package.json", "angular.json"}) {
            Path root = Files.createDirectories(tmp.resolve(marker.replace('.', '_')));
            Path nested = Files.createDirectories(root.resolve("src/app"));
            Files.writeString(root.resolve(marker), "{}");
            assertThat(ProjectRoot.above(nested.toFile()))
                    .as(marker + " names the root")
                    .isEqualTo(root.toFile());
        }
        // .git is checked with exists(), not isFile(): a worktree's .git
        // is a FILE, an ordinary clone's is a DIRECTORY, and both are roots
        Path gitDir = Files.createDirectories(tmp.resolve("clone"));
        Path nestedInClone = Files.createDirectories(gitDir.resolve("src/app"));
        Files.createDirectory(gitDir.resolve(".git"));
        assertThat(ProjectRoot.above(nestedInClone.toFile())).isEqualTo(gitDir.toFile());

        Path worktree = Files.createDirectories(tmp.resolve("worktree"));
        Path nestedInWorktree = Files.createDirectories(worktree.resolve("src"));
        Files.writeString(worktree.resolve(".git"), "gitdir: /elsewhere\n");
        assertThat(ProjectRoot.above(nestedInWorktree.toFile())).isEqualTo(worktree.toFile());

        // a marker-shaped name that is NOT one of the three does not count
        Path stranger = Files.createDirectories(tmp.resolve("stranger"));
        Path nestedStranger = Files.createDirectories(stranger.resolve("src"));
        Files.writeString(stranger.resolve("deno.json"), "{}");
        assertThat(ProjectRoot.above(nestedStranger.toFile()))
                .as("no marker: the file's own folder, never a guess higher up")
                .isEqualTo(nestedStranger.toFile());
    }

    @Test
    @DisplayName("the climb stops at the depth bound — a file outside a project never walks to /")
    void theDepthBound(@TempDir Path tmp) throws Exception {
        // marker six levels above the start: one past the last directory
        // the default depth inspects (up = 0..5)
        Path root = Files.createDirectories(tmp.resolve("root"));
        Files.writeString(root.resolve("package.json"), "{}");
        Path deep = Files.createDirectories(root.resolve("a/b/c/d/e/f"));

        assertThat(ProjectRoot.above(deep.toFile()))
                .as("six levels up is out of reach at the default depth")
                .isEqualTo(deep.toFile());
        assertThat(ProjectRoot.above(deep.getParent().toFile()))
                .as("five levels up is the last one reached")
                .isEqualTo(root.toFile());
        assertThat(ProjectRoot.above(deep.toFile(), 8))
                .as("Angular's deeper climb reaches it")
                .isEqualTo(root.toFile());
        assertThat(ProjectRoot.DEFAULT_DEPTH).isEqualTo(6);
    }

    @Test
    @DisplayName("a directory that IS the root answers itself; a null file answers null")
    void edges(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("package.json"), "{}");
        assertThat(ProjectRoot.above(tmp.toFile())).isEqualTo(tmp.toFile());
        assertThat(ProjectRoot.above(null)).isNull();
        assertThat(ProjectRoot.of((org.openide.filesystems.FileObject) null)).isNull();
    }

    @Test
    @DisplayName("a bounded marker walk lives in ProjectRoot alone — no thirteenth copy")
    void oneHome() throws Exception {
        Path main = Path.of("src/main/java");
        assertThat(Files.isDirectory(main)).isTrue();

        StringBuilder copies = new StringBuilder();
        try (var files = Files.walk(main)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (p.getFileName().toString().equals("ProjectRoot.java")) {
                    continue;
                }
                String where = markerWalkIn(stripComments(Files.readString(p)));
                if (where != null) {
                    copies.append("\n  ").append(p).append(' ').append(where);
                }
            }
        }
        assertThat(copies.toString())
                .as("the marker triple IS the definition of a project root for these surfaces;"
                        + " a second home is the v2.131.0 defect even when the copies agree."
                        + " Call ProjectRoot.of/above instead. Found:" + copies)
                .isEmpty();
    }

    /**
     * The defect's own shape, derived rather than listed: code that
     * climbs with {@code getParentFile()} and, within the same stretch
     * of source, decides on all three markers. Deliberately NOT a match
     * on the literals alone — {@code LanguageServers.rootAbove} names
     * two of them for a different rule (a named marker, with .git as a
     * boundary) and is not a copy of this one.
     */
    private static String markerWalkIn(String src) {
        String[] lines = src.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].contains("getParentFile()")) {
                continue;
            }
            String window = String.join("\n",
                    java.util.Arrays.copyOfRange(lines,
                            Math.max(0, i - 8), Math.min(lines.length, i + 9)));
            if (window.contains("\"package.json\"")
                    && window.contains("\"angular.json\"")
                    && window.contains("\".git\"")) {
                return "walks parents on the marker triple at line " + (i + 1);
            }
        }
        return null;
    }

    /** A gate that matches a literal must read the CODE, not a comment naming it. */
    private static String stripComments(String src) {
        StringBuilder sb = new StringBuilder();
        boolean inBlock = false;
        for (String line : src.split("\n", -1)) {
            String t = line.strip();
            if (inBlock) {
                if (t.contains("*/")) {
                    inBlock = false;
                }
                sb.append('\n');
                continue;
            }
            if (t.startsWith("/*")) {
                inBlock = !t.contains("*/");
                sb.append('\n');
                continue;
            }
            sb.append(t.startsWith("//") ? "" : line).append('\n');
        }
        return sb.toString();
    }
}
