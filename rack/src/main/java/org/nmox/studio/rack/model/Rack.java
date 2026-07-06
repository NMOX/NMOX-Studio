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

    // A neutral, non-scanning placeholder: the dedicated ~/NMOX workspace,
    // NOT created here and never enumerated on construction. The app-startup
    // path (RackService) creates it and aims here for real; pointing the
    // default at a normally-empty (and often not-yet-existent) directory means
    // a freshly constructed rack never walks $HOME or a TCC-protected folder.
    private volatile File projectDir = new File(System.getProperty("user.home"), "NMOX");
    private int cableColorCursor;

    /** One reversible edit: dropping a device, deleting a cable, reordering. */
    private interface Edit {
        void undo();

        void redo();

        String label();
    }

    private static final int UNDO_LIMIT = 100;
    private final java.util.Deque<Edit> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<Edit> redoStack = new java.util.ArrayDeque<>();
    /** Off during bulk load (RackIO, presets, resume); on for interactive edits. */
    private boolean captureUndo;

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
        int at = Math.max(0, Math.min(index, devices.size()));
        devices.add(at, d);
        d.attach(this);
        fireStructure();
        record(new Edit() {
            @Override public void undo() {
                removeDevice(d);
            }
            @Override public void redo() {
                addDevice(d, at);
            }
            @Override public String label() {
                return "Add " + d.getTitle();
            }
        });
    }

    public synchronized void removeDevice(RackDevice d) {
        int index = devices.indexOf(d);
        if (index >= 0) {
            devices.remove(index);
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
            record(new Edit() {
                @Override public void undo() {
                    // re-mount the device and re-patch every cable it carried
                    devices.add(Math.min(index, devices.size()), d);
                    d.attach(Rack.this);
                    cables.addAll(dead);
                    fireStructure();
                    if (!dead.isEmpty()) {
                        fireCables();
                    }
                }
                @Override public void redo() {
                    removeDevice(d);
                }
                @Override public String label() {
                    return "Remove " + d.getTitle();
                }
            });
        }
    }

    public synchronized void moveDevice(RackDevice d, int newIndex) {
        int old = devices.indexOf(d);
        if (old < 0) {
            return;
        }
        int at = Math.max(0, Math.min(newIndex, devices.size() - 1));
        if (at == old) {
            return;
        }
        devices.remove(old);
        devices.add(at, d);
        fireStructure();
        record(new Edit() {
            @Override public void undo() {
                moveDevice(d, old);
            }
            @Override public void redo() {
                moveDevice(d, at);
            }
            @Override public String label() {
                return "Move " + d.getTitle();
            }
        });
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
        record(new Edit() {
            @Override public void undo() {
                removeCable(cable);
            }
            @Override public void redo() {
                readdCable(cable);
            }
            @Override public String label() {
                return "Patch cable";
            }
        });
        return cable;
    }

    public synchronized void disconnect(Cable c) {
        if (cables.remove(c)) {
            lastTriggerAt.remove(c);
            fireCables();
            record(new Edit() {
                @Override public void undo() {
                    readdCable(c);
                }
                @Override public void redo() {
                    removeCable(c);
                }
                @Override public String label() {
                    return "Unpatch cable";
                }
            });
        }
    }

    /** Puts an existing cable object back verbatim (undo keeps its color). */
    private synchronized void readdCable(Cable c) {
        if (!cables.contains(c)) {
            cables.add(c);
            fireCables();
        }
    }

    private synchronized void removeCable(Cable c) {
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

    // ---- undo / redo ----

    /**
     * Turns interactive edit-recording on (once the initial patch has
     * loaded). Bulk operations - RackIO, presets, session resume - run
     * with it off so restoring a saved rack doesn't fill the undo stack.
     */
    public synchronized void enableUndoCapture() {
        captureUndo = true;
    }

    /** Forgets all history - called after a fresh patch loads. */
    public synchronized void clearUndoHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    private void record(Edit edit) {
        if (!captureUndo) {
            return;
        }
        undoStack.push(edit);
        redoStack.clear();
        while (undoStack.size() > UNDO_LIMIT) {
            undoStack.removeLast();
        }
    }

    public synchronized boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public synchronized boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /** The next undo's description, or null when there's nothing to undo. */
    public synchronized String undoLabel() {
        return undoStack.isEmpty() ? null : undoStack.peek().label();
    }

    public synchronized String redoLabel() {
        return redoStack.isEmpty() ? null : redoStack.peek().label();
    }

    /** Reverses the last edit; the inverse runs without recording itself. */
    public synchronized void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Edit edit = undoStack.pop();
        boolean was = captureUndo;
        captureUndo = false;
        try {
            edit.undo();
        } finally {
            captureUndo = was;
        }
        redoStack.push(edit);
    }

    public synchronized void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        Edit edit = redoStack.pop();
        boolean was = captureUndo;
        captureUndo = false;
        try {
            edit.redo();
        } finally {
            captureUndo = was;
        }
        undoStack.push(edit);
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
                RackDevice target = c.getTo().getDevice();
                if (target.isDisposed()) {
                    return; // removed while this signal sat in the router queue
                }
                try {
                    target.receive(c.getTo(), signal);
                } catch (RuntimeException ex) {
                    java.util.logging.Logger.getLogger(Rack.class.getName())
                            .warning("Device " + target.getTitle() + " failed on signal: " + ex);
                }
            });
        }
    }

    /**
     * Fans a coalesced manifest-edit batch out to every device on the
     * router thread — the same single-threaded path signals travel, so a
     * test's settle (EDT flush + {@link #awaitRouterIdle}) drains it and
     * device reactions stay ordered against signal deliveries.
     */
    public void manifestChanged(List<java.nio.file.Path> changed) {
        if (changed == null || changed.isEmpty()) {
            return;
        }
        List<java.nio.file.Path> batch = List.copyOf(changed);
        router.submit(() -> {
            for (RackDevice d : getDevices()) {
                try {
                    d.manifestChanged(batch);
                } catch (RuntimeException ex) {
                    java.util.logging.Logger.getLogger(Rack.class.getName())
                            .warning("Device " + d.getTitle() + " failed on manifest change: " + ex);
                }
            }
        });
    }

    /**
     * Block until the router thread has delivered every signal emitted before
     * this call. Delivery is asynchronous on a single background thread, so a
     * caller that needs to observe a receiver's state after an {@link #emit}
     * must synchronize on the router rather than race it. Test/diagnostic
     * support; not part of the normal signal flow.
     */
    public void awaitRouterIdle() {
        try {
            // the router is single-threaded and FIFO, so a barrier submitted
            // now runs only after every already-queued delivery has finished
            router.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
            throw new IllegalStateException("rack router did not drain", ex);
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
