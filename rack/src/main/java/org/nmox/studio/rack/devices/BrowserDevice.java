package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.awt.Desktop;
import java.net.URI;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * SCOPE Browser Link: opens the system browser at the dialed URL.
 * Patch SURGE's URL output into the URL jack and its READY trigger
 * into OPEN, and the browser pops as soon as the dev server is up.
 */
public class BrowserDevice extends RackDevice {

    private final LcdDisplay urlLcd;
    private final Led openedLed;
    private final org.nmox.studio.rack.ui.controls.Knob targetKnob;
    /** The URL a cable delivered, until the EDT paints it (see {@link CabledUrl}). */
    private final CabledUrl cabledUrl = new CabledUrl();
    /** Test seam: how a URL reaches the system browser (production: java.awt.Desktop). */
    java.util.function.Consumer<String> systemBrowser = BrowserDevice::browseWithDesktop;

    public BrowserDevice() {
        super("browser", "SCOPE", "BROWSER LINK", new Color(54, 174, 222), 2);

        urlLcd = place(new LcdDisplay(300, 1), 112, 46);
        urlLcd.setText("http://localhost:5173"); // matches SURGE's default port
        urlLcd.setEditable("URL to open");
        RackButton open = place(new RackButton("OPEN", RackStyle.GO), RackStyle.TRANSPORT_X, 46);
        openedLed = place(new Led("SENT", new Color(64, 200, 255)), 424, 52);
        // TARGET appended at the END (knob options persist by index —
        // the append-only law): SYSTEM keeps the historic behavior as
        // position 0 so every saved patch means what it always meant.
        targetKnob = place(new org.nmox.studio.rack.ui.controls.Knob(
                "TARGET", new String[]{"SYSTEM", "IN-APP"}, 0), 470, 40);

        open.addActionListener(e -> openBrowser());

        addInPort("open", "OPEN", SignalType.TRIGGER);
        addInPort("url", "URL", SignalType.DATA);
        addOutPort("opened", "OPENED", SignalType.TRIGGER);

        param("url", urlLcd);
        param("target", targetKnob);
    }

    /** Production opener: the desktop's browser, or an exception the LCD reports. */
    private static void browseWithDesktop(String url) {
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                // used to fall through in silence — OPEN did nothing and said nothing
                throw new java.io.IOException("no system browser on this desktop");
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    private void openBrowser() {
        // the cabled URL wins until the EDT has painted it: OPEN runs on the
        // router thread one signal after URL, and reading the LCD alone opened
        // the factory default on a first serve (the Angular template on 4200
        // opened 5173 — the 2026-09-17 rack walk; see CabledUrl)
        String url = cabledUrl.resolve(urlLcd);
        if (url.isEmpty()) {
            return;
        }
        if (!url.startsWith("http")) {
            // the LCD holds a refusal or a hand-typed non-URL: say so rather
            // than hand it to a browser (which would only fail more vaguely);
            // a refusal already on the LCD is not prefixed a second time
            if (!url.startsWith(CabledUrl.REFUSAL)) {
                onEdt(() -> urlLcd.setText(CabledUrl.REFUSAL + " — " + url));
            }
            return;
        }
        try {
            boolean opened = false;
            if (targetKnob.getSelectedIndex() == 1) {
                // IN-APP: the embedded browser via the soft-dependency
                // seam (v1.199.0); unavailable (dev JDK without JavaFX,
                // or no ui module) falls through to the system browser —
                // the OPEN press must never be a dead click
                org.nmox.studio.core.spi.EmbeddedBrowser embedded =
                        org.nmox.studio.core.spi.EmbeddedBrowser.find();
                opened = embedded != null && embedded.open(url);
            }
            if (!opened) {
                systemBrowser.accept(url);
                opened = true;
            }
            if (opened) {
                onEdt(() -> {
                    // momentary: SENT means "just sent", not "sent once ever"
                    openedLed.setOn(true);
                    javax.swing.Timer off = new javax.swing.Timer(450, ev -> openedLed.setOn(false));
                    off.setRepeats(false);
                    off.start();
                });
                emit("opened", Signal.trigger());
            }
        } catch (Exception ex) {
            onEdt(() -> {
                openedLed.setOn(false);
                urlLcd.setText("OPEN FAILED: " + ex.getMessage());
            });
        }
    }

    @Override
    public void receive(Port in, Signal signal) {
        switch (in.getId()) {
            case "open" -> openBrowser();
            // SCOPE has one LCD, so a non-URL payload is refused on it: a wrong
            // cable used to be dropped in silence (refusals speak)
            case "url" -> cabledUrl.deliver(signal, urlLcd,
                    reason -> onEdt(() -> urlLcd.setText(reason)));
            default -> {
            }
        }
    }
}
