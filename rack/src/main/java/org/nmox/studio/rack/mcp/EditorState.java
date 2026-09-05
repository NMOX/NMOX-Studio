package org.nmox.studio.rack.mcp;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * What the user has open in the editor (v2.78.0) — the Agent Port's
 * {@code editor_state}: the active file and every open editor tab, read
 * from the window registry on the EDT (the registry is Swing state) with
 * a bounded wait, so an HTTP thread never touches it directly and a busy
 * EDT answers "unavailable" instead of a lie. Files only, once each —
 * the Workbench's OPEN FILES rule (v1.279.0): a folder-backed editor tab
 * is not a file. Read-only by construction.
 */
final class EditorState {

    /** One open editor tab. */
    record OpenFile(String file, boolean modified, boolean active) {
    }

    private EditorState() {
    }

    /** The structured object — the single source of truth Texts renders. */
    static JSONObject editorState(String activeFile, List<OpenFile> open, String note) {
        JSONArray files = new JSONArray();
        for (OpenFile f : open) {
            files.put(new JSONObject()
                    .put("file", f.file())
                    .put("modified", f.modified())
                    .put("active", f.active()));
        }
        JSONObject o = new JSONObject()
                .put("activeFile", activeFile == null ? JSONObject.NULL : activeFile)
                .put("openCount", open.size())
                .put("openFiles", files);
        if (note != null && !note.isBlank()) {
            o.put("note", note);
        }
        return o;
    }

    /** The live state: read on the EDT, bounded; unavailable is said, not faked. */
    static JSONObject live() {
        FutureTask<List<OpenFile>> task = new FutureTask<>(EditorState::snapshotOnEdt);
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
        try {
            List<OpenFile> open = task.get(3, TimeUnit.SECONDS);
            String active = open.stream().filter(OpenFile::active).map(OpenFile::file).findFirst().orElse(null);
            return editorState(active, open, null);
        } catch (java.util.concurrent.TimeoutException slow) {
            task.cancel(false);
            return editorState(null, List.of(), "editor state unavailable: the IDE's event thread did not answer within 3 s");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return editorState(null, List.of(), "editor state unavailable: interrupted");
        } catch (java.util.concurrent.ExecutionException ex) {
            return editorState(null, List.of(), "editor state unavailable: " + ex.getCause());
        }
    }

    /** EDT only: the open editor tabs, files once each, the active one flagged. */
    private static List<OpenFile> snapshotOnEdt() {
        List<OpenFile> out = new ArrayList<>();
        TopComponent active = TopComponent.getRegistry().getActivated();
        Set<String> listed = new HashSet<>();
        for (TopComponent tc : TopComponent.getRegistry().getOpened()) {
            if (!WindowManager.getDefault().isOpenedEditorTopComponent(tc)) {
                continue;
            }
            DataObject dob = tc.getLookup().lookup(DataObject.class);
            if (dob == null || dob.getPrimaryFile().isFolder()) {
                continue;
            }
            File file = FileUtil.toFile(dob.getPrimaryFile());
            String path = file != null ? file.getAbsolutePath() : dob.getPrimaryFile().getPath();
            if (!listed.add(path)) {
                continue;
            }
            out.add(new OpenFile(path, dob.isModified(), tc == active));
        }
        return out;
    }
}
