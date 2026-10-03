package org.nmox.studio.editor.snippets;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.util.BoundedReads;
import org.nmox.studio.core.util.Containment;
import org.nmox.studio.editor.ProjectRoot;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Parsed;
import org.nmox.studio.editor.snippets.VsCodeSnippets.Snippet;
import org.nmox.studio.editor.standards.VsCodeSettings;
import org.openide.util.RequestProcessor;

/**
 * The snippets a repository commits for its team:
 * {@code .vscode/*.code-snippets}, the files VS Code calls project
 * snippets. ({@code .vscode/javascript.json} is not one: a
 * {@code <language>.json} is a USER snippet file, which lives in VS
 * Code's own settings folder and is not project configuration.)
 *
 * <p><b>Where.</b> In the {@code .vscode} of every folder between the
 * edited file and its repository's root
 * ({@link VsCodeSettings#directoriesToRepositoryRoot}, the walk the
 * settings reader uses): a monorepo keeps its {@code .vscode} at the top
 * while the nearest {@code package.json} is three folders down. A file
 * in no repository gets no project snippets at all, as it gets no
 * settings: a {@code package.json} and a {@code .vscode} in a shared
 * folder such as {@code /tmp} are whoever planted them. Never the home
 * folder's.
 *
 * <p><b>The files are data and are never executed</b>, so reading them
 * asks for no Workspace Trust: nothing here spawns, loads code or
 * evaluates anything, and the one thing in a snippet that behaves like a
 * program, a transform's regular expression, is bounded where it runs
 * ({@link SnippetTransforms}). What a clone can do is be large or
 * strange, so:
 * <ul>
 * <li>at most {@link #MAX_FILES} snippet files for one edited file, in
 *     name order, each at most {@link #MAX_BYTES} and read through
 *     {@link BoundedReads}, which refuses before reading a byte;</li>
 * <li>a file that is a link out of the project is not read
 *     ({@link Containment}); a link to somewhere inside it is;</li>
 * <li>a file that does not parse is left out and says so once, and the
 *     others still load; a snippet that cannot be honoured is left out
 *     by name ({@link VsCodeSnippets});</li>
 * <li>disk is touched on this class's own lane
 *     ({@code nmox-project-snippets}), never the event thread, and a
 *     caller waits a bounded time for it ({@link #within}): a wedged
 *     network mount costs a completion its snippets, not the editor its
 *     completion.</li>
 * </ul>
 * Each file is parsed once per version (path, modification time, size),
 * which is also what makes every "left out" line appear once.
 */
public final class ProjectSnippets {

    private static final Logger LOG = Logger.getLogger(ProjectSnippets.class.getName());

    /** Snippet files read for one edited file; more are counted and said. */
    static final int MAX_FILES = 20;

    /** A snippet file larger than this is not read. */
    static final long MAX_BYTES = 256L * 1024;

    /** Parsed files kept; past it the map starts over rather than grow. */
    static final int CACHE_CAP = 64;

    /** The reading lane; package-visible so a test can hold it busy. */
    static final RequestProcessor RP = new RequestProcessor("nmox-project-snippets", 1);

    private record Loaded(long modified, long length, List<Snippet> snippets) {
    }

    private static final Map<String, Loaded> CACHE = new ConcurrentHashMap<>();

    /** The last complete answer for a folder: what a caller that could not wait is given. */
    private static final Map<String, Found> LAST = new ConcurrentHashMap<>();

    /** The read queued or running for a folder, so a folder never has two. */
    private static final Map<String, Future<Found>> PENDING = new ConcurrentHashMap<>();

    /** Things already said to the log, so each is said once. */
    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();

    private ProjectSnippets() {
    }

    /**
     * What was found for an edited file.
     *
     * @param snippets every snippet of every file read, unfiltered by language
     * @param workspace the folder whose {@code .vscode} they came from (the outermost, when several), or the project root when there are none
     */
    public record Found(List<Snippet> snippets, File workspace) {
    }

    private static final Found NOTHING = new Found(List.of(), null);

    /**
     * The snippets for {@code file}, read on this class's lane, waiting at
     * most {@code millis} for it. A caller that runs out of time gets the
     * last complete answer for the file's folder, or nothing; the read
     * goes on and the next call has it. Never call this on the event
     * thread: it waits.
     */
    public static Found within(File file, long millis) {
        if (file == null || file.getParentFile() == null) {
            return NOTHING;
        }
        String key = file.getParentFile().getPath();
        Future<Found> reading = pending(key, file);
        try {
            return reading.get(millis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException slow) {
            sayOnce("slow:" + key, Level.INFO,
                    "The project snippets beside {0} took longer than {1} ms to read; offered when they arrive",
                    key, String.valueOf(millis));
            return LAST.getOrDefault(key, NOTHING);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            return NOTHING;
        } catch (ExecutionException failed) {
            LOG.log(Level.INFO, "Project snippets were not read", failed.getCause());
            return NOTHING;
        }
    }

    /**
     * The read for {@code key}'s folder: the one already queued or running
     * when there is one, else a new one. A completion query asks for every
     * keystroke and the lane reads one folder at a time, so without this a
     * slow disk would queue a read per keystroke; with it a folder has at
     * most one, and every caller waiting on that folder waits on it.
     */
    static Future<Found> pending(String key, File file) {
        return PENDING.computeIfAbsent(key, k -> RP.submit(() -> {
            try {
                return read(file);
            } finally {
                PENDING.remove(k);
            }
        }));
    }

    /** Reads on the calling thread: disk. Tests call it directly; everything else goes through {@link #within}. */
    static Found read(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return NOTHING;
        }
        Set<File> roots = new LinkedHashSet<>(VsCodeSettings.directoriesToRepositoryRoot(file));
        if (roots.isEmpty()) {
            // no repository around the file: a .vscode beside a package.json in a
            // shared folder (/tmp) is whoever put it there, not this project's team
            return NOTHING;
        }
        // the project root is never added to the walk: it is inside it already (the
        // repository root holds .git, which ends ProjectRoot's climb too); here it is
        // only the workspace when no folder has snippets
        File project = ProjectRoot.above(parent);
        String home = System.getProperty("user.home");
        List<Snippet> all = new ArrayList<>();
        File workspace = null;
        int left = MAX_FILES;
        for (File root : roots) {
            if (home != null && root.getAbsolutePath().equals(new File(home).getAbsolutePath())) {
                continue; // ~/.vscode is VS Code's own folder, not a project's
            }
            int before = all.size();
            left = readFolder(root, left, all);
            if (all.size() > before) {
                workspace = root; // the outermost folder that has any ends up here
            }
        }
        Found found = new Found(List.copyOf(all), workspace != null ? workspace : project);
        LAST.put(parent.getPath(), found);
        if (LAST.size() > CACHE_CAP * 4) {
            LAST.clear();
        }
        return found;
    }

    /** Adds the snippets of {@code root/.vscode/*.code-snippets} to {@code out}; answers how many files may still be read. */
    private static int readFolder(File root, int left, List<Snippet> out) {
        if (!new File(root, ".vscode").exists()) {
            return left;
        }
        File folder = Containment.resolve(root, ".vscode");
        if (folder == null) {
            sayOnce("out:" + root, Level.INFO,
                    "{0}{1}.vscode leads out of the project; its snippets are not read",
                    root, File.separator);
            return left;
        }
        String[] names = folder.list((dir, name) -> name.endsWith(".code-snippets"));
        if (names == null || names.length == 0) {
            return left;
        }
        // the drop-in law: name order, so which files are inside the cap is the same on every machine
        Arrays.sort(names);
        for (String name : names) {
            if (left <= 0) {
                sayOnce("cap:" + folder, Level.INFO,
                        "{0} holds more than {1} snippet files; the rest are not read", folder, String.valueOf(MAX_FILES));
                break;
            }
            File file = Containment.resolve(root, ".vscode/" + name);
            if (file == null) {
                sayOnce("out:" + folder + "/" + name, Level.INFO,
                        "{0}{1}{2} is a link out of the project and is not read",
                        folder, File.separator, name);
                continue;
            }
            if (!file.isFile()) {
                continue;
            }
            left--;
            out.addAll(load(file, name));
        }
        return left;
    }

    private static List<Snippet> load(File file, String name) {
        String key = file.getPath();
        long modified = file.lastModified();
        long length = file.length();
        Loaded hit = CACHE.get(key);
        if (hit != null && hit.modified() == modified && hit.length() == length) {
            return hit.snippets();
        }
        List<Snippet> snippets = List.of();
        try {
            Parsed parsed = VsCodeSnippets.parse(BoundedReads.read(file, MAX_BYTES), name);
            snippets = parsed.snippets();
            for (String note : parsed.notes()) {
                LOG.log(Level.INFO, "{0}: {1}", new Object[] {file, note});
            }
        } catch (IOException | VsCodeSnippets.Malformed ex) {
            LOG.log(Level.INFO, "{0} offers no snippets: {1}", new Object[] {file, ex.getMessage()});
        }
        if (CACHE.size() >= CACHE_CAP) {
            CACHE.clear();
        }
        CACHE.put(key, new Loaded(modified, length, snippets));
        return snippets;
    }

    private static void sayOnce(String key, Level level, String message, Object... args) {
        if (SAID.size() > 256) {
            SAID.clear();
        }
        if (SAID.add(key)) {
            LOG.log(level, message, args);
        }
    }

    /** Waits for the reading lane to be idle: a test's barrier. */
    static void awaitIdle() throws InterruptedException, ExecutionException {
        RP.submit(() -> { }).get();
    }
}
