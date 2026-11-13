package org.nmox.studio.dbstudio.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DB Studio's notifications carry text the product did not write: a
 * driver's error (whose head pgJDBC fills from the server's own severity
 * field, see HumanizeScopeTest) as the detail, a connection's name from
 * {@code .nmoxdb.json} in the title.
 *
 * <p>Read from the platform's bytecode (core.ui RELEASE310,
 * {@code NotificationImpl.createDetails}): with no action the detail is
 * handed to {@code new JLabel(text)} as it is, so a detail beginning
 * {@code <html>} is painted as markup; the title goes through
 * {@code XMLUtil.toElementContent}, which throws for a control character.
 * The notification that is handed a caller's text ({@code balloon}, whose
 * detail is a variable and not a bundle sentence) therefore gives its
 * detail to {@code PlainText.plain} and its title to
 * {@code PlainText.oneLine}; the others begin with the product's own words.
 */
class NotificationDetailIsPlainTest {

    private static final Pattern NOTIFY = Pattern.compile(
            "NotificationDisplayer\\s*\\.\\s*getDefault\\(\\)\\s*\\.\\s*notify\\(([^;]*);", Pattern.DOTALL);

    @Test
    @DisplayName("a notification handed a caller's detail or title passes a plain detail and a one-line title")
    void everyNotificationIsPlain() throws Exception {
        Path root = Path.of("src/main/java");
        int seen = 0;
        try (var files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String source = Files.readString(file, StandardCharsets.UTF_8)
                        .replaceAll("(?m)^\\s*//.*$", "");
                Matcher m = NOTIFY.matcher(source);
                while (m.find()) {
                    String args = m.group(1);
                    // a variable where the text goes: someone else's words
                    if (args.matches("(?s).*\\b(detail|error|openError|message)\\b.*")) {
                        seen++;
                        assertThat(args).as("the detail of the notification in %s is a driver's or a file's text", file)
                                .contains("PlainText.plain(detail)");
                    }
                    if (args.matches("(?s).*\\btitle\\b.*")) {
                        assertThat(args).as("the title of the notification in %s may carry a name from disk", file)
                                .contains("PlainText.oneLine(title");
                    }
                }
            }
        }
        assertThat(seen).as("the population is not empty: the balloon is still there to be held").isPositive();
    }

    @Test
    @DisplayName("measured: the platform's markup builder refuses a control character, which a connection's name may hold")
    void thePlatformThrowsForAControlCharacterInATitle() {
        assertThatThrownBy(() -> org.openide.xml.XMLUtil.toElementContent("Connect failed: shop\u0001db"))
                .isInstanceOf(java.io.CharConversionException.class);
        assertThat(org.nmox.studio.core.util.PlainText.oneLine("Connect failed: shop\u0001db", 300))
                .isEqualTo("Connect failed: shop db");
    }
}
