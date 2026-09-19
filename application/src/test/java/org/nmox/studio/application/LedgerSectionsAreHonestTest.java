package org.nmox.studio.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A section of the debt ledger headed "Open" contains something open.
 *
 * <p>Eleven of them did not. They held twenty-four items between them, every
 * one struck through, CLOSED or DELIVERED, under headings that still read
 * <i>"Open — deferred deliberately, with reasons"</i>. A reader scanning the
 * headings for what is still owed was misled about a third of the time, and
 * the file whose whole job is to say what is outstanding was the one lying
 * about it.
 *
 * <p>This is the project's own recurring class, and this is its third round:
 * v2.153.1 corrected five entries that read as open work, v2.183.0 corrected
 * one more, and both times the fix was the entries rather than the reason
 * entries could go stale. Nothing read the ledger's status — the only test
 * that opened the file at all read it for a bundle claim. So the fix this time
 * is a reader.
 *
 * <p>It deliberately checks the weaker of the two directions. A section headed
 * Open must hold at least one open item, because that is the claim a reader
 * acts on; a section headed Closed holding an open item is untidy rather than
 * misleading, and pinning both would fight the way items are actually marked.
 */
class LedgerSectionsAreHonestTest {

    private static final Path LEDGER = Path.of("../docs/engineering/tech-debt.md");

    /** How an item's own heading says it is finished. */
    private static boolean closed(String heading) {
        String upper = heading.toUpperCase(java.util.Locale.ROOT);
        return heading.contains("~~") || upper.contains("CLOSED") || upper.contains("DELIVERED");
    }

    @Test
    @DisplayName("the ledger is where it is expected, so the law below is not vacuously green")
    void theLedgerIsReadable() throws Exception {
        assertThat(Files.isRegularFile(LEDGER)).as("%s", LEDGER.toAbsolutePath()).isTrue();
        assertThat(Files.readString(LEDGER, StandardCharsets.UTF_8))
                .contains("## Open")
                .as("a ledger with no Open section would make this gate meaningless");
    }

    @Test
    @DisplayName("every section headed Open holds at least one item that is not closed")
    void openSectionsHoldOpenWork() throws Exception {
        List<String> lines = List.of(Files.readString(LEDGER, StandardCharsets.UTF_8)
                .replace("\r\n", "\n").split("\n", -1));

        List<String> liars = new ArrayList<>();
        String section = null;
        int sectionLine = 0;
        int items = 0;
        int open = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("## ")) {
                if (section != null && items > 0 && open == 0) {
                    liars.add("L" + sectionLine + " " + section.substring(0, Math.min(72, section.length()))
                            + " — " + items + " items, none open");
                }
                section = line.startsWith("## Open") ? line : null;
                sectionLine = i + 1;
                items = 0;
                open = 0;
            } else if (line.startsWith("### ") && section != null) {
                items++;
                if (!closed(line)) {
                    open++;
                }
            }
        }
        if (section != null && items > 0 && open == 0) {
            liars.add("L" + sectionLine + " " + section + " — " + items + " items, none open");
        }

        assertThat(liars)
                .as("these headings promise outstanding work and hold none; retitle them Closed")
                .isEmpty();
    }
}
