package org.nmox.studio.rack.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.core.util.AtomicFiles;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.rack.devices.DeviceCatalog;

/**
 * Saves and restores a rack patch - the device stack, every control
 * position, and the full cable harness - as JSON ("song file" for the
 * rack). Default location is .nmoxrack.json in the project directory.
 */
public final class RackIO {

    public static final String DEFAULT_FILENAME = ".nmoxrack.json";

    /** The patch's version, which {@link #toJson} stamps and every reader checks. */
    public static final String VERSION = "version";
    /** The device stack, in mount order: a cable names its ends by INDEX into it. */
    public static final String DEVICES = "devices";
    /** The cable harness. */
    public static final String CABLES = "cables";
    /** A device slot's catalog type id. */
    public static final String TYPE = "type";
    /** A device slot's saved control positions, every value a string. */
    public static final String STATE = "state";
    /** A cable's OUT end: the device index and the port id. */
    public static final String FROM_DEVICE = "fromDevice";
    public static final String FROM_PORT = "fromPort";
    /** A cable's IN end. */
    public static final String TO_DEVICE = "toDevice";
    public static final String TO_PORT = "toPort";
    /**
     * Which device each cable end was patched to, beside its slot index
     * (3.4). A cable names its ends by POSITION, and positions move when a
     * teammate removes or reorders a device: git merges the two edits
     * cleanly and the old index now names a different device, so a cable
     * saved as {@code reflex.changed → lint.run} loaded as
     * {@code reflex.changed → test.run} — silently rewired. With the type
     * and title recorded, {@link #fromJson} notices the slot no longer holds
     * the device the cable was patched to and follows the device to its new
     * slot, or drops the cable and says so. Additive: a patch written before
     * 3.4 carries neither and loads by index exactly as it always did.
     */
    public static final String FROM_TYPE = "fromType";
    public static final String FROM_TITLE = "fromTitle";
    public static final String TO_TYPE = "toType";
    public static final String TO_TITLE = "toTitle";
    /**
     * A device slot's identity ({@link RackDevice#getUid}), and each cable
     * end's (3.4). Type and title cannot tell two PURITYs apart: Bob saved
     * {@code [cmd, reflex, lintA, lintB]} with {@code reflex → lintA}, Alice
     * removed {@code cmd}, git merged cleanly, and the old index 2 named
     * lintB — the same type, the same title, so the cable was trusted and
     * landed on the wrong device with nothing said. With the id a cable
     * follows the DEVICE. Additive: a build that does not read these keys
     * loads the patch by index exactly as before (org.json ignores a key
     * nobody asks for), and a patch without them loads by the rules below.
     */
    public static final String ID = "id";
    public static final String FROM_ID = "fromId";
    public static final String TO_ID = "toId";

    /**
     * The patch format this build writes (3.4). A patch whose {@link #VERSION}
     * is higher came from a newer NMOX Studio: it loads, but it is never
     * written over, because a save here would drop whatever that build added
     * and this one does not know.
     */
    public static final int FORMAT = 1;

    /** The format a patch document says it is in; a missing or odd value reads as {@link #FORMAT}. */
    public static int formatOf(JSONObject root) {
        return root.opt(VERSION) instanceof Number n ? n.intValue() : FORMAT;
    }

    /**
     * What the patch format holds, declared where it is WRITTEN. The
     * community-rack gate ({@code gallery.RackJudge}) permits exactly these
     * keys and keeps no copy of them: until v2.179.2 it hand-kept all three
     * sets, so a key added to the format here would have made the gate refuse
     * every rack that used it, with "unknown key" — the second-home defect
     * v2.179.1 removed one authority over. {@code RackJudgeFormatKeysTest}
     * holds each set equal to what {@link #toJson} really writes, so a key
     * added to the writer and not to its set fails the build rather than
     * quietly refusing a rack.
     */
    public static final Set<String> TOP_LEVEL_KEYS = Set.of(VERSION, DEVICES, CABLES);
    /** @see #TOP_LEVEL_KEYS */
    public static final Set<String> DEVICE_KEYS = Set.of(TYPE, STATE, ID);
    /** @see #TOP_LEVEL_KEYS */
    public static final Set<String> CABLE_KEYS = Set.of(FROM_DEVICE, FROM_PORT, TO_DEVICE, TO_PORT,
            FROM_TYPE, FROM_TITLE, TO_TYPE, TO_TITLE, FROM_ID, TO_ID);

    /**
     * Port ids a saved patch may still name, keyed {@code <typeId>.<oldId>}
     * → the id that port carries now. Cables persist by port ID, so a
     * rename without this table silently drops every cable into the
     * renamed jack on the next load (the id is what the file says; the
     * label is what the user saw, and the label never changed). Three ids
     * drifted from the rack's vocabulary — TEMPO's STOP was {@code halt},
     * INSPECTOR's and WORMHOLE's RUNNING gates were {@code live} — and were
     * renamed on 2026-09-17. Applied when a cable's ports are resolved in
     * {@link #fromJson}; the next save writes the current id.
     */
    static final Map<String, String> LEGACY_PORT_IDS = Map.of(
            "tempo.halt", "stop",
            "debug.live", "running",
            "tunnel.live", "running");

    /** The id a device's port carries today for the id a patch named. */
    static String currentPortId(RackDevice device, String savedId) {
        return LEGACY_PORT_IDS.getOrDefault(device.getTypeId() + "." + savedId, savedId);
    }

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(RackIO.class.getName());

    private RackIO() {
    }

    public static JSONObject toJson(Rack rack) {
        JSONObject root = new JSONObject();
        root.put(VERSION, FORMAT);

        List<RackDevice> devices = rack.getDevices();
        JSONArray deviceArr = new JSONArray();
        for (RackDevice d : devices) {
            JSONObject dj = new JSONObject();
            dj.put(TYPE, d.getTypeId());
            dj.put(ID, d.getUid());
            dj.put(STATE, new JSONObject(d.getState()));
            deviceArr.put(dj);
        }
        root.put(DEVICES, deviceArr);

        JSONArray cableArr = new JSONArray();
        for (Cable c : rack.getCables()) {
            JSONObject cj = new JSONObject();
            RackDevice from = c.getFrom().getDevice();
            RackDevice to = c.getTo().getDevice();
            cj.put(FROM_DEVICE, devices.indexOf(from));
            cj.put(FROM_PORT, c.getFrom().getId());
            cj.put(FROM_TYPE, from.getTypeId());
            cj.put(FROM_TITLE, from.getTitle());
            cj.put(FROM_ID, from.getUid());
            cj.put(TO_DEVICE, devices.indexOf(to));
            cj.put(TO_PORT, c.getTo().getId());
            cj.put(TO_TYPE, to.getTypeId());
            cj.put(TO_TITLE, to.getTitle());
            cj.put(TO_ID, to.getUid());
            cableArr.put(cj);
        }
        root.put(CABLES, cableArr);
        return root;
    }

    /**
     * What a load did to the cable harness that a reader should hear about
     * (3.4): cables that followed their device to a new slot, and cables
     * that could not be connected at all. Both used to be log lines only.
     */
    public record CableReport(int followed, int dropped, int format) {

        /** A report about a patch in this build's own format. */
        public CableReport(int followed, int dropped) {
            this(followed, dropped, FORMAT);
        }

        /**
         * The patch came from a newer NMOX Studio (3.4): it loaded, and it
         * must not be written over — a save here would drop what that
         * build added. Not part of {@link #quiet()}: the cables are fine.
         */
        public boolean newerFormat() {
            return format > FORMAT;
        }

        /** Nothing to say: every cable landed where the file put it. */
        public boolean quiet() {
            return followed == 0 && dropped == 0;
        }
    }

    /**
     * Resolves one cable end to a device.
     *
     * <ol>
     *   <li>An end that names its device's ID (3.4, written by every save
     *       since) resolves to that device wherever it now sits, and to
     *       nothing when no device carries the id — the teammate removed it.
     *       The index is not consulted at all: it is the thing a merge
     *       moves.</li>
     *   <li>An end with a type and title but no id (the first 3.4 format)
     *       trusts its index only while EXACTLY ONE device carries that type
     *       and title. With two, a matching slot proves nothing — Bob's lint A
     *       and lint B are both "lint / PURITY", and the old index lands on
     *       whichever now sits there — so the cable is dropped and counted
     *       rather than guessed onto one of them. With one, the end follows
     *       that device.</li>
     *   <li>An end with neither (a patch written before 3.4) is trusted by
     *       index, exactly as it always was: it carries nothing to check
     *       against, and dropping every such cable would punish every patch
     *       ever saved.</li>
     * </ol>
     */
    static RackDevice resolveEnd(List<RackDevice> devices, int index, String id, String type, String title,
            boolean[] followed) {
        RackDevice atIndex = index >= 0 && index < devices.size() ? devices.get(index) : null;
        if (id != null) {
            for (RackDevice d : devices) {
                if (id.equals(d.getUid())) {
                    if (d != atIndex) {
                        followed[0] = true;
                    }
                    return d;
                }
            }
            return null; // the device it was patched to is gone
        }
        if (type == null) {
            return atIndex; // pre-3.4 patch: position is all it knows
        }
        RackDevice only = null;
        for (RackDevice d : devices) {
            if (sameDevice(d, type, title)) {
                if (only != null) {
                    return null; // two candidates: a matching slot proves nothing, refusing beats guessing
                }
                only = d;
            }
        }
        if (only != null && only != atIndex) {
            followed[0] = true;
        }
        return only;
    }

    /** The longest saved device id taken as one; a UUID is 36. */
    static final int MAX_ID_CHARS = 64;

    private static boolean sameDevice(RackDevice d, String type, String title) {
        return type.equals(d.getTypeId()) && (title == null || title.equals(d.getTitle()));
    }

    private static String optText(JSONObject o, String key) {
        Object v = o.opt(key);
        return v instanceof String s && !s.isEmpty() ? s : null;
    }

    /** Replaces the rack's contents with the patch in the JSON document. */
    public static CableReport fromJson(Rack rack, JSONObject root) {
        for (RackDevice d : rack.getDevices()) {
            rack.removeDevice(d);
        }
        int format = formatOf(root);
        JSONArray deviceArr = root.optJSONArray(DEVICES);
        if (deviceArr == null) {
            rack.clearUndoHistory();
            return new CableReport(0, 0, format);
        }
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (int i = 0; i < deviceArr.length(); i++) {
            JSONObject dj = deviceArr.getJSONObject(i);
            String typeId = dj.getString(TYPE);
            // an unknown type id (a plugin device not installed here) keeps
            // its slot as a MissingDevice: cables are index-based, so
            // dropping it would silently re-route every cable saved after
            // it, and its state must survive the next save untouched
            RackDevice device = DeviceCatalog.byId(typeId)
                    .map(DeviceCatalog.Entry::create)
                    .orElseGet(() -> new MissingDevice(typeId));
            // the saved identity, healed at parse (a keep-both merge can
            // duplicate a device entry, id and all): the first holder keeps
            // it — cables naming it reach that one — and a repeat or a
            // missing id gets a fresh one. Never trusted as anything but a
            // string of bounded length: it is a stranger's text in a shared rack.
            String uid = optText(dj, ID);
            if (uid != null && uid.length() <= MAX_ID_CHARS && seenIds.add(uid)) {
                device.setUid(uid);
            } else {
                seenIds.add(device.getUid());
            }
            rack.addDevice(device);
            JSONObject state = dj.optJSONObject(STATE);
            if (state != null) {
                Map<String, String> map = new LinkedHashMap<>();
                for (String key : state.keySet()) {
                    map.put(key, state.getString(key));
                }
                device.applyState(map);
            }
        }
        List<RackDevice> devices = rack.getDevices();
        JSONArray cableArr = root.optJSONArray(CABLES);
        int followed = 0;
        int dropped = 0;
        if (cableArr != null) {
            for (int i = 0; i < cableArr.length(); i++) {
                // By the time cables load, the rack has been CLEARED and its
                // devices mounted: an exception here leaves the user with half a
                // rack and no way back (loading is an undo boundary). A cable
                // entry that is not an object, or names no device or jack, is one
                // lost cable said by name — never org.json's exception (found by
                // RackCompatTest, 2026-09-18: v2.178.0 hardened the device slots
                // of a stranger's file and not the cable slots).
                JSONObject cj = cableArr.optJSONObject(i);
                if (cj == null) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "rack patch cable #{0} dropped: not a cable entry", i + 1);
                    dropped++;
                    continue;
                }
                int fi = cj.optInt(FROM_DEVICE, -1), ti = cj.optInt(TO_DEVICE, -1);
                boolean[] moved = new boolean[1];
                RackDevice fd = resolveEnd(devices, fi, optText(cj, FROM_ID), optText(cj, FROM_TYPE),
                        optText(cj, FROM_TITLE), moved);
                RackDevice td = resolveEnd(devices, ti, optText(cj, TO_ID), optText(cj, TO_TYPE),
                        optText(cj, TO_TITLE), moved);
                if (fd == null || td == null) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "rack patch cable #{0} dropped: the device it was patched to is not in this"
                            + " patch, or not in a slot it can be told apart in", i + 1);
                    dropped++;
                    continue;
                }
                // a renamed jack keeps its cables: the saved id maps to today's
                String fromId = currentPortId(fd, cj.optString(FROM_PORT, ""));
                String toId = currentPortId(td, cj.optString(TO_PORT, ""));
                Port from = fd.getPort(fromId);
                Port to = td.getPort(toId);
                // a missing device adopts the ports its saved cables name,
                // typed like the live peer so canConnectTo accepts the patch
                if (from == null && fd instanceof MissingDevice m) {
                    from = m.adoptPort(fromId, Port.Direction.OUT,
                            to != null ? to.getType() : SignalType.DATA);
                }
                if (to == null && td instanceof MissingDevice m) {
                    to = m.adoptPort(toId, Port.Direction.IN,
                            from != null ? from.getType() : SignalType.DATA);
                }
                // a port a device no longer has (STELLAR's ENABLE, removed
                // 2026-09-17) loses its cable and nothing else: the patch
                // still loads, every other cable intact — and the loss is
                // SAID, by name (refusals speak; the 2026-09-17 arc review
                // found this branch silent, and Rack.connect's null for a
                // duplicate or illegal pair silent beside it)
                String cable = fd.getTypeId() + "." + fromId + " -> " + td.getTypeId() + "." + toId;
                if (from == null || to == null) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "rack patch cable {0} dropped: {1} has no such port",
                            new Object[]{cable, from == null ? fd.getTypeId() + "." + fromId : td.getTypeId() + "." + toId});
                    dropped++;
                } else if (rack.connect(from, to) == null) {
                    LOG.log(java.util.logging.Level.WARNING,
                            "rack patch cable {0} not connected: duplicate, incompatible or a loop", cable);
                    dropped++;
                } else if (moved[0]) {
                    LOG.log(java.util.logging.Level.INFO,
                            "rack patch cable {0} followed its device to a new slot", cable);
                    followed++;
                }
            }
        }
        // A patch/preset load REPLACES the rack's contents, so the device
        // removals and additions above must not be reachable by ⌘Z: undoing
        // past a load would peel the just-loaded patch apart device by device
        // and eventually resurrect the PREVIOUS patch's structure (a real
        // correctness bug — the undo edits predate the current patch). This is
        // THE single choke point every load routes through — the Presets menu,
        // the Load Patch button, and RackService's project-switch autoload —
        // so clearing here covers them all. RackService also clears after a
        // project switch with no patch file, a case that never reaches here.
        rack.clearUndoHistory();
        return new CableReport(followed, dropped, format);
    }

    public static void save(Rack rack, File file) throws IOException {
        // atomic swap: mtime pollers and external readers of .nmoxrack.json
        // must never observe a truncated patch
        AtomicFiles.writeString(file.toPath(), toJson(rack).toString(2));
    }

    /**
     * Replaces the rack's contents with the patch in {@code file}.
     *
     * <p>load()'s contract is "replace the rack's contents" (the v1.107.0
     * corrupt-patch rule), so on EVERY failure the rack is emptied before the
     * refusal is thrown: the previous project's devices must never stay
     * mounted under this project's name (the v1.278.0 class — Save Patch
     * would write project A's pipeline into project B's file). Until 3.4 only
     * a parse failure emptied it; a permission error, a directory wearing the
     * patch's name or undecodable bytes threw from the read and left the old
     * devices mounted.
     *
     * <p>What happens to the FILE depends on what it is, and nothing here
     * ever moves or rewrites it:
     * <ul>
     *   <li>unread (too large, unreadable, not a file) — left alone:
     *       {@link PatchTooLargeException}, {@link PatchUnreadableException};</li>
     *   <li>git's unresolved merge conflict — left alone, it holds both
     *       people's work: {@link PatchConflictedException};</li>
     *   <li>not a patch — its bytes are COPIED to a rescue sibling
     *       ({@link org.nmox.studio.core.util.Backups#keep}) and the original
     *       stays where it is, so {@code git commit -am} never records a
     *       deletion: {@link CorruptPatchException}, whose
     *       {@link CorruptPatchException#backupName()} is null when no copy
     *       could be written.</li>
     * </ul>
     * Whether the caller may later WRITE the file is its decision
     * ({@link #mayOverwrite}); the rule is that nothing this build could not
     * read, and nothing git has not finished merging, is written over.
     */
    public static CableReport load(Rack rack, File file) throws IOException {
        JSONObject root;
        try {
            root = readDocument(file);
        } catch (IOException | RuntimeException refused) {
            emptyRack(rack);
            throw refused;
        }
        try {
            return fromJson(rack, root);
        } catch (RuntimeException notAPatch) {
            // valid JSON that is not a patch (a device slot with no type, a
            // cables value of the wrong kind): the same answer as bytes that
            // are not JSON — keep a copy, leave the file, empty the rack
            String kept = keepCopy(file);
            emptyRack(rack);
            throw new CorruptPatchException("Rack patch " + file.getName() + " is not a patch"
                    + (kept == null ? " (no copy could be kept)" : " (kept as " + kept + ")")
                    + ": " + notAPatch.getMessage(), kept, notAPatch);
        }
    }

    private static void emptyRack(Rack rack) {
        for (RackDevice d : rack.getDevices()) {
            rack.removeDevice(d);
        }
        rack.clearUndoHistory();
    }

    /**
     * Reads and parses a patch file WITHOUT touching the rack — safe to call
     * off the EDT (the caller applies the returned document with
     * {@link #fromJson} on the EDT, where the device components are mutated).
     * The refusals are {@link #load}'s, and so is the promise: the file is
     * never moved or rewritten here.
     */
    public static JSONObject readDocument(File file) throws IOException {
        if (file.exists() && !file.isFile()) {
            throw new PatchUnreadableException("Rack patch " + file.getName()
                    + " is not a file", null);
        }
        String text;
        try {
            text = readCapped(file);
        } catch (PatchTooLargeException tooLarge) {
            throw tooLarge;
        } catch (IOException unreadable) {
            throw new PatchUnreadableException("Rack patch " + file.getName()
                    + " could not be read: " + unreadable.getMessage(), unreadable);
        }
        if (org.nmox.studio.core.util.MergeConflicts.hasMarkers(text)) {
            throw new PatchConflictedException("Rack patch " + file.getName()
                    + " holds an unresolved git merge conflict");
        }
        try {
            return new JSONObject(text);
        } catch (JSONException corrupt) {
            String kept = keepCopy(file);
            throw new CorruptPatchException("Corrupt rack patch " + file.getName()
                    + (kept == null ? " (no copy could be kept)" : " (kept as " + kept + ")")
                    + ": " + corrupt.getMessage(), kept, corrupt);
        }
    }

    /**
     * Whether a refused load leaves the file safe to write over: only when
     * the refusal was a parse failure and its bytes were copied aside. A file
     * this build could not read, a conflict git is waiting on, or a broken
     * file no copy of exists is the user's only copy of something, and a
     * save would destroy it.
     */
    public static boolean mayOverwrite(Throwable refusal) {
        return refusal instanceof CorruptPatchException corrupt && corrupt.backupName() != null;
    }

    /**
     * A patch file this build could not read at all: a permission error, a
     * directory wearing the patch's name, bytes that do not decode. Left
     * exactly where it is.
     */
    public static final class PatchUnreadableException extends IOException {
        private static final long serialVersionUID = 1L;

        PatchUnreadableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * A patch holding git's unresolved merge conflict (3.4). Both people's
     * racks are in it; the file is left exactly as it is, and nothing is
     * written over it until git's markers are gone.
     */
    public static final class PatchConflictedException extends IOException {
        private static final long serialVersionUID = 1L;

        PatchConflictedException(String message) {
            super(message);
        }
    }

    /**
     * The most a patch file may be before it is refused unread: a saved rack
     * is a few kilobytes, and since v2.176.0 Import… reads a file from another
     * machine — the bounded-read law (every outside read capped) reached the
     * one text read in this class on the 2026-09-17 arc review.
     */
    public static final long MAX_PATCH_BYTES = 8L * 1024 * 1024;

    /**
     * A patch over {@link #MAX_PATCH_BYTES}: refused before a byte is read, and
     * never moved aside. It carries the SIZE as a number, not only inside its
     * message, so a consumer can say what happened in the reader's own language
     * (ledger 106: an argument is data — v2.100.0).
     */
    public static final class PatchTooLargeException extends IOException {
        private final long size;

        PatchTooLargeException(String message, long size) {
            super(message);
            this.size = size;
        }

        /** The file's size in KiB — what a reader needs to know, in a unit they read. */
        public long kib() {
            return size / 1024;
        }

        /** The ceiling in MiB, so a sentence can name it without knowing this class's constant. */
        public static long capMib() {
            return MAX_PATCH_BYTES / 1024 / 1024;
        }
    }

    /**
     * A patch whose bytes are not JSON. The parser's own complaint stays in
     * {@code getMessage()} for the log; the consumer's sentence needs only the
     * name of the backup, because a parser's English is not a translation the
     * product can offer (ledger 106).
     */
    public static final class CorruptPatchException extends IOException {
        private final String backupName;

        CorruptPatchException(String message, String backupName, Throwable cause) {
            super(message, cause);
            this.backupName = backupName;
        }

        /** The rescue the user's bytes were copied to, or null when no copy could be written (3.4). */
        public String backupName() {
            return backupName;
        }
    }

    /**
     * The patch text, or a refusal naming the size — the file is untouched
     * either way. The measuring is {@link BoundedReads}' since v2.180.0 (this
     * was its first home and its fifth consumer promoted it); the wording and
     * the exception TYPE stay this class's, because {@link #load} branches on
     * that type to decide whether to empty the rack.
     */
    private static String readCapped(File file) throws IOException {
        try {
            return BoundedReads.read(file.toPath(), MAX_PATCH_BYTES);
        } catch (BoundedReads.TooLarge tooLarge) {
            throw new PatchTooLargeException(BoundedReads.refusal("Rack patch",
                    tooLarge.fileName(), tooLarge.size(), tooLarge.maxBytes()), tooLarge.size());
        }
    }

    /**
     * Copies a broken patch's bytes to a rescue sibling and returns the
     * rescue's name, or null when no copy could be written.
     *
     * <p>Until 3.4 this MOVED the file to {@code <name>.bak} with
     * {@code REPLACE_EXISTING}: the patch vanished from the project, so
     * {@code git commit -am} recorded its deletion, and a second rescue
     * overwrote the first. A copy leaves the file where git expects it, and
     * {@link org.nmox.studio.core.util.Backups#keep} never overwrites an
     * earlier rescue (and reuses one already holding these bytes, since a
     * broken patch is re-read on every aim).
     */
    private static String keepCopy(File file) {
        try {
            byte[] bytes;
            try (java.io.InputStream in = Files.newInputStream(file.toPath())) {
                // bounded: readCapped already refused anything over the cap
                bytes = in.readNBytes((int) MAX_PATCH_BYTES);
            }
            return org.nmox.studio.core.util.Backups.keep(file.toPath(), bytes)
                    .getFileName().toString();
        } catch (IOException | RuntimeException noCopy) {
            LOG.log(java.util.logging.Level.WARNING, "no copy of {0} could be kept: {1}",
                    new Object[]{file.getName(), noCopy.getMessage()});
            return null;
        }
    }
}
