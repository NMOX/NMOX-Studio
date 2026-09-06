package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checkpoint laws: parsing skips malformed entries WHOLE with a
 * note (a partially-honored checkpoint lies to the learner), command
 * tools are bare names (the device-file law — a drop-in catalog must
 * not point Check at an arbitrary binary), file checks verify
 * contains AND absent (absent is how "you changed it" is checkable),
 * and paths ride the same containment as sample files.
 */
class CheckpointsTest {

    private static List<Checkpoints.Checkpoint> parse(String json, List<String> notes) {
        return Checkpoints.parse(new JSONArray(json), notes);
    }

    @Test
    @DisplayName("malformed entries skip whole with a note; good ones survive around them")
    void skipWithNote() {
        List<String> notes = new ArrayList<>();
        List<Checkpoints.Checkpoint> cs = parse("""
            [ {"label":"ok file","file":{"path":"a.txt","contains":"x"}},
              {"file":{"path":"a.txt","contains":"x"}},
              {"label":"pathless","file":{"contains":"x"}},
              {"label":"both","file":{"path":"a","contains":"x"},"command":["go"]},
              {"label":"ok cmd","command":["go","test","."]} ]
            """, notes);
        assertThat(cs).hasSize(2);
        assertThat(cs.get(0).label()).isEqualTo("ok file");
        assertThat(cs.get(1).command()).containsExactly("go", "test", ".");
        assertThat(notes).hasSize(3);
    }

    @Test
    @DisplayName("a command tool with a path separator is refused — the device-file law")
    void bareToolNameOnly() {
        List<String> notes = new ArrayList<>();
        List<Checkpoints.Checkpoint> cs = parse("""
            [ {"label":"sneaky","command":["../../usr/bin/evil","x"]},
              {"label":"sneaky2","command":["C:\\\\evil.exe"]} ]
            """, notes);
        assertThat(cs).isEmpty();
        assertThat(notes).hasSize(2);
    }

    @Test
    @DisplayName("file checks: contains passes, absent catches the unchanged sample")
    void fileKind(@TempDir Path work) throws IOException {
        File dir = work.toFile();
        Files.writeString(new File(dir, "index.html").toPath(),
                "<h1>Hello, web!</h1>");
        List<String> notes = new ArrayList<>();
        Checkpoints.Checkpoint changed = parse("""
            [ {"label":"changed","hint":"edit the h1",
               "file":{"path":"index.html","contains":"<h1>","absent":"<h1>Hello, web!</h1>"}} ]
            """, notes).get(0);
        Checkpoints.Result before = Checkpoints.run(dir, changed, null);
        assertThat(before.passed()).as("untouched sample fails the absent check").isFalse();
        assertThat(before.detail()).contains("edit the h1");

        Files.writeString(new File(dir, "index.html").toPath(),
                "<h1>Meridian Coffee</h1>");
        assertThat(Checkpoints.run(dir, changed, null).passed()).isTrue();
    }

    @Test
    @DisplayName("atLeast counts occurrences: a seed that already has two </li> fails a third-item task")
    void atLeastCountsOccurrences(@TempDir Path work) throws IOException {
        List<String> notes = new ArrayList<>();
        List<Checkpoints.Checkpoint> cps = parse("""
            [{"label": "You added a third list item", "hint": "add one",
              "file": {"path": "index.html", "contains": "</li>", "atLeast": 3}},
             {"label": "bad count", "file": {"path": "index.html", "contains": "x", "atLeast": -1}},
             {"label": "count without contains", "file": {"path": "index.html", "absent": "x", "atLeast": 2}}]
            """, notes);
        assertThat(cps).hasSize(1);
        assertThat(notes).hasSize(2).allMatch(n -> n.contains("atLeast needs a contains"));
        File dir = work.toFile();
        Files.writeString(work.resolve("index.html"), "<ul><li>a</li><li>b</li></ul>");
        assertThat(Checkpoints.run(dir, cps.get(0), null).passed())
                .as("two items: the seed, not the work").isFalse();
        Files.writeString(work.resolve("index.html"), "<ul><li>a</li><li>b</li><li>c</li></ul>");
        assertThat(Checkpoints.run(dir, cps.get(0), null).passed())
                .as("three items: the work").isTrue();
        assertThat(Checkpoints.occurrences("aaaa", "aa")).as("non-overlapping").isEqualTo(2);
    }

    @Test
    @DisplayName("a traversal path never reads outside the space; a missing file is a failed check")
    void containment(@TempDir Path work) throws IOException {
        File dir = new File(work.toFile(), "space");
        assertThat(dir.mkdirs()).isTrue();
        Files.writeString(work.resolve("SECRET.txt"), "outside");
        List<String> notes = new ArrayList<>();
        Checkpoints.Checkpoint sneaky = parse("""
            [ {"label":"sneaky","file":{"path":"../SECRET.txt","contains":"outside"}} ]
            """, notes).get(0);
        assertThat(Checkpoints.run(dir, sneaky, null).passed()).isFalse();
    }

    @Test
    @DisplayName("command runs: exit 0 passes, nonzero and missing expect fail with the hint")
    void commandKind(@TempDir Path work) {
        List<String> notes = new ArrayList<>();
        Checkpoints.Checkpoint c = parse("""
            [ {"label":"tests","hint":"read the failure","command":["go","test"],"expect":"ok"} ]
            """, notes).get(0);
        Checkpoints.Runner pass = (d, argv) -> new Checkpoints.Runner.Run(0, "ok  0.1s");
        Checkpoints.Runner failExit = (d, argv) -> new Checkpoints.Runner.Run(2, "FAIL");
        Checkpoints.Runner failExpect = (d, argv) -> new Checkpoints.Runner.Run(0, "nope");
        assertThat(Checkpoints.run(work.toFile(), c, pass).passed()).isTrue();
        Checkpoints.Result r1 = Checkpoints.run(work.toFile(), c, failExit);
        assertThat(r1.passed()).isFalse();
        assertThat(r1.detail()).contains("exit 2").contains("read the failure");
        assertThat(Checkpoints.run(work.toFile(), c, failExpect).passed()).isFalse();
    }
}
