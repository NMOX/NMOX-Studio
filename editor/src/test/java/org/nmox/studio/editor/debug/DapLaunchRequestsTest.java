package org.nmox.studio.editor.debug;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The requests the debug adapters are sent, as values: what a launch
 * configuration's runtime, interpreter and attach become on the wire. The
 * real js-debug is driven with these same requests in
 * {@code RealJsDebugIntegrationTest}; this pins their shape where no node
 * is needed.
 */
class DapLaunchRequestsTest {

    private static final File PROGRAM = new File("/work/app/server.js");
    private static final File CWD = new File("/work/app");
    private static final File WORKSPACE = new File("/work");

    @Test
    @DisplayName("a launch that names no runtime is the request it always was: no runtime field, no workspace field")
    void plainLaunchIsUnchanged() {
        Map<String, Object> plain = DapDebugAction.nodeLaunchRequest(PROGRAM, CWD, List.of(), Map.of());
        assertThat(plain).containsOnlyKeys("type", "request", "name", "program", "cwd", "console", "outputCapture")
                .containsEntry("type", "pwa-node").containsEntry("request", "launch")
                .containsEntry("name", "server.js")
                .containsEntry("program", PROGRAM.getAbsolutePath())
                .containsEntry("cwd", CWD.getAbsolutePath())
                .containsEntry("outputCapture", "std");
        assertThat(DapDebugAction.nodeLaunchRequest("cfg", PROGRAM, CWD, List.of("a"), Map.of("K", "v"),
                null, List.of(), WORKSPACE))
                .containsEntry("args", List.of("a")).containsEntry("env", Map.of("K", "v"))
                .doesNotContainKeys("runtimeExecutable", "runtimeArgs", "__workspaceFolder");
    }

    @Test
    @DisplayName("a runtime crosses as js-debug's own runtimeExecutable and runtimeArgs, with the workspace it is looked for under")
    void runtimeCrosses() {
        Map<String, Object> tsx = DapDebugAction.nodeLaunchRequest("cfg", PROGRAM, CWD, List.of(), Map.of(),
                "tsx", List.of("--inspect-wait"), WORKSPACE);
        assertThat(tsx).containsEntry("runtimeExecutable", "tsx")
                .containsEntry("runtimeArgs", List.of("--inspect-wait"))
                .containsEntry("__workspaceFolder", WORKSPACE.getAbsolutePath())
                .containsEntry("program", PROGRAM.getAbsolutePath());

        Map<String, Object> flags = DapDebugAction.nodeLaunchRequest("cfg", PROGRAM, CWD, List.of(), Map.of(),
                null, List.of("--experimental-strip-types"), WORKSPACE);
        assertThat(flags).containsEntry("runtimeArgs", List.of("--experimental-strip-types"))
                .as("arguments to the default runtime name no runtime").doesNotContainKeys("runtimeExecutable",
                        "__workspaceFolder");
    }

    @Test
    @DisplayName("a runtime that is the whole command sends no program, and is named after its configuration")
    void runtimeWithoutProgram() {
        Map<String, Object> npm = DapDebugAction.nodeLaunchRequest("npm run dev", null, CWD, List.of(), Map.of(),
                "npm", List.of("run", "dev"), WORKSPACE);
        assertThat(npm).doesNotContainKey("program")
                .containsEntry("name", "npm run dev")
                .containsEntry("runtimeExecutable", "npm")
                .containsEntry("runtimeArgs", List.of("run", "dev"));
    }

    @Test
    @DisplayName("an attach names the address, the port and nothing that would start or resume a program")
    void attachRequest() {
        assertThat(DapDebugAction.nodeAttachRequest("Attach", "localhost", 9229, CWD))
                .containsOnlyKeys("type", "request", "name", "address", "port", "cwd")
                .containsEntry("type", "pwa-node").containsEntry("request", "attach")
                .containsEntry("address", "localhost").containsEntry("port", 9229)
                .containsEntry("cwd", CWD.getAbsolutePath());
    }

    @Test
    @DisplayName("a Python launch carries its interpreter only when one is named")
    void pythonInterpreter() {
        File program = new File("/work/app/main.py");
        assertThat(DapDebugAction.pythonLaunchRequest(program, CWD, List.of(), Map.of(), null))
                .containsOnlyKeys("type", "request", "program", "cwd", "console", "justMyCode");
        assertThat(DapDebugAction.pythonLaunchRequest(program, CWD, List.of("-v"), Map.of("K", "v"),
                "/work/app/.venv/bin/python"))
                .containsEntry("python", "/work/app/.venv/bin/python")
                .containsEntry("args", List.of("-v")).containsEntry("env", Map.of("K", "v"));
    }

    @Test
    @DisplayName("listening: true where a loopback port accepts, false where nothing does or the name is not this machine")
    void listening() throws Exception {
        int port;
        try (ServerSocket open = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = open.getLocalPort();
            assertThat(DapDebugAction.listening(InetAddress.getLoopbackAddress().getHostAddress(), port)).isTrue();
        }
        assertThat(DapDebugAction.listening(InetAddress.getLoopbackAddress().getHostAddress(), port))
                .as("the listener is gone").isFalse();
        assertThat(DapDebugAction.listening("192.0.2.1", port)).as("a literal address that is not this machine is never dialed")
                .isFalse();
    }
    @Test
    @DisplayName("an attach to localhost names the IPv4 loopback when that is the one listening; a literal passes as written")
    void attachNamesTheLoopbackThatAnswered() throws Exception {
        try (java.net.ServerSocket v4 = new java.net.ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            int port = v4.getLocalPort();
            // node's inspector listens here; the adapter's Node may resolve localhost to ::1 first
            assertThat(DapDebugAction.answeringAddress("localhost", port)).isEqualTo("127.0.0.1");
            assertThat(DapDebugAction.answeringAddress("127.0.0.1", port)).isEqualTo("127.0.0.1");
        }
        try (java.net.ServerSocket v4 = new java.net.ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            int port = v4.getLocalPort();
            // the macOS runner's resolver: localhost is ::1 and nothing else
            InetAddress[] v6Only = {InetAddress.getByName("::1")};
            assertThat(DapDebugAction.answeringAddress("localhost", port, v6Only))
                    .as("both loopbacks are this machine; the one listening is named").isEqualTo("127.0.0.1");
            assertThat(DapDebugAction.answeringAddress("::1", port, v6Only))
                    .as("a literal is asked as written, and nothing else").isNull();
        }
        try (java.net.ServerSocket closed = new java.net.ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            int port = closed.getLocalPort();
            closed.close();
            assertThat(DapDebugAction.answeringAddress("localhost", port)).as("nobody listening").isNull();
        }
    }
}
