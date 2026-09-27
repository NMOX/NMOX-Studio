package org.nmox.studio.rack;

import java.io.File;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.RackIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Share… to a file may not write a project's own patch (3.4, question 1).
 * A shared rack is a copy for somebody else — home paths rewritten to
 * {@code ~}, a header naming the version — and before the review Share wrote
 * wherever it was pointed, so choosing {@code .nmoxrack.json} replaced the
 * project's rack past every check Save asks: the lock, the conflict, the
 * teammate's pull.
 */
class ShareRefusesThePatchTest {

    @Test
    @DisplayName("Share refuses a file named like a project's patch, in any folder, and says why")
    void shareRefusesThePatchFile() {
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        try {
            String refused = RackTopComponent.shareRefusal(
                    new File(new File("proj"), RackIO.DEFAULT_FILENAME));
            assertThat(refused).as("a shared copy never lands on the project's own patch")
                    .isNotNull().contains(RackIO.DEFAULT_FILENAME);
            assertThat(RackTopComponent.shareRefusal(
                    new File(new File("elsewhere/deeper"), RackIO.DEFAULT_FILENAME)))
                    .as("any folder: the name is the project's patch wherever it sits").isNotNull();
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("any other name is the user's to choose")
    void otherNamesAreAllowed() {
        assertThat(RackTopComponent.shareRefusal(new File("proj", "my-rack.nmoxrack.json"))).isNull();
        assertThat(RackTopComponent.shareRefusal(new File("proj", "shared.json"))).isNull();
    }
}
