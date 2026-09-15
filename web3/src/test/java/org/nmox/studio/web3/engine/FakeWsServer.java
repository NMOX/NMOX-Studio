package org.nmox.studio.web3.engine;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import org.json.JSONObject;

/**
 * A minimal RFC 6455 server for driving the real JDK WebSocket client in
 * a test: one accepted connection at a time, a scripted handshake, and
 * text frames both ways. Just enough protocol to be honest — the client
 * under test is the production one.
 */
final class FakeWsServer implements AutoCloseable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket server;

    FakeWsServer() throws IOException {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        server.setSoTimeout(10_000);
    }

    /** The endpoint, with a secret-looking path so redaction can be asserted. */
    String url() {
        return "ws://127.0.0.1:" + server.getLocalPort() + "/v2/SECRETKEY";
    }

    Conn accept() throws IOException {
        Socket s = server.accept();
        s.setSoTimeout(10_000);
        return new Conn(s);
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    static final class Conn implements AutoCloseable {

        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        Conn(Socket socket) throws IOException {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        /** Reads the upgrade request and answers 101. */
        void handshake() throws IOException {
            String key = null;
            for (String line = readLine(); !line.isEmpty(); line = readLine()) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("sec-websocket-key:")) {
                    key = line.substring(line.indexOf(':') + 1).trim();
                }
            }
            if (key == null) {
                throw new IOException("no Sec-WebSocket-Key");
            }
            String accept;
            try {
                accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IOException(impossible);
            }
            out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        /** Swallows the upgrade request and never answers. */
        void readUpgradeOnly() throws IOException {
            for (String line = readLine(); !line.isEmpty(); line = readLine()) {
                // discard
            }
        }

        /** The next text message from the client, as JSON. */
        JSONObject readRequest() throws IOException {
            while (true) {
                int b0 = readByte();
                int b1 = readByte();
                int opcode = b0 & 0x0f;
                long len = b1 & 0x7f;
                if (len == 126) {
                    len = (readByte() << 8) | readByte();
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) {
                        len = (len << 8) | readByte();
                    }
                }
                byte[] mask = new byte[4];
                if ((b1 & 0x80) != 0) {
                    readFully(mask);
                }
                byte[] payload = new byte[(int) len];
                readFully(payload);
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i % 4];
                }
                if (opcode == 0x1) {
                    return new JSONObject(new String(payload, StandardCharsets.UTF_8));
                }
                if (opcode == 0x8) {
                    throw new EOFException("client closed");
                }
                // pings, pongs, continuation: skipped
            }
        }

        void reply(JSONObject request, Object result) throws IOException {
            sendText(new JSONObject().put("jsonrpc", "2.0").put("id", request.getLong("id"))
                    .put("result", result).toString());
        }

        void notify(String subscription, JSONObject result) throws IOException {
            sendText(new JSONObject().put("jsonrpc", "2.0").put("method", "eth_subscription")
                    .put("params", new JSONObject().put("subscription", subscription)
                            .put("result", result)).toString());
        }

        void sendText(String text) throws IOException {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x81);
            if (payload.length < 126) {
                frame.write(payload.length);
            } else if (payload.length < 65_536) {
                frame.write(126);
                frame.write(payload.length >>> 8);
                frame.write(payload.length & 0xff);
            } else {
                frame.write(127);
                for (int i = 7; i >= 0; i--) {
                    frame.write((int) ((long) payload.length >>> (8 * i)) & 0xff);
                }
            }
            frame.write(payload);
            out.write(frame.toByteArray());
            out.flush();
        }

        void sendRaw(byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        /** Drops the TCP connection with no close frame — a crashed node, a cut cable. */
        void kill() throws IOException {
            socket.setSoLinger(true, 0);
            socket.close();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int c;
            while ((c = readByte()) != '\n') {
                if (c != '\r') {
                    line.write(c);
                }
            }
            return line.toString(StandardCharsets.US_ASCII);
        }

        private int readByte() throws IOException {
            int c = in.read();
            if (c < 0) {
                throw new EOFException();
            }
            return c;
        }

        private void readFully(byte[] buffer) throws IOException {
            int off = 0;
            while (off < buffer.length) {
                int n = in.read(buffer, off, buffer.length - off);
                if (n < 0) {
                    throw new EOFException();
                }
                off += n;
            }
        }
    }
}
