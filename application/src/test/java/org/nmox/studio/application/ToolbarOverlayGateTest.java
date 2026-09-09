package org.nmox.studio.application;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform toolbar speaks the user's language (ledger 90, v2.102.0).
 *
 * <p>These are branding+locale overlays over the PLATFORM's own bundles, so
 * nothing in our own source can hold them honest — the only proof is reading
 * the files and rendering them. Two things can go wrong and neither shows up
 * as a compile error: a language can be missed, and a {@code choice} pattern
 * can be malformed, which throws at PAINT time in the one locale nobody on
 * this team reads.
 *
 * <p>The patterns are the platform's own shape and it is a strange one — the
 * count argument is {@code -1} when no main project is set, which is what
 * renders "Run Main Project" on a fresh start. Slavic languages carry two
 * extra branches (2 and 5) because they inflect the noun across 1 / 2–4 / 5+;
 * the others carry {@code 1<}.
 */
class ToolbarOverlayGateTest {

    private static final Path BRANDING =
            Path.of("..", "branding", "src", "main", "nbm-branding", "modules");

    private static final List<String> LOCALES =
            List.of("es", "fr", "de", "ru", "uk", "pl", "pt", "id", "tl", "vi", "zh", "hi");

    /** Every overlay this release added, by jar and package. */
    private static final List<String[]> OVERLAYS = List.of(
            new String[] {"org-netbeans-modules-projectui.jar", "org/netbeans/modules/project/ui/actions"},
            new String[] {"org-netbeans-spi-debugger-ui.jar", "org/netbeans/modules/debugger/ui/actions"},
            new String[] {"org-netbeans-modules-versioning-util.jar", "org/netbeans/modules/versioning/diff"},
            new String[] {"org-openide-actions.jar", "org/openide/actions"},
            new String[] {"org-netbeans-core-ui.jar", "org/netbeans/core/ui/sampler"});

    @Test
    @DisplayName("every toolbar overlay exists in all twelve languages, with the same keys")
    void everyLanguageIsCovered() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String[] o : OVERLAYS) {
            List<String> reference = null;
            for (String locale : LOCALES) {
                Path p = BRANDING.resolve(o[0]).resolve(o[1]).resolve("Bundle_" + locale + ".properties");
                if (!Files.isRegularFile(p)) {
                    wrong.add(o[0] + " [" + locale + "]: missing");
                    continue;
                }
                List<String> keys = new ArrayList<>(load(p).stringPropertyNames());
                keys.sort(String::compareTo);
                if (reference == null) {
                    reference = keys;
                } else if (!reference.equals(keys)) {
                    wrong.add(o[0] + " [" + locale + "]: keys " + keys + " != " + reference);
                }
            }
            assertThat(reference).as("%s should declare keys", o[0]).isNotNull().isNotEmpty();
        }
        assertThat(wrong).as("toolbar overlays that do not cover every language identically").isEmpty();
    }

    @Test
    @DisplayName("every choice pattern renders for -1 (no main project) through many, in every language")
    void everyPatternRenders() throws IOException {
        List<String> broken = new ArrayList<>();
        int rendered = 0;
        for (String[] o : OVERLAYS) {
            for (String locale : LOCALES) {
                Path p = BRANDING.resolve(o[0]).resolve(o[1]).resolve("Bundle_" + locale + ".properties");
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                Properties props = load(p);
                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key);
                    // a bare ASCII apostrophe opens a MessageFormat quote and
                    // eats the rest of the message (the v2.98.0 rule)
                    if (value.contains("'")) {
                        broken.add(key + " [" + locale + "]: bare ASCII apostrophe");
                    }
                    if (!value.contains("{0,choice")) {
                        continue;
                    }
                    for (int n : new int[] {-1, 0, 1, 2, 3, 5, 11}) {
                        try {
                            String out = new MessageFormat(value, Locale.of(locale))
                                    .format(new Object[] {n, "demo"});
                            rendered++;
                            if (out.contains("{") || out.contains("}")) {
                                broken.add(key + " [" + locale + "] n=" + n + ": unresolved placeholder in " + out);
                            }
                        } catch (RuntimeException malformed) {
                            broken.add(key + " [" + locale + "] n=" + n + ": " + malformed);
                        }
                    }
                }
            }
        }
        assertThat(broken).as("overlay values that would throw or misrender at paint time").isEmpty();
        assertThat(rendered).as("choice patterns should actually have been exercised").isGreaterThan(200);
    }

    @Test
    @DisplayName("the no-main-project branch is the one the toolbar shows on a fresh start, and it is translated")
    void theMinusOneBranchIsTranslated() throws IOException {
        // -1 is what a fresh IDE renders, and it is the branch that read
        // "&Run Main Project" in English in every translated build
        Path p = BRANDING.resolve("org-netbeans-modules-projectui.jar")
                .resolve("org/netbeans/modules/project/ui/actions").resolve("Bundle_uk.properties");
        String run = load(p).getProperty("LBL_RunMainProjectAction_Name");
        String out = new MessageFormat(run, Locale.of("uk")).format(new Object[] {-1, ""});
        assertThat(out).as("Ukrainian toolbar Run, with no main project set")
                .doesNotContain("Main Project").doesNotContain("Run").contains("Запустити");
    }

    private static Properties load(Path p) throws IOException {
        Properties props = new Properties();
        // .properties are ISO-8859-1 with \\uXXXX escapes, which is what the
        // platform's own loader expects and what these files are written as
        props.load(new StringReader(Files.readString(p, StandardCharsets.UTF_8)));
        return props;
    }
}
