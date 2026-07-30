package org.nmox.studio.ui.browser.devtools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.browser.devtools.SvelteSnapshotParser.Loc;
import org.nmox.studio.ui.browser.devtools.SvelteSnapshotParser.SvelteFile;
import org.nmox.studio.ui.browser.devtools.SvelteSnapshotParser.SvelteTree;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Svelte snapshot parser: the per-file location shape, the honest
 * no-Svelte answer, and hostile-input safety (malformed JSON, missing
 * fields, oversized names and lists all re-capped Java-side).
 */
class SvelteSnapshotParserTest {

    private static final String FIXTURE =
            "{\"files\":[{\"file\":\"src/App.svelte\",\"count\":3,\"locs\":["
            + "{\"line\":5,\"column\":0,\"path\":[1,0]},"
            + "{\"line\":8,\"column\":2,\"path\":[1,0,0]},"
            + "{\"line\":12,\"column\":2,\"path\":[1,0,1]}]},"
            + "{\"file\":\"src/lib/Counter.svelte\",\"count\":1,\"locs\":["
            + "{\"line\":4,\"column\":0,\"path\":[1,0,1,0]}]}],\"total\":4}";

    @Test
    @DisplayName("parses files, counts, and line:column locations with DOM paths")
    void parsesFixture() {
        SvelteTree t = SvelteSnapshotParser.parse(FIXTURE);
        assertThat(t.empty()).isFalse();
        assertThat(t.total).isEqualTo(4);
        assertThat(t.files).hasSize(2);

        SvelteFile app = t.files.get(0);
        assertThat(app.file).isEqualTo("src/App.svelte");
        assertThat(app.basename()).isEqualTo("App.svelte");
        assertThat(app.count).isEqualTo(3);
        assertThat(app.locs).hasSize(3);

        Loc first = app.locs.get(0);
        assertThat(first.line).isEqualTo(5);
        assertThat(first.column).isZero();
        assertThat(first.path).containsExactly(1, 0);
        assertThat(first.toString()).isEqualTo("line 5:0");

        assertThat(t.files.get(1).basename()).isEqualTo("Counter.svelte");
    }

    @Test
    @DisplayName("no Svelte on the page is a first-class empty answer")
    void noSvelteIsEmpty() {
        SvelteTree t = SvelteSnapshotParser.parse("{\"files\":[],\"total\":0}");
        assertThat(t.empty()).isTrue();
        assertThat(t.total).isZero();
    }

    @Test
    @DisplayName("malformed input is 'no Svelte', never a throw")
    void malformedIsEmpty() {
        for (String bad : new String[]{null, "", "nope", "[]", "{\"files\":"}) {
            assertThat(SvelteSnapshotParser.parse(bad).empty()).as(bad).isTrue();
        }
    }

    @Test
    @DisplayName("missing fields default; a blank file name is honestly unknown")
    void missingFieldsDefault() {
        SvelteTree t = SvelteSnapshotParser.parse(
                "{\"files\":[{\"x\":1},{\"file\":\"  \"}],\"total\":-5}");
        assertThat(t.files).hasSize(2);
        assertThat(t.files.get(0).file).isEqualTo("(unknown)");
        assertThat(t.files.get(1).file).isEqualTo("(unknown)");
        assertThat(t.files.get(0).locs).isEmpty();
        assertThat(t.total).isZero(); // a hostile negative total clamps
    }

    @Test
    @DisplayName("hostile oversized names, loc lists, and file lists are re-capped Java-side")
    void hostileSizesCapped() {
        StringBuilder locs = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            if (i > 0) {
                locs.append(',');
            }
            locs.append("{\"line\":").append(i).append(",\"column\":0,\"path\":[]}");
        }
        String big = "{\"files\":[{\"file\":\"" + "F".repeat(2000) + "\",\"count\":400,"
                + "\"locs\":[" + locs + "]}],\"total\":400}";
        SvelteFile f = SvelteSnapshotParser.parse(big).files.get(0);
        assertThat(f.file).hasSize(500);
        assertThat(f.locs).hasSize(200);            // the script cap, re-imposed
        assertThat(f.count).isEqualTo(400);          // the honest total survives

        // a flood of files stops at 500
        StringBuilder files = new StringBuilder("{\"files\":[");
        for (int i = 0; i < 600; i++) {
            if (i > 0) {
                files.append(',');
            }
            files.append("{\"file\":\"f").append(i).append("\",\"count\":1,\"locs\":[]}");
        }
        files.append("],\"total\":600}");
        assertThat(SvelteSnapshotParser.parse(files.toString()).files).hasSize(500);
    }

    @Test
    @DisplayName("negative lines/columns clamp and non-numeric path entries are skipped")
    void hostileValuesClamped() {
        SvelteTree t = SvelteSnapshotParser.parse(
                "{\"files\":[{\"file\":\"a.svelte\",\"count\":1,\"locs\":["
                + "{\"line\":-7,\"column\":-1,\"path\":[0,\"x\",2]}]}],\"total\":1}");
        Loc loc = t.files.get(0).locs.get(0);
        assertThat(loc.line).isZero();
        assertThat(loc.column).isZero();
        assertThat(loc.path).containsExactly(0, 2);
    }

    @Test
    @DisplayName("count never reads lower than the locations actually present")
    void countAtLeastLocs() {
        // a page lying count:0 with two real locs still reports 2
        SvelteTree t = SvelteSnapshotParser.parse(
                "{\"files\":[{\"file\":\"a.svelte\",\"count\":0,\"locs\":["
                + "{\"line\":1,\"column\":0,\"path\":[]},"
                + "{\"line\":2,\"column\":0,\"path\":[]}]}],\"total\":2}");
        assertThat(t.files.get(0).count).isEqualTo(2);
        assertThat(t.files.get(0).toString()).isEqualTo("a.svelte (2)");
    }

    @Test
    @DisplayName("Windows-style paths still yield a basename for the tree row")
    void windowsBasename() {
        SvelteTree t = SvelteSnapshotParser.parse(
                "{\"files\":[{\"file\":\"src\\\\lib\\\\Card.svelte\",\"count\":1,"
                + "\"locs\":[]}],\"total\":1}");
        assertThat(t.files.get(0).basename()).isEqualTo("Card.svelte");
    }
}
