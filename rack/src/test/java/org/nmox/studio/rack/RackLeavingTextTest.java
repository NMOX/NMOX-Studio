package org.nmox.studio.rack;

import java.nio.file.Path;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.RackShare;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the sender reads before a rack leaves (v2.179.0). The order matters —
 * what should give them pause comes first — and so does what is NOT shown: a
 * credential is never printed whole, and an empty pane never sits under a
 * heading that promises content (walked 2026-09-18).
 */
class RackLeavingTextTest {

    private static final Path HOME = Path.of(System.getProperty("java.io.tmpdir"), "sender-home");
    private Locale before;

    @BeforeEach
    void english() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    private static JSONObject shared(String... commands) {
        JSONArray devices = new JSONArray();
        for (String c : commands) {
            devices.put(new JSONObject().put("type", "cmd").put("state", new JSONObject().put("command", c)));
        }
        devices.put(new JSONObject().put("type", "console"));
        JSONObject patch = new JSONObject().put("version", 1).put("devices", devices).put("cables", new JSONArray());
        return RackShare.export(patch, HOME, "2.179.0");
    }

    @Test
    @DisplayName("a rack whose devices store no command says that nothing travels — never a heading over an empty pane")
    void nothingTravels() {
        String text = RackTopComponent.leavingText(shared());
        assertThat(text).contains("1 device, 0 cables").contains("No command, path or address travels");
        assertThat(text).doesNotContain("Settings that travel:");
    }

    @Test
    @DisplayName("a command travels and is shown exactly as it will arrive, with the sender's home already hidden")
    void settingsAreShownAsTheyLeave() {
        String home = HOME.toAbsolutePath().normalize().toString().replace('\\', '/');
        String text = RackTopComponent.leavingText(shared("tail -f " + home + "/logs/app.log"));
        assertThat(text).contains("Settings that travel:").contains("tail -f ~/logs/app.log");
        assertThat(text).doesNotContain(home);
    }

    @Test
    @DisplayName("a value that looks like a credential is warned about FIRST and never printed whole")
    void secretsComeFirstAndMasked() {
        String token = "ghp_0123456789abcdefghijABCDEFGHIJ012345";
        String text = RackTopComponent.leavingText(shared("curl -H token=" + token + " http://localhost:3000"));
        assertThat(text).doesNotContain(token);
        assertThat(text.indexOf("LOOKS LIKE A CREDENTIAL"))
                .as("the warning leads the page").isNotNegative()
                .isLessThan(text.indexOf("devices,"));
    }

    @Test
    @DisplayName("somebody else's home directory survives the rewrite — it is not the sender's to hide — and is pointed out")
    void anotherHomeIsPointedOut() {
        String text = RackTopComponent.leavingText(shared("cat /Users/somebodyelse/notes.txt"));
        assertThat(text).contains("Still names somebody");
        assertThat(text.indexOf("Still names somebody")).isLessThan(text.indexOf("devices,"));
    }
}
