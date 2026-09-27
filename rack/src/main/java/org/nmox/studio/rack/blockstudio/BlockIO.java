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
        if (!f.exists()) {
            return null;
        }
        if (!f.isFile()) {
            throw new IOException(f.getName() + " is not a file");
        }
        // .nmoxblocks.json sits beside the project and travels with a clone
        String text = org.nmox.studio.core.util.BoundedReads.read(f.toPath());
        if (org.nmox.studio.core.util.MergeConflicts.hasMarkers(text)) {
            throw new ConflictedException(f.getName());
        }
        return BlockWorkspace.fromJson(new JSONObject(text));
    }

    /** The workspace file holds git's unresolved merge conflict (3.4). */
    public static final class ConflictedException extends IOException {
        private static final long serialVersionUID = 1L;

        ConflictedException(String name) {
            super(name + " holds an unresolved git merge conflict");
        }
    }

    /**
     * What the studio binds after a load (3.4): the workspace to edit, or
     * null with the reason the file may not be written; and a note to say
     * when a broken file was copied aside before a fresh workspace took its
     * place.
     */
    record Loaded(BlockWorkspace workspace, String lockedReason, String note) {
    }

    /**
     * Loads the project's workspace the way the studio binds it. Pure apart
     * from the one rescue copy, so every branch is a unit test:
     * <ul>
     *   <li>absent — a fresh workspace;</li>
     *   <li>git's unresolved conflict, pieces from a newer NMOX Studio, or
     *       bytes that could not be read — NO workspace: the file is left
     *       exactly as it is and the reason says nothing will be written over
     *       it. Until 3.4 each of these became a {@code .bak} plus a fresh
     *       workspace, and the next ordinary edit saved over the file;</li>
     *   <li>anything else unparseable — its bytes copied to a rescue sibling
     *       that never overwrites an earlier one, then a fresh workspace; when
     *       no copy can be written, no workspace (the file is the only copy).</li>
     * </ul>
     */
    static Loaded loadForStudio(File projectDir) {
        File f = workspaceFile(projectDir);
        try {
            BlockWorkspace ws = load(projectDir);
            return new Loaded(ws != null ? ws : new BlockWorkspace(), null, null);
        } catch (ConflictedException conflicted) {
            return new Loaded(null, Bundle.BlockStudioTopComponent_conflicted(WORKSPACE_FILE), null);
        } catch (BlockDoc.UnknownKindException newer) {
            return new Loaded(null, Bundle.BlockStudioTopComponent_newerFormat(WORKSPACE_FILE, newer.kind()), null);
        } catch (IOException unreadable) {
            return new Loaded(null, Bundle.BlockStudioTopComponent_unreadable(WORKSPACE_FILE), null);
        } catch (RuntimeException broken) {
            try {
                byte[] bytes;
                try (java.io.InputStream in = Files.newInputStream(f.toPath())) {
                    bytes = in.readNBytes((int) org.nmox.studio.core.util.BoundedReads.DEFAULT_MAX_BYTES);
                }
                String kept = org.nmox.studio.core.util.Backups.keep(f.toPath(), bytes)
                        .getFileName().toString();
                return new Loaded(new BlockWorkspace(), null, Bundle.BlockStudioTopComponent_readFailed(
                        WORKSPACE_FILE, broken.getMessage(), Bundle.BlockStudioTopComponent_keptCopyAs(kept)));
            } catch (IOException noCopy) {
                return new Loaded(null, Bundle.BlockStudioTopComponent_unrescued(WORKSPACE_FILE), null);
            }
        }
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
