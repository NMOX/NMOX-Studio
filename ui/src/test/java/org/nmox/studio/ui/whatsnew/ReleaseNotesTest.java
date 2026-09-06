package org.nmox.studio.ui.whatsnew;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.ui.whatsnew.ReleaseNotes.Decision;
import org.nmox.studio.ui.whatsnew.ReleaseNotes.Entry;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseNotesTest {

    private static final String LOG = String.join("\n",
            "# Changelog", "", "prose before the first entry", "",
            "## [2.64.0] - 2026-09-03", "", "The PM release.", "- one", "",
            "## [2.63.0] - 2026-09-02", "", "The senior-RCP pass.", "",
            "## [2.62.2] - 2026-09-02", "deps.", "",
            "## [2.62.1] - 2026-09-02", "docs.", "");

    @Test
    @DisplayName("Keep-a-Changelog headings split entries; prose before the first heading is ignored")
    void parse() {
        List<Entry> e = ReleaseNotes.parse(LOG);
        assertThat(e).extracting(Entry::version).containsExactly("2.64.0", "2.63.0", "2.62.2", "2.62.1");
        assertThat(e.get(0).date()).isEqualTo("2026-09-03");
        assertThat(e.get(0).body()).isEqualTo("The PM release.\n- one");
        assertThat(ReleaseNotes.head(e).version()).isEqualTo("2.64.0");
        assertThat(ReleaseNotes.entryFor(e, "2.62.2").body()).isEqualTo("deps.");
        assertThat(ReleaseNotes.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("since() shows what the install has not seen — newest first, never beyond the running version")
    void since() {
        List<Entry> e = ReleaseNotes.parse(LOG);
        assertThat(ReleaseNotes.since(e, "2.62.1", "2.64.0")).extracting(Entry::version)
                .containsExactly("2.64.0", "2.63.0", "2.62.2");
        // an install running an OLDER build than the changelog head sees only up to itself
        assertThat(ReleaseNotes.since(e, "2.62.1", "2.63.0")).extracting(Entry::version)
                .containsExactly("2.63.0", "2.62.2");
        assertThat(ReleaseNotes.since(e, null, "2.63.0")).hasSize(3);
        assertThat(ReleaseNotes.since(e, "2.64.0", "2.64.0")).isEmpty();
    }

    @Test
    @DisplayName("The cap hides the oldest unseen entries and counts them honestly")
    void cap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 30; i >= 1; i--) {
            sb.append("## [2.").append(i).append(".0] - d\nbody ").append(i).append("\n\n");
        }
        List<Entry> e = ReleaseNotes.parse(sb.toString());
        assertThat(ReleaseNotes.since(e, "2.1.0", "2.30.0")).hasSize(ReleaseNotes.MAX_ENTRIES);
        assertThat(ReleaseNotes.omitted(e, "2.1.0", "2.30.0")).isEqualTo(29 - ReleaseNotes.MAX_ENTRIES);
        String text = ReleaseNotes.render(ReleaseNotes.since(e, "2.1.0", "2.30.0"),
                ReleaseNotes.omitted(e, "2.1.0", "2.30.0"));
        assertThat(text).startsWith("2.30.0 — d\nbody 30").contains("… and 19 earlier releases not shown");
    }

    @Test
    @DisplayName("First boot: dev builds show nothing, a fresh install records silently, an updated install shows")
    void decide() {
        assertThat(ReleaseNotes.decide("2.64.0", "2.63.0", false)).isEqualTo(Decision.NONE);
        assertThat(ReleaseNotes.decide("2.64.0", null, true)).isEqualTo(Decision.RECORD_ONLY);
        assertThat(ReleaseNotes.decide("2.64.0", "", true)).isEqualTo(Decision.RECORD_ONLY);
        assertThat(ReleaseNotes.decide("2.64.0", "2.64.0", true)).isEqualTo(Decision.NONE);
        assertThat(ReleaseNotes.decide("2.64.0", "2.63.0", true)).isEqualTo(Decision.SHOW);
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("the Markdown rendering keeps the Keep-a-Changelog heading and the body as written, and notes what was omitted")
    void rendersMarkdown() {
        java.util.List<ReleaseNotes.Entry> es = java.util.List.of(
                new ReleaseNotes.Entry("2.87.0", "2026-09-06", "Lead.\n\n1. **A** — a.\n"),
                new ReleaseNotes.Entry("2.86.0", "", "Older.\n"));
        String md = ReleaseNotes.renderMarkdown(es, 3);
        org.assertj.core.api.Assertions.assertThat(md).isEqualTo(
                "## [2.87.0] - 2026-09-06\n\nLead.\n\n1. **A** — a.\n\n\n## [2.86.0]\n\nOlder.\n\n_…and 3 earlier releases not shown._\n");
        org.assertj.core.api.Assertions.assertThat(ReleaseNotes.renderMarkdown(es.subList(0, 1), 0)).doesNotContain("not shown");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("the dialog offers Copy as Markdown only when entries are shown, and the copy is the Markdown rendering")
    void copyOptionWiring() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/org/nmox/studio/ui/whatsnew/WhatsNew.java"));
        org.assertj.core.api.Assertions.assertThat(src).contains("ReleaseNotes.renderMarkdown(shown, omitted)")
                .contains("markdown == null ? new Object[]{github, close} : new Object[]{copy, github, close}")
                .contains("new java.awt.datatransfer.StringSelection(markdown)");
    }
}
