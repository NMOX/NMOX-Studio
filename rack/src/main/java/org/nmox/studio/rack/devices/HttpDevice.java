package org.nmox.studio.rack.devices;

import java.awt.BorderLayout;
import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * PING Request Probe: a REST console on a faceplate. Fires HTTP
 * requests, reports status and latency, and VIEW opens the console -
 * pretty-printed responses and the last 50 exchanges with one-click
 * replay. HEADERS carries auth and content types but is session-only:
 * tokens must never ride the committable patch file (the ATMOS rule).
 */
public class HttpDevice extends RackDevice {

    private static final String[] METHODS = {"GET", "HEAD", "POST", "PUT", "DELETE"};
    private static final int HISTORY_MAX = 50;

    private final Knob methodKnob;
    private final LcdDisplay urlLcd;
    private final LcdDisplay bodyLcd;
    private final LcdDisplay headersLcd;
    private final LcdDisplay statusLcd;
    private final VuMeter latencyMeter;
    private final Led okLed;
    private final Led failLed;
    private final Deque<Exchange> history = new ArrayDeque<>();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** One request/response round trip, for the console history. */
    record Exchange(String method, String url, int status, long ms,
            String responseHeaders, String responseBody) {
    }

    public HttpDevice() {
        super("http", "PING", "REQUEST PROBE", new Color(96, 180, 100), 2);

        methodKnob = place(new Knob("METHOD", METHODS, 0), 112, 40);
        urlLcd = place(new LcdDisplay(260, 1), 184, 40);
        urlLcd.setText("http://localhost:3000");
        urlLcd.setEditable("Request URL");
        headersLcd = place(new LcdDisplay(260, 1), 184, 66);
        headersLcd.setText("");
        headersLcd.setEditable("Headers (Name: value; Name: value) — session-only");
        headersLcd.setToolTipText("Auth and content headers. Session-only — tokens never persist into the patch file.");
        bodyLcd = place(new LcdDisplay(260, 1), 184, 92);
        bodyLcd.setText("");
        bodyLcd.setEditable("Request body (sent on POST/PUT)");
        RackButton send = place(new RackButton("SEND", RackStyle.GO), RackStyle.TRANSPORT_X, 40);
        RackButton view = place(new RackButton("VIEW", RackStyle.QUERY), RackStyle.TRANSPORT_X, 78);
        view.setToolTipText("Open the console: pretty-printed responses and the last 50 exchanges");
        statusLcd = place(new LcdDisplay(120, 1), 460, 40);
        statusLcd.setText("—");
        latencyMeter = place(new VuMeter("LATENCY", false), 460, 78);
        okLed = place(new Led("2XX", RackStyle.GO), 600, 46);
        failLed = place(new Led("ERR", RackStyle.STOP), 640, 46);

        send.addActionListener(e -> fire());
        view.addActionListener(e -> showConsole());

        addInPort("send", "SEND", SignalType.TRIGGER);
        addInPort("url", "URL", SignalType.DATA);
        addOutPort("ok", "OK", SignalType.TRIGGER);
        addOutPort("fail", "FAIL", SignalType.TRIGGER);
        addOutPort("body", "BODY", SignalType.DATA);

        param("method", methodKnob);
        param("url", urlLcd);
        param("body", bodyLcd);
        // headers are NOT a param: auth tokens must not persist into the
        // committable .nmoxrack.json (same rule as ATMOS's EXTRA line)
    }

    private void fire() {
        String url = urlLcd.getText().trim();
        if (url.isEmpty()) {
            return;
        }
        onEdt(() -> {
            okLed.setOn(false);
            failLed.setOn(false);
            statusLcd.setTextColor(RackStyle.LCD_AMBER);
            statusLcd.setText("…");
        });
        String method = METHODS[methodKnob.getSelectedIndex()];
        long start = System.nanoTime();
        HttpRequest request;
        try {
            request = buildRequest(method, url, bodyLcd.getText(), parseHeaders(headersLcd.getText()));
        } catch (RuntimeException ex) {
            onEdt(() -> {
                failLed.setOn(true);
                statusLcd.setTextColor(new Color(255, 90, 80));
                statusLcd.setText("BAD URL");
            });
            return;
        }
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    long ms = (System.nanoTime() - start) / 1_000_000;
                    if (error != null) {
                        record(new Exchange(method, url, -1, ms, "",
                                error.getMessage() == null ? "no route" : error.getMessage()));
                        onEdt(() -> {
                            failLed.setOn(true);
                            statusLcd.setTextColor(new Color(255, 90, 80));
                            statusLcd.setText("NO ROUTE " + ms + "ms");
                        });
                        emit("fail", Signal.trigger(false));
                        return;
                    }
                    boolean ok = response.statusCode() < 400;
                    String body = response.body() == null ? "" : response.body();
                    record(new Exchange(method, url, response.statusCode(), ms,
                            formatHeaders(response), body));
                    latencyMeter.setLevel(Math.min(1.0, ms / 2000.0));
                    onEdt(() -> {
                        okLed.setOn(ok);
                        failLed.setOn(!ok);
                        statusLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                        statusLcd.setText(response.statusCode() + "  " + ms + "ms");
                    });
                    emit(ok ? "ok" : "fail", Signal.trigger(ok));
                    if (!body.isEmpty()) {
                        // full payload down the cable (PHOSPHOR holds 5k lines)
                        emit("body", Signal.data(body.length() > 65_536
                                ? body.substring(0, 65_536) + "…" : body));
                    }
                });
    }

    private synchronized void record(Exchange exchange) {
        history.addFirst(exchange);
        while (history.size() > HISTORY_MAX) {
            history.removeLast();
        }
    }

    /**
     * Builds the request: POST/PUT carry the body field, dialed headers
     * apply on top (an explicit Content-Type wins over the JSON sniff).
     * Static and side-effect-free so the wiring is unit-testable.
     */
    static HttpRequest buildRequest(String method, String url, String body, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15));
        boolean sendsBody = ("POST".equals(method) || "PUT".equals(method))
                && body != null && !body.isBlank();
        if (sendsBody) {
            b.method(method, HttpRequest.BodyPublishers.ofString(body));
            if (looksJson(body) && !headers.containsKey("Content-Type")) {
                b.header("Content-Type", "application/json");
            }
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        headers.forEach(b::header); // dialed headers win: auth, overrides
        return b.build();
    }

    static boolean looksJson(String body) {
        String t = body.strip();
        return t.startsWith("{") || t.startsWith("[");
    }

    /** Parses "Name: value; Other: value" into an ordered header map. */
    static Map<String, String> parseHeaders(String line) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (line == null || line.isBlank()) {
            return headers;
        }
        for (String segment : line.split(";")) {
            int colon = segment.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = segment.substring(0, colon).trim();
            String value = segment.substring(colon + 1).trim();
            if (!name.isEmpty() && !value.isEmpty()) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    /** Indents a JSON body for the console; anything else passes through. */
    static String prettyJson(String body) {
        if (body == null) {
            return "";
        }
        String t = body.strip();
        try {
            if (t.startsWith("{")) {
                return new JSONObject(t).toString(2);
            }
            if (t.startsWith("[")) {
                return new JSONArray(t).toString(2);
            }
        } catch (RuntimeException notJson) {
            // not valid JSON; show it raw
        }
        return body;
    }

    private static String formatHeaders(HttpResponse<?> response) {
        StringBuilder sb = new StringBuilder();
        response.headers().map().forEach((k, v) ->
                sb.append(k).append(": ").append(String.join(", ", v)).append('\n'));
        return sb.toString();
    }

    // ---- the console ----

    private void showConsole() {
        java.util.List<Exchange> snapshot;
        synchronized (this) {
            snapshot = new java.util.ArrayList<>(history);
        }
        javax.swing.JDialog dialog = new javax.swing.JDialog(
                (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(this),
                "PING — REST console", false);
        javax.swing.DefaultListModel<Exchange> model = new javax.swing.DefaultListModel<>();
        snapshot.forEach(model::addElement);
        javax.swing.JList<Exchange> list = new javax.swing.JList<>(model);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l,
                    Object v, int i, boolean s, boolean f) {
                Exchange x = (Exchange) v;
                String label = x.method() + " " + x.url() + "  →  "
                        + (x.status() < 0 ? "no route" : x.status()) + " · " + x.ms() + "ms";
                return super.getListCellRendererComponent(l, label, i, s, f);
            }
        });
        javax.swing.JTextArea detail = new javax.swing.JTextArea();
        detail.setEditable(false);
        detail.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        list.addListSelectionListener(e -> {
            Exchange x = list.getSelectedValue();
            if (x != null) {
                detail.setText((x.status() < 0 ? "NO ROUTE" : "HTTP " + x.status())
                        + "  ·  " + x.ms() + "ms\n\n"
                        + x.responseHeaders() + "\n" + prettyJson(x.responseBody()));
                detail.setCaretPosition(0);
            }
        });
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }

        javax.swing.JButton replay = new javax.swing.JButton("Replay");
        replay.addActionListener(e -> {
            Exchange x = list.getSelectedValue();
            if (x != null) {
                onEdt(() -> {
                    methodKnob.selectOption(x.method());
                    urlLcd.setText(x.url());
                });
                fire();
            }
        });
        javax.swing.JPanel south = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        south.add(replay);
        if (model.isEmpty()) {
            south.add(new javax.swing.JLabel("No exchanges yet — SEND a request."));
        }

        javax.swing.JSplitPane split = new javax.swing.JSplitPane(
                javax.swing.JSplitPane.HORIZONTAL_SPLIT,
                new javax.swing.JScrollPane(list),
                new javax.swing.JScrollPane(detail));
        split.setDividerLocation(320);
        dialog.add(split, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(860, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "send" -> fire();
            case "url" -> {
                if (signal.payload() != null && signal.payload().startsWith("http")) {
                    onEdt(() -> urlLcd.setText(signal.payload()));
                }
            }
            default -> {
            }
        }
    }
}
