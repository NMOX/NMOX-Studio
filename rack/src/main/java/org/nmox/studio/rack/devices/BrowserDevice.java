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

    private void openBrowser() {
        String url = urlLcd.getText().trim();
        if (url.isEmpty()) {
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
            if (!opened && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
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
