package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every registered window can be renamed when the language changes
 * (v2.103.0).
 *
 * <p>`LocaleRefresher` re-reads each open window's title from
 * {@code CTL_<window id>} after a live switch. That only works while the
 * convention holds, and a window whose key cannot be found keeps its old
 * title silently — a defect nobody would notice in English, and everybody
 * would notice in Ukrainian. So the convention is a build law: a window
 * added tomorrow either follows it or is aliased, and this fails until it is.
 *
 * <p>It also holds the alias map in step with the one
 * {@code BundleHeadGateTest} has kept since v2.98.0. Two maps of the same
 * exceptions drift; this makes them one vocabulary in practice by failing
 * when they disagree.
 */
class LocaleRefresherKeysTest {

    private static final Pattern REGISTRATION = Pattern.compile("@TopComponent\\.Registration");
    private static final Pattern PREFERRED_ID = Pattern.compile("preferredID\\s*=\\s*\"(\\w+)\"");
    private static final Pattern ALIAS = Pattern.compile("\"(\\w+)\",\\s*\"(CTL_\\w+)\"");

    private static final List<String> MODULES = List.of("core", "editor", "tools", "project",
            "ui", "rack", "apiclient", "dbstudio", "web3", "infra");

    @Test
    @DisplayName("every registered window's title key resolves — by convention or by a written alias")
    void everyWindowCanBeRenamed() throws IOException {
        List<String> unnameable = new ArrayList<>();
        int checked = 0;
        for (Path p : registeredWindows()) {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            Matcher id = PREFERRED_ID.matcher(body);
            String windowId = id.find() ? id.group(1) : p.getFileName().toString().replace(".java", "");
            checked++;
            String aliased = aliases().get(windowId);
            String key = aliased != null ? aliased : "CTL_" + windowId;
            if (!keyExists(p, key) && !keyExists(p, "CTL_" + p.getFileName().toString().replace(".java", ""))) {
                unnameable.add(p.getFileName() + " (id " + windowId + "): no " + key);
            }
        }
        assertThat(checked).as("the window census should not be empty").isGreaterThan(10);
        assertThat(unnameable)
                .as("windows LocaleRefresher could not rename — they would keep English titles after a switch")
                .isEmpty();
    }

    @Test
    @DisplayName("the alias map matches the one BundleHeadGateTest keeps — one vocabulary, not two")
    void aliasMapsAgree() throws IOException {
        Path gate = Path.of("src", "test", "java", "org", "nmox", "studio", "application",
                "BundleHeadGateTest.java");
        String gateBody = Files.readString(gate, StandardCharsets.UTF_8);
        String gateMap = gateBody.substring(gateBody.indexOf("TITLE_KEY_ALIASES"));
        gateMap = gateMap.substring(0, gateMap.indexOf(";"));
        var theirs = new java.util.TreeMap<String, String>();
        Matcher m = ALIAS.matcher(gateMap);
        while (m.find()) {
            theirs.put(m.group(1), m.group(2));
        }
        assertThat(theirs).as("BundleHeadGateTest should declare aliases").isNotEmpty();
        assertThat(new java.util.TreeMap<>(aliases()))
                .as("LocaleRefresher and BundleHeadGateTest disagree about which windows are exceptions")
                .isEqualTo(theirs);
    }

    /** LocaleRefresher's own alias map, read from its source. */
    private static java.util.Map<String, String> aliases() throws IOException {
        Path refresher = Path.of("..", "ui", "src", "main", "java", "org", "nmox", "studio",
                "ui", "options", "LocaleRefresher.java");
        String body = Files.readString(refresher, StandardCharsets.UTF_8);
        String map = body.substring(body.indexOf("TITLE_KEY_ALIASES"));
        map = map.substring(0, map.indexOf(";"));
        var out = new java.util.TreeMap<String, String>();
        Matcher m = ALIAS.matcher(map);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static boolean keyExists(Path windowSource, String key) throws IOException {
        if (Files.readString(windowSource, StandardCharsets.UTF_8).contains("\"" + key + "=")) {
            return true;
        }
        Path bundle = Path.of(windowSource.getParent().toString()
                .replace("/src/main/java/", "/src/main/resources/")).resolve("Bundle.properties");
        return Files.isRegularFile(bundle)
                && Files.readAllLines(bundle, StandardCharsets.UTF_8).stream()
                        .anyMatch(l -> l.startsWith(key + "="));
    }

    private static List<Path> registeredWindows() throws IOException {
        List<Path> out = new ArrayList<>();
        for (String module : MODULES) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    if (REGISTRATION.matcher(Files.readString(p, StandardCharsets.UTF_8)).find()) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
    }
}
