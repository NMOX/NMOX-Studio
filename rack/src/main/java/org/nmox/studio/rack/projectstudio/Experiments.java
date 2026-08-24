package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.service.RackService;
import org.nmox.studio.rack.service.WorkspaceTrust;

/**
 * Throwaway workspaces, first-class: an experiment is a real project
 * generated from a template into {@code ~/.nmox/experiments}, minus the
 * ceremony - no git repo, no recents pollution, the parent directory
 * trusted once so devices run without prompts. Promote turns a keeper
 * into a normal project (move + git init); discard stops anything
 * running there and deletes the tree. The {@code .nmox-experiment}
 * marker is the contract: only marked directories can be discarded.
 */
public final class Experiments {

    public static final String MARKER = ".nmox-experiment";

    private Experiments() {
    }

    public static File root() {
        return new File(System.getProperty("user.home"), ".nmox/experiments");
    }

    /** Generates a fresh experiment and returns its directory. */
    public static File create(ProjectTemplates template, String name) throws IOException {
        File root = root();
        Files.createDirectories(root.toPath());
        WorkspaceTrust.trust(root); // parent-path match pre-trusts every experiment
        String base = name == null || name.isBlank()
                ? template.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                : name.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        File dir = unique(root, base);
        template.generate(dir, dir.getName()); // deliberately no git init
        Files.writeString(new File(dir, MARKER).toPath(),
                "created=" + java.time.LocalDate.now() + "\ntemplate=" + template.name() + "\n", java.nio.charset.StandardCharsets.UTF_8);
        writeGuide(dir, template);
        return dir;
    }

    /** The walkthrough the experiment is born with (v2.36.0). */
    public static final String GUIDE = "EXPERIMENT.md";

    /**
     * Writes EXPERIMENT.md beside the scaffold. Package-private and
     * dir-parameterized so the write is testable without touching the
     * real {@code ~/.nmox/experiments} home.
     */
    static void writeGuide(File dir, ProjectTemplates template) throws IOException {
        Files.writeString(new File(dir, GUIDE).toPath(),
                ExperimentGuide.walkthrough(template, dir.getName()),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static File unique(File root, String base) {
        File dir = new File(root, base);
        int n = 2;
        while (dir.exists()) {
            dir = new File(root, base + "-" + n++);
        }
        return dir;
    }

    public static boolean isExperiment(File dir) {
        return dir != null && new File(dir, MARKER).isFile();
    }

    /** What the marker recorded at creation, for listings. */
    public record Info(String created, String template) {
    }

    /** Reads the marker; unknown or unreadable fields come back as "?". */
    public static Info info(File experiment) {
        String created = "?";
        String template = "?";
        try {
            for (String line : Files.readAllLines(
                    new File(experiment, MARKER).toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                if (line.startsWith("created=")) {
                    created = line.substring("created=".length()).strip();
                } else if (line.startsWith("template=")) {
                    template = line.substring("template=".length()).strip();
                }
            }
        } catch (IOException unreadable) {
            // a listing must not fail because one marker is broken
        }
        return new Info(created, template);
    }

    /** Existing experiments, most recently touched first. */
    public static List<File> list() {
        File[] kids = root().listFiles(File::isDirectory);
        List<File> result = new ArrayList<>();
        if (kids != null) {
            for (File kid : kids) {
                if (isExperiment(kid)) {
                    result.add(kid);
                }
            }
        }
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        return result;
    }

    /**
     * Bytes under an experiment tree — the honest cost of keeping it
     * (node_modules makes these big). A disk walk: callers run it OFF
     * the EDT (the v1.33.1 law) and paint the answer when it lands.
     */
    public static long sizeOf(File experiment) {
        long[] total = {0};
        try (var walk = Files.walk(experiment.toPath())) {
            walk.forEach(p -> {
                File f = p.toFile();
                if (f.isFile()) {
                    total[0] += f.length();
                }
            });
        } catch (IOException partial) {
            // a half-walked size is still a useful size
        }
        return total[0];
    }

    /**
     * The shelf header's teaching line (v2.36.1): the count, the disk
     * cost, and the lifecycle in one sentence — experiments are meant
     * to be discarded, and a number makes the nudge concrete. Pure so
     * the wording and the unit scaling are pinned by tests.
     */
    public static String shelfSummary(int count, long bytes) {
        String size = bytes >= 1024L * 1024 * 1024
                ? String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
                : bytes >= 1024L * 1024
                ? (bytes / (1024 * 1024)) + " MB"
                : bytes >= 1024 ? (bytes / 1024) + " KB" : bytes + " B";
        return count + (count == 1 ? " experiment · " : " experiments · ") + size
                + " on disk — discard what you're done with, promote what grew up.";
    }

    /**
     * A keeper graduates: moved under destParent, marker removed, git
     * repo initialized - from here on it is an ordinary project.
     */
    public static File promote(File experiment, File destParent) throws IOException {
        if (!isExperiment(experiment)) {
            throw new IOException("Not an experiment: " + experiment);
        }
        File dest = new File(destParent, experiment.getName());
        if (dest.exists()) {
            throw new IOException("Already exists: " + dest);
        }
        Files.createDirectories(destParent.toPath());
        Files.move(experiment.toPath(), dest.toPath());
        Files.deleteIfExists(new File(dest, MARKER).toPath());
        ProjectTemplates.initGitRepo(dest);
        return dest;
    }

    /**
     * Stops anything running there and deletes the tree. Refuses
     * directories without the marker - this method never becomes a
     * general-purpose rm -rf.
     */
    public static void discard(File experiment) throws IOException {
        if (!isExperiment(experiment)) {
            throw new IOException("Not an experiment: " + experiment);
        }
        RackService service = RackService.getDefault();
        Rack rack = service.getRack();
        boolean wasAimedHere = experiment.equals(rack.getProjectDir());
        if (wasAimedHere) {
            for (RackDevice d : rack.getDevices()) {
                d.panic();
            }
        }
        deleteTree(experiment.toPath());
        if (wasAimedHere) {
            // the v1.290.0 law, which this method missed for ~90 releases
            // while its learning-space sibling carried it (caught live in
            // the v2.36.1 walk: the Workbench showed the discarded
            // experiment's deleted path with "no toolchain yet"): an
            // aimed discard re-aims at the known-good home, AFTER the
            // delete (aiming first lets watchers and the patch autosave
            // race the removal), quietly (never back into the recents).
            service.openProjectQuietly(LearningSpace.fallbackWorkspace());
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
