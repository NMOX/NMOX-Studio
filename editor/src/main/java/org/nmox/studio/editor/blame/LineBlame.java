package org.nmox.studio.editor.blame;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nmox.studio.core.process.ProcessSupport;
import org.nmox.studio.core.util.GitFacts;
import org.openide.util.RequestProcessor;

/**
 * Who wrote a line, answered off the EDT (3.2.0).
 *
 * <p>One {@code git blame --porcelain --no-textconv -- <name>} per file
 * VERSION: the answer is cached by the file's path, length and modification
 * time and by the repository's {@link GitFacts#headStamp HEAD stamp}, so
 * moving the caret costs a map lookup, and a save, a commit or a checkout
 * asks git again. A failed or empty answer is cached too (an untracked file
 * does not re-spawn git on every caret move).
 *
 * <p><b>Newest wins.</b> Every request takes a generation number; the lane
 * checks it before spawning and before answering, and {@link #isCurrent}
 * lets the painter check it once more on the EDT. A slow blame of a file
 * the user has already left is never painted — a result belongs to the
 * file that produced it.
 *
 * <p><b>What never spawns.</b> A file outside a repository (decided from
 * {@code .git} on disk, no process), a missing file, and a file over
 * {@link #MAX_FILE_BYTES} — blame's output is the whole file plus a header
 * per line, and a generated bundle is not a file anyone asks this of.
 *
 * <p>The process is the user's own {@code git} with fixed words and a file
 * name after {@code --}; {@code --no-textconv} keeps a repository's
 * {@code .gitattributes} from routing the read through a configured
 * converter program. Bounded in time and in output by
 * {@link ProcessSupport#runBounded}; a truncated answer is treated as no
 * answer rather than as a partial one.
 */
public final class LineBlame {

    private static final Logger LOG = Logger.getLogger(LineBlame.class.getName());

    /** Files larger than this are not blamed: 1 MiB of source is ~30k lines. */
    public static final long MAX_FILE_BYTES = 1024L * 1024L;

    /** A blame of a large file on a slow disk finishes well inside this. */
    static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** File versions kept; the working set is the handful of files one edits. */
    static final int CACHE_SIZE = 16;

    /** The spawn, seamed so tests count and stall it. */
    @FunctionalInterface
    public interface Runner {
        ProcessSupport.BoundedResult run(List<String> argv, File dir, Duration timeout) throws IOException;
    }

    /** What a lookup produced: the file and line asked about, and the commit, or null. */
    public record Answer(long generation, File file, int line, BlamePorcelain.Line entry) {
    }

    /** One file version. */
    record Key(String path, long length, long modified, String head) {
    }

    private final Runner runner;
    private final RequestProcessor lane;
    private final AtomicLong generation = new AtomicLong();
    private final Map<Key, BlamePorcelain.Blame> cache =
            new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, BlamePorcelain.Blame> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /** The product's instance: the user's git through the bounded runner. */
    public static LineBlame create() {
        return new LineBlame((argv, dir, timeout) -> ProcessSupport.runBounded(argv, dir, timeout),
                new RequestProcessor("Line Blame", 1));
    }

    LineBlame(Runner runner, RequestProcessor lane) {
        this.runner = runner;
        this.lane = lane;
    }

    /** The argv, fixed: nothing in it comes from the repository but the file's own name. */
    static List<String> argv(String fileName) {
        return List.of("git", "blame", "--porcelain", "--no-textconv", "--", fileName);
    }

    /**
     * Asks who last changed {@code line} (1-based) of {@code file}. The
     * answer reaches {@code sink} on the lane thread, only if no newer
     * request was made meanwhile; the returned generation lets the caller
     * check again where it paints.
     */
    public long request(File file, int line, Consumer<Answer> sink) {
        long gen = generation.incrementAndGet();
        lane.post(() -> {
            if (gen != generation.get()) {
                return; // superseded before it started: do not spawn for a file the user left
            }
            BlamePorcelain.Line entry = lookup(file, line);
            if (gen != generation.get()) {
                return; // superseded while git ran: this answer belongs to a file no longer asked about
            }
            sink.accept(new Answer(gen, file, line, entry));
        });
        return gen;
    }

    /** Drops every pending answer: the editor now shows something blame has nothing to say about. */
    public void cancel() {
        generation.incrementAndGet();
    }

    /** Whether {@code gen} is still the newest request. */
    public boolean isCurrent(long gen) {
        return gen == generation.get();
    }

    /** The lane's work: cache, else one bounded spawn. Package-private for the tests. */
    BlamePorcelain.Line lookup(File file, int line) {
        if (file == null || !file.isFile()) {
            return null;
        }
        long length = file.length();
        if (length > MAX_FILE_BYTES) {
            return null;
        }
        File dir = file.getParentFile();
        File root = GitFacts.repoRoot(dir);
        if (root == null) {
            return null; // not in a repository: decided on disk, no process
        }
        Key key = new Key(file.getAbsolutePath(), length, file.lastModified(), GitFacts.headStamp(root));
        BlamePorcelain.Blame blame;
        synchronized (cache) {
            blame = cache.get(key);
        }
        if (blame == null) {
            blame = run(file, dir);
            if (blame == null) {
                // timed out (cold caches, a huge history): say nothing now,
                // and ask again next time rather than remember the silence
                return null;
            }
            synchronized (cache) {
                cache.put(key, blame);
            }
        }
        return blame.at(line);
    }

    private BlamePorcelain.Blame run(File file, File dir) {
        try {
            ProcessSupport.BoundedResult r = runner.run(argv(file.getName()), dir, TIMEOUT);
            if (r.timedOut()) {
                return null; // not an answer: never cached
            }
            if (!r.ok() || r.truncated()) {
                // untracked, outside the index, git missing its object, or too much: nothing to say
                return BlamePorcelain.parse(null);
            }
            return BlamePorcelain.parse(r.stdout());
        } catch (IOException ex) {
            // no git on this machine: the chip beside us says the same by showing nothing
            LOG.log(Level.FINE, "git blame could not run", ex);
            return BlamePorcelain.parse(null);
        }
    }
}
