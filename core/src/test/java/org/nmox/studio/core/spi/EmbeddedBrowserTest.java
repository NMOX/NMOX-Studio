package org.nmox.studio.core.spi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.junit.MockServices;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The soft-dependency seam's two states (v1.199.0, the OracleAsk
 * idiom): with no provider installed {@link EmbeddedBrowser#find()}
 * must be null — the caller's cue to use the system browser — and with
 * a provider registered the lookup must return it and route the URL.
 * Consumers (SCOPE's TARGET knob) branch on exactly this.
 */
class EmbeddedBrowserTest {

    @AfterEach
    void resetLookup() {
        MockServices.setServices();
    }

    @Test
    @DisplayName("no provider installed → find() is null, the caller falls back")
    void absentProviderIsNull() {
        assertThat(EmbeddedBrowser.find())
                .as("a bare Lookup must not invent a browser — null is the "
                        + "documented fall-back-to-system-browser signal")
                .isNull();
    }

    @Test
    @DisplayName("a registered provider is found and receives the URL")
    void registeredProviderIsFoundAndRouted() {
        MockServices.setServices(RecordingBrowser.class);

        EmbeddedBrowser found = EmbeddedBrowser.find();
        assertThat(found).isInstanceOf(RecordingBrowser.class);
        assertThat(found.open("https://example.com")).isTrue();
        assertThat(((RecordingBrowser) found).lastUrl)
                .isEqualTo("https://example.com");
    }

    public static final class RecordingBrowser implements EmbeddedBrowser {
        String lastUrl;

        @Override
        public boolean open(String url) {
            lastUrl = url;
            return true;
        }
    }
}
