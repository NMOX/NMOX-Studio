package org.nmox.studio.ui.irc;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Command feedback lands where the command was typed (v2.34.4, found
 * live on Libera): commandFilter answered into the NETWORK-STATUS
 * transcript while the user watched a channel — spoken in the wrong
 * room reads as silence, and /lastlog has always answered in place.
 * The source gate pins the routing (the CrudGesturesSpeakTest idiom):
 * commandFilter's status key must consult the ACTIVE transcript first.
 */
class FilterFeedbackInPlaceTest {

    @Test
    @DisplayName("commandFilter's feedback key consults the active transcript")
    void filterAnswersInPlace() throws Exception {
        String src = Files.readString(Path.of("src", "main", "java", "org",
                "nmox", "studio", "ui", "irc", "IrcTopComponent.java"))
                .replace("\r\n", "\n");
        int at = src.indexOf("private void commandFilter(String args)");
        assertThat(at).as("commandFilter exists").isGreaterThanOrEqualTo(0);
        String head = src.substring(at, src.indexOf("switch", at));
        assertThat(head)
                .as("the status key prefers the ACTIVE transcript, network "
                        + "status only as the nothing-selected fallback")
                .contains("activeKey != null ? activeKey : key(activeNetwork(), \"\")");
    }
}
