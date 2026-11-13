package org.nmox.studio.infra.ops.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Terminal tab's title cannot be markup. The title of a Logs or Shell
 * tab begins with the service's label, which the HOST reports; the
 * Terminal's tabs are a JTabbedPane, and Swing renders a title that
 * begins with {@code <html>}. Found by the review of 3.12.0 as a
 * hypothesis it could not reach; held here at the one place a title is
 * handed to the platform.
 */
class TerminalDoorTitleTest {

    @Test
    @DisplayName("a label that is markup becomes a title Swing does not render")
    void markupLabelIsText() {
        String hostile = "<html><img src=\"http://203.0.113.9/seen\"> logs";
        // the control: this is what a tab would have been given
        assertThat(BasicHTML.isHTMLString(hostile)).as("Swing renders the raw title").isTrue();
        String title = TerminalDoor.tabTitle(hostile);
        assertThat(BasicHTML.isHTMLString(title)).as("and not the title the door makes").isFalse();
        assertThat(title).doesNotContain("<").doesNotContain(">");
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(title, new javax.swing.JPanel());
        assertThat(tabs.getClientProperty(BasicHTML.propertyKey)).isNull();
    }

    @Test
    @DisplayName("an ordinary title is unchanged, a control character goes, null is empty")
    void ordinaryTitles() {
        assertThat(TerminalDoor.tabTitle("shop-web logs")).isEqualTo("shop-web logs");
        assertThat(TerminalDoor.tabTitle("\u05DE\u05E2\u05D8\u05E4\u05EA web")).isEqualTo("\u05DE\u05E2\u05D8\u05E4\u05EA web");
        assertThat(TerminalDoor.tabTitle("a\nb\u0007c")).isEqualTo("abc");
        assertThat(TerminalDoor.tabTitle(null)).isEmpty();
    }

    @Test
    @DisplayName("the door makes the title before the platform sees it")
    void theDoorUsesIt() throws Exception {
        // a Windows checkout has CRLF, and the slice below ends at a line feed
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/infra/ops/ui/TerminalDoor.java"))
                .replace("\r\n", "\n");
        int open = src.indexOf("public static void open(String rawTitle");
        assertThat(open).as("open takes the raw title under a name that says so").isPositive();
        String made = "String title = tabTitle(rawTitle);";
        int firstUse = src.indexOf(made, open);
        int firstTab = src.indexOf("openTab(", open);
        assertThat(firstUse).as("the title is made unable to be markup").isPositive();
        assertThat(firstUse).as("before a tab is opened with it").isLessThan(firstTab);
        assertThat(src.substring(firstUse + made.length(), src.indexOf("\n    }\n", firstTab)))
                .as("and the raw title is not used again").doesNotContain("rawTitle");
    }
}
