package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Linux menu entry speaks every language the IDE speaks (v2.105.0).
 *
 * <p>{@code Comment=} is the line a GNOME or KDE menu shows under the
 * application's name, and {@code GenericName=} is what an overview shows
 * instead of the brand — both were English for every user of the other
 * twelve languages. freedesktop reads {@code Comment[xx]} for the session
 * locale and falls back to the bare key, so this is a dozen lines rather
 * than a mechanism.
 *
 * <p>The values live inside an UNQUOTED shell heredoc, which is the sharp
 * edge here: a {@code $} or a backtick in a translation would be expanded
 * by the build and ship something else entirely, or nothing. That is
 * checked below rather than remembered.
 */
class DesktopEntryLanguagesTest {

    private static final Path SCRIPT = Path.of("..", "packaging", "linux", "build-packages.sh");

    private static String entry() throws IOException {
        String body = Files.readString(SCRIPT, StandardCharsets.UTF_8).replace("\r\n", "\n");
        int a = body.indexOf("[Desktop Entry]");
        int b = body.indexOf("\nDESKTOP", a);
        assertThat(a).as("the .desktop heredoc").isGreaterThan(0);
        return body.substring(a, b);
    }

    /** The languages the IDE's chrome speaks, English aside — one vocabulary. */
    private static List<String> uiLanguages() throws IOException {
        Path src = Path.of("..", "core", "src", "main", "java", "org", "nmox", "studio",
                "core", "util", "UiLocale.java");
        String body = Files.readString(src, StandardCharsets.UTF_8);
        String list = body.substring(body.indexOf("SUPPORTED = List.of("));
        list = list.substring(0, list.indexOf(";"));
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("new Choice\\(\"([a-z]{2})\"").matcher(list);
        while (m.find()) {
            if (!"en".equals(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    @Test
    @DisplayName("every IDE language has a localized Comment and GenericName")
    void everyLanguageIsNamed() throws IOException {
        String entry = entry();
        List<String> missing = new ArrayList<>();
        for (String lang : uiLanguages()) {
            if (!entry.contains("Comment[" + lang + "]=")) {
                missing.add("Comment[" + lang + "]");
            }
            if (!entry.contains("GenericName[" + lang + "]=")) {
                missing.add("GenericName[" + lang + "]");
            }
        }
        assertThat(uiLanguages()).as("the IDE's languages").hasSizeGreaterThanOrEqualTo(12);
        assertThat(missing).as("a menu line left in English for that language's users").isEmpty();
        assertThat(entry).as("the fallbacks the spec reads when no locale matches")
                .contains("\nComment=").contains("\nGenericName=");
    }

    @Test
    @DisplayName("no localized value can be expanded by the unquoted heredoc")
    void nothingExpandsInTheHeredoc() throws IOException {
        List<String> dangerous = new ArrayList<>();
        for (String line : entry().split("\n")) {
            if (!line.contains("=")) {
                continue;
            }
            String value = line.substring(line.indexOf('=') + 1);
            // Exec= carries the spec's own %F and the wrapper path; it is not
            // a translated value and is left exactly as the spec wants it
            if (line.startsWith("Exec=")) {
                continue;
            }
            if (value.contains("$") || value.contains("`") || value.contains("\\")) {
                dangerous.add(line);
            }
        }
        assertThat(dangerous)
                .as("an unquoted heredoc would expand these at build time, shipping "
                        + "something other than what is written")
                .isEmpty();
    }

    @Test
    @DisplayName("no two languages are declared twice, and none is empty")
    void valuesAreWellFormed() throws IOException {
        Set<String> seen = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^((?:Comment|GenericName)(?:\\[[a-z]{2}\\])?)=(.*)$")
                .matcher(entry());
        int count = 0;
        while (m.find()) {
            count++;
            if (!seen.add(m.group(1))) {
                problems.add("declared twice: " + m.group(1));
            }
            if (m.group(2).isBlank()) {
                problems.add("blank: " + m.group(1));
            }
        }
        assertThat(count).as("the census should see every menu line").isGreaterThanOrEqualTo(26);
        assertThat(problems).isEmpty();
    }
}
