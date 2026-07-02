package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * HELM: the rack reaches your servers. Dial user@host and a command,
 * and it runs over ssh with the standard treatment - output on the
 * bus, OK/FAIL/DONE jacks, CI export. BatchMode only: key auth, no
 * password prompts, no hanging pipelines. Patch LAUNCHPAD's OK in and
 * a deploy can finish with a remote migration or service restart.
 */
public class SshDevice extends CommandDevice {

    private final LcdDisplay hostLcd;
    private final LcdDisplay commandLcd;

    public SshDevice() {
        super("ssh", "HELM", "REMOTE RUNNER", new Color(95, 158, 199), 2);

        RackButton run = place(new RackButton("RUN", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        run.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        hostLcd = place(new LcdDisplay(180, 1), 180, 46);
        hostLcd.setText("");
        hostLcd.setEditable("user@host");
        commandLcd = place(new LcdDisplay(280, 1), 380, 46);
        commandLcd.setText("");
        commandLcd.setEditable("Remote command");

        run.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        // a droplet's address can arrive by cable (infra designer, TAIL of
        // a provisioning log, anything that knows the host)
        addInPort("host", "HOST", SignalType.DATA);

        param("host", hostLcd);
        param("command", commandLcd);
    }

    /** Remote work needs a host, not a local manifest. */
    @Override
    protected boolean requiresProjectManifest() {
        return false;
    }

    @Override
    protected List<String> buildCommand() {
        String host = hostLcd.getText().trim();
        List<String> remote = CommandLineDevice.splitArgs(commandLcd.getText());
        if (host.isEmpty() || remote.isEmpty()) {
            return null;
        }
        List<String> cmd = new ArrayList<>(List.of("ssh",
                "-o", "BatchMode=yes", "-o", "ConnectTimeout=10", host));
        cmd.addAll(remote);
        return cmd;
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("host".equals(in.getId())) {
            if (signal.payload() != null && !signal.payload().isBlank()) {
                onEdt(() -> hostLcd.setText(signal.payload().trim()));
            }
            return;
        }
        super.receive(in, signal);
    }
}
