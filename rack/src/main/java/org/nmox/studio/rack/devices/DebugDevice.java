package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.Map;
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
 * INSPECTOR Debug Launcher: starts the program under its language's
 * debug server and puts the attach endpoint on the LCD - Node's
 * inspector (chrome://inspect or any DAP client), Python's debugpy,
 * Go's delve, JDWP for Maven projects, Ruby's rdbg, PHP's Xdebug.
 * The ENDPOINT data jack emits the address for downstream devices.
 */
public class DebugDevice extends CommandDevice {

    private static final String[] TARGETS = {"auto", "node", "python", "go", "maven", "ruby", "php"};
    private static final Pattern NODE_WS = Pattern.compile("(ws://\\S+)");

    private final Knob targetKnob;
    private final LcdDisplay endpointLcd;
    private final Led armedLed;
    private volatile boolean announced;

    public DebugDevice() {
        super("debug", "INSPECTOR", "DEBUG LAUNCHER", new Color(186, 85, 255), 2);

        RackButton attach = place(new RackButton("LAUNCH", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        attach.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        targetKnob = place(new Knob("TARGET", TARGETS, 0), 180, 40);
        endpointLcd = place(new LcdDisplay(210, 1), 254, 52);
        endpointLcd.setText("—");
        endpointLcd.setToolTipText("Attach your debugger client here");
        armedLed = place(new Led("WIRED", new Color(186, 85, 255)), 254 + 216, 84);

        attach.addActionListener(e -> primaryAction());
        stop.addActionListener(e -> stopProcess());

        addInPort("stop", "STOP", SignalType.TRIGGER);
        addOutPort("endpoint", "ENDPOINT", SignalType.DATA);
        addOutPort("live", "RUNNING", SignalType.GATE);

        param("target", targetKnob);
    }

    private String effectiveTarget() {
        String target = targetKnob.getSelectedOption();
        if (!"auto".equals(target)) {
            return target;
        }
        return switch (effectiveKind()) {
            case PYTHON -> "python";
            case GO -> "go";
            case MAVEN, GRADLE -> "maven";
            case RUBY -> "ruby";
            case PHP -> "php";
            default -> "node";
        };
    }


    private static ProjectInspector.ProjectKind kindForTarget(String target) {
        return switch (target) {
            case "rust" -> ProjectInspector.ProjectKind.RUST;
            case "go" -> ProjectInspector.ProjectKind.GO;
            case "maven" -> ProjectInspector.ProjectKind.MAVEN;
            case "gradle" -> ProjectInspector.ProjectKind.GRADLE;
            case "python" -> ProjectInspector.ProjectKind.PYTHON;
            case "ruby" -> ProjectInspector.ProjectKind.RUBY;
            case "php" -> ProjectInspector.ProjectKind.PHP;
            case "make" -> ProjectInspector.ProjectKind.MAKE;
            default -> ProjectInspector.ProjectKind.NODE;
        };
    }

    /** Commands run where the selected target's manifest lives. */
    @Override
    protected java.io.File commandDir() {
        return ProjectInspector.kindDir(projectDir(), kindForTarget(effectiveTarget()));
    }

    /** First existing candidate file, else the first candidate. */
    private String entryPoint(String... candidates) {
        for (String candidate : candidates) {
            if (new File(commandDir(), candidate).isFile()) {
                return candidate;
            }
        }
        return candidates[0];
    }

    @Override
    protected void primaryAction() {
        announced = false;
        String target = effectiveTarget();
        String endpoint = endpointFor(target);
        onEdt(() -> {
            endpointLcd.setTextColor(RackStyle.LCD_AMBER);
            endpointLcd.setText(endpoint);
            armedLed.setBlinking(true);
        });
        emit("live", Signal.gate(true));
        emit("endpoint", Signal.data(endpoint));

        // JDWP rides in on MAVEN_OPTS; everything else is plain argv
        if ("maven".equals(target)) {
            launchWithEnv(buildCommand(), Map.of("MAVEN_OPTS",
                    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"));
        } else {
            launch(buildCommand());
        }
    }

    private String endpointFor(String target) {
        return switch (target) {
            case "python" -> "ATTACH :5678 (debugpy)";
            case "go" -> "ATTACH :2345 (delve)";
            case "maven" -> "ATTACH :5005 (jdwp)";
            case "ruby" -> "ATTACH :12345 (rdbg)";
            case "php" -> "XDEBUG :9003 + :8000 (serve)";
            default -> "INSPECT :9229 (chrome://inspect)";
        };
    }

    @Override
    protected List<String> buildCommand() {
        return switch (effectiveTarget()) {
            case "python" -> List.of("python3", "-m", "debugpy",
                    "--listen", "5678", "--wait-for-client",
                    entryPoint("main.py", "app.py"));
            case "go" -> List.of("dlv", "debug", "--headless",
                    "--listen=:2345", "--api-version=2", "--accept-multiclient");
            case "maven" -> List.of("mvn", "-q", "compile", "exec:java");
            case "ruby" -> List.of("rdbg", "--open", "--port", "12345",
                    entryPoint("main.rb", "app.rb"));
            case "php" -> List.of("php", "-dxdebug.mode=debug",
                    "-dxdebug.start_with_request=yes", "-S", "localhost:8000");
            default -> List.of("node", "--inspect=9229",
                    entryPoint("index.js", "main.js", "src/index.js"));
        };
    }

    @Override
    protected void onLine(String line) {
        if (announced) {
            return;
        }
        // Node prints the definitive ws endpoint; show truth over template
        Matcher m = NODE_WS.matcher(line);
        if (m.find()) {
            announced = true;
            String ws = m.group(1);
            onEdt(() -> {
                endpointLcd.setTextColor(RackStyle.LCD_TEXT);
                endpointLcd.setText(ws.replaceFirst("ws://", ""));
                endpointLcd.setToolTipText(ws + " — open chrome://inspect");
                armedLed.setBlinking(false);
                armedLed.setOn(true);
            });
            emit("endpoint", Signal.data(ws));
        } else if (line.contains("API server listening") // delve
                || line.contains("wait_for_client")      // debugpy
                || line.contains("DEBUGGER: wait")) {    // rdbg
            announced = true;
            onEdt(() -> {
                endpointLcd.setTextColor(RackStyle.LCD_TEXT);
                armedLed.setBlinking(false);
                armedLed.setOn(true);
            });
        }
    }

    @Override
    protected void onFinished(int exitCode) {
        emit("live", Signal.gate(false));
        onEdt(() -> {
            armedLed.setBlinking(false);
            armedLed.setOn(false);
            endpointLcd.setText("—");
        });
    }

    @Override
    public void receive(Port in, Signal signal) {
        if ("stop".equals(in.getId())) {
            stopProcess();
        } else {
            super.receive(in, signal);
        }
    }
}
