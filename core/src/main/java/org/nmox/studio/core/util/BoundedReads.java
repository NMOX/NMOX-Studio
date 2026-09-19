package org.nmox.studio.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The one whole-file read in the product that asks how big the file is
 * BEFORE reading it — the bounded-read law's home, promoted out of
 * {@code RackIO.readCapped} (v2.178.0) on its fifth consumer, the way
 * {@link FilePulse} and {@code BoundedWalk} were promoted before it.
 *
 * <p>The files this exists for arrive with a {@code git clone} and are
 * read because the user AIMED at a project — a manifest on aim, a
 * {@code .editorconfig} on save, a {@code package.json} on every file
 * open, a studio's workspace file, a drop-in someone dropped in.
 * Nobody asked for those reads, so nobody chose the file, so a
 * pathological or hostile one (a 500 MB {@code .nmoxtasks.json} in a
 * repository a user just cloned) takes the IDE's heap down at aim time
 * and the user's unsaved work with it. {@link Files#readString} has no
 * ceiling; this does, and it refuses before a byte is read rather than
 * reading a prefix, because half a manifest parses into a LIE while a
 * refusal is only a refusal.
 *
 * <p>Refusals speak: {@link TooLarge} names the file and its size, and
 * a caller that already shrugs at an unreadable file should shrug the
 * same way here — but SAY so where it already says things.
 *
 * <p>This bounds MEMORY, not the walk that found the file: a scan over
 * a cloned project still belongs behind {@code BoundedWalk}.
 */
public final class BoundedReads {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(BoundedReads.class.getName());

    private BoundedReads() {
    }

    /**
     * The ceiling for a workspace file, a manifest, a lockfile or a
     * drop-in when the caller names none. Eight mebibytes is the same
     * number a rack patch has carried since v2.178.0, and it is chosen
     * to be absurd rather than tight: every honest file of these kinds
     * is kilobytes, so the cap can only ever be met by a mistake or by
     * malice, and no user is ever told their real file is too big.
     */
    public static final long DEFAULT_MAX_BYTES = 8L * 1024 * 1024;

    /**
     * A file over the ceiling, refused before it was read. Carries the
     * measured size so a caller building its own message can name it.
     */
    public static final class TooLarge extends IOException {

        private static final long serialVersionUID = 1L;

        private final transient String fileName;
        private final long size;
        private final long maxBytes;

        TooLarge(String message, String fileName, long size, long maxBytes) {
            super(message);
            this.fileName = fileName;
            this.size = size;
            this.maxBytes = maxBytes;
        }

        /** The name of the file that was not read. */
        public String fileName() {
            return fileName;
        }

        /** What the file measured, in bytes. */
        public long size() {
            return size;
        }

        /** The ceiling it was over, in bytes. */
        public long maxBytes() {
            return maxBytes;
        }
    }

    /**
     * The refusal sentence in the product's voice, for a caller that
     * throws its own exception type or writes its own log line — so a
     * user meets one wording whichever door refused. {@code subject} is
     * a noun phrase placed before the name ("Rack patch"), or blank.
     */
    public static String refusal(String subject, String fileName, long size, long maxBytes) {
        String head = subject == null || subject.isBlank() ? "" : subject.strip() + " ";
        return head + fileName + " is " + (size / 1024) + " KiB, over the "
                + capText(maxBytes) + " cap — not read";
    }

    /** A whole number of mebibytes reads as MiB; anything else as KiB. */
    private static String capText(long maxBytes) {
        long mib = 1024L * 1024;
        return maxBytes % mib == 0
                ? (maxBytes / mib) + " MiB"
                : (maxBytes / 1024) + " KiB";
    }

    /** The file's text as UTF-8, or {@link TooLarge} over {@link #DEFAULT_MAX_BYTES}. */
    public static String read(Path file) throws IOException {
        return read(file, DEFAULT_MAX_BYTES);
    }

    /** The file's text as UTF-8, or {@link TooLarge} over {@code maxBytes}. */
    public static String read(Path file, long maxBytes) throws IOException {
        long size = Files.size(file);
        if (size > maxBytes) {
            String name = file.getFileName() == null
                    ? file.toString() : file.getFileName().toString();
            String message = refusal("", name, size, maxBytes);
            // Most callers of this are detection paths that shrug at an
            // unreadable file and say nothing — correct for a manifest with
            // a typo in it, wrong for a file we refused on purpose. The one
            // place that knows the fact says it, so every caller speaks by
            // construction rather than by twenty-six remembered log lines.
            LOG.log(java.util.logging.Level.WARNING, "{0} ({1})",
                    new Object[]{message, file});
            throw new TooLarge(message, name, size, maxBytes);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** {@link #read(Path)} for a caller holding a {@link File}. */
    public static String read(File file) throws IOException {
        return read(file.toPath(), DEFAULT_MAX_BYTES);
    }

    /** {@link #read(Path, long)} for a caller holding a {@link File}. */
    public static String read(File file, long maxBytes) throws IOException {
        return read(file.toPath(), maxBytes);
    }

    /**
     * The file's lines, bounded the same way — the line-oriented callers
     * ({@code .editorconfig}, {@code Cargo.lock}, {@code pnpm-workspace.yaml})
     * get the same ceiling as the document ones rather than a second rule.
     */
    public static List<String> readLines(Path file, long maxBytes) throws IOException {
        return read(file, maxBytes).lines().toList();
    }

    /** {@link #readLines(Path, long)} at {@link #DEFAULT_MAX_BYTES}. */
    public static List<String> readLines(Path file) throws IOException {
        return readLines(file, DEFAULT_MAX_BYTES);
    }
}
