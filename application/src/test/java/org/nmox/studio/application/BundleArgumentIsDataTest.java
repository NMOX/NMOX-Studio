package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A string handed to a bundle as {@code {0}} is DATA — a name, a path, a
 * number — never prose (ledger 88, v2.100.0).
 *
 * <p>The defect this gate exists to catch is invisible to every other l10n
 * gate in the build, and that is exactly why it survived seven releases:
 * {@code LiveRuns.since()} returned the phrase {@code "since 14:32"} and
 * three surfaces spliced it in as an argument, so the BUNDLES were all
 * correct and complete in twelve languages while a Chinese user read
 * «正在运行 since 14:32». The English entered below the bundles, as data.
 * `LocaleBundleParityTest` compares key sets and placeholders — both were
 * right. `ChromeLiteralRatchetTest` looks for a literal AT a sink — the
 * literal was two modules away, inside a helper.
 *
 * <p>Hindi is the reason the contract is written this way rather than the
 * other way. Its translator had already read {@code {0}} as a bare time and
 * supplied the postposition ({@code {0} से चल रहा है}), so Hindi was rendering
 * the preposition twice while the other eleven rendered an English word. The
 * ambiguity was real, the code never resolved it, and the fix is to make the
 * argument data and let each language own the word that introduces it.
 */
class BundleArgumentIsDataTest {

    /** The keys that take a live run's start time as an argument. */
    private static final List<String> TIME_KEYS = List.of(
            "WorkbenchRunning_runningSince",
            "WorkbenchRunning_since",
            "LiveRunSearchProvider_stopSince",
            "NpmExplorerTopComponent_since");

    /**
     * The word each language introduces a point in time with — the reviewed
     * vocabulary, pinned here because no general rule can derive it. A value
     * that has lost its own word is a value whose meaning can only be arriving
     * inside the argument, which is the defect. Add a language, add its word.
     */
    private static final Map<String, String> SINCE_WORD = Map.ofEntries(
            Map.entry("es", "desde"), Map.entry("fr", "depuis"), Map.entry("de", "seit"),
            Map.entry("ru", "с"), Map.entry("uk", "з"), Map.entry("pl", "od"),
            Map.entry("pt", "desde"), Map.entry("id", "sejak"), Map.entry("tl", "mula"),
            Map.entry("vi", "từ"), Map.entry("zh", "自"), Map.entry("hi", "से"));

    private static List<Path> sources() throws IOException {
        List<Path> all = new ArrayList<>();
        for (String module : List.of("core", "editor", "tools", "project", "ui",
                "rack", "apiclient", "dbstudio", "web3", "infra")) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (Files.isDirectory(src)) {
                try (Stream<Path> files = Files.walk(src)) {
                    all.addAll(files.filter(f -> f.toString().endsWith(".java")).toList());
                }
            }
        }
        return all;
    }

    @Test
    @DisplayName("the prose-returning since() is gone: no production source can splice a phrase in as an argument")
    void noProductionSourceCallsTheProseForm() throws IOException {
        List<String> callers = new ArrayList<>();
        for (Path p : sources()) {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            // sinceTime( is the data form; since( on LiveRuns was the phrase
            if (body.contains("LiveRuns.since(")) {
                callers.add(p.toString().replace('\\', '/'));
            }
        }
        assertThat(callers)
                .as("LiveRuns.since() returned \"since HH:mm\" — prose in an argument (ledger 88)")
                .isEmpty();
    }

    @Test
    @DisplayName("the word that introduces the time lives in each language's own value, never in the argument")
    void everyLanguageOwnsItsPreposition() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String key : TIME_KEYS) {
            for (Map.Entry<String, String> e : SINCE_WORD.entrySet()) {
                String locale = e.getKey();
                String value = value(key, "Bundle_" + locale + ".properties");
                if (value == null) {
                    wrong.add(key + " [" + locale + "]: absent");
                    continue;
                }
                // the value must be able to introduce the time BY ITSELF. A
                // placeholder with no word of its own is the pre-fix state:
                // "działa {0}" reads correctly only while the argument smuggles
                // an English preposition in, which is the whole defect.
                if (!value.contains(e.getValue())) {
                    wrong.add(key + " [" + locale + "]: \"" + value + "\" lacks its own word for since ("
                            + e.getValue() + ") — the time can only be introduced by the argument");
                }
                if (value.matches("(?s).*\\bsince\\b.*")) {
                    wrong.add(key + " [" + locale + "]: carries the English word \"since\"");
                }
            }
        }
        assertThat(wrong).as("translated values that cannot introduce the time themselves").isEmpty();
    }

    @Test
    @DisplayName("English says the word too — it is a bundle value here, not a hidden default in the code")
    void englishCarriesTheWordInItsBundle() throws IOException {
        // WorkbenchRunning_* are generated from @NbBundle.Messages, so English
        // lives in the annotation; the other two have hand-written bundles.
        String workbench = Files.readString(
                Path.of("..", "project", "src", "main", "java", "org", "nmox", "studio",
                        "project", "WorkbenchRunning.java"), StandardCharsets.UTF_8);
        assertThat(workbench)
                .as("the Workbench row's English says \"since\" in its own message")
                .contains("WorkbenchRunning_runningSince=running since {0}")
                .contains("WorkbenchRunning_since=since {0}");
        assertThat(value("LiveRunSearchProvider_stopSince", "Bundle.properties"))
                .as("the Quick Search result's English says \"since\" in its own value").contains("since {1}");
        assertThat(value("NpmExplorerTopComponent_since", "Bundle.properties"))
                .as("the NPM Explorer marker's English says \"since\" in its own value").contains("since {0}");
    }

    /** The first value for {@code key} in any bundle of that {@code fileName}. */
    private static String value(String key, String fileName) throws IOException {
        for (String module : List.of("core", "editor", "tools", "project", "ui",
                "rack", "apiclient", "dbstudio", "web3", "infra")) {
            Path res = Path.of("..", module, "src", "main", "resources");
            if (!Files.isDirectory(res)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(res)) {
                for (Path p : files.filter(f -> f.getFileName().toString().equals(fileName)).toList()) {
                    for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                        if (line.startsWith(key + "=")) {
                            return line.substring(key.length() + 1);
                        }
                    }
                }
            }
        }
        return null;
    }
}
