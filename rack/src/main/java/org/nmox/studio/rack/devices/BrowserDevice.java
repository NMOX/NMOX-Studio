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

/**
 * SCOPE Browser Link: opens the system browser at the dialed URL.
 * Patch SURGE's URL output into the URL jack and its READY trigger
 * into OPEN, and the browser pops as soon as the dev server is up.
 */
public class BrowserDevice extends RackDevice {

    private final LcdDisplay urlLcd;
    private final Led openedLed;

    public BrowserDevice() {
        super("browser", "SCOPE", "BROWSER LINK", new Color(54, 174, 222), 2);

        urlLcd = place(new LcdDisplay(300, 1), 44, 46);
        urlLcd.setText("http://localhost:5173"); // matches SURGE's default port
        urlLcd.setEditable("URL to open");
        RackButton open = place(new RackButton("OPEN", new Color(80, 235, 100)), 354, 52);
        openedLed = place(new Led("SENT", new Color(64, 200, 255)), 422, 58);

        open.addActionListener(e -> openBrowser());

        addInPort("open", "OPEN", SignalType.TRIGGER);
        addInPort("url", "URL", SignalType.DATA);
        addOutPort("opened", "OPENED", SignalType.TRIGGER);

        param("url", urlLcd);
    }

    private void openBrowser() {
        String url = urlLcd.getText().trim();
        if (url.isEmpty()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                onEdt(() -> openedLed.setOn(true));
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
