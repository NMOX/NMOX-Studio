package org.nmox.studio.ui.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The facade's degradation law (v1.199.0): when the platform's embedded
 * WebKit is absent — module missing, JavaFX missing, or a factory that
 * cannot instantiate — {@link EmbeddedBrowserProvider} must say so with
 * {@code open() == false} / a null factory, so every caller (SCOPE's
 * TARGET knob, the serving chip) falls back to the system browser and an
 * OPEN press is never a dead click. An engine problem is an absent
 * engine, never an error dialog.
 */
class EmbeddedBrowserProviderTest {

    private static final String SETTINGS_PATH
            = "Services/Browsers/webviewBrowser.settings";

    @AfterEach
    void cleanConfig() throws Exception {
        FileObject created = FileUtil.getConfigFile(SETTINGS_PATH);
        if (created != null) {
            created.delete();
        }
    }

    @Test
    @DisplayName("no webview settings file → open() returns false so the caller falls back")
    void openReturnsFalseWithoutEngine() {
        assertThat(FileUtil.getConfigFile(SETTINGS_PATH))
                .as("test config filesystem must not carry the platform's settings file")
                .isNull();
        assertThat(new EmbeddedBrowserProvider().open("https://example.com"))
                .as("an absent engine must be reported, not swallowed — the "
                        + "false return is what routes the URL to the system browser")
                .isFalse();
        assertThat(EmbeddedBrowserProvider.embeddedFactory()).isNull();
    }

    @Test
    @DisplayName("hidden=true (the platform's own no-JavaFX verdict) → no factory")
    void hiddenSettingsMeansNoEngine() throws Exception {
        FileObject settings = FileUtil.createData(
                FileUtil.getConfigRoot(), SETTINGS_PATH);
        // the shipped file's "hidden" is a methodvalue on the platform's
        // BrowserFactory.isHidden(); a plain boolean reproduces the read
        settings.setAttribute("hidden", Boolean.TRUE);

        assertThat(EmbeddedBrowserProvider.embeddedFactory())
                .as("the platform's hidden verdict IS the availability probe — "
                        + "a hidden settings file must never yield a factory")
                .isNull();
        assertThat(new EmbeddedBrowserProvider().open("https://example.com"))
                .isFalse();
    }

    @Test
    @DisplayName("a settings file that cannot instantiate a factory is an absent engine, not an error")
    void unparsableSettingsDegradesToNull() throws Exception {
        FileObject settings = FileUtil.createData(
                FileUtil.getConfigRoot(), SETTINGS_PATH);
        try (var out = settings.getOutputStream()) {
            out.write("not a settings serialization".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        assertThat(EmbeddedBrowserProvider.embeddedFactory())
                .as("instantiation failure must fall through to null — the "
                        + "browser window shows its honest note instead")
                .isNull();
        assertThat(new EmbeddedBrowserProvider().open("https://example.com"))
                .isFalse();
    }
}
