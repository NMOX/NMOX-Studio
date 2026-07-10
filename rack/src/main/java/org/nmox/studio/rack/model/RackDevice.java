package org.nmox.studio.rack.model;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.nmox.studio.rack.engine.CommandExecutor;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * Base class for every rack device. A device is simultaneously its own
 * Swing component: it paints a hardware faceplate on the front and a
 * jack field on the back, and hosts its control-surface children
 * (knobs, buttons, switches...) when the rack faces forward.
 */
public abstract class RackDevice extends JPanel {

    private final String typeId;
    private final String title;
    private final String tagline;
    private final Color accent;
    private final int units;
    private final List<Port> ports = new ArrayList<>();
    private final Map<String, Supplier<String>> paramGetters = new LinkedHashMap<>();
    private final Map<String, Consumer<String>> paramSetters = new LinkedHashMap<>();

    private Rack rack;
    private boolean front = true;
    /** Recent signal per port, for the front patch-bay strip. */
    private final java.util.Map<Port, Long> portActivity = new java.util.concurrent.ConcurrentHashMap<>();
    private final Rack.Listener bayListener = new Rack.Listener() {
        @Override
        public void signalTravelled(org.nmox.studio.rack.model.Cable cable) {
            boolean mine = false;
            if (cable.getFrom().getDevice() == RackDevice.this) {
                portActivity.put(cable.getFrom(), System.currentTimeMillis());
                mine = true;
            }
            if (cable.getTo().getDevice() == RackDevice.this) {
                portActivity.put(cable.getTo(), System.currentTimeMillis());
                mine = true;
            }
            if (mine) {
                onEdt(() -> {
                    repaint();
                    javax.swing.Timer fade = new javax.swing.Timer(450, e -> repaint());
                    fade.setRepeats(false);
                    fade.start();
                });
            }
        }

        @Override
        public void cablesChanged() {
            onEdt(RackDevice.this::repaint);
        }
    };
    private int inPortCount;
    private int outPortCount;
    private volatile CommandExecutor.Handle running;
    /**
     * True after {@link #dispose()}, cleared by {@link #attach} — undo of a
     * remove re-attaches the SAME instance, so a permanent flag would leave
     * undo-restored devices dead. While set, queued signals and exec launches
     * are refused: a trigger delivered after removal must never start a
     * process inside a deleted device.
     */
    private volatile boolean disposed;

    protected RackDevice(String typeId, String title, String tagline, Color accent, int units) {
        this.typeId = typeId;
        this.title = title;
        this.tagline = tagline;
        this.accent = accent;
        this.units = units;
        setLayout(null);
        setOpaque(true);
        int h = units * RackStyle.UNIT;
        Dimension size = new Dimension(RackStyle.RACK_WIDTH, h);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        setSize(size);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
    }

    // ---- identity ----

    public String getTypeId() {
        return typeId;
    }

    public String getTitle() {
        return title;
    }

    public Color getAccent() {
        return accent;
    }

    public int getUnits() {
        return units;
    }

    // ---- lifecycle ----

    void attach(Rack rack) {
        // re-attach (undo of a remove) brings the same instance back to life
        disposed = false;
        this.rack = rack;
        rack.addListener(bayListener);
        onAttached();
    }

    /** Called once the device is in a rack; project dir is available now. */
    protected void onAttached() {
    }

    /** Called when the rack's project directory changes. */
    public void projectChanged(File dir) {
    }

    /**
     * Called on the rack router thread when project manifests changed on
     * disk (coalesced; see {@code ManifestPulse}). Overriders check the
     * batch for THEIR files, do file work via {@link #offEdt}, marshal UI
     * via {@link #onEdt}, and stay idempotent: a reload that finds
     * nothing new must not fire knob/option events.
     */
    public void manifestChanged(java.util.List<java.nio.file.Path> changed) {
    }

    /**
     * The manifest file this device is configured from, when it exists —
     * the "Open package.json" faceplate context action reads this.
     * Devices not backed by a manifest return empty.
     */
    public java.util.Optional<File> primaryManifest() {
        return java.util.Optional.empty();
    }

    /** Kill any running process and release resources. */
    public void dispose() {
        disposed = true;
        if (rack != null) {
            rack.removeListener(bayListener);
        }
        stopProcess();
    }

    /** True after dispose() until a re-attach (undo of remove) revives it. */
    public final boolean isDisposed() {
        return disposed;
    }

    public Rack getRack() {
        return rack;
    }

    protected File projectDir() {
        return rack != null ? rack.getProjectDir() : new File(System.getProperty("user.home"));
    }

    // ---- ports ----

    protected Port addInPort(String id, String label, SignalType type) {
        Port p = new Port(this, id, label, Port.Direction.IN, type);
        int h = getPreferredSize().height;
        p.setLocation(RackStyle.EAR_WIDTH + 52 + inPortCount * 82, h - 44);
        inPortCount++;
        ports.add(p);
        return p;
    }

    protected Port addOutPort(String id, String label, SignalType type) {
        Port p = new Port(this, id, label, Port.Direction.OUT, type);
        int h = getPreferredSize().height;
        p.setLocation(RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH - 52 - outPortCount * 82, h - 44);
        outPortCount++;
        ports.add(p);
        return p;
    }

    public List<Port> getPorts() {
        return new ArrayList<>(ports);
    }

    public Port getPort(String id) {
        for (Port p : ports) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    /** Hit-test a back-view point against the jack field. */
    public Port portAt(Point pt) {
        for (Port p : ports) {
            int dx = pt.x - p.getX(), dy = pt.y - p.getY();
            if (dx * dx + dy * dy <= 16 * 16) {
                return p;
            }
        }
        return null;
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        if (front && e.getY() > getHeight() - 20
                && e.getX() > getWidth() - RackStyle.EAR_WIDTH - 20 - getPorts().size() * 12) {
            long cabled = rack == null ? 0
                    : getPorts().stream().filter(p -> !rack.cablesAt(p).isEmpty()).count();
            return "<html><b>Patch bay</b> — " + cabled + " of " + getPorts().size()
                    + " ports cabled<br>Press Tab to flip the rack and patch</html>";
        }
        if (!front) {
            Port p = portAt(e.getPoint());
            if (p != null) {
                boolean in = p.getDirection() == Port.Direction.IN;
                return "<html><b>" + p.getLabel() + "</b> &mdash; " + p.getType()
                        + (in ? " input" : " output")
                        + "<br>Drag a cable to a matching " + (in ? "output" : "input")
                        + " jack; right-click to unplug</html>";
            }
            return null;
        }
        return super.getToolTipText(e);
    }

    // ---- signals ----

    /** Emits a signal from one of this device's output ports. */
    protected void emit(String portId, Signal signal) {
        if (rack != null) {
            Port p = getPort(portId);
            if (p != null) {
                rack.emit(p, signal);
            }
        }
    }

    /**
     * Receives a signal on an input port. Runs on the rack router thread;
     * use SwingUtilities.invokeLater for UI mutations.
     */
    public void receive(Port in, Signal signal) {
    }

    // ---- command execution ----

    /**
     * Runs a tool command in the project directory with rack env applied.
     * Only one process per device at a time; a second call stops the first.
     */
    protected void exec(List<String> command, Consumer<String> onLine, IntConsumer onExit) {
        exec(command, Map.of(), onLine, onExit);
    }

    /** Like {@link #exec} with additional environment for this launch only. */
    protected void exec(List<String> command, Map<String, String> extraEnv,
            Consumer<String> onLine, IntConsumer onExit) {
        exec(command, extraEnv, projectDir(), onLine, onExit);
    }

    /** Full control: extra env and an explicit working directory. */
    protected void exec(List<String> command, Map<String, String> extraEnv, File workingDir,
            Consumer<String> onLine, IntConsumer onExit) {
        if (disposed) {
            return; // a queued trigger must not launch into a deleted device
        }
        stopProcess();
        // dotenv first (project root, then the lane's own dir in a
        // monorepo), rack-wide overrides above it, per-launch extras on
        // top - the same file the dev's own tooling reads, no re-typing
        File root = rack != null ? rack.getProjectDir() : null;
        Map<String, String> env = new LinkedHashMap<>(
                org.nmox.studio.rack.engine.EnvFiles.load(root));
        if (workingDir != null && !workingDir.equals(root)) {
            env.putAll(org.nmox.studio.rack.engine.EnvFiles.load(workingDir));
        }
        env.putAll(rack != null ? rack.getEnvOverrides() : Map.of());
        env.putAll(extraEnv);
        // identity-guarded swap: a previous run's onExit, arriving late on a
        // worker thread, must not null out the handle of the run that replaced
        // it — that would orphan a live process (isLive()/panic() would see
        // nothing to kill).
        CommandExecutor.Handle[] self = new CommandExecutor.Handle[1];
        self[0] = CommandExecutor.run(title, workingDir, env,
                command, onLine, code -> {
                    if (running == self[0]) {
                        running = null;
                    }
                    onExit.accept(code);
                });
        running = self[0];
    }

    protected boolean isProcessRunning() {
        CommandExecutor.Handle h = running;
        return h != null && h.isAlive();
    }

    protected void stopProcess() {
        CommandExecutor.Handle h = running;
        if (h != null) {
            running = null;
            h.kill();
        }
    }

    /**
     * Readiness-gated long-running: serve while the gate is high, stop
     * when it drops. Patch an upstream RUNNING/SERVING gate (HARBOR's
     * engine, a dev server) into a long-runner's ENABLE input and it
     * starts only once its dependency is up - DB → API → web ordering
     * by cable. A high edge while already running is ignored so the
     * gate never double-launches. Mirrors TEMPO's enable semantics.
     */
    protected void enableGate(boolean high, Runnable start, Runnable stop) {
        if (high) {
            if (!isProcessRunning()) {
                start.run();
            }
        } else {
            stop.run();
        }
    }

    /** True while this device has a tool process running. */
    public boolean isLive() {
        CommandExecutor.Handle h = running;
        return h != null && h.isAlive();
    }

    /**
     * Whether session resurrection should bring this device back after a
     * crash. Process devices tie it to {@link #isLive()}; timer devices
     * (TAIL following a log, TEMPO clocking) that carry no process
     * override this to report their armed switch, so a kill -9 mid-follow
     * comes back following.
     */
    public boolean isResumable() {
        return isLive();
    }

    /**
     * Re-fires this device's primary action - session resurrection
     * calls this to bring back what was running before the IDE died.
     * Devices without a primary action ignore it.
     */
    public void resume() {
    }

    /**
     * Emergency stop, callable from outside (STOP ALL, and the JVM
     * shutdown reaper). Kills synchronously with forced escalation:
     * shutdown hooks get no second chance, so neither do dev servers.
     */
    public void panic() {
        CommandExecutor.Handle h = running;
        if (h != null) {
            running = null;
            h.killAndWait(1_500);
        }
    }

    // ---- control placement & persistence ----

    /** Adds a control at fixed faceplate coordinates. */
    protected <T extends Component> T place(T comp, int x, int y) {
        comp.setLocation(x, y);
        comp.setSize(comp.getPreferredSize());
        add(comp);
        return comp;
    }

    protected void param(String key, Knob knob) {
        if (knob.getSelectedOption() != null) {
            paramGetters.put(key, () -> String.valueOf(knob.getSelectedIndex()));
            paramSetters.put(key, v -> knob.setSelectedIndex(Integer.parseInt(v)));
        } else {
            paramGetters.put(key, () -> String.valueOf(knob.getValue()));
            paramSetters.put(key, v -> knob.setValue(Double.parseDouble(v)));
        }
    }

    protected void param(String key, ToggleSwitch toggle) {
        paramGetters.put(key, () -> String.valueOf(toggle.isOn()));
        paramSetters.put(key, v -> toggle.setOn(Boolean.parseBoolean(v)));
    }

    /**
     * Persists a stepped knob by option NAME rather than index - for
     * knobs whose option list is dynamic (npm scripts), where an index
     * is meaningless across projects. Reads legacy numeric values too.
     */
    protected void paramByName(String key, Knob knob) {
        paramGetters.put(key, () -> String.valueOf(knob.getSelectedOption()));
        paramSetters.put(key, knob::selectOption);
    }

    protected void param(String key, LcdDisplay lcd) {
        paramGetters.put(key, lcd::getText);
        paramSetters.put(key, lcd::setText);
        // the persistence key names the panel's purpose ("url", "glob") —
        // reuse it as the accessible name where the device set none, so
        // screen readers never meet an anonymous display
        if (lcd.getAccessibleContext().getAccessibleName() == null) {
            lcd.getAccessibleContext().setAccessibleName(key);
        }
    }

    public Map<String, String> getState() {
        Map<String, String> state = new LinkedHashMap<>();
        paramGetters.forEach((k, g) -> state.put(k, g.get()));
        return state;
    }

    public void applyState(Map<String, String> state) {
        state.forEach((k, v) -> {
            Consumer<String> setter = paramSetters.get(k);
            if (setter != null) {
                try {
                    setter.accept(v);
                } catch (RuntimeException ignored) {
                    // stale or malformed patch value; keep the default
                }
            }
        });
    }

    // ---- view flipping ----

    public boolean isFront() {
        return front;
    }

    public void setFront(boolean front) {
        if (this.front != front) {
            this.front = front;
            for (Component c : getComponents()) {
                c.setVisible(front);
            }
            repaint();
        }
    }

    /** The draggable grip on the faceplate (used for rack reordering). */
    public boolean isGrip(Point p) {
        return front && p.y < 24 && p.x > RackStyle.EAR_WIDTH && p.x < getWidth() - RackStyle.EAR_WIDTH;
    }

    // ---- painting ----

    @Override
    protected void paintComponent(Graphics gr) {
        Graphics2D g = (Graphics2D) gr.create();
        RackStyle.antialias(g);
        int w = getWidth(), h = getHeight();

        if (front) {
            RackStyle.paintFaceplate(g, w, h, accent);
            paintEars(g, h);
            // title block
            g.setFont(RackStyle.TITLE_FONT);
            g.setColor(RackStyle.SILKSCREEN);
            g.drawString(title, RackStyle.EAR_WIDTH + 14, 22);
            g.setFont(RackStyle.TINY_FONT);
            g.setColor(RackStyle.SILKSCREEN_DIM);
            g.drawString(tagline, RackStyle.EAR_WIDTH + 14, 34);
            // brand
            g.setFont(RackStyle.BRAND_FONT);
            g.setColor(accent.brighter());
            String brand = "NMOX";
            int bw = g.getFontMetrics().stringWidth(brand);
            g.drawString(brand, w - RackStyle.EAR_WIDTH - bw - 12, 20);
            paintFront(g, w, h);
            paintPatchBay(g, w, h);
        } else {
            RackStyle.paintBackPanel(g, w, h);
            paintEars(g, h);
            g.setFont(RackStyle.LABEL_FONT);
            g.setColor(RackStyle.SILKSCREEN_DIM);
            g.drawString(title + "  —  REAR", RackStyle.EAR_WIDTH + 14, 18);
            paintRearHardware(g, w, h);
            paintJackGroups(g, h);
            for (Port p : ports) {
                paintJack(g, p);
            }
            paintBack(g, w, h);
        }
        g.dispose();
    }

    private void paintEars(Graphics2D g, int h) {
        g.setColor(new Color(33, 33, 36));
        g.fillRect(0, 0, RackStyle.EAR_WIDTH, h);
        g.fillRect(getWidth() - RackStyle.EAR_WIDTH, 0, RackStyle.EAR_WIDTH, h);
        g.setColor(RackStyle.PANEL_EDGE);
        g.drawLine(RackStyle.EAR_WIDTH, 0, RackStyle.EAR_WIDTH, h);
        g.drawLine(getWidth() - RackStyle.EAR_WIDTH, 0, getWidth() - RackStyle.EAR_WIDTH, h);
        RackStyle.paintScrew(g, RackStyle.EAR_WIDTH / 2, 12);
        RackStyle.paintScrew(g, RackStyle.EAR_WIDTH / 2, h - 12);
        RackStyle.paintScrew(g, getWidth() - RackStyle.EAR_WIDTH / 2, 12);
        RackStyle.paintScrew(g, getWidth() - RackStyle.EAR_WIDTH / 2, h - 12);
    }

    /**
     * The hardware every real unit carries on its back: an IEC power
     * inlet with its fuse drawer, voltage silkscreen, and a serial
     * sticker. Pure decoration - but it is what makes the rear of the
     * rack read as GEAR rather than a diagram. 2U and up only; a 1U
     * back is all jacks.
     */
    private void paintRearHardware(Graphics2D g, int w, int h) {
        if (h < 2 * RackStyle.UNIT) {
            return;
        }
        int x = w - RackStyle.EAR_WIDTH - 96;
        int y = 10;
        // IEC C14 inlet: recessed black trapezoid with three pin slots
        g.setColor(new Color(16, 16, 18));
        g.fillRoundRect(x, y, 44, 30, 4, 4);
        g.setColor(new Color(70, 71, 75));
        g.drawRoundRect(x, y, 44, 30, 4, 4);
        g.setColor(new Color(8, 8, 9));
        g.fillRoundRect(x + 5, y + 5, 34, 20, 6, 6);
        g.setColor(new Color(170, 172, 176));
        g.fillRect(x + 11, y + 12, 4, 8);   // L
        g.fillRect(x + 20, y + 9, 4, 8);    // E
        g.fillRect(x + 29, y + 12, 4, 8);   // N
        // fuse drawer beneath the inlet
        g.setColor(new Color(30, 30, 33));
        g.fillRoundRect(x + 50, y + 6, 26, 12, 3, 3);
        g.setColor(new Color(8, 8, 9));
        g.drawRoundRect(x + 50, y + 6, 26, 12, 3, 3);
        g.setFont(RackStyle.TINY_FONT);
        g.setColor(RackStyle.SILKSCREEN_DIM);
        g.drawString("FUSE T1A", x + 50, y + 30);
        g.drawString("100-240V~ 50/60Hz", x - 110, y + 12);
        // serial sticker: pale label, barcode, number from the type id
        int sx = x - 110, sy = y + 18;
        g.setColor(new Color(214, 211, 196));
        g.fillRect(sx, sy, 96, 20);
        g.setColor(new Color(20, 20, 22));
        int bar = sx + 4;
        int seed = typeId.hashCode();
        while (bar < sx + 64) {
            int bw = 1 + Math.floorMod(seed >> (bar % 13), 3);
            g.fillRect(bar, sy + 3, bw, 10);
            bar += bw + 2;
        }
        g.setFont(RackStyle.TINY_FONT);
        g.drawString("S/N " + String.format("%07d", Math.floorMod(seed, 10_000_000)),
                sx + 4, sy + 18);
    }

    private void paintJackGroups(Graphics2D g, int h) {
        if (inPortCount > 0) {
            int wBox = inPortCount * 82 + 20;
            RackStyle.paintGroup(g, RackStyle.EAR_WIDTH + 10, h - 84, wBox, 74, "INPUTS");
        }
        if (outPortCount > 0) {
            int wBox = outPortCount * 82 + 20;
            RackStyle.paintGroup(g, RackStyle.RACK_WIDTH - RackStyle.EAR_WIDTH - 10 - wBox, h - 84, wBox, 74, "OUTPUTS");
        }
    }

    private void paintJack(Graphics2D g, Port p) {
        int x = p.getX(), y = p.getY();
        // hex flange
        var hex = new java.awt.Polygon();
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            hex.addPoint((int) (x + Math.cos(a) * 13), (int) (y + Math.sin(a) * 13));
        }
        g.setColor(new Color(58, 59, 63));
        g.fill(hex);
        g.setColor(new Color(10, 10, 12));
        g.draw(hex);
        // socket
        g.setPaint(new RadialGradientPaint(x, y, 9f,
                new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(5, 5, 6), new Color(25, 25, 28), new Color(70, 71, 75)}));
        g.fill(new Ellipse2D.Float(x - 9, y - 9, 18, 18));
        // colored type ring so patchable jacks are visually matched
        g.setColor(p.getType().cableColor(0));
        g.draw(new Ellipse2D.Float(x - 9, y - 9, 18, 18));

        g.setFont(RackStyle.TINY_FONT);
        g.setColor(p.getDirection() == Port.Direction.IN ? RackStyle.SILKSCREEN : RackStyle.LCD_AMBER);
        int lw = g.getFontMetrics().stringWidth(p.getLabel());
        g.drawString(p.getLabel(), x - lw / 2, y + 26);
    }

    /**
     * The patch bay: one mini-LED per port along the bottom-right of the
     * faceplate. Dim = nothing cabled, steady = cabled, bright flash = a
     * signal just passed. The rack's wiring stays visible - and visibly
     * ALIVE - without ever flipping it around.
     */
    private void paintPatchBay(Graphics2D g, int w, int h) {
        if (ports.isEmpty() || rack == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int cell = 12;
        int x0 = w - RackStyle.EAR_WIDTH - 14 - ports.size() * cell;
        int y = h - 13;
        g.setFont(RackStyle.TINY_FONT);
        g.setColor(RackStyle.SILKSCREEN_DIM);
        String label = "PATCH";
        g.drawString(label, x0 - g.getFontMetrics().stringWidth(label) - 8, y + 8);
        for (int i = 0; i < ports.size(); i++) {
            Port p = ports.get(i);
            Color c = p.getType().cableColor(0);
            boolean cabled = !rack.cablesAt(p).isEmpty();
            Long flash = portActivity.get(p);
            boolean hot = flash != null && now - flash < 450;
            int alpha = hot ? 255 : cabled ? 165 : 38;
            int x = x0 + i * cell;
            if (hot) {
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 80));
                g.fillRoundRect(x - 2, y - 2, 12, 12, 6, 6);
            }
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            g.fillRoundRect(x, y, 8, 8, 3, 3);
            g.setColor(new Color(0, 0, 0, 120));
            g.drawRoundRect(x, y, 8, 8, 3, 3);
            // direction tick: inputs notch on the left, outputs on the right
            g.setColor(new Color(255, 255, 255, cabled || hot ? 150 : 50));
            if (p.getDirection() == Port.Direction.IN) {
                g.drawLine(x - 2, y + 4, x, y + 4);
            } else {
                g.drawLine(x + 8, y + 4, x + 10, y + 4);
            }
        }
    }

    /** Subclasses may paint extra front-panel decoration (group boxes etc). */
    protected void paintFront(Graphics2D g, int w, int h) {
    }

    /** Subclasses may paint extra back-panel decoration. */
    protected void paintBack(Graphics2D g, int w, int h) {
    }

    // ---- EDT helper ----

    protected static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    /**
     * Shared background executor for device work that must never run on the
     * EDT — chiefly directory-walking project detection triggered by
     * {@link #projectChanged} or {@link #onAttached}, which can fire on the EDT
     * during window-system restore and, on a $HOME aim, stack macOS TCC
     * permission prompts. A single daemon thread keeps ordering per device
     * predictable and cheap.
     */
    private static final org.openide.util.RequestProcessor DEVICE_BG =
            new org.openide.util.RequestProcessor("nmox-device-bg", 1, true);

    /** Runs {@code r} off the EDT on the shared device background thread. */
    protected static void offEdt(Runnable r) {
        DEVICE_BG.post(r);
    }
}
