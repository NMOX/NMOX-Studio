package org.nmox.studio.application;

import java.io.IOException;
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
 * DocsCountGateTest's blind spot, closed (v2.85.0): a count baked into a
 * USER-VISIBLE STRING rots exactly like one in prose, and no docs gate
 * can see it. The New Experiment dialog said "Browse 92 Learning
 * Spaces…" and the empty Learning Spaces shelf "Browse the 92
 * tutorials…" while the catalog held 93 — a space had shipped
 * (v2.58.0) and the buttons kept promising the old number. A count the
 * product shows must come from the thing it counts (the catalog, the
 * census, the registry); this gate reads every module's main sources
 * and refuses a numeral beside a counted noun inside a string literal.
 */
class UiCountLiteralGateTest {

    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"");
    private static final Pattern CLAIM = Pattern.compile(
            "\\b\\d{2,3}[- ](learning spaces|Learning Spaces|tutorials|spaces|grammars|devices"
            + "|manifests|templates|languages|frameworks|libraries)\\b");

    @Test
    @DisplayName("no user-visible string literal carries a hand-typed count of a counted noun")
    void countsComeFromTheCatalogNotTheLiteral() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String module : new String[]{"core", "editor", "tools", "rack", "project",
            "ui", "apiclient", "dbstudio", "web3", "infra"}) {
            Path src = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String body = Files.readString(p);
                    Matcher s = STRING.matcher(body);
                    while (s.find()) {
                        if (CLAIM.matcher(s.group()).find()) {
                            int line = 1 + (int) body.chars().limit(s.start()).filter(c -> c == '\n').count();
                            offenders.add(module + "/" + p.getFileName() + ":" + line + " " + s.group());
                        }
                    }
                }
            }
            // the same rot's other homes: hand-written bundles and the bundled
            // website's copy (v2.40.0) are user-visible strings too
            Path resources = Path.of("..", module, "src", "main", "resources");
            Path site = Path.of("..", module, "src", "main", "release", "website");
            for (Path home : new Path[]{resources, site}) {
                if (!Files.isDirectory(home)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(home)) {
                    for (Path p : files.filter(f -> {
                        String n = f.toString();
                        return n.endsWith(".properties") || n.endsWith(".html") || n.endsWith(".js") || n.endsWith(".json");
                    }).filter(f -> !f.toString().contains("learn-catalog")).toList()) {
                        String body = Files.readString(p);
                        Matcher c = CLAIM.matcher(body);
                        // separator-normalized: on Windows the path reads release\website and the
                        // exemption never matched — the #704 windows lane found it (the CRLF trap's sibling)
                        boolean website = p.toString().replace('\\', '/').contains("release/website");
                        while (c.find()) {
                            // the website is static bytes with no runtime to derive from; its
                            // numerals are allowed ONLY where SiteShipsTest.countsAreTrue binds
                            // them to the catalogs at build time (v2.91.0) — the count still
                            // comes from the thing it counts, enforced instead of derived. The
                            // words that hid there before ("Fifty-three") were this gate's
                            // blind spot and rotted for thirty releases.
                            if (website && c.group().endsWith(" devices")) {
                                continue;
                            }
                            int line = 1 + (int) body.chars().limit(c.start()).filter(ch -> ch == '\n').count();
                            offenders.add(module + "/" + p.getFileName() + ":" + line + " " + c.group());
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("a count typed into a user-visible string — derive it from what it counts")
                .isEmpty();
    }
}
