package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.Map;
import java.util.stream.Collectors;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * ROSETTA Language Selector: the mixed-project conductor. Its LCD
 * shows every toolchain detected in the project (root and one level
 * deep - the classic frontend/ + backend/ monorepo); the knob steers
 * what every AUTO knob in the rack assumes. On AUTO, devices follow
 * detection precedence; dial RUST and IGNITION, VERITAS, FORGE and
 * CRATE all speak cargo from the Cargo.toml directory.
 */
public class RosettaDevice extends RackDevice {

    private static final String[] TOOLCHAINS = {
        "auto", "node", "rust", "go", "elixir", "erlang", "clojure", "swift", "dotnet", "dart", "scala", "haskell", "zig", "ocaml", "crystal", "maven", "gradle", "python", "ruby", "php", "make", "julia", "nim", "dlang", "racket", "elm", "rescript", "purescript", "vlang", "fortran", "ada"};

    private final Knob toolchainKnob;
    private final LcdDisplay detectedLcd;

    public RosettaDevice() {
        super("rosetta", "ROSETTA", "LANGUAGE SELECTOR", new Color(64, 224, 178), 2);

        toolchainKnob = place(new Knob("TOOLCHAIN", TOOLCHAINS, 0), 44, 40);
        detectedLcd = place(new LcdDisplay(330, 1), 130, 46);
        detectedLcd.getAccessibleContext().setAccessibleName("detected lanes");
        detectedLcd.setText("—");
        detectedLcd.setToolTipText("Toolchains detected in the project (root + one level deep)");

        toolchainKnob.addChangeListener(this::apply);

        addOutPort("kind", "KIND", SignalType.DATA);

        paramByName("toolchain", toolchainKnob);
    }

    @Override
    protected void onAttached() {
        apply();
    }

    @Override
    public void projectChanged(File dir) {
        refreshDetected();
    }

    private void apply() {
        if (getRack() == null) {
            return;
        }
        String selected = toolchainKnob.getSelectedOption();
        getRack().setToolchainOverride(kindNameFor(selected));
        refreshDetected();
        emit("kind", Signal.data(selected));
    }

    /** Maps knob labels onto ProjectKind names ("maven" -> MAVEN...). */
    private static String kindNameFor(String knobOption) {
        return "auto".equals(knobOption) ? null : knobOption.toUpperCase();
    }

    private void refreshDetected() {
        // detectKinds walks the project directory; on a $HOME aim that would
        // touch the TCC-protected folders on the EDT during startup. Detect on
        // the background thread and marshal the LCD update back to the EDT.
        File dir = projectDir();
        offEdt(() -> {
            var kinds = ProjectInspector.detectKinds(dir);
            String mix = kinds.isEmpty() ? "NO TOOLCHAINS"
                    : kinds.keySet().stream().map(Enum::name).collect(Collectors.joining("+"));
            String selected = toolchainKnob.getSelectedOption();
            String active = "auto".equals(selected)
                    ? (kinds.isEmpty() ? "" : "  [" + kinds.keySet().iterator().next() + "]")
                    : "  [" + selected.toUpperCase() + "]";
            onEdt(() -> {
                detectedLcd.setTextColor(kinds.size() > 1 ? RackStyle.LCD_AMBER : RackStyle.LCD_TEXT);
                detectedLcd.setText(mix + active);
            });
        });
    }

    @Override
    public void applyState(Map<String, String> state) {
        super.applyState(state);
        apply();
    }

    @Override
    public void dispose() {
        // releasing the selector releases the override
        if (getRack() != null) {
            getRack().setToolchainOverride(null);
        }
        super.dispose();
    }
}
