package org.nmox.studio.infra.ops.remote;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.nmox.studio.core.util.HeavyDirs;

/**
 * Turns a project folder into the bytes a deploy uploads, with the
 * bounds and refusals a stranger's checkout deserves. Heavy directories
 * ({@code node_modules}, {@code .git}, build output) never travel — the
 * server builds its own; {@code .env} and its siblings never travel
 * either, because a secret that was never meant to leave this machine
 * must not ride a deploy by accident; symlinks are skipped by name. Past
 * the caps the bundle is REFUSED, not cut: a half-uploaded project builds
 * a wrong image, and a wrong image on a server is worse than no deploy.
 */
public final class ProjectBundle {

    public static final int MAX_FILES = 20_000;
    public static final long MAX_BYTES = 200L * 1024 * 1024;
    public static final int MAX_DEPTH = 32;

    /**
     * Why a bundle was refused, as data: the kind and its one figure (and,
     * for depth, where). The words a reader sees are chosen where they
     * are shown; {@link Manifest#refusal()} is the same thing in English
     * for the deploy's own log, which is a log.
     */
    public record Refusal(Kind kind, long number, String where) {

        /** What a bundle can be refused for. */
        public enum Kind {
            /** More files than {@link #MAX_FILES}; the number is the cap. */
            TOO_MANY_FILES,
            /** More bytes than {@link #MAX_BYTES}; the number is the cap in megabytes. */
            TOO_BIG,
            /** Folders nested deeper than {@link #MAX_DEPTH}; the number is the cap, {@code where} the top two levels of the chain. */
            TOO_DEEP
        }
    }

    /**
     * What the walk decided: the entries that go, the entries left behind
     * with reasons, a refusal (in English for the log, and as data), and
     * notes about what was and was not applied.
     */
    public record Manifest(List<String> files, long bytes, List<String> skipped, String refusal, Refusal why,
            List<String> notes) {

        /** A manifest with no typed refusal and no notes: a test's, or one made by hand. */
        public Manifest(List<String> files, long bytes, List<String> skipped, String refusal) {
            this(files, bytes, skipped, refusal, null, List.of());
        }

        public boolean refused() {
            return refusal != null;
        }
    }

    /** The most a {@code .dockerignore} may weigh and still be read; a larger one is not applied. */
    static final int MAX_IGNORE_BYTES = 64 * 1024;
    /** How many entries left out by {@code .dockerignore} are named in the log before they are only counted. */
    static final int MAX_IGNORED_NAMED = 20;

    private ProjectBundle() {
    }

    /** True for a name that holds secrets by convention: {@code .env}, {@code .env.local}, {@code .env.production}. */
    public static boolean isSecretFile(String name) {
        return name.equals(".env") || name.startsWith(".env.");
    }

    /** Walks the project and decides what travels. Pure apart from reading the tree. */
    public static Manifest plan(Path root) throws IOException {
        return plan(root, MAX_FILES, MAX_BYTES);
    }

    /** The walk with its caps as parameters, so a test reaches them without 20,000 files. */
    static Manifest plan(Path root, int maxFiles, long maxBytes) throws IOException {
        List<String> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        long[] bytes = {0};
        Refusal[] refusal = {null};
        Path base = root.toAbsolutePath().normalize();
        DockerIgnore ignore = readIgnore(base, notes);
        int[] ignored = {0};
        walk(base, base, 0, files, skipped, bytes, refusal, maxFiles, maxBytes, ignore, ignored);
        if (ignored[0] > MAX_IGNORED_NAMED) {
            skipped.add((ignored[0] - MAX_IGNORED_NAMED) + " more entries (.dockerignore)");
        }
        return new Manifest(files, bytes[0], skipped, refusal[0] == null ? null : english(refusal[0]), refusal[0],
                List.copyOf(notes));
    }

    /**
     * The project's {@code .dockerignore}, as far as it can be applied to
     * the upload with certainty ({@link DockerIgnore} says what that is).
     * What is not applied is noted, so the log says why a folder the file
     * names still travelled.
     */
    private static DockerIgnore readIgnore(Path base, List<String> notes) {
        Path file = base.resolve(".dockerignore");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return DockerIgnore.NONE;
        }
        String text;
        try {
            text = org.nmox.studio.core.util.BoundedReads.read(file.toFile(), MAX_IGNORE_BYTES);
        } catch (IOException tooBigOrUnreadable) {
            notes.add(".dockerignore was not applied to the upload (" + tooBigOrUnreadable.getMessage()
                    + "); docker applies it on the host");
            return DockerIgnore.NONE;
        }
        DockerIgnore ignore = DockerIgnore.parse(text);
        if (ignore.hasExceptions()) {
            notes.add(".dockerignore has exception lines (!), so it was not applied to the upload; docker applies it on the host");
        }
        for (String line : ignore.passedOver()) {
            notes.add(".dockerignore line not applied to the upload: " + line);
        }
        return ignore;
    }

    /** A refusal in English, for the deploy's log. */
    private static String english(Refusal r) {
        return switch (r.kind()) {
            case TOO_MANY_FILES -> "more than " + r.number() + " files";
            case TOO_BIG -> "more than " + r.number() + " MB";
            case TOO_DEEP -> "folders nested deeper than " + r.number() + " levels under " + r.where();
        };
    }

    private static void walk(Path base, Path dir, int depth, List<String> files, List<String> skipped,
            long[] bytes, Refusal[] refusal, int maxFiles, long maxBytes, DockerIgnore ignore, int[] ignored)
            throws IOException {
        if (refusal[0] != null) {
            return;
        }
        if (depth > MAX_DEPTH) {
            // where the chain STARTS, not the folder thirty-three levels down: its first two levels are what
            // a reader can find, and the whole path would fill the feed
            Path chain = base.relativize(dir);
            String top = chain.subpath(0, Math.min(2, chain.getNameCount())).toString();
            refusal[0] = new Refusal(Refusal.Kind.TOO_DEEP, MAX_DEPTH,
                    java.io.File.separatorChar == '\\' ? top.replace('\\', '/') : top);
            return;
        }
        List<Path> children;
        try (Stream<Path> s = Files.list(dir)) {
            // by name, code point order: a Path's own order is case-insensitive on Windows
            // (the Windows lane caught `bin` sorting before `Dockerfile`), and the bundle
            // must be the same bytes from every OS
            children = s.sorted(java.util.Comparator.comparing(c -> c.getFileName().toString())).toList();
        }
        for (Path child : children) {
            // the tar's separator is '/', so Windows' is rewritten — and ONLY Windows': on every other
            // system a backslash is an ordinary character of a NAME, and rewriting it there would turn a
            // file a cloned repository calls "..\\..\\.ssh\\id_rsa" into a path that climbs out of the project
            String rel = base.relativize(child).toString();
            if (java.io.File.separatorChar == '\\') {
                rel = rel.replace('\\', '/');
            }
            String name = child.getFileName().toString();
            if (Files.isSymbolicLink(child)) {
                skipped.add(rel + " (a symlink)");
                continue;
            }
            boolean directory = Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS);
            if (ignore.excludes(rel)) {
                // what docker would drop on arrival does not travel, and does not count against the caps;
                // a folder goes whole, as it does for docker
                if (ignored[0]++ < MAX_IGNORED_NAMED) {
                    skipped.add(rel + (directory ? "/" : "") + " (.dockerignore)");
                }
                continue;
            }
            if (directory) {
                if (HeavyDirs.isHeavy(name) || name.equals(".git")) {
                    skipped.add(rel + "/ (the server builds its own)");
                    continue;
                }
                walk(base, child, depth + 1, files, skipped, bytes, refusal, maxFiles, maxBytes, ignore, ignored);
                if (refusal[0] != null) {
                    return;
                }
                continue;
            }
            if (!Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                skipped.add(rel + " (not a regular file)");
                continue;
            }
            if (isSecretFile(name)) {
                skipped.add(rel + " (secrets stay on this machine)");
                continue;
            }
            files.add(rel);
            bytes[0] += Files.size(child);
            if (files.size() > maxFiles) {
                refusal[0] = new Refusal(Refusal.Kind.TOO_MANY_FILES, maxFiles, null);
                return;
            }
            if (bytes[0] > maxBytes) {
                refusal[0] = new Refusal(Refusal.Kind.TOO_BIG, maxBytes / (1024 * 1024), null);
                return;
            }
        }
    }

    /** Writes a planned bundle as tar.gz. The manifest must not be refused. */
    public static void write(Path root, Manifest manifest, OutputStream out) throws IOException {
        if (manifest.refused()) {
            throw new IOException("refused bundle: " + manifest.refusal());
        }
        Path base = root.toAbsolutePath().normalize();
        try (TarGz tar = new TarGz(out)) {
            java.util.Set<String> dirs = new java.util.LinkedHashSet<>();
            for (String rel : manifest.files()) {
                int slash = rel.lastIndexOf('/');
                if (slash > 0) {
                    String d = rel.substring(0, slash);
                    // every ancestor, outermost first
                    int from = 0;
                    while (true) {
                        int next = d.indexOf('/', from);
                        String ancestor = next < 0 ? d : d.substring(0, next);
                        if (dirs.add(ancestor)) {
                            tar.directory(ancestor);
                        }
                        if (next < 0) {
                            break;
                        }
                        from = next + 1;
                    }
                }
                // every path is proven inside the project again at the moment it is opened: the plan was
                // made a moment ago, and a manifest is a list of strings
                // through the one guard, like every other containment decision in the product: a path it
                // cannot prove inside (a name with .. in it, a link that leads out) is refused. The guard's
                // answer is the link's TARGET, so the file is still opened by its own name: the tar does not
                // follow a link, and a file swapped for a link to something inside is refused there
                if (org.nmox.studio.core.util.Containment.resolvePath(base.toFile(), rel) == null) {
                    throw new IOException(rel + " is not inside the project");
                }
                tar.file(rel, base.resolve(rel));
            }
        }
    }
}
