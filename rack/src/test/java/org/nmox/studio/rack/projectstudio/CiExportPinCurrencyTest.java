package org.nmox.studio.rack.projectstudio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exported-workflow pin currency gate (v1.236.0): Export CI hands
 * users a GitHub Actions workflow, and its action pins are STRING
 * LITERALS in {@link CiExporter} — Dependabot keeps our own
 * {@code .github/workflows} current but cannot read Java, so the
 * generated pins rotted silently (found in the night-shift DevOps
 * pass: the exporter still said {@code actions/checkout@v4} while our
 * own workflows — and GitHub's starter templates — were on v7).
 *
 * <p>The gate rides the freshness we already pay for: any action that
 * appears in BOTH the exporter and our own workflows must pin the
 * SAME major. When Dependabot bumps ours, this test fails until the
 * exporter follows. Exporter-only actions (setup-nim, setup-scarb, …)
 * have no bot watching them; they were hand-verified against each
 * repo's latest release on 2026-08-03 and belong to the periodic
 * review, not this gate.
 */
class CiExportPinCurrencyTest {

    private static final Pattern USES = Pattern.compile("uses: ([A-Za-z0-9_./-]+)@(v?[0-9]+)");

    @Test
    @DisplayName("exporter pins match our own workflows' pins for every shared action")
    void sharedActionPinsMatch() throws IOException {
        String exporter = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/CiExporter.java"));
        Map<String, String> exported = pins(exporter);

        Map<String, String> ours = new HashMap<>();
        try (Stream<Path> files = Files.list(Path.of("..", ".github", "workflows"))) {
            for (Path wf : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                // our workflows may pin minor/patch too; majors must agree
                pins(Files.readString(wf)).forEach(ours::putIfAbsent);
            }
        }

        assertThat(exported).isNotEmpty();
        for (Map.Entry<String, String> pin : exported.entrySet()) {
            String ownVersion = ours.get(pin.getKey());
            if (ownVersion != null) {
                assertThat(pin.getValue())
                        .as(pin.getKey() + ": the exporter hands users @"
                                + pin.getValue() + " while our own workflows use @"
                                + ownVersion + " — bump the CiExporter literal")
                        .isEqualTo(ownVersion);
            }
        }
        // the gate is only real if it covers something: checkout is in both
        assertThat(ours).containsKey("actions/checkout");
        assertThat(exported).containsKey("actions/checkout");
    }

    private static Map<String, String> pins(String text) {
        Map<String, String> out = new HashMap<>();
        Matcher m = USES.matcher(text);
        while (m.find()) {
            out.putIfAbsent(m.group(1), m.group(2));
        }
        return out;
    }
}
