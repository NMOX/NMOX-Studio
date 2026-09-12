package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A key that is allowed to be missing is looked up, never thrown at.
 *
 * <p>The catalogue seams (v2.132.0–v2.134.0) each expressed one rule — show
 * the translation when somebody wrote one, the English when nobody did — as
 * {@code NbBundle.getMessage} inside a {@code catch
 * (MissingResourceException)}. Correct, and expensive in exactly the wrong
 * place: these keys have no English base bundle BY DESIGN, so an English
 * reader misses every one of them, and the miss path built a stack trace
 * inside Swing paint loops that repaint on every scroll and hover.
 *
 * <p>Measured on the Block Studio palette before the change: 300,000
 * lookups cost 834 ms in English against 71 ms in German — twelve times
 * slower on the majority path. Through {@link
 * org.nmox.studio.core.util.Bundles#optional} the same 300,000 cost 141 ms.
 * The lesson is worth more than the milliseconds: <b>a fallback written as
 * an exception is a fallback that costs most where it is taken most</b>,
 * and here it was taken on every lookup by every English reader.
 *
 * <p>So this gate reads the shape, not the speed. A timing assertion on a
 * shared runner is a flake (v2.99.1); the catch is deterministic.
 */
class OptionalKeyLookupGateTest {

    private static final Path REPO = Path.of("..");

    private static final List<String> MODULES = List.of(
            "core", "editor", "tools", "rack", "infra", "apiclient",
            "dbstudio", "web3", "project", "ui");

    /**
     * The catch, however it is spelled. A fully-qualified
     * {@code catch (java.util.MissingResourceException e)} is the same
     * defect and slipped past the first cut of this gate, which matched a
     * bare literal — the v2.19.1 lesson: gate the outcome, not the spelling.
     */
    private static final Pattern CATCH = Pattern.compile(
            "catch\\s*\\(\\s*(?:java\\.util\\.)?MissingResourceException\\b");

    /** Sites where catching it is the honest shape, and why. */
    private static final Map<String, String> BLESSED = new LinkedHashMap<>();

    static {
        BLESSED.put("ProductVersionBundle.java",
                "reads the BRANDED startup bundle through the system classloader, a "
                + "different overload whose throw also covers the bundle being invisible "
                + "entirely — the v2.67.0 defect. Called once per version read, never in "
                + "a paint loop.");
        BLESSED.put("Bundles.java",
                "IS the seam: it catches the absent-bundle case once so every caller "
                + "stops catching the absent-KEY case forever.");
    }

    @Test
    @DisplayName("only the blessed sites catch a missing key; the rest ask whether it is there")
    void noSeamCatchesAMissingKey() throws IOException {
        List<String> caught = new ArrayList<>();
        for (String module : MODULES) {
            Path src = REPO.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(src)) {
                for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String name = p.getFileName().toString();
                    if (BLESSED.containsKey(name)) {
                        continue;
                    }
                    if (CATCH.matcher(Files.readString(p, StandardCharsets.UTF_8)).find()) {
                        caught.add(module + "/" + name);
                    }
                }
            }
        }
        assertThat(caught).as("a missing key is a question, not an accident — use "
                + "Bundles.optional, or bless the site here with its reason").isEmpty();
    }

    @Test
    @DisplayName("every blessing names a file that exists and gives a real reason")
    void everyBlessingIsSpentOnSomethingReal() throws IOException {
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> e : BLESSED.entrySet()) {
            if (e.getValue().length() < 40) {
                stale.add(e.getKey() + ": a blessing this short decides nothing");
            }
            if (!exists(e.getKey())) {
                stale.add(e.getKey() + ": blessed, but no such file ships");
            }
        }
        assertThat(stale).as("a blessing nobody can check has stopped being a decision")
                .isEmpty();
    }

    private static boolean exists(String simpleName) throws IOException {
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
