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
            File target = new File(dir, f.path());
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

    private static void writeMarker(File dir, LearningCatalog.Space space) throws IOException {
        Files.writeString(new File(dir, MARKER).toPath(),
                "slug=" + space.slug() + "\nname=" + space.name()
                        + "\ncreated=" + java.time.LocalDate.now() + "\n",
                StandardCharsets.UTF_8);
    }

    /** Prepends the OS-appropriate install hint to the tutorial. */
    static String tutorialWithInstall(LearningCatalog.Space space) {
        StringBuilder sb = new StringBuilder(space.tutorial());
        String hint = installHint(space);
        if (!hint.isBlank()) {
            sb.append("\n\n---\n\n## Install\n\nIf the tool isn't found when you press "
                    + "START, install it:\n\n```sh\n").append(hint).append("\n```\n");
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

    static String osKey() {
        return osKey(System.getProperty("os.name", ""));
    }

    /** mac/darwin → mac (checked first: "darwin" contains "win"), win → windows, else linux. */
    static String osKey(String osName) {
        String os = osName.toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return "mac";
        }
        return os.contains("win") ? "windows" : "linux";
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
