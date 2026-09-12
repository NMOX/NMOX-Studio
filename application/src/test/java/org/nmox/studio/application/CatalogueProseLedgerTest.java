package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every catalogue of words the product ships is classified: translated, or
 * blessed in writing as machine text.
 *
 * <p>Three releases found the same defect in three places, and each one was
 * invisible to every l10n gate for the same reason — the English never
 * passed through a bundle. v2.130.0 found it as a SEED (string literals in
 * two model classes), v2.132.0 as a device SHELF (constructor arguments in
 * an enum), v2.133.0 as a learning CATALOGUE (prose in a data file). Three
 * instances make a class, and a class deserves a census rather than a
 * fourth search.
 *
 * <p>The population is derived, not remembered: every {@code enum} in every
 * module's shipping source whose constants pass a string literal as their
 * first constructor argument. A new one fails this build until somebody
 * writes down which it is — the {@code SpawnSiteTrustLedgerTest} shape
 * (v1.224.0), where enumeration beats recollection.
 *
 * <p>Two verdicts, and the second carries its reason:
 * <ul>
 *   <li>{@code TRANSLATED} — a reader sees these words in their own
 *       language; the entry names the seam that renders them.
 *   <li>{@code MACHINE} — the literal is an id, a filename, a URL or a
 *       product's own name, and translating it would break something a
 *       person types or a parser reads.
 * </ul>
 */
class CatalogueProseLedgerTest {

    private static final Path REPO = Path.of("..");

    private static final List<String> MODULES = List.of(
            "core", "editor", "tools", "rack", "infra", "apiclient",
            "dbstudio", "web3", "project", "ui");

    private static final Pattern ENUM = Pattern.compile("(?m)^\\s*(?:public\\s+|private\\s+)?enum\\s+\\w+");

    /** A constant whose first argument is a string literal: {@code FOO("bar", …}. */
    private static final Pattern CONSTANT =
            Pattern.compile("(?m)^\\s{4,}[A-Z][A-Z_0-9]*\\(\"([^\"]{2,})\"");

    /** The ledger. Key: the file's simple name. Value: the verdict and why. */
    private static final Map<String, String> LEDGER = new LinkedHashMap<>();

    static {
        LEDGER.put("DeviceType.java",
                "MACHINE — the leading literal is the device's type id, which a saved "
                + "patch stores and a JSON drop-in names. The prose beside it is the "
                + "shelf description, TRANSLATED through DeviceText (v2.132.0).");
        LEDGER.put("ProjectInspector.java",
                "MACHINE — manifest filenames (package.json, Cargo.toml). Translating "
                + "one would stop the product recognising a project.");
        LEDGER.put("JavaScriptTokenId.java",
                "MACHINE — lexer token categories, read by the editor's colouring, "
                + "never painted as words.");
        LEDGER.put("ProjectTemplates.java",
                "TRANSLATED through TemplateText, rendered by NewProjectDialog.");
        LEDGER.put("BlockKind.java",
                "TRANSLATED through BlockText, rendered by the Block Studio palette "
                + "and canvas.");
        LEDGER.put("ContractKit.java",
                "TRANSLATED through ChainText, rendered by ContractKitAction's combo.");
        LEDGER.put("DbEngine.java",
                "MACHINE — the engines' own names (MySQL, PostgreSQL). A product name "
                + "is the same word in every language.");
        LEDGER.put("LearningCatalog.java",
                "TRANSLATED through CatalogText — the picker's category headings "
                + "(v2.133.0).");
        LEDGER.put("KvasirProvider.java",
                "MACHINE — provider ids, stored in a preference and matched literally.");
        LEDGER.put("CloudProvider.java",
                "MACHINE — the providers' API base URLs, sent on the wire. The "
                + "provider names beside them are company names.");
        LEDGER.put("ErcStandards.java",
                "MACHINE — ERC-20 is a standard's name, the same in every language.");
    }

    @Test
    @DisplayName("every enum that carries words is classified, and every classification is used")
    void theLedgerCoversThePopulation() throws IOException {
        TreeSet<String> found = new TreeSet<>(census().keySet());
        TreeSet<String> written = new TreeSet<>(LEDGER.keySet());

        TreeSet<String> unclassified = new TreeSet<>(found);
        unclassified.removeAll(written);
        assertThat(unclassified).as("a catalogue of words nobody has decided about — "
                + "classify it TRANSLATED (naming the seam) or MACHINE (with the reason)")
                .isEmpty();

        TreeSet<String> stale = new TreeSet<>(written);
        stale.removeAll(found);
        assertThat(stale).as("ledger entries for enums that no longer carry words — "
                + "a ledger nobody prunes stops being read").isEmpty();
    }

    @Test
    @DisplayName("every verdict is one of the two, and every MACHINE verdict gives its reason")
    void everyVerdictIsSpelled() {
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> e : LEDGER.entrySet()) {
            String verdict = e.getValue();
            if (verdict.startsWith("TRANSLATED")) {
                if (!verdict.contains("through ")) {
                    bad.add(e.getKey() + ": TRANSLATED without naming the seam");
                }
            } else if (verdict.startsWith("MACHINE")) {
                // a bare "MACHINE" is a shrug; the reason is the whole point
                if (verdict.length() < "MACHINE — ".length() + 20) {
                    bad.add(e.getKey() + ": MACHINE without a reason");
                }
            } else {
                bad.add(e.getKey() + ": neither TRANSLATED nor MACHINE");
            }
        }
        assertThat(bad).as("a verdict that does not say what it decided decides nothing")
                .isEmpty();
    }

    @Test
    @DisplayName("a seam named TRANSLATED exists in the shipping source")
    void everyNamedSeamExists() throws IOException {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> e : LEDGER.entrySet()) {
            String verdict = e.getValue();
            if (!verdict.startsWith("TRANSLATED")) {
                continue;
            }
            Matcher m = Pattern.compile("through (\\w+)").matcher(verdict);
            assertThat(m.find()).as("%s names its seam", e.getKey()).isTrue();
            String seam = m.group(1);
            if (!sourceFileExists(seam + ".java")) {
                missing.add(e.getKey() + " names " + seam + ", which does not exist");
            }
        }
        assertThat(missing).as("a ledger that names a seam nobody wrote is a ledger "
                + "that has stopped being true").isEmpty();
    }

    /** Enum files carrying a leading string literal, by simple name. */
    private static Map<String, Integer> census() throws IOException {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String module : MODULES) {
            Path src = REPO.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(p, StandardCharsets.UTF_8);
                    if (!ENUM.matcher(text).find()) {
                        continue;
                    }
                    Matcher m = CONSTANT.matcher(text);
                    int n = 0;
                    while (m.find()) {
                        n++;
                    }
                    if (n > 0) {
                        out.put(p.getFileName().toString(), n);
                    }
                }
            }
        }
        assertThat(out).as("the census should find the catalogues we know about")
                .containsKeys("DeviceType.java", "ProjectTemplates.java", "BlockKind.java");
        return out;
    }

    private static boolean sourceFileExists(String simpleName) throws IOException {
        for (String module : MODULES) {
            Path src = REPO.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                if (files.anyMatch(p -> p.getFileName().toString().equals(simpleName))) {
                    return true;
                }
            }
        }
        return false;
    }
}
