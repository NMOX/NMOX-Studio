package org.nmox.studio.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The product version is read in ONE place (core.util.ProductVersion):
 * a module's own classloader cannot load the platform's branded startup
 * bundle, so a raw {@code getBundle("org.netbeans.core.startup.Bundle")}
 * anywhere else is a null in every shipped build (v2.67.0 — the Welcome
 * footer, What's New, Report a Problem and the daily update notifier
 * all read null that way).
 */
class ProductVersionGateTest {

    private static final List<String> MODULES = List.of(
            "core", "editor", "tools", "rack", "infra", "apiclient", "dbstudio", "web3", "project", "ui");

    @Test
    @DisplayName("No module reads the startup bundle raw — every product-version read goes through ProductVersion")
    void oneReader() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String m : MODULES) {
            Path root = Path.of("..", m, "src", "main", "java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String src = Files.readString(p);
                    if (src.contains("org.netbeans.core.startup.Bundle") && !p.endsWith("ProductVersion.java")) {
                        offenders.add(m + "/" + root.relativize(p));
                    }
                }
            }
        }
        assertThat(offenders).as("raw startup-bundle reads (null in every shipped build)").isEmpty();
    }
}
