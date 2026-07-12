package org.nmox.studio.core.spi.device;

/**
 * The faceplate builder the host hands to {@link DeviceExtension#build}.
 * Controls are created from the rack's own widget vocabulary and laid
 * out by the host — first {@link ButtonRole#GO} and {@link ButtonRole#STOP}
 * buttons take the transport columns every device shares, everything
 * else flows left to right. There is no way to place an unlabeled
 * control (labels become accessible names — the accessibility law) and
 * no way to pick a button color (the role decides it — the color law).
 *
 * <p>Controls created with a {@code key} persist their state in the
 * patch file and survive save/load; keys are part of the saved format,
 * so never rename one. Handle mutators are safe from any thread (the
 * host marshals to the EDT); change callbacks arrive on the EDT.
 *
 * <p>A face that does not fit the declared {@code units} fails at
 * construction with a message naming the overflowing control — knobs
 * and toggles are taller than one rack unit.
 *
 * @since 1.55
 */
public interface DeviceFace {

    /** A stepped selector knob; state persists under {@code key}. */
    KnobHandle knob(String key, String label, String[] options, int initialIndex);

    /** A pressable pad. The role decides color and transport placement. */
    ButtonHandle button(String label, ButtonRole role);

    /** A two-position switch; state persists under {@code key}. */
    ToggleHandle toggle(String key, String label, boolean initial);

    /** An indicator lamp. The tone names its color for screen readers too. */
    LedHandle led(String label, LedTone tone);

    /** A read-only status display ({@code lines} rows, {@code widthPx} wide). */
    LcdHandle lcd(String label, int widthPx, int lines);

    /** An editable one-line display whose text persists under {@code key}. */
    LcdHandle lcdField(String key, String label, int widthPx);

    /** A level meter. */
    VuHandle vu(String label);

    /**
     * What a button does, and therefore what it looks like: the rack
     * reserves green for GO, red for STOP, amber for actions that
     * mutate the project, and blue for read-only queries.
     *
     * @since 1.55
     */
    enum ButtonRole { GO, STOP, MUTATE, QUERY }

    /**
     * What an LED reports: OK glows green, FAIL red, BUSY amber,
     * INFO blue — announced by name to assistive tech, not color alone.
     *
     * @since 1.55
     */
    enum LedTone { OK, FAIL, BUSY, INFO }

    /** @since 1.55 */
    interface KnobHandle {

        /** Runs on the EDT whenever the selection changes. */
        void onChange(Runnable r);

        String selected();

        void select(String option);
    }

    /** @since 1.55 */
    interface ButtonHandle {

        /** Runs on the EDT on every press. */
        void onPress(Runnable r);

        void setLit(boolean lit);
    }

    /** @since 1.55 */
    interface ToggleHandle {

        /** Runs on the EDT whenever the switch flips. */
        void onChange(Runnable r);

        boolean isOn();

        void setOn(boolean on);
    }

    /** @since 1.55 */
    interface LedHandle {

        void setOn(boolean on);

        void setBlinking(boolean blinking);
    }

    /** @since 1.55 */
    interface LcdHandle {

        void setText(String text);

        String text();

        /** Runs on the EDT when the user edits an editable field. */
        void onEdit(Runnable r);
    }

    /** @since 1.55 */
    interface VuHandle {

        /** Kicks the meter; it decays on its own. */
        void pulse(double level);

        void setLevel(double level);
    }
}
