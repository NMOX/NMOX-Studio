package org.nmox.studio.ui.browser.devtools;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.browser.devtools.StorageSnapshotParser.Row;

import static org.assertj.core.api.Assertions.assertThat;

/** The Storage snapshot parser: three areas, cookie splitting, caps. */
class StorageSnapshotParserTest {

    @Test
    @DisplayName("parses all three areas into rows")
    void parsesAreas() {
        List<Row> rows = StorageSnapshotParser.parse(
                "{\"l\":[{\"k\":\"theme\",\"v\":\"dark\"}],"
                + "\"s\":[{\"k\":\"tab\",\"v\":\"2\"}],"
                + "\"c\":[\"sid=abc123\",\"flagonly\"]}");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0)).isEqualTo(new Row("localStorage", "theme", "dark"));
        assertThat(rows.get(1)).isEqualTo(new Row("sessionStorage", "tab", "2"));
        assertThat(rows.get(2)).isEqualTo(new Row("cookie", "sid", "abc123"));
        assertThat(rows.get(3)).isEqualTo(new Row("cookie", "flagonly", ""));
    }

    @Test
    @DisplayName("malformed input is an empty list, never a throw")
    void malformedIsEmpty() {
        for (String bad : new String[]{null, "", "junk", "[]", "{\"l\":"}) {
            assertThat(StorageSnapshotParser.parse(bad)).isEmpty();
        }
    }

    @Test
    @DisplayName("hostile oversized values are re-capped at 500 chars")
    void hostileValuesCapped() {
        List<Row> rows = StorageSnapshotParser.parse(
                "{\"l\":[{\"k\":\"" + "k".repeat(2000) + "\",\"v\":\"" + "v".repeat(2000) + "\"}]}");
        assertThat(rows.get(0).key()).hasSize(500);
        assertThat(rows.get(0).value()).hasSize(500);
    }

    @Test
    @DisplayName("missing areas simply contribute nothing")
    void missingAreas() {
        assertThat(StorageSnapshotParser.parse("{\"c\":[\"a=b\"]}"))
                .containsExactly(new Row("cookie", "a", "b"));
    }
}
