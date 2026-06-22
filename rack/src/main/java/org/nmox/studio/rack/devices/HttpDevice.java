package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 * PING Request Probe: fires HTTP requests at an endpoint and reports
 * status and latency - a smoke tester for APIs and dev servers.
 */
public class HttpDevice extends RackDevice {

    private static final String[] METHODS = {"GET", "HEAD", "POST", "PUT", "DELETE"};

    private final Knob methodKnob;
    private final LcdDisplay urlLcd;
    private final LcdDisplay bodyLcd;
    private final LcdDisplay statusLcd;
    private final VuMeter latencyMeter;
    private final Led okLed;
    private final Led failLed;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public HttpDevice() {
        super("http", "PING", "REQUEST PROBE", new Color(96, 180, 100), 2);

        methodKnob = place(new Knob("METHOD", METHODS, 0), 112, 40);
        urlLcd = place(new LcdDisplay(260, 1), 184, 46);
        urlLcd.setText("http://localhost:3000");
        urlLcd.setEditable("Request URL");
        bodyLcd = place(new LcdDisplay(260, 1), 184, 78);
        bodyLcd.setText("");
        bodyLcd.setEditable("Request body (sent on POST/PUT)");
        RackButton send = place(new RackButton("SEND", RackStyle.GO), RackStyle.TRANSPORT_X, 46);
        statusLcd = place(new LcdDisplay(120, 1), 460, 46);
        statusLcd.setText("—");
        latencyMeter = place(new VuMeter("LATENCY", false), 460, 80);
        okLed = place(new Led("2XX", RackStyle.GO), 600, 52);
        failLed = place(new Led("ERR", RackStyle.STOP), 640, 52);

        send.addActionListener(e -> fire());

        addInPort("send", "SEND", SignalType.TRIGGER);
        addInPort("url", "URL", SignalType.DATA);
        addOutPort("ok", "OK", SignalType.TRIGGER);
        addOutPort("fail", "FAIL", SignalType.TRIGGER);
        addOutPort("body", "BODY", SignalType.DATA);

        param("method", methodKnob);
        param("url", urlLcd);
        param("body", bodyLcd);
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
        long start = System.nanoTime();
        HttpRequest request;
        try {
            request = buildRequest(METHODS[methodKnob.getSelectedIndex()], url, bodyLcd.getText());
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
                        onEdt(() -> {
                            failLed.setOn(true);
                            statusLcd.setTextColor(new Color(255, 90, 80));
                            statusLcd.setText("NO ROUTE " + ms + "ms");
                        });
                        emit("fail", Signal.trigger(false));
                        return;
                    }
                    boolean ok = response.statusCode() < 400;
                    latencyMeter.setLevel(Math.min(1.0, ms / 2000.0));
                    onEdt(() -> {
                        okLed.setOn(ok);
                        failLed.setOn(!ok);
                        statusLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                        statusLcd.setText(response.statusCode() + "  " + ms + "ms");
                    });
                    emit(ok ? "ok" : "fail", Signal.trigger(ok));
                    String body = response.body();
                    if (body != null && !body.isEmpty()) {
                        emit("body", Signal.data(body.length() > 400 ? body.substring(0, 400) + "…" : body));
                    }
                });
    }

    /**
     * Builds the request: POST/PUT carry the body field (JSON gets a
     * Content-Type so APIs accept it); everything else is body-less.
     * Static and side-effect-free so the wiring is unit-testable.
     */
    static HttpRequest buildRequest(String method, String url, String body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15));
        boolean sendsBody = ("POST".equals(method) || "PUT".equals(method))
                && body != null && !body.isBlank();
        if (sendsBody) {
            b.method(method, HttpRequest.BodyPublishers.ofString(body));
            if (looksJson(body)) {
                b.header("Content-Type", "application/json");
            }
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return b.build();
    }

    static boolean looksJson(String body) {
        String t = body.strip();
        return t.startsWith("{") || t.startsWith("[");
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
