package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every search surface goes through the product's one matcher (ledger 95,
 * v2.106.0).
 *
 * <p>v1.215.0 measured that 24 of 49 ordinary search terms returned
 * NOTHING, replaced {@code haystack.toLowerCase().contains(query)} with
 * {@code SearchTerms} everywhere, and left no way to notice a surface that
 * arrived later. Two did: the Task Board's Quick Search provider (v1.323.0)
 * and the project switcher's filter kept the raw shape, so a phrase in the
 * wrong order missed and — once thirteen languages of names existed — a card
 * or a directory called {@code Ćwiczenie} could not be reached by typing
 * {@code cwiczenie}.
 *
 * <p>The rule: a case-folded {@code contains} over text a person typed is a
 * search, and searches live in {@code SearchTerms}. The exceptions are
 * literal by design and named below — a filter the user wrote themselves,
 * and a text search an agent asked for, both of which mean exactly what they
 * say and must not be widened.
 */
class SearchSurfaceLedgerTest {

    /**
     * {@code x.toLowerCase(…).contains(y)} — case-folded text asked whether
     * it holds a fragment.
     *
     * <p>Deliberately NOT the mirror form {@code set.contains(x.toLowerCase())}:
     * that is a membership test on a collection, which is not a search at
     * all. The first cut caught it and flagged a duplicate-name check in
     * Contract Studio's network dialog — a census that reports things it
     * cannot judge trains people to ignore it.
     */
    private static final Pattern RAW_MATCH = Pattern.compile(
            "toLowerCase\\([^)]*\\)\\s*\\.contains\\(");

    /** Literal by decision. Each value says why widening it would be wrong. */
    private static final Map<String, String> LITERAL = Map.ofEntries(
            Map.entry("SearchTerms.java",
                    "the matcher itself: its javadoc quotes the shape it replaced"),
            Map.entry("TextFilters.java",
                    "an IRC /filter is a literal the user wrote to hide lines; WeeChat's "
            + "semantics are exact substring, and a filter that hid MORE than it "
            + "was told to would hide messages the user meant to see"),
            Map.entry("TextSearch.java",
                    "the Agent Port's search_text answers an agent that asked for a "
            + "literal; widening it would return matches the caller did not ask "
            + "for and cannot tell apart from the ones it did"),
            Map.entry("DevServerDevice.java",
                    "reading a dev server's own output for \"address already in use\" — "
            + "matching what a program printed, not what a person typed"),
            Map.entry("ProjectStudioTopComponent.java",
                    "finding the platform's Terminal action by its layer file name; a "
            + "registration id is a machine identifier and means itself exactly"),
            Map.entry("HeaderGrader.java",
                    "an HTTP header value checked for a policy token; the wire says "
            + "\"unsafe-url\" or it does not, and a near miss is not a match")
    );

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    @Test
    @DisplayName("no search surface case-folds and contains its way around SearchTerms")
    void everySearchUsesTheMatcher() throws IOException {
        List<String> unclassified = new ArrayList<>();
        int found = 0;
        for (Path p : sources()) {
            String body = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
            String[] lines = body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.startsWith("//") || line.startsWith("*")) {
                    continue;
                }
                if (!RAW_MATCH.matcher(lines[i]).find()) {
                    continue;
                }
                found++;
                String file = p.getFileName().toString();
                if (!LITERAL.containsKey(file)) {
                    unclassified.add(file + ":" + (i + 1)
                            + " — a search that does not use the product's matcher");
                }
            }
        }
        assertThat(found).as("the census should still find the blessed literal surfaces")
                .isGreaterThanOrEqualTo(LITERAL.size() - 1);
        assertThat(unclassified)
                .as("route it through SearchTerms, or name it literal here with its reason")
                .isEmpty();
    }

    @Test
    @DisplayName("every literal blessing names a file that exists and gives a reason")
    void blessingsAreRealAndReasoned() throws IOException {
        List<String> files = sources().stream().map(p -> p.getFileName().toString()).toList();
        List<String> ghosts = new ArrayList<>();
        List<String> thin = new ArrayList<>();
        for (Map.Entry<String, String> e : LITERAL.entrySet()) {
            if (!files.contains(e.getKey())) {
                ghosts.add(e.getKey());
            }
            if (e.getValue().length() < 40) {
                thin.add(e.getKey());
            }
        }
        assertThat(ghosts).as("blessed literal but no longer in the product").isEmpty();
        assertThat(thin).as("a blessing without a reason is a guess").isEmpty();
    }

    private static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                out.addAll(files.filter(f -> f.toString().endsWith(".java")).toList());
            }
        }
        return out;
    }
}
