package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import org.json.JSONObject;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * SENTRY Security Analyzer: npm audit with a severity ladder per
 * class of vulnerability. Meters show min(count, 10) out of ten.
 */
public class AuditDevice extends CommandDevice {

    private final VuMeter critMeter = new VuMeter("CRIT", true);
    private final VuMeter highMeter = new VuMeter("HIGH", true);
    private final VuMeter modMeter = new VuMeter("MOD", true);
    private final VuMeter lowMeter = new VuMeter("LOW", true);
    private final Led clearLed;
    private final StringBuilder jsonBuffer = new StringBuilder();

    public AuditDevice() {
        super("audit", "SENTRY", "SECURITY ANALYZER", new Color(188, 42, 48), 3);

        RackButton scan = place(new RackButton("SCAN", RackStyle.GO), 44, 52);
        clearLed = place(new Led("SECURE", RackStyle.GO), 112, 58);
        place(critMeter, 170, 44);
        place(highMeter, 206, 44);
        place(modMeter, 242, 44);
        place(lowMeter, 278, 44);

        scan.addActionListener(e -> primaryAction());
    }

    @Override
    protected void primaryAction() {
        jsonBuffer.setLength(0);
        onEdt(() -> clearLed.setOn(false));
        launch(buildCommand());
    }

    @Override
    protected List<String> buildCommand() {
        return List.of("npm", "audit", "--json");
    }

    @Override
    protected void onLine(String line) {
        jsonBuffer.append(line).append('\n');
    }

    @Override
    protected void onFinished(int exitCode) {
        int crit = 0, high = 0, moderate = 0, low = 0;
        boolean parsed = false;
        try {
            JSONObject json = new JSONObject(jsonBuffer.toString());
            JSONObject vulns = json.getJSONObject("metadata").getJSONObject("vulnerabilities");
            crit = vulns.optInt("critical");
            high = vulns.optInt("high");
            moderate = vulns.optInt("moderate");
            low = vulns.optInt("low");
            parsed = true;
        } catch (RuntimeException ex) {
            // not valid audit JSON (npm missing, no lockfile...) - leave meters
        }
        if (parsed) {
            final int c = crit, h = high, m = moderate, l = low;
            critMeter.setLevel(Math.min(1.0, c / 10.0));
            highMeter.setLevel(Math.min(1.0, h / 10.0));
            modMeter.setLevel(Math.min(1.0, m / 10.0));
            lowMeter.setLevel(Math.min(1.0, l / 10.0));
            onEdt(() -> {
                clearLed.setOn(c + h + m + l == 0);
                statusLcd.setText("C:" + c + " H:" + h + " M:" + m + " L:" + l);
            });
        }
    }
}
