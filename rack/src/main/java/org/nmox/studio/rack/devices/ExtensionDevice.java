package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import org.nmox.studio.core.spi.device.DeviceDescriptor;
import org.nmox.studio.core.spi.device.DeviceExtension;
import org.nmox.studio.core.spi.device.DeviceFace;
import org.nmox.studio.core.spi.device.DeviceLogic;
import org.nmox.studio.core.spi.device.DeviceServices;
import org.nmox.studio.core.spi.device.PortSpec;
import org.nmox.studio.rack.model.Port;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.model.SignalType;
import org.nmox.studio.rack.service.ServingRegistry;
import org.nmox.studio.rack.service.WorkspaceTrust;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.Led;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;
import org.nmox.studio.rack.ui.controls.VuMeter;

/**
 * Hosts a {@link DeviceExtension} as a real rack device. The house laws
 * are enforced HERE, not trusted to plugin code: workspace trust gates
 * every exec, button colors come from roles, every control label
 * becomes an accessible name (blank refused), the first GO/STOP take
 * the shared transport columns, and a face that overflows its declared
 * units fails loudly at mount instead of painting off the plate.
 */
public final class ExtensionDevice extends RackDevice {

    /**
     * The workspace-trust gate, swappable by tests: headless JVMs
     * auto-allow real prompts, so proving "no trust, no spawn" needs a
     * deniable seam. Production never touches it.
     */
    static Predicate<File> trustGate = WorkspaceTrust::requestTrust;

    private static final Color DEFAULT_ACCENT = new Color(120, 150, 170);

    private final DeviceLogic logic;
    private final Services services;
    private volatile boolean serving;

    public ExtensionDevice(DeviceExtension extension) {
        this(extension, extension.descriptor());
    }

    private ExtensionDevice(DeviceExtension extension, DeviceDescriptor d) {
        super(d.id(), d.title(),
                d.tagline() == null ? "" : d.tagline(),
                d.accent() == null ? DEFAULT_ACCENT : d.accent(),
                Math.max(1, Math.min(3, d.units())));
        for (PortSpec p : d.ports()) {
            SignalType type = SignalType.valueOf(p.signal().name());
            if (p.direction() == PortSpec.Direction.IN) {
                addInPort(p.id(), p.label(), type);
            } else {
                addOutPort(p.id(), p.label(), type);
            }
        }
        Face face = new Face();
        this.services = new Services();
        this.logic = extension.build(face, services);
        face.assertFits(d);
    }

    @Override
    protected void onAttached() {
        // fires on first mount AND on undo re-attach of the same instance;
        // lets the plugin re-arm a poll/clock/serving a removal tore down —
        // the hook built-ins use (v1.50 TAIL/TEMPO), now on the SPI too
        try {
            logic.onAttached(services);
        } catch (RuntimeException ex) {
            // a plugin's re-arm failure must not break the rack's re-attach
        }
    }

    @Override
    public void receive(Port in, Signal signal) {
        if (isDisposed()) {
            return; // a queued signal must not wake a deleted device
        }
        switch (signal.type()) {
            case TRIGGER -> logic.onTrigger(in.getId(), signal.high());
            case DATA -> logic.onData(in.getId(), signal.payload());
            case GATE -> logic.onGate(in.getId(), signal.high());
        }
    }

    @Override
    public void projectChanged(File dir) {
        logic.onProjectChanged(dir);
    }

    @Override
    public void dispose() {
        // stop the process FIRST so onDispose()'s contract holds — the
        // javadoc promises the process is already stopped when it runs
        stopProcess();
        deregisterServing();
        try {
            logic.onDispose();
        } catch (RuntimeException ex) {
            // a plugin's cleanup failure must not block the rack's
        }
        super.dispose();
    }

    private String servingId() {
        return getTypeId() + "@" + System.identityHashCode(this);
    }

    private void deregisterServing() {
        if (serving) {
            serving = false;
            ServingRegistry.getDefault().deregister(servingId());
        }
    }

    // ---- the services the plugin's logic calls ----

    private final class Services implements DeviceServices {

        @Override
        public void exec(List<String> command, Consumer<String> onLine, IntConsumer onExit) {
            // the trust gate is the host's job, not the plugin's: running
            // a stranger's tasks asks the human first, exactly like every
            // built-in command device
            if (!trustGate.test(projectDir())) {
                if (onExit != null) {
                    onExit.accept(-1);
                }
                return;
            }
            ExtensionDevice.this.exec(command,
                    onLine == null ? line -> { } : onLine,
                    onExit == null ? code -> { } : onExit);
        }

        @Override
        public void stop() {
            stopProcess();
        }

        @Override
        public boolean isRunning() {
            return isProcessRunning();
        }

        @Override
        public void emitTrigger(String portId, boolean ok) {
            emitIfDeclared(portId, SignalType.TRIGGER, Signal.trigger(ok));
        }

        @Override
        public void emitData(String portId, String text) {
            emitIfDeclared(portId, SignalType.DATA, Signal.data(text == null ? "" : text));
        }

        @Override
        public void emitGate(String portId, boolean high) {
            emitIfDeclared(portId, SignalType.GATE, Signal.gate(high));
        }

        private void emitIfDeclared(String portId, SignalType type, Signal signal) {
            Port p = getPort(portId);
            if (p != null && p.getDirection() == Port.Direction.OUT && p.getType() == type) {
                emit(portId, signal);
            }
        }

        @Override
        public File projectDir() {
            return ExtensionDevice.this.projectDir();
        }

        @Override
        public void announceServing(String url) {
            serving = true;
            ServingRegistry.getDefault().register(new ServingRegistry.Serving(
                    servingId(), getTitle(), url, ServingRegistry.Kind.WEB,
                    ExtensionDevice.this.projectDir()));
        }

        @Override
        public void withdrawServing() {
            deregisterServing();
        }
    }

    // ---- the face builder: widgets, laws, layout ----

    private final class Face implements DeviceFace {

        private static final int FLOW_START_X = 176;
        private static final int LEFT_X = RackStyle.EAR_WIDTH + 18;
        private static final int TOP_Y = 10;
        private static final int GAP = 14;

        private int x = FLOW_START_X;
        private int rowY = TOP_Y;
        private int rowH = 0;
        private int maxBottom = 0;
        private boolean goPlaced;
        private boolean stopPlaced;
        private String lastLabel = "";

        private void require(String what, String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "every " + what + " needs a non-blank value — labels become "
                                + "accessible names, keys become saved state");
            }
        }

        private <T extends java.awt.Component> T flow(T comp, String label) {
            lastLabel = label;
            int w = comp.getPreferredSize().width;
            int h = comp.getPreferredSize().height;
            int maxX = RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH - 10;
            if (x + w > maxX) {
                x = LEFT_X;
                rowY += rowH + 8;
                rowH = 0;
            }
            place(comp, x, rowY);
            x += w + GAP;
            rowH = Math.max(rowH, h);
            maxBottom = Math.max(maxBottom, rowY + h);
            return comp;
        }

        void assertFits(DeviceDescriptor d) {
            int height = getUnits() * RackStyle.UNIT;
            if (maxBottom > height - 4) {
                throw new IllegalStateException("device \"" + d.id() + "\": control \""
                        + lastLabel + "\" reaches " + maxBottom + "px but " + getUnits()
                        + "U is only " + height + "px — declare more units "
                        + "(knobs and toggles need 2)");
            }
        }

        @Override
        public KnobHandle knob(String key, String label, String[] options, int initialIndex) {
            require("knob key", key);
            require("knob label", label);
            Knob knob = flow(new Knob(label, options, initialIndex), label);
            paramByName(key, knob);
            return new KnobHandle() {
                @Override
                public void onChange(Runnable r) {
                    knob.addChangeListener(r);
                }

                @Override
                public String selected() {
                    return knob.getSelectedOption();
                }

                @Override
                public void select(String option) {
                    onEdt(() -> knob.selectOption(option));
                }
            };
        }

        @Override
        public ButtonHandle button(String label, ButtonRole role) {
            require("button label", label);
            RackButton button = new RackButton(label, roleColor(role));
            // the transport law: the primary GO and STOP live in the same
            // columns on every device in the rack
            if (role == ButtonRole.GO && !goPlaced) {
                goPlaced = true;
                place(button, RackStyle.TRANSPORT_X, 12);
                maxBottom = Math.max(maxBottom, 12 + button.getPreferredSize().height);
            } else if (role == ButtonRole.STOP && !stopPlaced) {
                stopPlaced = true;
                place(button, RackStyle.TRANSPORT_STOP_X, 12);
                maxBottom = Math.max(maxBottom, 12 + button.getPreferredSize().height);
            } else {
                flow(button, label);
            }
            return new ButtonHandle() {
                @Override
                public void onPress(Runnable r) {
                    button.addActionListener(e -> r.run());
                }

                @Override
                public void setLit(boolean lit) {
                    onEdt(() -> button.setLit(lit));
                }
            };
        }

        @Override
        public ToggleHandle toggle(String key, String label, boolean initial) {
            require("toggle key", key);
            require("toggle label", label);
            ToggleSwitch toggle = flow(new ToggleSwitch(label, initial), label);
            param(key, toggle);
            return new ToggleHandle() {
                @Override
                public void onChange(Runnable r) {
                    toggle.addChangeListener(r);
                }

                @Override
                public boolean isOn() {
                    return toggle.isOn();
                }

                @Override
                public void setOn(boolean on) {
                    onEdt(() -> toggle.setOn(on));
                }
            };
        }

        @Override
        public LedHandle led(String label, LedTone tone) {
            require("led label", label);
            Led led = flow(new Led(label, toneColor(tone)), label);
            return new LedHandle() {
                @Override
                public void setOn(boolean on) {
                    onEdt(() -> led.setOn(on));
                }

                @Override
                public void setBlinking(boolean blinking) {
                    onEdt(() -> led.setBlinking(blinking));
                }
            };
        }

        @Override
        public LcdHandle lcd(String label, int widthPx, int lines) {
            require("lcd label", label);
            LcdDisplay lcd = new LcdDisplay(Math.max(60, widthPx), Math.max(1, lines));
            lcd.getAccessibleContext().setAccessibleName(label);
            flow(lcd, label);
            return lcdHandle(lcd);
        }

        @Override
        public LcdHandle lcdField(String key, String label, int widthPx) {
            require("lcd field key", key);
            require("lcd field label", label);
            LcdDisplay lcd = new LcdDisplay(Math.max(60, widthPx), 1);
            lcd.getAccessibleContext().setAccessibleName(label);
            lcd.setEditable(label);
            flow(lcd, label);
            param(key, lcd);
            return lcdHandle(lcd);
        }

        private LcdHandle lcdHandle(LcdDisplay lcd) {
            return new LcdHandle() {
                @Override
                public void setText(String text) {
                    onEdt(() -> lcd.setText(text == null ? "" : text));
                }

                @Override
                public String text() {
                    return lcd.getText();
                }

                @Override
                public void onEdit(Runnable r) {
                    lcd.addEditListener(r);
                }
            };
        }

        @Override
        public VuHandle vu(String label) {
            require("vu label", label);
            VuMeter vu = flow(new VuMeter(label, false), label);
            return new VuHandle() {
                @Override
                public void pulse(double level) {
                    onEdt(() -> vu.pulse(level));
                }

                @Override
                public void setLevel(double level) {
                    onEdt(() -> vu.setLevel(level));
                }
            };
        }

        private Color roleColor(ButtonRole role) {
            return switch (role) {
                case GO -> RackStyle.GO;
                case STOP -> RackStyle.STOP;
                case MUTATE -> RackStyle.MUTATE;
                case QUERY -> RackStyle.QUERY;
            };
        }

        private Color toneColor(LedTone tone) {
            return switch (tone) {
                case OK -> RackStyle.GO;
                case FAIL -> RackStyle.STOP;
                case BUSY -> RackStyle.MUTATE;
                case INFO -> RackStyle.QUERY;
            };
        }
    }
}
