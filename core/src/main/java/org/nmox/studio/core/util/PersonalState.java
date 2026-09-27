package org.nmox.studio.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * One person's state about one project, kept OUT of the project
 * (3.4, question 1).
 *
 * <p>The studios commit their workspace files beside the code, and before
 * 3.4 three of them also held what only one person cares about: DB Studio's
 * query history (with every SQL text), API Studio's send history and its
 * active environment. Each was rewritten on every Run, Send or pick, so two
 * people working on one project conflicted on every merge — over state
 * neither of them meant to share. That state lives here instead: one small
 * file per studio per project, in the IDE's own user directory, never in
 * the repository.
 *
 * <p>The document is a string (each studio writes its own JSON): the
 * modules each load their own org.json, so only JDK types cross a module
 * boundary. Reads are bounded, writes atomic. Callers do the disk work off
 * the EDT, like every other workspace read and write.
 *
 * <p><b>Where.</b> Inside the platform: {@code <userdir>/var/nmox/personal/
 * <studio>/<key>.json}, where {@code key} is a hash of the project's
 * absolute path. Outside it (plain unit tests, which have no user
 * directory), a directory under {@code java.io.tmpdir}: a test must never
 * write into a developer's real IDE state.
 */
public final class PersonalState {

    /** Personal documents are small; a larger one is refused, not read. */
    public static final long MAX_BYTES = 1024 * 1024;

    private static volatile Path baseOverride;

    private PersonalState() {
    }

    /** Test seam: keep personal state under {@code base}; null restores the default. */
    public static void setBaseForTest(Path base) {
        baseOverride = base;
    }

    /** The directory every studio's personal documents live under. */
    static Path base() {
        Path override = baseOverride;
        if (override != null) {
            return override;
        }
        File userDir = null;
        try {
            userDir = org.openide.modules.Places.getUserDirectory();
        } catch (RuntimeException | LinkageError noPlatform) {
            // plain tests, stripped platform: fall through
        }
        if (userDir != null) {
            return userDir.toPath().resolve("var").resolve("nmox").resolve("personal");
        }
        return Path.of(System.getProperty("java.io.tmpdir"),
                "nmox-personal-" + System.getProperty("user.name", "user"));
    }

    /** The file one studio keeps about one project. */
    public static Path fileFor(File projectDir, String studio) {
        return base().resolve(studio).resolve(key(projectDir) + ".json");
    }

    /**
     * A stable key for a project: the first 16 hex digits of the SHA-256 of
     * its REAL path. The absolute path was the first key, and a project
     * reached through a symlink (macOS's {@code /var} is one, to
     * {@code /private/var}) then kept two personal files, each spelling
     * losing what the other had written. A directory that cannot be
     * resolved (it does not exist yet) keys on its normalized absolute path.
     */
    static String key(File projectDir) {
        Path absolute = projectDir.getAbsoluteFile().toPath().normalize();
        String path;
        try {
            path = absolute.toRealPath().toString();
        } catch (IOException | SecurityException unresolvable) {
            path = absolute.toString();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(path.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(Character.forDigit((digest[i] >> 4) & 0xF, 16))
                        .append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", impossible);
        }
    }

    /**
     * The studio's document for this project, or null when there is none
     * yet (the caller migrates from the shared file) or it cannot be read.
     */
    public static String read(File projectDir, String studio) {
        Path file = fileFor(projectDir, studio);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return BoundedReads.read(file, MAX_BYTES);
        } catch (IOException unreadable) {
            java.util.logging.Logger.getLogger(PersonalState.class.getName()).log(
                    java.util.logging.Level.WARNING, "Cannot read personal state {0}: {1}",
                    new Object[]{file, unreadable.getMessage()});
            return null;
        }
    }

    /** True when {@code document} is small enough for {@link #read} to read it back. */
    public static boolean fits(String document) {
        return document.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    /**
     * Writes the studio's document for this project, atomically. A document
     * {@link #read} would refuse is refused HERE, before anything is written:
     * the write had no cap while the read had one, so fifty sends with a
     * 24 KB body wrote a 1.2 MB file that the next load could not read — the
     * history came back empty and the next save made that permanent. The
     * file already on disk (which fits) stays; a studio trims its document
     * with {@link #fits} before it gets here.
     */
    public static void write(File projectDir, String studio, String document) throws IOException {
        if (!fits(document)) {
            throw new IOException("A personal " + studio + " document of "
                    + document.getBytes(StandardCharsets.UTF_8).length
                    + " bytes is over the " + MAX_BYTES + "-byte limit it is read with; not written");
        }
        Path file = fileFor(projectDir, studio);
        Files.createDirectories(file.getParent());
        AtomicFiles.writeString(file, document);
    }
}
