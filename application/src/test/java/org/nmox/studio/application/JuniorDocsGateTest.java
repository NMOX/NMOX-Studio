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
 * The junior-documentation floor (v2.7.1). Two properties, both born
 * from the v1.202 comments arc and re-measured before this gate
 * existed:
 * <ul>
 *   <li>Every public top-level type in main sources carries a
 *       class-level javadoc — the measurement that day was 638/638,
 *       and this gate keeps a new class from being the first bare
 *       one.</li>
 *   <li>The curated packages a newcomer walks first each carry a
 *       {@code package-info.java} neighborhood map (what lives here,
 *       which RCP mechanism, reading order).</li>
 * </ul>
 * The detector is annotation-tolerant: javadoc separated from the
 * declaration by {@code @TopComponent.Registration(...)} blocks still
 * counts — it looks for a {@code /**} block anywhere between the last
 * import and the type declaration.
 */
class JuniorDocsGateTest {

    private static final Pattern TYPE_DECL = Pattern.compile(
            "^public\\s+(?:final\\s+|abstract\\s+)?(?:class|interface|enum|record)\\s+\\w+",
            Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile(
            "^import\\s+[^;]+;", Pattern.MULTILINE);
    private static final Pattern PACKAGE = Pattern.compile(
            "^package\\s+[^;]+;", Pattern.MULTILINE);

    private static final List<String> MODULES = List.of(
            "core", "editor", "tools", "rack", "apiclient", "dbstudio",
            "web3", "infra", "project", "ui");

    /** The packages whose package-info is part of the onboarding docs. */
    private static final List<String> MAPPED_PACKAGES = List.of(
            "core/src/main/java/org/nmox/studio/core/util",
            "core/src/main/java/org/nmox/studio/core/spi",
            "core/src/main/java/org/nmox/studio/core/spi/device",
            "rack/src/main/java/org/nmox/studio/rack/model",
            "rack/src/main/java/org/nmox/studio/rack/engine",
            "rack/src/main/java/org/nmox/studio/rack/service",
            "rack/src/main/java/org/nmox/studio/rack/devices",
            "rack/src/main/java/org/nmox/studio/rack/ui/controls",
            "ui/src/main/java/org/nmox/studio/ui/tasks",
            "ui/src/main/java/org/nmox/studio/ui/irc",
            "ui/src/main/java/org/nmox/studio/ui/browser",
            "editor/src/main/java/org/nmox/studio/editor/grammars",
            "editor/src/main/java/org/nmox/studio/editor/languages",
            "editor/src/main/java/org/nmox/studio/editor/lsp",
            "editor/src/main/java/org/nmox/studio/editor/completion",
            "editor/src/main/java/org/nmox/studio/editor/angular",
            "editor/src/main/java/org/nmox/studio/editor/design",
            "editor/src/main/java/org/nmox/studio/editor/emmet",
            "tools/src/main/java/org/nmox/studio/tools/npm",
            "apiclient/src/main/java/org/nmox/studio/apiclient/api",
            "dbstudio/src/main/java/org/nmox/studio/dbstudio/engine",
            "web3/src/main/java/org/nmox/studio/web3/engine",
            "infra/src/main/java/org/nmox/studio/infra/api");

    @Test
    @DisplayName("every public top-level type has a class javadoc — nobody ships the first bare class")
    void everyPublicTypeHasClassJavadoc() throws IOException {
        List<String> bare = new ArrayList<>();
        for (String module : MODULES) {
            Path root = Path.of("..", module, "src", "main", "java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(f);
                    Matcher decl = TYPE_DECL.matcher(src);
                    if (!decl.find()) {
                        continue; // package-info, package-private types
                    }
                    int regionStart = 0;
                    Matcher imp = IMPORT.matcher(src);
                    while (imp.find()) {
                        regionStart = imp.end();
                    }
                    if (regionStart == 0) {
                        Matcher pkg = PACKAGE.matcher(src);
                        if (pkg.find()) {
                            regionStart = pkg.end();
                        }
                    }
                    String region = src.substring(regionStart, decl.start());
                    if (!region.contains("/**")) {
                        bare.add(module + "/" + root.relativize(f));
                    }
                }
            }
        }
        assertThat(bare)
                .as("public types without a class-level javadoc — write the"
                        + " header a junior needs: what it does, which RCP"
                        + " mechanism it rides, why it looks the way it does")
                .isEmpty();
    }

    @Test
    @DisplayName("the curated onboarding packages each keep their package-info map")
    void mappedPackagesKeepTheirPackageInfo() {
        List<String> missing = new ArrayList<>();
        for (String pkg : MAPPED_PACKAGES) {
            if (!Files.isRegularFile(Path.of("..", pkg, "package-info.java"))) {
                missing.add(pkg);
            }
        }
        assertThat(missing)
                .as("onboarding package-info files — the neighborhood maps the"
                        + " codebase guide points a junior at")
                .isEmpty();
    }
}
