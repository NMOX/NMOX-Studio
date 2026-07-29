package org.nmox.studio.core.http;

import java.net.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one-pool law: every HTTP-speaking subsystem shares a single
 * {@link HttpClient}, so the factory must hand back the same instance
 * every time, with the connect deadline baked in and redirects followed
 * the way the consumers were written for. No request leaves this test —
 * building a client opens no connection.
 */
class HttpClientFactoryTest {

    @Test
    @DisplayName("shared() is one client for the whole IDE, not a client per call")
    void sharedIsASingleton() {
        assertThat(HttpClientFactory.shared()).isSameAs(HttpClientFactory.shared());
    }

    @Test
    @DisplayName("the shared client carries a real connect timeout")
    void connectTimeoutIsSet() {
        assertThat(HttpClientFactory.shared().connectTimeout()).isPresent();
    }
}
