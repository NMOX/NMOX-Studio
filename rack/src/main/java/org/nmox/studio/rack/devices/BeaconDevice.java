package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * BEACON: the certificate and uptime sentinel. One CHECK answers the
 * two questions that page people at 3am: is the site up, and how many
 * days does its TLS certificate have left? Dial MIN DAYS and a cert
 * inside the window fires FAIL - patch TEMPO's BAR into CHECK and the
 * rack watches your production domain on a clock.
 */
public class BeaconDevice extends RackDevice {

    private static final String[] MINIMUMS = {"off", "7", "14", "30"};
    /** Factory URL; while the LCD still shows it, CHECK may auto-aim. */
    private static final String DEFAULT_URL = "https://example.com";

    private final LcdDisplay urlLcd;
    private final LcdDisplay resultLcd;
    private final Knob minKnob;
    private final Led upLed;
    private final Led warnLed;
    private final HttpClient client = org.nmox.studio.core.http.HttpClientFactory.shared();

    public BeaconDevice() {
        super("beacon", "BEACON", "CERT & UPTIME SENTINEL", new Color(240, 180, 60), 2);

        RackButton check = place(new RackButton("CHECK", RackStyle.GO), RackStyle.TRANSPORT_X, 46);
        urlLcd = place(new LcdDisplay(220, 1), 130, 46);
        urlLcd.setText(DEFAULT_URL);
        urlLcd.setEditable("URL to watch");
        minKnob = place(new Knob("MIN DAYS", MINIMUMS, 0), 370, 40);
        minKnob.setToolTipText("Certificate floor: fewer days left than this fires FAIL");
        resultLcd = place(new LcdDisplay(240, 1), 444, 46);
        resultLcd.setText("—");
        upLed = place(new Led("UP", RackStyle.GO), 700, 52);
        warnLed = place(new Led("WARN", RackStyle.STOP), 744, 52);

        check.addActionListener(e -> check());

        addInPort("run", "CHECK", SignalType.TRIGGER);
        addOutPort("ok", "OK", SignalType.TRIGGER);
        addOutPort("fail", "FAIL", SignalType.TRIGGER);

        param("url", urlLcd);
        param("min", minKnob);
    }

    int minimumDays() {
        String sel = minKnob.getSelectedOption();
        return "off".equals(sel) ? 0 : Integer.parseInt(sel);
    }

    /** The gate: up, and (with a floor dialed) enough runway on the cert. */
    static boolean verdict(boolean reachable, long certDays, int floorDays) {
        return reachable && (floorDays == 0 || certDays < 0 || certDays >= floorDays);
    }

    /**
     * The watch target: an explicitly dialed URL always wins; a blank,
     * factory-default, or auto LCD aims at the project's live WEB server
     * from the serving registry (read at CHECK time, never polled), with
     * the LCD showing the pick as "auto: &lt;url&gt;".
     */
    String effectiveUrl() {
        String dialed = urlLcd.getText().trim();
        if (!AutoUrl.isAuto(dialed, DEFAULT_URL)) {
            return dialed;
        }
        String auto = AutoUrl.firstWebServing(projectDir());
        if (auto != null) {
            onEdt(() -> urlLcd.setText(AutoUrl.AUTO_PREFIX + auto));
            return auto;
        }
        return AutoUrl.fallback(dialed);
    }

    private void check() {
        String url = effectiveUrl();
        if (url.isEmpty()) {
            return;
        }
        onEdt(() -> {
            resultLcd.setTextColor(RackStyle.LCD_AMBER);
            resultLcd.setText("CHECKING…");
        });
        Thread worker = new Thread(() -> {
            boolean reachable = false;
            long days = -1;
            String detail = "";
            try {
                URI uri = URI.create(url);
                if ("https".equals(uri.getScheme())) {
                    days = certDaysRemaining(uri.getHost(),
                            uri.getPort() > 0 ? uri.getPort() : 443);
                }
                HttpResponse<Void> response = client.send(
                        HttpRequest.newBuilder(uri).method("HEAD",
                                HttpRequest.BodyPublishers.noBody())
                                .timeout(Duration.ofSeconds(10)).build(),
                        HttpResponse.BodyHandlers.discarding());
                reachable = response.statusCode() < 500;
                detail = "HTTP " + response.statusCode();
            } catch (Exception ex) {
                detail = "DOWN: " + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                        : ex.getMessage());
            }
            boolean ok = verdict(reachable, days, minimumDays());
            final boolean up = reachable;
            final long d = days;
            final String det = detail;
            onEdt(() -> {
                upLed.setOn(up);
                warnLed.setOn(!ok);
                resultLcd.setTextColor(ok ? RackStyle.LCD_TEXT : new Color(255, 90, 80));
                resultLcd.setText((d >= 0 ? "TLS " + d + "d · " : "") + det
                        + (!ok && up && d >= 0 && d < minimumDays() ? " — CERT INSIDE FLOOR" : ""));
            });
            emit(ok ? "ok" : "fail", Signal.trigger(ok));
        }, "nmox-beacon");
        worker.setDaemon(true);
        worker.start();
    }

    private long certDaysRemaining(String host, int port) {
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
                .createSocket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 8_000);
            socket.startHandshake();
            var certs = socket.getSession().getPeerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                return ChronoUnit.DAYS.between(Instant.now(),
                        x509.getNotAfter().toInstant());
            }
        } catch (Exception ex) {
            // handshake failed: reachability check below will tell the story
        }
        return -1;
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("run".equals(in.getId())) {
            check();
        }
    }
}
