package org.nmox.studio.apiclient.api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Request;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 3.4, "when something goes wrong": a server answered 200 with a
 * Content-Length of 1000, sent 500 bytes and dropped the connection. API
 * Studio said "No route — closed": the JDK's bare {@code IOException}
 * message, with the real cause ("fixed content-length: 1000, bytes
 * received: 500") two levels down and a verdict that denied the server had
 * answered at all. The response now carries the status that arrived and
 * the shortfall, and is still not a usable response.
 */
class ApiBodyBrokeTest {

    @Test
    @DisplayName("A 200 whose body drops mid-transfer names the status and the shortfall, not \"closed\"")
    void midBodyDropSaysWhatHappened() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            Thread serving = new Thread(() -> {
                try (Socket s = server.accept()) {
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                    String line;
                    while ((line = r.readLine()) != null && !line.isEmpty()) {
                        // consume the request head
                    }
                    OutputStream out = s.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    out.write(new byte[500]);
                    out.flush();
                } catch (IOException ignored) {
                    // the client side asserts the outcome
                }
            }, "api-body-broke-fixture");
            serving.setDaemon(true);
            serving.start();

            Request req = new Request();
            req.method = "GET";
            req.url = "http://127.0.0.1:" + server.getLocalPort() + "/items";
            ApiResponse r = new ApiClient().send(req, Map.of());

            assertThat(r.bodyBroke()).as("the head arrived, the body did not").isTrue();
            assertThat(r.headStatus()).isEqualTo(200);
            assertThat(r.reached())
                    .as("a torn body is not a response tests or Save may treat as the answer")
                    .isFalse();
            assertThat(r.error()).contains("500").isNotEqualTo("closed");
        }
    }

    @Test
    @DisplayName("A plain failure is not mistaken for a broken body")
    void refusedIsStillNoRoute() {
        assertThat(ApiResponse.failure(5, "Connection refused").bodyBroke()).isFalse();
    }
}
