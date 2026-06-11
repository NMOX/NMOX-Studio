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
    private int inPortCount;
    private int outPortCount;
    private volatile CommandExecutor.Handle running;

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
        this.rack = rack;
        onAttached();
    }

    /** Called once the device is in a rack; project dir is available now. */
    protected void onAttached() {
    }

    /** Called when the rack's project directory changes. */
    public void projectChanged(File dir) {
    }

    /** Kill any running process and release resources. */
    public void dispose() {
        stopProcess();
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
        stopProcess();
        running = CommandExecutor.run(title, projectDir(),
                rack != null ? rack.getEnvOverrides() : Map.of(),
                command, onLine, code -> {
                    running = null;
                    onExit.accept(code);
                });
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

    /** Emergency stop, callable from outside (the master section's STOP ALL). */
    public void panic() {
        stopProcess();
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
        } else {
            RackStyle.paintBackPanel(g, w, h);
            paintEars(g, h);
            g.setFont(RackStyle.LABEL_FONT);
            g.setColor(RackStyle.SILKSCREEN_DIM);
            g.drawString(title + "  —  REAR", RackStyle.EAR_WIDTH + 14, 18);
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
}
