package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The macOS bundle does not claim a language control it does not have
 * (ledger 94, v2.106.0).
 *
 * <p>Declaring {@code CFBundleLocalizations} lists an app under System
 * Settings ▸ Language &amp; Region ▸ Applications and lets a user choose a
 * language for it. Measured with a probe bundle of this exact shape — a
 * shell wrapper that execs {@code java} — the per-app {@code AppleLanguages}
 * preference never reaches the JVM: {@code Locale.getDefault()} stayed
 * {@code en_US} with the preference set and without it, byte-identical.
 *
 * <p>So the key would advertise a control the IDE ignores, which is worse
 * than its absence. This is the unusual gate that exists to keep something
 * OUT — a reasonable-looking one-line addition, made by someone who did not
 * run the probe, would otherwise ship a language menu that does nothing.
 */
class BundleLocalizationsTest {

    private static final Path DMG = Path.of("..", "packaging", "macos", "build-dmg.sh");

    @Test
    @DisplayName("the Info.plist declares no localizations, and says why at the point of temptation")
    void noLocalizationsClaimed() throws IOException {
        String script = Files.readString(DMG, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(script).as("the plist heredoc").contains("<key>CFBundleName</key>");
        // the KEY, not the word: the comment beside it names the key on purpose
        assertThat(script)
                .as("CFBundleLocalizations would offer a language control the IDE ignores "
                        + "— measured, ledger 94; remove this only with a probe that says otherwise")
                .doesNotContain("<key>CFBundleLocalizations</key>");
        assertThat(script)
                .as("the reason must sit where the next author would add the key")
                .contains("ledger 94");
    }

    @Test
    @DisplayName("the ledger keeps the evidence, not just the verdict")
    void theLedgerShowsItsWork() throws IOException {
        String ledger = Files.readString(Path.of("..", "docs", "engineering", "tech-debt.md"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(ledger).contains("### 94.");
        // a deferral without a reproduction is a guess with a date on it
        assertThat(ledger).as("the probe's own command line").contains("AppleLanguages -array uk");
        assertThat(ledger).as("what the probe measured").contains("en_US");
        assertThat(ledger).as("what closing it would take").contains("UiLocale.SUPPORTED");
    }
}
