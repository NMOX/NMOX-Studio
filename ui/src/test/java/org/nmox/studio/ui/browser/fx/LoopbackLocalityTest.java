package org.nmox.studio.ui.browser.fx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binds the v1.259.0 loopback rewrite to the v1.228.0 save-to-reload
 * gate: core's LoopbackUrls may rewrite a localhost URL to [::1] or
 * 127.0.0.1, and if either form stopped counting as LOCAL here, the
 * rewrite would silently disable save -> see for exactly the dev
 * servers it exists to reach.
 */
class LoopbackLocalityTest {

    @Test
    @DisplayName("both loopback rewrite forms stay local for save-to-reload")
    void rewrittenFormsStayLocal() {
        assertThat(LocalUrls.isLocal("http://[::1]:4321/")).isTrue();
        assertThat(LocalUrls.isLocal("http://127.0.0.1:4321/")).isTrue();
    }
}
