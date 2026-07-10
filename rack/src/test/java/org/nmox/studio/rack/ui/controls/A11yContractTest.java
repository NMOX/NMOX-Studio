package org.nmox.studio.rack.ui.controls;

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleValue;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The accessibility contract of the widget library: every control the
 * devices place must be visible to assistive technology (role, name,
 * value/state) and the operable ones must work from the keyboard.
 * Keyboard operation is exercised by invoking the bound Actions
 * directly - the same objects a real key press dispatches to - so the
 * suite stays headless.
 */
class A11yContractTest {

    private static Action boundAction(JComponent c, String keyStroke) {
        Object key = c.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyStroke));
        assertThat(key).as("binding for " + keyStroke).isNotNull();
        Action a = c.getActionMap().get(key);
        assertThat(a).as("action for " + keyStroke).isNotNull();
        return a;
    }

    private static void invoke(JComponent c, String keyStroke) {
        boundAction(c, keyStroke).actionPerformed(new ActionEvent(c, ActionEvent.ACTION_PERFORMED, keyStroke));
    }

    /** Runs on the EDT so property-change fires land synchronously. */
    private static void onEdt(Runnable r) {
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ---- roles and names ----

    @Test
    @DisplayName("Every widget reports its role and its label as accessible name")
    void rolesAndNames() {
        assertThat(new Knob("GAIN", 0.5).getAccessibleContext().getAccessibleRole())
                .isEqualTo(AccessibleRole.SLIDER);
        assertThat(new Knob("GAIN", 0.5).getAccessibleContext().getAccessibleName())
                .isEqualTo("GAIN");

        assertThat(new RackButton("GO", RackStyle.GO).getAccessibleContext().getAccessibleRole())
                .isEqualTo(AccessibleRole.PUSH_BUTTON);
        assertThat(new RackButton("GO", RackStyle.GO).getAccessibleContext().getAccessibleName())
                .isEqualTo("GO");

        assertThat(new ToggleSwitch("ARM", false).getAccessibleContext().getAccessibleRole())
                .isEqualTo(AccessibleRole.TOGGLE_BUTTON);
        assertThat(new ToggleSwitch("ARM", false).getAccessibleContext().getAccessibleName())
                .isEqualTo("ARM");

        assertThat(new Led("LIVE", RackStyle.GO).getAccessibleContext().getAccessibleRole())
                .isEqualTo(AccessibleRole.LABEL);
        assertThat(new Led("LIVE", RackStyle.GO).getAccessibleContext().getAccessibleName())
                .isEqualTo("LIVE");

        assertThat(new LcdDisplay(100, 1).getAccessibleContext().getAccessibleRole())
                .isEqualTo(AccessibleRole.LABEL);

        assertThat(new VuMeter("OUT", true).getAccessibleContext().getAccessibleRole())
                .isEqualTo(AccessibleRole.PROGRESS_BAR);
        assertThat(new VuMeter("OUT", true).getAccessibleContext().getAccessibleName())
                .isEqualTo("OUT");
    }

    @Test
    @DisplayName("Focus policy: operable widgets are focusable, indicators are not")
    void focusability() {
        assertThat(new Knob("GAIN", 0.5).isFocusable()).isTrue();
        assertThat(new RackButton("GO", RackStyle.GO).isFocusable()).isTrue();
        assertThat(new ToggleSwitch("ARM", false).isFocusable()).isTrue();
        assertThat(new Led("LIVE", RackStyle.GO).isFocusable()).isFalse();
        assertThat(new LcdDisplay(100, 1).isFocusable()).isFalse();
        assertThat(new VuMeter("OUT", true).isFocusable()).isFalse();
    }

    // ---- Knob ----

    @Test
    @DisplayName("Continuous knob: accessible value is percent, arrows step 5, Home/End hit the rails")
    void continuousKnobAccessibleValue() {
        Knob k = new Knob("GAIN", 0.5);
        AccessibleValue av = k.getAccessibleContext().getAccessibleValue();
        assertThat(av).isNotNull();
        assertThat(av.getCurrentAccessibleValue().intValue()).isEqualTo(50);
        assertThat(av.getMinimumAccessibleValue().intValue()).isEqualTo(0);
        assertThat(av.getMaximumAccessibleValue().intValue()).isEqualTo(100);

        k.setValue(0.8);
        assertThat(av.getCurrentAccessibleValue().intValue()).isEqualTo(80);

        invoke(k, "UP");
        assertThat(k.getValue()).isCloseTo(0.85, org.assertj.core.data.Offset.offset(1e-9));
        invoke(k, "DOWN");
        invoke(k, "LEFT");
        assertThat(k.getValue()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(1e-9));
        invoke(k, "HOME");
        assertThat(av.getCurrentAccessibleValue().intValue()).isEqualTo(0);
        invoke(k, "END");
        assertThat(av.getCurrentAccessibleValue().intValue()).isEqualTo(100);
    }

    @Test
    @DisplayName("Stepped knob: accessible value is the position index, description names the position")
    void steppedKnobAccessibleValue() {
        Knob k = new Knob("MODE", new String[]{"dev", "test", "prod"}, 0);
        AccessibleValue av = k.getAccessibleContext().getAccessibleValue();
        assertThat(av.getCurrentAccessibleValue().intValue()).isEqualTo(0);
        assertThat(av.getMaximumAccessibleValue().intValue()).isEqualTo(2);
        assertThat(k.getAccessibleContext().getAccessibleDescription()).contains("1 of 3").contains("dev");

        invoke(k, "RIGHT");
        assertThat(k.getSelectedIndex()).isEqualTo(1);
        assertThat(k.getAccessibleContext().getAccessibleDescription()).contains("2 of 3").contains("test");

        invoke(k, "END");
        assertThat(k.getSelectedOption()).isEqualTo("prod");
        invoke(k, "RIGHT"); // clamped at the last position
        assertThat(k.getSelectedIndex()).isEqualTo(2);
        invoke(k, "HOME");
        assertThat(k.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("Keyboard steps fire the same change events mouse drags fire")
    void knobKeyboardFiresChangeListeners() {
        Knob k = new Knob("GAIN", 0.5);
        AtomicInteger fired = new AtomicInteger();
        k.addChangeListener(fired::incrementAndGet);
        invoke(k, "UP");
        invoke(k, "DOWN");
        assertThat(fired.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Knob announces value changes via ACCESSIBLE_VALUE_PROPERTY")
    void knobFiresAccessibleValueProperty() {
        Knob k = new Knob("GAIN", 0.5);
        List<PropertyChangeEvent> events = new ArrayList<>();
        k.getAccessibleContext().addPropertyChangeListener(events::add);

        k.setValue(0.75);
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getPropertyName()).isEqualTo(AccessibleContext.ACCESSIBLE_VALUE_PROPERTY);
            assertThat(e.getOldValue()).isEqualTo(50);
            assertThat(e.getNewValue()).isEqualTo(75);
        });
    }

    // ---- RackButton ----

    @Test
    @DisplayName("Space and Enter press a RackButton exactly like a click")
    void rackButtonKeyboardFires() {
        RackButton b = new RackButton("GO", RackStyle.GO);
        AtomicInteger fired = new AtomicInteger();
        b.addActionListener(e -> fired.incrementAndGet());

        invoke(b, "SPACE");
        invoke(b, "ENTER");
        assertThat(fired.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("A dimmed RackButton ignores the keyboard like it ignores the mouse")
    void dimmedRackButtonIgnoresKeyboard() {
        RackButton b = new RackButton("GO", RackStyle.GO);
        AtomicInteger fired = new AtomicInteger();
        b.addActionListener(e -> fired.incrementAndGet());
        b.setEnabledLook(false);
        invoke(b, "SPACE");
        assertThat(fired.get()).isZero();
    }

    @Test
    @DisplayName("RackButton exposes one accessible action that fires its listeners")
    void rackButtonAccessibleAction() {
        RackButton b = new RackButton("GO", RackStyle.GO);
        AtomicInteger fired = new AtomicInteger();
        b.addActionListener(e -> fired.incrementAndGet());

        var action = b.getAccessibleContext().getAccessibleAction();
        assertThat(action).isNotNull();
        assertThat(action.getAccessibleActionCount()).isEqualTo(1);
        assertThat(action.doAccessibleAction(0)).isTrue();
        assertThat(fired.get()).isEqualTo(1);
    }

    // ---- ToggleSwitch ----

    @Test
    @DisplayName("ToggleSwitch: CHECKED tracks the bat and Space flips it")
    void toggleSwitchCheckedState() {
        ToggleSwitch t = new ToggleSwitch("ARM", false);
        assertThat(t.getAccessibleContext().getAccessibleStateSet()
                .contains(AccessibleState.CHECKED)).isFalse();

        invoke(t, "SPACE");
        assertThat(t.isOn()).isTrue();
        assertThat(t.getAccessibleContext().getAccessibleStateSet()
                .contains(AccessibleState.CHECKED)).isTrue();

        invoke(t, "SPACE");
        assertThat(t.isOn()).isFalse();
        assertThat(t.getAccessibleContext().getAccessibleStateSet()
                .contains(AccessibleState.CHECKED)).isFalse();
    }

    @Test
    @DisplayName("ToggleSwitch fires ACCESSIBLE_STATE_PROPERTY when flipped")
    void toggleSwitchFiresStateProperty() {
        ToggleSwitch t = new ToggleSwitch("ARM", false);
        List<PropertyChangeEvent> events = new ArrayList<>();
        t.getAccessibleContext().addPropertyChangeListener(events::add);

        t.setOn(true);
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getPropertyName()).isEqualTo(AccessibleContext.ACCESSIBLE_STATE_PROPERTY);
            assertThat(e.getNewValue()).isEqualTo(AccessibleState.CHECKED);
        });
    }

    // ---- Led ----

    @Test
    @DisplayName("Led description reflects off, on and blinking - with the color named")
    void ledDescriptionTracksState() {
        Led led = new Led("LIVE", RackStyle.GO);
        AccessibleContext ctx = led.getAccessibleContext();
        assertThat(ctx.getAccessibleDescription()).isEqualTo("off");

        led.setOn(true);
        assertThat(ctx.getAccessibleDescription()).isEqualTo("on (green)");

        led.setBlinking(true);
        assertThat(ctx.getAccessibleDescription()).startsWith("blinking");

        led.setBlinking(false);
        led.setColor(RackStyle.STOP);
        led.setOn(true);
        assertThat(ctx.getAccessibleDescription()).isEqualTo("on (red)");
    }

    @Test
    @DisplayName("Led fires a property change when it flips")
    void ledFiresOnFlip() {
        Led led = new Led("LIVE", RackStyle.GO);
        List<PropertyChangeEvent> events = new ArrayList<>();
        led.getAccessibleContext().addPropertyChangeListener(events::add);

        led.setOn(true);
        assertThat(events)
                .extracting(PropertyChangeEvent::getPropertyName)
                .contains(AccessibleContext.ACCESSIBLE_STATE_PROPERTY);
    }

    // ---- LcdDisplay ----

    @Test
    @DisplayName("LcdDisplay: name from the edit prompt, description is the glass")
    void lcdNameAndDescription() {
        LcdDisplay lcd = new LcdDisplay(120, 1);
        lcd.setEditable("URL to watch");
        assertThat(lcd.getAccessibleContext().getAccessibleName()).isEqualTo("URL to watch");

        lcd.setText("http://localhost:3000");
        assertThat(lcd.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("http://localhost:3000");
    }

    @Test
    @DisplayName("LcdDisplay: an explicit accessible name beats the derived one")
    void lcdExplicitNameWins() {
        LcdDisplay lcd = new LcdDisplay(120, 1);
        lcd.getAccessibleContext().setAccessibleName("STATUS");
        lcd.setEditable("something else");
        assertThat(lcd.getAccessibleContext().getAccessibleName()).isEqualTo("STATUS");
    }

    @Test
    @DisplayName("LcdDisplay announces text changes (visible data + description)")
    void lcdFiresOnTextChange() {
        LcdDisplay lcd = new LcdDisplay(120, 1);
        List<PropertyChangeEvent> events = new ArrayList<>();
        lcd.getAccessibleContext().addPropertyChangeListener(events::add);

        onEdt(() -> lcd.setText("READY"));
        assertThat(events)
                .extracting(PropertyChangeEvent::getPropertyName)
                .contains(AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY,
                        AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY);
    }

    @Test
    @DisplayName("Multi-line LcdDisplay reads all lines on the glass, newest last")
    void lcdMultiLineDescription() {
        LcdDisplay lcd = new LcdDisplay(200, 2);
        onEdt(() -> {
            lcd.appendLine("first");
            lcd.appendLine("second");
            lcd.appendLine("third"); // scrolls "first" off
        });
        assertThat(lcd.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("second\nthird");
    }

    // ---- VuMeter ----

    @Test
    @DisplayName("VuMeter: accessible value tracks the pinned level as percent")
    void vuMeterAccessibleValue() {
        VuMeter meter = new VuMeter("OUT", true);
        AccessibleValue av = meter.getAccessibleContext().getAccessibleValue();
        assertThat(av).isNotNull();
        assertThat(av.getMinimumAccessibleValue().intValue()).isEqualTo(0);
        assertThat(av.getMaximumAccessibleValue().intValue()).isEqualTo(100);

        onEdt(() -> meter.setLevel(0.5));
        assertThat(av.getCurrentAccessibleValue().intValue()).isEqualTo(50);

        // a meter reports; it must refuse to be driven from outside
        assertThat(av.setCurrentAccessibleValue(10)).isFalse();
        assertThat(av.getCurrentAccessibleValue().intValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("VuMeter announces level moves via ACCESSIBLE_VALUE_PROPERTY")
    void vuMeterFiresAccessibleValueProperty() {
        VuMeter meter = new VuMeter("OUT", true);
        List<PropertyChangeEvent> events = new ArrayList<>();
        meter.getAccessibleContext().addPropertyChangeListener(events::add);

        onEdt(() -> meter.setLevel(0.7));
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getPropertyName()).isEqualTo(AccessibleContext.ACCESSIBLE_VALUE_PROPERTY);
            assertThat(e.getNewValue()).isEqualTo(70);
        });
    }
}
