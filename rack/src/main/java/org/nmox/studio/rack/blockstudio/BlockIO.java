package org.nmox.studio.rack.blockstudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.nmox.studio.core.util.AtomicFiles;

/**
 * Block Studio persistence: the block tree lives in
 * {@code .nmoxblocks.json} beside the project's other studio files, and
 * the generated component lands in {@code src/components/<tag>.js}.
 * Writes are atomic (temp sibling + move, the house law); the component
 * write is never-clobber — a file without the {@link BlockCodegen#MARKER}
 * first line was not ours and is refused.
 */
public final class BlockIO {

    public static final String WORKSPACE_FILE = ".nmoxblocks.json";

    private BlockIO() {
    }

    /** The workspace file for a project dir. */
    public static File workspaceFile(File projectDir) {
        return new File(projectDir, WORKSPACE_FILE);
    }

    /**
     * Loads the workspace (v1 single-doc files wrap as one component —
     * see {@link BlockWorkspace#fromJson}), or null when absent; a
     * corrupt file throws (caller keeps .bak).
     */
    public static BlockWorkspace load(File projectDir) throws IOException {
        File f = workspaceFile(projectDir);
        if (!f.isFile()) {
            return null;
        }
        return BlockWorkspace.fromJson(new JSONObject(Files.readString(f.toPath())));
    }

    public static void save(File projectDir, BlockWorkspace ws) throws IOException {
        AtomicFiles.writeString(workspaceFile(projectDir).toPath(), ws.toJson().toString(2) + "\n");
    }

    /** Where the generated component goes: src/components/&lt;tag&gt;.js. */
    public static File componentFile(File projectDir, String tag) {
        return new File(new File(new File(projectDir, "src"), "components"), tag + ".js");
    }

    /**
     * Writes the generated code. Refuses (returning false, writing
     * nothing) when the target exists without our marker — the studio
     * never clobbers a file it did not generate.
     */
    public static boolean writeComponent(File projectDir, String tag, String code)
            throws IOException {
        File target = componentFile(projectDir, tag);
        if (target.isFile()) {
            String first;
            try (var lines = Files.lines(target.toPath())) {
                first = lines.findFirst().orElse("");
            }
            if (!first.equals(BlockCodegen.MARKER)) {
                return false;
            }
        }
        Path path = target.toPath();
        Files.createDirectories(path.getParent());
        AtomicFiles.writeString(path, code);
        return true;
    }
}
