package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * WORMHOLE Public Tunnel: exposes the local dev server to the
 * internet through cloudflared, ngrok or localtunnel. The public URL
 * it scrapes from the provider lands on the URL data jack - patch it
 * into SCOPE to pop the public address in a browser, or read it off
 * the LCD and text it to whoever needs a preview.
 */
public class TunnelDevice extends CommandDevice {

    private static final String[] PROVIDERS = {"cloudflared", "ngrok", "localtunnel"};
    private static final String[] PORTS = {"3000", "4200", "5173", "8000", "8080", "9000"};
    private static final Pattern PUBLIC_URL = Pattern.compile(
            "(https://[\\w.-]+\\.(?:trycloudflare\\.com|ngrok-free\\.app|ngrok\\.io|ngrok\\.app|loca\\.lt)\\S*)");

    private final Knob providerKnob;
    private final Knob portKnob;
    private final LcdDisplay urlLcd;
    private final Led liveLed;
    private volatile boolean announced;

    public TunnelDevice() {
        super("tunnel", "WORMHOLE", "PUBLIC TUNNEL", new Color(156, 89, 209), 2);

        RackButton open = place(new RackButton("OPEN", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        open.setCommandPreview(this::commandPreview);
        RackButton close = place(new RackButton("CLOSE", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        providerKnob = place(new Knob("CARRIER", PROVIDERS, 0), 180, 40);
        portKnob = place(new Knob("PORT", PORTS, 2), 254, 40);
        urlLcd = place(new LcdDisplay(150, 1), 328, 52);
        urlLcd.getAccessibleContext().setAccessibleName("public URL");
        urlLcd.setText("—");
        liveLed = place(new Led("LIVE", new Color(156, 89, 209)), 442, 58);

        open.addActionListener(e -> primaryAction());
        close.addActionListener(e -> stopProcess());

        addInPort("stop", "STOP", SignalType.TRIGGER);
        addInPort("enable", "ENABLE", SignalType.GATE);
        addOutPort("url", "URL", SignalType.DATA);
        addOutPort("live", "RUNNING", SignalType.GATE);

        param("provider", providerKnob);
        param("port", portKnob);
    }

    /** Tunnels work for any local server, project manifest or not. */
    @Override
    protected boolean requiresProjectManifest() {
        return false;
    }

    private String port() {
        return PORTS[portKnob.getSelectedIndex()];
    }

    @Override
    protected void primaryAction() {
        announced = false;
        onEdt(() -> {
            urlLcd.setTextColor(RackStyle.LCD_AMBER);
            urlLcd.setText("DIALING…");
            liveLed.setBlinking(true);
        });
        if (launch(buildCommand())) {
            emit("live", Signal.gate(true));
        }
    }

    @Override
    protected List<String> buildCommand() {
        return switch (providerKnob.getSelectedOption()) {
            case "ngrok" -> List.of("ngrok", "http", port(), "--log", "stdout");
            case "localtunnel" -> List.of("npx", "localtunnel", "--port", port());
            default -> List.of("cloudflared", "tunnel", "--url", "http://localhost:" + port());
        };
    }

    @Override
    protected void onLine(String line) {
        if (announced) {
            return;
        }
        Matcher m = PUBLIC_URL.matcher(line);
        if (m.find()) {
            announced = true;
            String url = m.group(1);
            onEdt(() -> {
                urlLcd.setTextColor(RackStyle.LCD_TEXT);
                urlLcd.setText(url.replaceFirst("https://", ""));
                urlLcd.setToolTipText(url);
                liveLed.setBlinking(false);
                liveLed.setOn(true);
            });
            emit("url", Signal.data(url));
        }
    }

    @Override
    protected void onFinished(int exitCode) {
        emit("live", Signal.gate(false));
        onEdt(() -> {
            liveLed.setBlinking(false);
            liveLed.setOn(false);
            urlLcd.setText("—");
        });
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("stop".equals(in.getId())) {
            stopProcess();
        } else if ("enable".equals(in.getId())) {
            enableGate(signal.high(), this::primaryAction, this::stopProcess);
        } else {
            super.receive(in, signal);
        }
    }
}
