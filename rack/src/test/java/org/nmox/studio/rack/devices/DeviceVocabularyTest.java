package org.nmox.studio.rack.devices;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.search.SearchTerms;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The findability gate.
 *
 * <p>Before v1.215.0 a measured 24 of 49 ordinary search terms returned
 * no device at all — not because the devices were missing but because
 * nobody had ever checked that the words people type reach them. This
 * test is the structural answer: the vocabulary is asserted as a whole,
 * so the class of bug is closed rather than the instances.
 *
 * <p>If you add a device, {@link #everyDeviceCarriesSearchWords()}
 * fails until it has search words. If you add a term here, it fails
 * until some device answers it. Both are the point.
 */
class DeviceVocabularyTest {

    /**
     * How a user asks for each device — the exact strings, including the
     * ones that used to fail. Left side is what gets typed, right side
     * is the device title that must come back.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    static {
        // Each of these returned NOTHING before this release.
        EXPECTED.put("coverage", "VERITAS");
        EXPECTED.put("run tests", "VERITAS");
        EXPECTED.put("typescript", "TYPEGUARD");
        EXPECTED.put("postgres", "NEPTUNE");
        EXPECTED.put("mysql", "NEPTUNE");
        EXPECTED.put("sql", "NEPTUNE");
        EXPECTED.put("accessibility", "VITALS");
        EXPECTED.put("a11y", "VITALS");
        EXPECTED.put("breakpoint", "INSPECTOR");
        EXPECTED.put("start server", "SURGE");
        EXPECTED.put("logs", "TAIL");
        EXPECTED.put("environment variables", "ATMOS");
        EXPECTED.put("secrets", "ATMOS");
        EXPECTED.put("branch", "TIMELINE");
        EXPECTED.put("bundle size", "PRISM");
        EXPECTED.put("dependencies", "CRATE");
        EXPECTED.put("vulnerabilities", "SENTRY");
        EXPECTED.put("cron", "TEMPO");
        EXPECTED.put("schedule", "TEMPO");
        EXPECTED.put("claude", "ORACLE");
        EXPECTED.put("llm", "ORACLE");
        EXPECTED.put("port in use", "SONAR");
        EXPECTED.put("containers", "HARBOR");
        EXPECTED.put("end to end", "SPECTER");
        EXPECTED.put("benchmark", "GAUNTLET");
        EXPECTED.put("ssl certificate", "BEACON");
        EXPECTED.put("monorepo", "WAYPOINT");
        EXPECTED.put("ethereum", "ANVIL");
        EXPECTED.put("solana", "ANCHOR");

        // These already worked; they are here so a vocabulary edit
        // cannot quietly break what the descriptions already carried.
        EXPECTED.put("eslint", "PURITY");
        EXPECTED.put("prettier", "GLOSS");
        EXPECTED.put("lighthouse", "VITALS");
        EXPECTED.put("docker", "HARBOR");
        EXPECTED.put("ngrok", "WORMHOLE");
        EXPECTED.put("webpack", "FORGE");
    }

    /** Titles of every device the term reaches, in catalog order. */
    private static List<String> find(String term) {
        List<String> hits = new ArrayList<>();
        for (DeviceType t : DeviceType.values()) {
            if (SearchTerms.matches(term, t.getTitle(), t.getDescription(),
                    DeviceVocabulary.forId(t.getId()))) {
                hits.add(t.getTitle());
            }
        }
        return hits;
    }

    @Test
    @DisplayName("every ordinary search term reaches the device it names")
    void ordinaryTermsFindTheirDevice() {
        List<String> failures = new ArrayList<>();
        EXPECTED.forEach((term, expectedTitle) -> {
            List<String> hits = find(term);
            if (!hits.contains(expectedTitle)) {
                failures.add("\"" + term + "\" should find " + expectedTitle
                        + " but found " + (hits.isEmpty() ? "NOTHING" : hits));
            }
        });
        assertThat(failures)
                .as("search terms that do not reach their device")
                .isEmpty();
    }

    @Test
    @DisplayName("no ordinary search term comes back empty")
    void noTermIsADeadEnd() {
        List<String> dead = new ArrayList<>();
        for (String term : EXPECTED.keySet()) {
            if (find(term).isEmpty()) {
                dead.add(term);
            }
        }
        assertThat(dead).as("terms returning an empty result").isEmpty();
    }

    @Test
    @DisplayName("every built-in device carries search words")
    void everyDeviceCarriesSearchWords() {
        List<String> bare = new ArrayList<>();
        for (DeviceType t : DeviceType.values()) {
            if (DeviceVocabulary.forId(t.getId()).isBlank()) {
                bare.add(t.getTitle() + " (" + t.getId() + ")");
            }
        }
        assertThat(bare)
                .as("devices with no search vocabulary — add them to DeviceVocabulary")
                .isEmpty();
    }

    @Test
    @DisplayName("the vocabulary names no device that does not exist")
    void vocabularyHasNoOrphans() {
        List<String> known = new ArrayList<>();
        for (DeviceType t : DeviceType.values()) {
            known.add(t.getId());
        }
        assertThat(known)
                .as("a vocabulary entry whose device was renamed or removed")
                .containsAll(DeviceVocabulary.coveredIds());
    }

    @Test
    @DisplayName("a codename still finds its own device")
    void codenamesStillWork() {
        assertThat(find("veritas")).contains("VERITAS");
        assertThat(find("oracle")).contains("ORACLE");
        assertThat(find("npm-9000")).contains("NPM-9000");
    }
}
