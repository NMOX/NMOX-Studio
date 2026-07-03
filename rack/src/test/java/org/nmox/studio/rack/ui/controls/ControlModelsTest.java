package org.nmox.studio.rack.ui.controls;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The control-surface widgets are studio hardware in Swing, but their
 * MODELS are plain state machines: a knob clamps and cycles, a switch
 * flips and notifies, an LCD holds text, an LED lights. This exercises
 * that model layer - value/index math and change events - never the
 * paint code.
 */
class ControlModelsTest {

    // ---- Knob: stepped ----

    @Test
    @DisplayName("Stepped knob clamps the initial index into range")
    void steppedKnobClampsInitialIndex() {
        String[] opts = {"a", "b", "c"};
        assertThat(new Knob("K", opts, 99).getSelectedIndex()).isEqualTo(2);
        assertThat(new Knob("K", opts, -5).getSelectedIndex()).isEqualTo(0);
        assertThat(new Knob("K", opts, 1).getSelectedOption()).isEqualTo("b");
    }

    @Test
    @DisplayName("Stepped knob: setSelectedIndex clamps and fires only on change")
    void steppedKnobSetIndexClampsAndFires() {
        Knob k = new Knob("K", new String[]{"a", "b", "c"}, 0);
        AtomicInteger fired = new AtomicInteger();
        k.addChangeListener(fired::incrementAndGet);

        k.setSelectedIndex(2);
        assertThat(k.getSelectedIndex()).isEqualTo(2);
        assertThat(fired.get()).isEqualTo(1);

        k.setSelectedIndex(2); // no change: no event
        assertThat(fired.get()).isEqualTo(1);

        k.setSelectedIndex(50); // clamps to 2: no change either
        assertThat(k.getSelectedIndex()).isEqualTo(2);
        assertThat(fired.get()).isEqualTo(1);

        k.setSelectedIndex(-9); // clamps to 0: a change
        assertThat(k.getSelectedIndex()).isEqualTo(0);
        assertThat(fired.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("isStepped distinguishes selector knobs from continuous ones")
    void isStepped() {
        assertThat(new Knob("K", new String[]{"a"}, 0).isStepped()).isTrue();
        assertThat(new Knob("K", 0.5).isStepped()).isFalse();
    }

    @Test
    @DisplayName("selectOption matches by name, then by numeric index, else keeps current")
    void selectOptionByNameOrIndex() {
        Knob k = new Knob("K", new String[]{"npm", "yarn", "pnpm"}, 0);
        k.selectOption("pnpm");
        assertThat(k.getSelectedOption()).isEqualTo("pnpm");

        k.selectOption("1"); // legacy numeric index format
        assertThat(k.getSelectedOption()).isEqualTo("yarn");

        k.selectOption("nonsense"); // neither name nor index: unchanged
        assertThat(k.getSelectedOption()).isEqualTo("yarn");
    }

    @Test
    @DisplayName("setOptions preserves the current selection by name when possible")
    void setOptionsPreservesSelectionByName() {
        Knob k = new Knob("SCRIPT", new String[]{"build", "test", "lint"}, 1); // "test"
        k.setOptions(new String[]{"lint", "test", "deploy"});
        assertThat(k.getSelectedOption()).as("test still present, follows it").isEqualTo("test");

        Knob k2 = new Knob("SCRIPT", new String[]{"build", "test"}, 1); // "test"
        k2.setOptions(new String[]{"a", "b", "c"}); // no "test": resets to 0
        assertThat(k2.getSelectedIndex()).isEqualTo(0);

        // null / empty replacements are ignored
        Knob k3 = new Knob("S", new String[]{"x", "y"}, 1);
        k3.setOptions(null);
        k3.setOptions(new String[0]);
        assertThat(k3.getSelectedOption()).isEqualTo("y");
    }

    @Test
    @DisplayName("Stepped-knob accessors are inert on a continuous knob")
    void steppedAccessorsInertOnContinuous() {
        Knob k = new Knob("VOL", 0.5);
        assertThat(k.getSelectedOption()).isNull();
        k.setSelectedIndex(3); // no-op on continuous
        k.selectOption("x");   // no-op on continuous
        assertThat(k.getValue()).isEqualTo(0.5);
    }

    // ---- Knob: continuous ----

    @Test
    @DisplayName("Continuous knob clamps to 0..1 and fires on meaningful change")
    void continuousKnobClampsAndFires() {
        Knob k = new Knob("VOL", 0.5);
        AtomicInteger fired = new AtomicInteger();
        k.addChangeListener(fired::incrementAndGet);

        k.setValue(2.0);
        assertThat(k.getValue()).isEqualTo(1.0);
        k.setValue(-3.0);
        assertThat(k.getValue()).isEqualTo(0.0);
        assertThat(fired.get()).isEqualTo(2);

        int before = fired.get();
        k.setValue(0.0); // already 0: no event (within epsilon)
        assertThat(fired.get()).isEqualTo(before);
    }

    @Test
    @DisplayName("Continuous knob initial value is clamped")
    void continuousInitialClamped() {
        assertThat(new Knob("V", 5.0).getValue()).isEqualTo(1.0);
        assertThat(new Knob("V", -1.0).getValue()).isEqualTo(0.0);
    }

    // ---- ToggleSwitch ----

    @Test
    @DisplayName("ToggleSwitch flips and fires only on a real change")
    void toggleFiresOnChange() {
        ToggleSwitch t = new ToggleSwitch("WATCH", false);
        AtomicInteger fired = new AtomicInteger();
        t.addChangeListener(fired::incrementAndGet);
        assertThat(t.isOn()).isFalse();

        t.setOn(true);
        assertThat(t.isOn()).isTrue();
        assertThat(fired.get()).isEqualTo(1);

        t.setOn(true); // same value: no event
        assertThat(fired.get()).isEqualTo(1);

        t.setOn(false);
        assertThat(t.isOn()).isFalse();
        assertThat(fired.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("ToggleSwitch honors a custom on/off label pair and an initial-on state")
    void toggleCustomLabelsAndInitialOn() {
        ToggleSwitch t = new ToggleSwitch("MODE", true, "PROD", "DEV");
        assertThat(t.isOn()).isTrue();
        t.setOn(false);
        assertThat(t.isOn()).isFalse();
    }

    // ---- LcdDisplay ----

    @Test
    @DisplayName("LcdDisplay single-line text round-trips; null becomes empty")
    void lcdTextRoundTrip() {
        LcdDisplay lcd = new LcdDisplay(120, 1);
        lcd.setText("READY");
        assertThat(lcd.getText()).isEqualTo("READY");
        lcd.setText(null);
        assertThat(lcd.getText()).isEmpty();
    }

    @Test
    @DisplayName("LcdDisplay clear wipes text; appendLine is safe on a multi-line panel")
    void lcdClearAndAppend() {
        LcdDisplay lcd = new LcdDisplay(200, 3);
        lcd.setText("x");
        lcd.appendLine("one");
        lcd.appendLine("two", Color.GREEN);
        lcd.clear();
        assertThat(lcd.getText()).isEmpty();
    }

    @Test
    @DisplayName("LcdDisplay lines are coerced to at least one")
    void lcdMinimumOneLine() {
        // zero/negative line counts must not throw at construction
        assertThat(new LcdDisplay(80, 0).getText()).isEmpty();
        assertThat(new LcdDisplay(80, -4).getText()).isEmpty();
    }

    // ---- Led ----

    @Test
    @DisplayName("Led on/off state tracks setOn")
    void ledOnOff() {
        Led led = new Led("OK", RackStyle.GO);
        assertThat(led.isOn()).isFalse();
        led.setOn(true);
        assertThat(led.isOn()).isTrue();
        led.setOn(false);
        assertThat(led.isOn()).isFalse();
    }

    @Test
    @DisplayName("Led blinking cancels when a solid state is set")
    void ledBlinkingCancelledBySetOn() {
        Led led = new Led("RUN", RackStyle.MUTATE);
        led.setBlinking(true);
        // setOn(true) while blinking stops the blink and lights solid
        led.setOn(true);
        assertThat(led.isOn()).isTrue();
        led.setBlinking(false); // idempotent stop
        led.setColor(RackStyle.STOP); // color change must not throw
    }

    // ---- VuMeter ----

    @Test
    @DisplayName("VuMeter pulse and setLevel do not throw and accept out-of-range input")
    void vuMeterPulseAndLevel() {
        VuMeter horizontal = new VuMeter("ACTIVITY", false);
        VuMeter vertical = new VuMeter("GAUGE", true);
        // clamped internally; the contract is "never throws, decays on its own"
        horizontal.pulse(0.5);
        horizontal.pulse(2.0);
        vertical.setLevel(0.8);
        vertical.setLevel(-1.0);
        vertical.setLevel(5.0);
    }

    // ---- RackButton ----

    @Test
    @DisplayName("RackButton lit state and enabled-look toggle independently")
    void rackButtonState() {
        RackButton b = new RackButton("BUILD", RackStyle.GO);
        assertThat(b.isLit()).isFalse();
        b.setLit(true);
        assertThat(b.isLit()).isTrue();
        b.setLit(true); // idempotent
        assertThat(b.isLit()).isTrue();
        b.setEnabledLook(false);
        b.setEnabledLook(true); // must not throw
    }

    @Test
    @DisplayName("RackButton command-preview supplies the tooltip, and a throwing supplier is swallowed")
    void rackButtonCommandPreview() {
        RackButton b = new RackButton("SEND", RackStyle.QUERY);
        b.setCommandPreview(() -> "$ npm test");
        assertThat(b.getToolTipText(null)).isEqualTo("$ npm test");

        // a blank preview falls back to the default tooltip (the label)
        b.setCommandPreview(() -> "  ");
        assertThat(b.getToolTipText(null)).isEqualTo("SEND");

        // a supplier that throws must never break hovering
        b.setCommandPreview(() -> {
            throw new IllegalStateException("boom");
        });
        assertThat(b.getToolTipText(null)).isEqualTo("SEND");
    }
}
