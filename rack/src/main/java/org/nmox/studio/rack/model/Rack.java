package org.nmox.studio.rack.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The rack: an ordered stack of devices plus the patch cables between
 * them. Also carries shared context every device needs - the project
 * directory the tasks operate on and environment overrides contributed
 * by devices like the Env Mixer.
 */
public final class Rack {

    /** Things the UI listens for. */
    public interface Listener {
        /** Devices added/removed/reordered. */
        default void structureChanged() { }
        /** Cables patched or unpatched. */
        default void cablesChanged() { }
        /** A signal travelled down a cable (for cable-flash animation). */
        default void signalTravelled(Cable cable) { }
        /** Project directory changed. */
        default void projectChanged() { }
    }

    private final List<RackDevice> devices = new ArrayList<>();
    private final List<Cable> cables = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, String> envOverrides = new ConcurrentHashMap<>();
    private final ExecutorService router = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nmox-rack-router");
        t.setDaemon(true);
        return t;
    });
    /** Cooldown per cable so trigger feedback loops can't run hot. */
    private final Map<Cable, Long> lastTriggerAt = new ConcurrentHashMap<>();
    private static final long TRIGGER_COOLDOWN_MS = 150;

    private volatile File projectDir = new File(System.getProperty("user.home"));
    private int cableColorCursor;

    /** Dev servers and watchers must not outlive the IDE as orphans. */
    private final Thread processReaper = new Thread(() -> {
        for (RackDevice d : getDevices()) {
            try {
                d.panic();
            } catch (RuntimeException ignored) {
                // best effort during JVM shutdown
            }
        }
    }, "nmox-rack-reaper");

    public Rack() {
        try {
            Runtime.getRuntime().addShutdownHook(processReaper);
        } catch (IllegalStateException ignored) {
            // already shutting down
        }
    }

    // ---- devices ----

    public synchronized List<RackDevice> getDevices() {
        return Collections.unmodifiableList(new ArrayList<>(devices));
    }

    public synchronized void addDevice(RackDevice d) {
        addDevice(d, devices.size());
    }

    public synchronized void addDevice(RackDevice d, int index) {
        devices.add(Math.max(0, Math.min(index, devices.size())), d);
        d.attach(this);
        fireStructure();
    }

    public synchronized void removeDevice(RackDevice d) {
        if (devices.remove(d)) {
            List<Cable> dead = new ArrayList<>();
            for (Cable c : cables) {
                if (c.touches(d)) {
                    dead.add(c);
                }
            }
            cables.removeAll(dead);
            d.dispose();
            fireStructure();
            if (!dead.isEmpty()) {
                fireCables();
            }
        }
    }

    public synchronized void moveDevice(RackDevice d, int newIndex) {
        int old = devices.indexOf(d);
        if (old < 0) {
            return;
        }
        devices.remove(old);
        devices.add(Math.max(0, Math.min(newIndex, devices.size())), d);
        fireStructure();
    }

    public synchronized int indexOf(RackDevice d) {
        return devices.indexOf(d);
    }

    // ---- cables ----

    public synchronized List<Cable> getCables() {
        return Collections.unmodifiableList(new ArrayList<>(cables));
    }

    private boolean pathExists(RackDevice start, RackDevice target) {
        if (start == target) {
            return true;
        }
        java.util.Set<RackDevice> visited = new java.util.HashSet<>();
        java.util.Queue<RackDevice> queue = new java.util.LinkedList<>();
        queue.add(start);
        visited.add(start);
        
        while (!queue.isEmpty()) {
            RackDevice curr = queue.poll();
            for (Cable c : cables) {
                if (c.getFrom().getDevice() == curr) {
                    RackDevice next = c.getTo().getDevice();
                    if (next == target) {
                        return true;
                    }
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
        return false;
    }

    /**
     * Patches a cable between two ports (either order). Returns the new
     * cable, or null if the connection is invalid, already exists, or creates a feedback loop.
     */
    public synchronized Cable connect(Port a, Port b) {
        if (a == null || !a.canConnectTo(b)) {
            return null;
        }
        Port out = a.getDirection() == Port.Direction.OUT ? a : b;
        Port in = out == a ? b : a;
        for (Cable c : cables) {
            if (c.getFrom() == out && c.getTo() == in) {
                return null;
            }
        }
        // Prevent infinite feedback loop cycles
        if (pathExists(in.getDevice(), out.getDevice())) {
            return null;
        }
        Cable cable = new Cable(out, in, out.getType().cableColor(cableColorCursor++));
        cables.add(cable);
        fireCables();
        return cable;
    }

    public synchronized void disconnect(Cable c) {
        if (cables.remove(c)) {
            lastTriggerAt.remove(c);
            fireCables();
        }
    }

    public synchronized void disconnectAll(Port p) {
        boolean changed = cables.removeIf(c -> c.touches(p));
        if (changed) {
            fireCables();
        }
    }

    public synchronized List<Cable> cablesAt(Port p) {
        List<Cable> result = new ArrayList<>();
        for (Cable c : cables) {
            if (c.touches(p)) {
                result.add(c);
            }
        }
        return result;
    }

    // ---- signal routing ----

    /**
     * Sends a signal out of an output port, fanning out to every input
     * patched to it. Delivery is asynchronous on the router thread so a
     * device can emit from any thread (process pumps, the EDT, timers).
     */
    public void emit(Port out, Signal signal) {
        if (out == null || out.getDirection() != Port.Direction.OUT) {
            return;
        }
        List<Cable> targets;
        synchronized (this) {
            targets = new ArrayList<>();
            for (Cable c : cables) {
                if (c.getFrom() == out) {
                    targets.add(c);
                }
            }
        }
        for (Cable c : targets) {
            if (signal.type() == SignalType.TRIGGER) {
                long now = System.currentTimeMillis();
                Long last = lastTriggerAt.get(c);
                if (last != null && now - last < TRIGGER_COOLDOWN_MS) {
                    continue;
                }
                lastTriggerAt.put(c, now);
            }
            router.submit(() -> {
                for (Listener l : listeners) {
                    l.signalTravelled(c);
                }
                try {
                    c.getTo().getDevice().receive(c.getTo(), signal);
                } catch (RuntimeException ex) {
                    java.util.logging.Logger.getLogger(Rack.class.getName())
                            .warning("Device " + c.getTo().getDevice().getTitle() + " failed on signal: " + ex);
                }
            });
        }
    }

    // ---- shared context ----

    public File getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(File dir) {
        if (dir != null && !dir.equals(projectDir)) {
            projectDir = dir;
            for (Listener l : listeners) {
                l.projectChanged();
            }
            for (RackDevice d : getDevices()) {
                d.projectChanged(dir);
            }
        }
    }

    /**
     * The toolchain every AUTO knob should assume, set by the ROSETTA
     * selector for mixed-language projects. Null = follow detection.
     */
    private volatile String toolchainOverride;

    public String getToolchainOverride() {
        return toolchainOverride;
    }

    public void setToolchainOverride(String kindName) {
        String normalized = kindName == null || kindName.isBlank()
                || "auto".equalsIgnoreCase(kindName) ? null : kindName;
        if (!java.util.Objects.equals(toolchainOverride, normalized)) {
            toolchainOverride = normalized;
            // devices re-resolve their AUTO labels exactly like a project switch
            for (RackDevice d : getDevices()) {
                d.projectChanged(projectDir);
            }
        }
    }

    /** Environment applied to every command the rack runs. */
    public Map<String, String> getEnvOverrides() {
        return new LinkedHashMap<>(envOverrides);
    }

    public void putEnv(String key, String value) {
        if (value == null || value.isEmpty()) {
            envOverrides.remove(key);
        } else {
            envOverrides.put(key, value);
        }
    }

    // ---- listeners ----

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void fireStructure() {
        for (Listener l : listeners) {
            l.structureChanged();
        }
    }

    private void fireCables() {
        for (Listener l : listeners) {
            l.cablesChanged();
        }
    }

    /** Stops all devices (kills running processes). */
    public void shutdown() {
        for (RackDevice d : getDevices()) {
            d.dispose();
        }
        router.shutdownNow();
        try {
            Runtime.getRuntime().removeShutdownHook(processReaper);
        } catch (IllegalStateException ignored) {
            // already shutting down
        }
    }
}
