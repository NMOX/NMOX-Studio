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
 *
 * <p>The third law is the .NET major. The exporter's
 * {@code dotnet-version}, the {@code TargetFramework} of every .NET
 * project file a learning space writes, and the SDK each space's
 * install hints name are one fact. On 2026-11-13 the exporter was
 * moved to 10.x alone and moved back the same day, because the spaces
 * still targeted {@code net8.0}; and the spaces themselves did not run
 * on what their own macOS hint installs ({@code brew install
 * dotnet-sdk} gave SDK 11.0.100, {@code dotnet run} exited 150: no 8.0
 * runtime). Now: {@code net10.0} (the LTS, supported until 2028-11-14)
 * with {@code <RollForward>Major</RollForward>}, measured to run on
 * SDK 11.0.100 with exit 0. An unversioned hint installs whatever is
 * newest, which is why the roll-forward line is part of the law. The
 * C# space's run command was {@code sh -c "dotnet run --project ."},
 * which the rack flattened to {@code sh -c dotnet run --project .}: a
 * bare {@code dotnet}, usage text and exit 129. It is {@code dotnet
 * run} now, as the ASP.NET space's always was.
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
        // what the generator WRITES, for every kind: its images live in constants
        // now (nginx has one home), so the source no longer spells "FROM node:26"
        StringBuilder written = new StringBuilder();
        for (org.nmox.studio.rack.devices.ProjectInspector.ProjectKind kind
                : org.nmox.studio.rack.devices.ProjectInspector.ProjectKind.values()) {
            for (boolean statics : new boolean[] {false, true}) {
                org.nmox.studio.rack.docker.DockerizeGenerator.generate(kind, "x", statics).values()
                        .forEach(content -> written.append(content).append('\n'));
            }
        }
        String dockerize = written.toString();
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

    @Test
    @DisplayName("a .NET learning space targets the major the exporter installs, and rolls forward")
    void dotnetSpacesAndExporterAgree() throws IOException {
        String exporter = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/rack/projectstudio/CiExporter.java"));
        String major = only(exporter, "dotnet-version: ([0-9]+)\\.x");

        int projectFiles = 0;
        for (LearningCatalog.Space space : LearningCatalog.builtIns()) {
            boolean dotnet = false;
            for (LearningCatalog.SampleFile f : space.files()) {
                if (!f.path().matches(".*\\.(cs|fs|vb)proj")) {
                    continue;
                }
                dotnet = true;
                projectFiles++;
                assertThat(all(f.content(), "<TargetFramework>net([0-9]+)\\.0</TargetFramework>"))
                        .as(space.slug() + "/" + f.path() + ": the TargetFramework major must be the"
                                + " exporter's dotnet-version major — move them together")
                        .containsExactly(major);
                assertThat(f.content())
                        .as(space.slug() + "/" + f.path() + ": an SDK newer than the target brings no"
                                + " runtime for it (dotnet run exits 150) and the macOS hint installs"
                                + " the newest SDK, so the project must roll forward")
                        .contains("<RollForward>Major</RollForward>");
            }
            if (!dotnet) {
                continue;
            }
            assertThat(space.driver().command())
                    .as(space.slug() + ": the rack joins a run command with spaces and SOLDER splits it"
                            + " again without a shell, so `sh -c \"dotnet run …\"` arrived as a bare"
                            + " `dotnet` (measured: usage text, exit 129). A .NET space runs dotnet itself")
                    .containsExactly("dotnet", "run");
            assertThat(space.install()).as(space.slug() + " install hints").isNotEmpty();
            for (Map.Entry<String, String> hint : space.install().entrySet()) {
                assertThat(all(hint.getValue(), "(?:dotnet-sdk-|DotNet\\.SDK\\.|dotnet@|dotnet-sdk@)([0-9]+)"))
                        .as(space.slug() + " " + hint.getKey() + " hint \"" + hint.getValue()
                                + "\": a hint that names an SDK version names the exporter's major")
                        .isSubsetOf(major);
            }
        }
        // the law is only real if it covers something: C# and ASP.NET each ship one
        assertThat(projectFiles).as(".NET project files in the catalog").isGreaterThanOrEqualTo(2);
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
