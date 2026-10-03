package org.nmox.studio.core.util;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.util.BaseUtilities;

/**
 * Which {@code .vscode/settings.json} speaks for a file or a folder, and
 * what it excludes. The one home of the lookup rule every reader of that
 * file shares (the editor's indentation and rulers, the trees'
 * {@code files.exclude}, the search's {@code search.exclude}):
 *
 * <ul>
 * <li>the NEAREST {@code .vscode/settings.json} at or above the place
 *     asked about, as opening that folder in VS Code would use;</li>
 * <li>never above the repository's root (the folder holding
 *     {@code .git}): walking on towards the filesystem root would apply a
 *     {@code /tmp/.vscode} anyone on the machine can write (the 3.1.0
 *     review). A place inside no repository has no settings;</li>
 * <li>never the home folder's, which are a person's own and not a
 *     project's.</li>
 * </ul>
 *
 * <p>Everything here touches the disk (a stat per level, a bounded read
 * when the file's time or size moved) and so runs off the EDT; what a
 * paint needs is the {@link VsCodeExcludes} value this hands back, or
 * {@link VsCodeHidden}, which keeps one current for a tree.
 */
public final class VsCodeSettingsFile {

    private static final Logger LOG = Logger.getLogger(VsCodeSettingsFile.class.getName());

    /** A settings file larger than this is not a settings file. */
    public static final long MAX_BYTES = 1024L * 1024;

    /** Folders walked upwards. */
    public static final int MAX_DEPTH = 16;

    /** Parsed files kept; past it the map starts over rather than grow. */
    static final int CACHE_CAP = 256;

    private record Parsed(long modified, long length, VsCodeExcludes excludes) {
    }

    private static final Map<String, Parsed> CACHE = new ConcurrentHashMap<>();

    private VsCodeSettingsFile() {
    }

    /** The settings file that speaks for {@code file}, or null. */
    public static File nearest(File file) {
        return file == null ? null : walk(file.getParentFile());
    }

    /** The settings file that speaks for what {@code folder} holds, or null. */
    public static File nearestForFolder(File folder) {
        return walk(folder);
    }

    private static File walk(File from) {
        String home = System.getProperty("user.home");
        File found = null;
        File dir = from;
        for (int depth = 0; dir != null && depth < MAX_DEPTH; depth++, dir = dir.getParentFile()) {
            if (home != null && dir.getAbsolutePath().equals(new File(home).getAbsolutePath())) {
                return null; // reached home with no repository around the place
            }
            File candidate = new File(new File(dir, ".vscode"), "settings.json");
            if (found == null && candidate.isFile()) {
                found = candidate;
            }
            if (new File(dir, ".git").exists()) {
                return found; // the repository's root: settings above it are somebody else's
            }
        }
        return null;
    }

    /**
     * What the project around {@code folder} excludes, asked about by
     * paths relative to {@code folder}; {@link VsCodeExcludes#NONE} when
     * no settings file speaks for it. Reads the disk.
     */
    public static VsCodeExcludes excludesFor(File folder) {
        if (folder == null) {
            return VsCodeExcludes.NONE;
        }
        File settings = nearestForFolder(folder);
        if (settings == null) {
            return VsCodeExcludes.NONE;
        }
        VsCodeExcludes excludes = parse(settings);
        if (excludes.isEmpty()) {
            return VsCodeExcludes.NONE;
        }
        // the folder holding .vscode is where the patterns are relative to;
        // it was reached by walking up from `folder`, so it is a prefix of it
        File base = settings.getParentFile().getParentFile();
        String below = base.toPath().relativize(folder.toPath()).toString();
        return excludes.under(below.replace(File.separatorChar, '/'));
    }

    private static VsCodeExcludes parse(File settings) {
        String key = settings.getAbsolutePath();
        long modified = settings.lastModified();
        long length = settings.length();
        Parsed hit = CACHE.get(key);
        if (hit != null && hit.modified() == modified && hit.length() == length) {
            return hit.excludes();
        }
        VsCodeExcludes excludes;
        try {
            excludes = VsCodeExcludes.parse(BoundedReads.read(settings, MAX_BYTES), ignoresCase());
        } catch (IOException ex) {
            LOG.log(Level.INFO, "{0} excludes nothing: {1}", new Object[] {settings, ex.getMessage()});
            excludes = VsCodeExcludes.NONE;
        }
        for (String line : excludes.notHonoured()) {
            // a refusal speaks: once per change of the file, and by name
            LOG.log(Level.INFO, "{0}: {1} - not applied", new Object[] {settings, line});
        }
        if (CACHE.size() >= CACHE_CAP) {
            CACHE.clear();
        }
        CACHE.put(key, new Parsed(modified, length, excludes));
        return excludes;
    }

    /**
     * Whether patterns ignore case here: VS Code follows the file system,
     * which it takes to ignore case everywhere but Linux.
     */
    public static boolean ignoresCase() {
        return BaseUtilities.isWindows() || BaseUtilities.isMac();
    }

    /** For the tests: forget every parsed file. */
    static void forgetForTest() {
        CACHE.clear();
    }
}
