package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackIO;
import org.nmox.studio.rack.service.WorkspaceTrust;

/**
 * Turns a {@link LearningCatalog.Space} into a real project you learn
 * by doing: sample files on disk, a TUTORIAL.md that walks them, and a
 * pre-wired rack — a REPL device already pointed at the right
 * interpreter with starter snippets loaded, or a SOLDER command ready
 * to run — so the moment the space opens you can press START and type.
 * Spaces live under {@code ~/.nmox/learn}, marked and pre-trusted.
 */
public final class LearningSpace {

    public static final String MARKER = ".nmox-learn";

    private LearningSpace() {
    }

    public static File root() {
        return new File(System.getProperty("user.home"), ".nmox/learn");
    }

    public static boolean isLearningSpace(File dir) {
        return dir != null && new File(dir, MARKER).isFile();
    }

    /** Existing learning spaces, most recently opened first. */
    public static List<File> list() {
        File[] kids = root().listFiles(File::isDirectory);
        List<File> out = new ArrayList<>();
        if (kids != null) {
            for (File kid : kids) {
                if (isLearningSpace(kid)) {
                    out.add(kid);
                }
            }
            out.sort(Comparator.comparingLong(File::lastModified).reversed());
        }
        return out;
    }

    /**
     * Generates the space (reusing an existing directory for the same
     * slug rather than piling up copies) and returns its directory,
     * pre-trusted and rack-wired.
     */
    public static File create(LearningCatalog.Space space) throws IOException {
        File dir = new File(root(), space.slug());
        Files.createDirectories(dir.toPath());
        WorkspaceTrust.trust(root()); // parent-path match pre-trusts every space
        writeMarker(dir, space);
        for (LearningCatalog.SampleFile f : space.files()) {
            // A community catalog dropped into ~/.nmox/learn-catalog.d is
            // data from anywhere (the drop-in family's standing law, v1.293+),
            // and this is its oldest surface (v1.53) — a sample file whose
            // path escapes the space dir (../../.zshrc, an absolute path)
            // must not let it write outside the space. Built-in spaces use
            // plain relative paths and are unaffected.
            File target = resolveInside(dir, f.path());
            if (target == null) {
                continue; // path escapes the space — refuse, never write outside
            }
            Files.createDirectories(target.getParentFile().toPath());
            if (!target.exists()) {
                Files.writeString(target.toPath(), f.content(), StandardCharsets.UTF_8);
            }
        }
        Files.writeString(new File(dir, "TUTORIAL.md").toPath(),
                tutorialWithInstall(space), StandardCharsets.UTF_8);
        writeRack(dir, space);
        return dir;
    }

    /**
     * Resolves a sample-file path against the space directory and returns
     * the target only if it stays INSIDE that directory; otherwise null.
     * Canonicalizes both sides so {@code ../} traversal, an absolute path,
     * or a symlinked segment can't escape — the strongest guard against a
     * malicious drop-in catalog writing over files elsewhere on disk.
     * Package-private so the traversal refusal is behaviorally testable.
     */
    static File resolveInside(File dir, String path) throws IOException {
        File base = dir.getCanonicalFile();
        File target = new File(dir, path).getCanonicalFile();
        java.nio.file.Path basePath = base.toPath();
        java.nio.file.Path targetPath = target.toPath();
        return targetPath.startsWith(basePath) && !targetPath.equals(basePath)
                ? new File(dir, path) : null;
    }

    /** What the marker recorded at creation, for listings ("?" when unreadable). */
    public record Info(String slug, String name, String created) {
    }

    public static Info info(File space) {
        String slug = "?", name = "?", created = "?";
        try {
            for (String line : Files.readAllLines(
                    new File(space, MARKER).toPath(), StandardCharsets.UTF_8)) {
                if (line.startsWith("slug=")) {
                    slug = line.substring(5).strip();
                } else if (line.startsWith("name=")) {
                    name = line.substring(5).strip();
                } else if (line.startsWith("created=")) {
                    created = line.substring(8).strip();
                }
            }
        } catch (IOException unreadable) {
            // a listing must not fail because one marker is broken
        }
        return new Info(slug, name, created);
    }

    /**
     * The shelf header's teaching line (v2.38.8, the experiments
     * manager's v2.36.1 sentence, spaces-worded): count, disk cost,
     * and the lifecycle in one sentence.
     */
    public static String shelfSummary(int count, long bytes) {
        String size = bytes >= 1024L * 1024 * 1024
                ? String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
                : bytes >= 1024L * 1024 ? (bytes / (1024 * 1024)) + " MB"
                : bytes >= 1024 ? (bytes / 1024) + " KB" : bytes + " B";
        return count + (count == 1 ? " space · " : " spaces · ") + size
                + " on disk — discard what you've finished, promote what grew up.";
    }

    /**
     * A space graduates (v2.38.8, the experiments parity): moved under
     * destParent, marker dropped, git repo initialized — from here on
     * it is an ordinary project. Refuses non-spaces and anything
     * outside {@link #root()} with the discard guards' reasoning: the
     * marker is the contract, and a marker elsewhere on disk must not
     * authorize a move.
     */
    public static File promote(File space, File destParent) throws IOException {
        if (!isLearningSpace(space)) {
            throw new IOException("Not a learning space: " + space);
        }
        if (!space.getCanonicalFile().toPath()
                .startsWith(root().getCanonicalFile().toPath())) {
            throw new IOException("Not under " + root() + ": " + space);
        }
        return graduate(space, destParent);
    }

    /**
     * The move mechanics, guard-free and dir-parameterized so the
     * behavior is testable outside the real home. Anything running in
     * the space stops FIRST: a device serving from the old path while
     * the tree moves under it would keep autosaving the rack file into
     * a recreated ghost of the old directory (the v1.290.0 aimed-
     * discard reasoning, applied to the move).
     */
    static File graduate(File space, File destParent) throws IOException {
        org.nmox.studio.rack.service.RackService service =
                org.nmox.studio.rack.service.RackService.getDefault();
        if (space.equals(service.getRack().getProjectDir())) {
            for (RackDevice d : service.getRack().getDevices()) {
                d.panic();
            }
        }
        File dest = new File(destParent, space.getName());
        if (dest.exists()) {
            throw new IOException("Already exists: " + dest);
        }
        Files.createDirectories(destParent.toPath());
        Files.move(space.toPath(), dest.toPath());
        Files.deleteIfExists(new File(dest, MARKER).toPath());
        ProjectTemplates.initGitRepo(dest);
        return dest;
    }

    /**
     * Discards a learning space: stops anything running there and
     * deletes the tree (v1.289.0, the organize sweep's last creator).
     *
     * <p>Every other place the product lets you make a named thing grew
     * a way to unmake it — API requests v1.263.0, saved queries
     * v1.266.0, components v1.268.0, networks and deployments v1.269.0,
     * Workbench rows v1.288.0. Learning spaces were the holdout, and
     * the one whose leftovers were most visible: the directories under
     * {@code ~/.nmox/learn} exist forever, so a space you tried once
     * sat in the daily PROJECTS list for the life of the install.
     *
     * <p>The marker is the contract, exactly as it is for experiments:
     * this method refuses any directory that is not a learning space,
     * so it can never become a general-purpose {@code rm -rf}. It also
     * refuses anything outside {@link #root()} — a symlinked or
     * hand-edited path must not let a marker file elsewhere on disk
     * authorize a tree delete.
     *
     * <p>Discarding the space the studio is currently AIMED at is the
     * common case, not the exotic one: Discard sits beside Open, so the
     * natural gesture is open a space, decide you are done with it, and
     * drop it. Left alone that aims the whole IDE at a directory which
     * no longer exists — the file tree renders an orphan root, the
     * status line names a deleted path, and Save Patch would try to
     * write into nothing. So an aimed discard stops the devices AND
     * re-aims at the {@code ~/NMOX} workspace, the same known-good home
     * a fresh launch uses (v1.33.1). Verified live before the fix: the
     * tree went blank with the old path still on the status line.
     */
    public static void discard(File space) throws IOException {
        if (!isLearningSpace(space)) {
            throw new IOException("Not a learning space: " + space);
        }
        File canonicalRoot = root().getCanonicalFile();
        File canonical = space.getCanonicalFile();
        if (!canonicalRoot.equals(canonical.getParentFile())) {
            throw new IOException("Outside the learning-space home: " + space);
        }
        org.nmox.studio.rack.service.RackService service =
                org.nmox.studio.rack.service.RackService.getDefault();
        Rack rack = service.getRack();
        boolean wasAimedHere = space.equals(rack.getProjectDir());
        if (wasAimedHere) {
            for (RackDevice d : rack.getDevices()) {
                d.panic();
            }
        }
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(space.toPath())) {
            for (java.nio.file.Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
        if (wasAimedHere) {
            // re-aim only after the tree is really gone: aiming first
            // would let the watchers and the patch autosave race the
            // delete and re-create files under a directory we are
            // removing. Quietly — a discarded space must not re-enter
            // the recents list the caller just cleaned up.
            service.openProjectQuietly(fallbackWorkspace());
        }
    }

    /**
     * Where the studio lands when the space under it is discarded: the
     * {@code ~/NMOX} workspace, created if this is the first time. The
     * v1.33.1 law applies — this is one shallow, self-made directory,
     * never {@code $HOME}, so re-aiming cannot walk into a TCC-protected
     * folder and stack permission prompts.
     */
    static File fallbackWorkspace() {
        File workspace = new File(System.getProperty("user.home"), "NMOX");
        try {
            Files.createDirectories(workspace.toPath());
        } catch (IOException ignore) {
            // aiming at a missing dir is still better than a deleted one
        }
        return workspace;
    }

    private static void writeMarker(File dir, LearningCatalog.Space space) throws IOException {
        Files.writeString(new File(dir, MARKER).toPath(),
                "slug=" + space.slug() + "\nname=" + space.name()
                        + "\ncreated=" + java.time.LocalDate.now() + "\n",
                StandardCharsets.UTF_8);
    }

    /**
     * Appends the OS-appropriate install section — framed as an IN-APP action,
     * never a hand-off to a terminal. NMOX Studio runs the install for you: a
     * REPL space has the REPL's INSTALL button (it runs this exact seeded
     * command, streamed onto the screen); a run space has SOLDER, which runs
     * any command to MONITOR. The command is shown only so you can see what
     * runs, not so you type it into a shell.
     */
    static String tutorialWithInstall(LearningCatalog.Space space) {
        StringBuilder sb = new StringBuilder(space.tutorial());
        String hint = installHint(space);
        if (!hint.isBlank()) {
            boolean repl = space.driver().kind() == LearningCatalog.DriverKind.REPL;
            String how = repl
                    ? "press **INSTALL** on the REPL device and NMOX Studio runs it for "
                            + "you, streamed onto the REPL screen"
                    : "drop it into the **SOLDER** device and press GO — NMOX Studio runs "
                            + "it for you and streams the output to MONITOR";
            sb.append("\n\n---\n\n## Install\n\nIf the tool isn't found when you start the "
                    + "space, ").append(how).append(". No terminal needed — this is the exact "
                    + "command it runs:\n\n```sh\n").append(hint).append("\n```\n");
        }
        return sb.toString();
    }

    /**
     * The install command for the running OS — the single selection the
     * tutorial hint, the REPL's INSTALL button, and the picker's
     * availability line all share (mac entry as the fallback when the
     * current OS has none). Blank when the catalog carries none.
     */
    public static String installHint(LearningCatalog.Space space) {
        String hint = space.install().getOrDefault(osKey(), space.install().get("mac"));
        return hint == null ? "" : hint;
    }

    /** The catalog's install-hint key for the OS this JVM runs on. */
    static String osKey() {
        if (org.openide.util.Utilities.isMac()) {
            return "mac";
        }
        return org.openide.util.Utilities.isWindows() ? "windows" : "linux";
    }

    /** Writes the pre-wired .nmoxrack.json for the space's driver. */
    private static void writeRack(File dir, LearningCatalog.Space space) throws IOException {
        JSONObject patch = RackPresets.buildPatchFrom(rack -> wire(rack, space));
        Files.writeString(new File(dir, RackIO.DEFAULT_FILENAME).toPath(),
                patch.toString(2), StandardCharsets.UTF_8);
    }

    /** REPL spaces get a seeded REPL; run spaces get SOLDER → MONITOR. */
    static void wire(Rack rack, LearningCatalog.Space space) {
        LearningCatalog.Driver driver = space.driver();
        String command = String.join(" ", driver.command());
        if (driver.kind() == LearningCatalog.DriverKind.REPL) {
            RackPresets.add(rack, DeviceType.REPL, Map.of(
                    "command", command,
                    "snippets", String.join("\n", driver.snippets()),
                    "install", installHint(space)));
        } else {
            RackDevice solder = RackPresets.add(rack, DeviceType.CMD,
                    Map.of("command", command));
            RackDevice monitor = RackPresets.add(rack, DeviceType.CONSOLE, Map.of());
            rack.connect(solder.getPort("out"), monitor.getPort("in"));
        }
    }
}
