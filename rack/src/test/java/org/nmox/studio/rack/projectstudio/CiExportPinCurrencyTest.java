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
 * repo's latest release on 2026-08-03, and again on 2026-11-13 (one
 * moved: setup-fpm v10 to v11), and belong to the periodic review, not
 * this gate.
 *
 * <p>The second law here is about the RUNTIMES the generators name. The
 * exporter writes {@code node-version}, {@code python-version} and
 * {@code php-version}; the Dockerize generator writes {@code FROM node},
 * {@code FROM python} and {@code FROM php}; the LEMP template writes a
 * compose file of its own. Before 2026-11-13 they said node 22 / 24,
 * python 3.12 / 3.14 and php 8.3 / 8.5 / 8.4: one fact with three homes,
 * each last touched by a different release. They must agree now, so
 * moving one without the others fails here.
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

    @Test
    @DisplayName("the CI the product exports and the images it generates name the same runtimes")
    void generatorsAgreeOnRuntimes() throws IOException {
        String exporter = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/CiExporter.java"));
        String dockerize = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/docker/DockerizeGenerator.java"));
        String templates = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/ProjectTemplates.java"));

        assertThat(all(exporter, "node-version: ([0-9]+)"))
                .as("every node-version the exporter writes")
                .isNotEmpty()
                .containsOnly(only(dockerize, "FROM node:([0-9]+)-alpine"));
        assertThat(all(exporter, "python-version: '([0-9.]+)'"))
                .as("the exporter's python-version")
                .containsExactly(only(dockerize, "FROM python:([0-9.]+)-slim"));
        String php = only(dockerize, "FROM php:([0-9.]+)-fpm-alpine");
        assertThat(all(exporter, "php-version: '([0-9.]+)'"))
                .as("the exporter's php-version")
                .containsExactly(php);
        assertThat(all(templates, "image: php:([0-9.]+)-fpm"))
                .as("the LEMP template's php image")
                .containsExactly(php);
        assertThat(all(templates, "image: nginx:([0-9.]+)-alpine"))
                .as("the LEMP template's nginx image")
                .containsExactly(only(dockerize, "image: nginx:([0-9.]+)-alpine"));
    }

    /** Every distinct capture of {@code regex} in {@code text}. */
    private static java.util.Set<String> all(String text, String regex) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        Matcher m = Pattern.compile(regex).matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** The one value a generator names; two different values is itself the defect. */
    private static String only(String text, String regex) {
        java.util.Set<String> found = all(text, regex);
        assertThat(found).as("exactly one value for " + regex).hasSize(1);
        return found.iterator().next();
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
