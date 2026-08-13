package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.spi.device.DeviceExtension;

/**
 * The rack's drop-in sibling: every {@code *.json} in
 * {@code ~/.nmox/devices.d/} becomes a device on the shelf.
 *
 * <p>The seventh and last of the named drop-in seams (learn-catalog.d,
 * templates.d, presets.d, api-library.d, dockerize.d, doctor.d — and now
 * devices.d), and the one that changes what the product IS: until
 * v2.0.0 the fleet was fixed unless you wrote a NetBeans module.
 *
 * <p>Family laws, all deliberate:
 * <ul>
 *   <li><b>Lazy.</b> Nothing is read at boot; the first shelf or patch
 *       load pays, behind a name+mtime+size signature cache.</li>
 *   <li><b>Skip with a note.</b> One malformed file never blocks the
 *       others or the built-ins — it is logged by name with its reason
 *       and left out.</li>
 *   <li><b>Never the product's own trust.</b> A user device runs
 *       nothing that a built-in would not: the host gates every spawn on
 *       workspace trust, and {@link DeviceFile} refuses shell lines,
 *       tool paths, and unknown variables before that.</li>
 * </ul>
 */
public final class UserDevices {

    private static final Logger LOG = Logger.getLogger(UserDevices.class.getName());

    /**
     * Re-stat no more often than this. {@code DeviceCatalog.all()} is
     * called per shelf paint and per patch-file id lookup, so an
     * unconditional directory stat there would be a syscall in a loop;
     * a second of staleness on a hand-edited file is invisible.
     */
    private static final long RESTAT_MS = 1000;

    /** Swappable so tests never depend on the developer's real home. */
    static File dirOverride;

    private static String signature;
    private static List<DeviceExtension> cached = List.of();
    private static long lastStat;

    private UserDevices() {
    }

    /** Where device files live: {@code ~/.nmox/devices.d}. */
    public static File dropInDir() {
        return dirOverride != null ? dirOverride
                : new File(System.getProperty("user.home"), ".nmox/devices.d");
    }

    /** Forgets the cache — for tests, and for a deliberate reload. */
    public static synchronized void invalidate() {
        signature = null;
        cached = List.of();
        lastStat = 0;
    }

    /**
     * Every well-formed user device, in filename order. Malformed files
     * are skipped with a logged note naming the file and the reason.
     */
    public static synchronized List<DeviceExtension> all() {
        long now = System.currentTimeMillis();
        if (signature != null && now - lastStat < RESTAT_MS) {
            return cached;
        }
        File dir = dropInDir();
        File[] files = dir.listFiles(f -> f.isFile()
                && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json"));
        if (files == null) {
            files = new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        StringBuilder sig = new StringBuilder();
        for (File f : files) {
            sig.append(f.getName()).append(':').append(f.lastModified())
                    .append(':').append(f.length()).append('\n');
        }
        String nowSig = sig.toString();
        lastStat = now;
        if (nowSig.equals(signature)) {
            return cached;
        }

        List<DeviceExtension> found = new ArrayList<>();
        for (File f : files) {
            String json;
            try {
                json = Files.readString(f.toPath());
            } catch (IOException | RuntimeException ex) {
                LOG.log(Level.WARNING, "device file {0} skipped: unreadable ({1})",
                        new Object[]{f.getName(), ex.toString()});
                continue;
            }
            DeviceFile.Result result = DeviceFile.read(json);
            if (!result.ok()) {
                LOG.log(Level.WARNING, "device file {0} skipped: {1}",
                        new Object[]{f.getName(), result.problem()});
                continue;
            }
            DeviceFile fitted = fitted(f.getName(), result.device());
            if (fitted == null) {
                continue; // fitted() logged the reason
            }
            found.add(new JsonDeviceExtension(fitted));
        }
        signature = nowSig;
        cached = List.copyOf(found);
        return cached;
    }

    /**
     * The fit law, enforced at LOAD: builds the face for real (a
     * throwaway device, never attached) so a file whose controls
     * overflow the plate is skipped HERE with its reason — not thrown
     * as an exception at the user's first click, which is where the
     * v2.0.0 walk found it landing. When the file does not declare
     * "units", the smallest height that fits wins: an author should
     * not need to know the widgets' pixel metrics, and the tutorial's
     * own knob-carrying example could not mount at the silent 1U
     * default. A DECLARED height that is too small still refuses —
     * the file said something the plate cannot honor.
     */
    private static DeviceFile fitted(String name, DeviceFile device) {
        String problem = fitProblem(device);
        if (problem == null) {
            return device;
        }
        if (!device.unitsDeclared()) {
            for (int u = device.units() + 1; u <= DeviceFile.MAX_UNITS; u++) {
                DeviceFile taller = device.withUnits(u);
                if (fitProblem(taller) == null) {
                    return taller;
                }
            }
        }
        LOG.log(Level.WARNING, "device file {0} skipped: {1}",
                new Object[]{name, problem});
        return null;
    }

    /**
     * The load path's fit judgement, shared by every source of JSON
     * devices (drop-ins, the bundled gallery, tests): what the shelf
     * would make of this parsed device — the fitted (possibly
     * auto-sized) device, or null when it can never mount.
     */
    static DeviceFile fit(DeviceFile device) {
        return fitted("(in-memory)", device);
    }

    /** Builds the real face once, discarded; the thrown message is the verdict. */
    private static String fitProblem(DeviceFile device) {
        ExtensionDevice probe = null;
        try {
            probe = new ExtensionDevice(new JsonDeviceExtension(device));
            return null;
        } catch (RuntimeException ex) {
            return ex.getMessage();
        } finally {
            // cleanup stays OUT of the judgment: a dispose failure must
            // not masquerade as a fit refusal for a good device
            if (probe != null) {
                try {
                    probe.dispose();
                } catch (RuntimeException ignore) {
                }
            }
        }
    }
}
