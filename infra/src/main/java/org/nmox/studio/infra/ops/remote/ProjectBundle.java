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

    /** What the walk decided: the entries that go, the entries left behind with reasons, and a refusal. */
    public record Manifest(List<String> files, long bytes, List<String> skipped, String refusal) {

        public boolean refused() {
            return refusal != null;
        }
    }

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
        long[] bytes = {0};
        String[] refusal = {null};
        Path base = root.toAbsolutePath().normalize();
        walk(base, base, 0, files, skipped, bytes, refusal, maxFiles, maxBytes);
        return new Manifest(files, bytes[0], skipped, refusal[0]);
    }

    private static void walk(Path base, Path dir, int depth, List<String> files, List<String> skipped,
            long[] bytes, String[] refusal, int maxFiles, long maxBytes) throws IOException {
        if (refusal[0] != null) {
            return;
        }
        if (depth > MAX_DEPTH) {
            refusal[0] = "folders nested deeper than " + MAX_DEPTH + " levels under " + base.relativize(dir);
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
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                if (HeavyDirs.isHeavy(name) || name.equals(".git")) {
                    skipped.add(rel + "/ (the server builds its own)");
                    continue;
                }
                walk(base, child, depth + 1, files, skipped, bytes, refusal, maxFiles, maxBytes);
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
                refusal[0] = "more than " + maxFiles + " files";
                return;
            }
            if (bytes[0] > maxBytes) {
                refusal[0] = "more than " + (maxBytes / (1024 * 1024)) + " MB";
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
