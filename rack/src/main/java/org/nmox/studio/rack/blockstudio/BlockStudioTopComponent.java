package org.nmox.studio.rack.blockstudio;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.text.DefaultHighlighter;
import org.json.JSONObject;
import org.netbeans.api.settings.ConvertAsProperties;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.service.RackService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * Block Studio: compose a web component from interlocking pieces, watch
 * the real code appear beside them, and click either side to see the
 * mapping — the canvas is the controller, the code pane the truthful
 * projection ({@link BlockCodegen} keeps a per-block character range).
 *
 * <p>House laws held: nothing happens at boot (all work rides
 * componentShowing), file IO on a named RequestProcessor, atomic writes
 * ({@link BlockIO}), listener add/remove symmetric across open/close,
 * the generated-file write is never-clobber, and every control carries
 * an accessible name. ⌘Z undoes structural edits via JSON snapshots.
 */
@ConvertAsProperties(dtd = "-//org.nmox.studio.rack.blockstudio//BlockStudio//EN", autostore = false)
@TopComponent.Description(
        preferredID = "BlockStudioTopComponent",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS)
@TopComponent.Registration(mode = "editor", openAtStartup = false, position = 76)
@ActionID(category = "Window", id = "org.nmox.studio.rack.blockstudio.BlockStudioTopComponent")
@org.openide.awt.ActionReferences({
    @ActionReference(path = "Menu/Window", position = 259),
    // The chord rides Shortcuts/ for the binding; the Window-menu
    // accelerator comes from the Keymaps shadow in ui/layer.xml (rack
    // windows host their shadows there — the v1.38.1/debt-28 split).
    // WindowShortcutsTest pins chord, label and shadow together.
    @ActionReference(path = "Shortcuts", name = "DA-5")
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_BlockStudioAction",
        preferredID = "BlockStudioTopComponent")
@NbBundle.Messages({
    "CTL_BlockStudioAction=Block Studio  ⌥⌘5",
    "CTL_BlockStudioTopComponent=Block Studio",
    "HINT_BlockStudioTopComponent=Compose web components from interlocking pieces"
})
public final class BlockStudioTopComponent extends TopComponent {

    private static final RequestProcessor RP = new RequestProcessor("Block Studio", 1);

    /** Test seam: block until every queued IO-lane task has run. */
    static void drainIoLane() {
        RP.post(() -> { }).waitFinished();
    }

    /** Test seam: the canvas's current doc (EDT-confined in production). */
    BlockDoc currentDoc() {
        return canvasDoc();
    }
    private static final int DEBOUNCE_MS = 150;
    private static final int UNDO_CAP = 100;

    private final BlockCanvas canvas;

    private BlockDoc canvasDoc() {
        return canvas.doc();
    }
    private final JEditorPane codePane = new JEditorPane();
    private final JLabel status = new JLabel(" ");
    private final Deque<String> undo = new ArrayDeque<>();
    private final Timer regen = new Timer(DEBOUNCE_MS, e -> regenerate());
    private final Timer saver = new Timer(800, e -> persist());
    private final Rack.Listener rackListener = new Rack.Listener() {
        @Override
        public void projectChanged() {
            SwingUtilities.invokeLater(BlockStudioTopComponent.this::loadForAim);
        }
    };

    private final org.nmox.studio.core.util.SelfWriteTracker selfWrites =
            new org.nmox.studio.core.util.SelfWriteTracker();
    private BlockFilePulse pulse;
    private final BlockPreviewServer preview;
    private javax.swing.JButton previewBtn;
    /** Test seam: headless tests keep the platform browser shut. */
    boolean openBrowser = true;

    private File projectDir;
    private volatile BlockCodegen.Result lastResult;
    private boolean loading;
    private boolean shownOnce;

    public BlockStudioTopComponent() {
        setName(Bundle.CTL_BlockStudioTopComponent());
        setToolTipText(Bundle.HINT_BlockStudioTopComponent());
        setLayout(new BorderLayout());
        regen.setRepeats(false);
        saver.setRepeats(false);

        canvas = new BlockCanvas(new BlockCanvas.Host() {
            @Override
            public void aboutToChange() {
                pushUndo();
            }

            @Override
            public void changed() {
                canvas.refresh();
                regen.restart();
                saver.restart();
            }

            @Override
            public void selected(Block block) {
                highlight(block);
            }

            @Override
            public void editParams(Block block) {
                editParamsDialog(block);
            }
        });

        // palette: every kind but the root, draggable by name
        JList<BlockKind> palette = new JList<>(java.util.Arrays.stream(BlockKind.values())
                .filter(k -> k != BlockKind.COMPONENT).toArray(BlockKind[]::new));
        palette.getAccessibleContext().setAccessibleName("Block palette");
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            // setDragEnabled needs a real toolkit; headless tests
            // instantiate the studio without one (the house idiom)
            palette.setDragEnabled(true);
        }
        palette.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(javax.swing.JComponent c) {
                return COPY;
            }

            @Override
            protected java.awt.datatransfer.Transferable createTransferable(javax.swing.JComponent c) {
                BlockKind kind = palette.getSelectedValue();
                return kind == null ? null : new StringSelection(kind.name());
            }
        });
        palette.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focus);
                BlockKind k = (BlockKind) value;
                l.setText(k.display());
                l.setIcon(new javax.swing.Icon() {
                    @Override
                    public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
                        g.setColor(BlockCanvas.fill(k.category()));
                        g.fillRoundRect(x, y, 12, 12, 4, 4);
                    }

                    @Override
                    public int getIconWidth() {
                        return 14;
                    }

                    @Override
                    public int getIconHeight() {
                        return 12;
                    }
                });
                return l;
            }
        });

        codePane.setEditable(false);
        codePane.setEditorKit(CloneableEditorSupport.getEditorKit("text/javascript"));
        codePane.getAccessibleContext().setAccessibleName("Generated component code");

        JButton undoBtn = new JButton(new AbstractAction("Undo") {
            @Override
            public void actionPerformed(ActionEvent e) {
                popUndo();
            }
        });
        undoBtn.getAccessibleContext().setAccessibleName("Undo block edit");
        JButton openBtn = new JButton(new AbstractAction("Open Component\u2026") {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openComponent();
            }
        });
        openBtn.getAccessibleContext().setAccessibleName("Open Component");
        JButton saveBtn = new JButton(new AbstractAction("Save Component") {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveComponent();
            }
        });
        saveBtn.getAccessibleContext().setAccessibleName("Save component to project");
        saveBtn.setToolTipText("Writes src/components/<tag>.js into the aimed project"
                + " — refuses files Block Studio did not generate");

        preview = new BlockPreviewServer(
                () -> {
                    // live tag, but never an unvalidated one: mid-edit an
                    // illegal tag would land raw inside the harness's JS
                    // string literal — degrade to the last generated tag
                    BlockDoc d = canvas.doc();
                    String live = d == null ? "" : d.root().param("tag");
                    if (BlockCodegen.validTag(live)) {
                        return live;
                    }
                    BlockCodegen.Result r = lastResult;
                    return r != null ? BlockParser.tagOf(r.code()) : "my-widget";
                },
                () -> {
                    BlockCodegen.Result r = lastResult;
                    return r == null ? "// fix the blocks first" : r.code();
                });
        previewBtn = new JButton(new AbstractAction("Preview") {
            @Override
            public void actionPerformed(ActionEvent e) {
                togglePreview();
            }
        });
        previewBtn.getAccessibleContext().setAccessibleName("Toggle live preview");
        previewBtn.setToolTipText("Serves the component on localhost (in-memory, refresh"
                + " after edits) and opens the browser; Stop deregisters the serving");

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(undoBtn);
        toolbar.add(openBtn);
        toolbar.add(saveBtn);
        toolbar.add(previewBtn);
        toolbar.add(status);
        status.getAccessibleContext().setAccessibleName("Block Studio status");

        JScrollPane paletteScroll = new JScrollPane(palette);
        paletteScroll.setPreferredSize(new Dimension(150, 100));
        paletteScroll.setBorder(BorderFactory.createTitledBorder("Pieces"));
        JScrollPane canvasScroll = new JScrollPane(canvas);
        canvasScroll.setBorder(BorderFactory.createTitledBorder("Canvas"));
        JScrollPane codeScroll = new JScrollPane(codePane);
        codeScroll.setBorder(BorderFactory.createTitledBorder("Generated code — click a piece to locate it"));

        JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasScroll, codeScroll);
        right.setResizeWeight(0.45);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, paletteScroll, right);
        main.setResizeWeight(0);
        add(toolbar, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);

        // menu-shortcut mask needs a real toolkit; headless tests skip
        // the binding (the button and popUndo() still cover the path)
        int shortcutMask = java.awt.GraphicsEnvironment.isHeadless()
                ? KeyEvent.CTRL_DOWN_MASK
                : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask), "block-undo");
        getActionMap().put("block-undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                popUndo();
            }
        });
    }

    // ---- lifecycle (symmetry: add in opened, remove in closed) ----

    @Override
    public void componentOpened() {
        RackService.getDefault().getRack().addListener(rackListener);
    }

    @Override
    public void componentClosed() {
        RackService.getDefault().getRack().removeListener(rackListener);
        stopPulse();
        stopPreview();
        // reopen must reload: while closed the rack listener is gone, so
        // any re-aim was missed and the pulse is stopped — the inverted-
        // lifecycle class the v1.36 review fixed in Infra Designer
        shownOnce = false;
    }

    @Override
    protected void componentShowing() {
        if (!shownOnce) {
            shownOnce = true;
            loadForAim();
        }
    }

    // ---- load/save (IO on RP, apply on EDT) ----

    private void loadForAim() {
        // the OLD aim's debounced save must land before the doc is swapped
        // (the v1.35.1 API Studio law: force-save old, then load new)
        if (saver.isRunning()) {
            saver.stop();
            persist();
        }
        File dir = RackService.getDefault().getRack().getProjectDir();
        projectDir = dir;
        restartPulse(dir);
        stopPreview(); // a re-aim serves the OLD component — never lie
        // never let Preview or ⌘I serve the previous aim's snapshot while
        // (or after) the new doc loads
        lastResult = null;
        org.nmox.studio.rack.blockstudio.search.BlockSearchProvider.clear();
        if (dir == null) {
            canvas.setDoc(null);
            codePane.setText("");
            setStatus("Aim a project to start composing");
            return;
        }
        loading = true;
        RP.post(() -> {
            BlockDoc loaded;
            try {
                loaded = BlockIO.load(dir);
            } catch (IOException | RuntimeException ex) {
                loaded = null;
                // keep the unreadable file as .bak BEFORE the fresh doc's
                // first debounced save can overwrite it (the v1.39 house
                // law, and this file's own javadoc promise)
                String note = "";
                try {
                    File broken = BlockIO.workspaceFile(dir);
                    java.nio.file.Files.copy(broken.toPath(),
                            broken.toPath().resolveSibling(BlockIO.WORKSPACE_FILE + ".bak"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    note = " (kept a copy at " + BlockIO.WORKSPACE_FILE + ".bak)";
                } catch (IOException unbacked) {
                    // best effort — the status still names the read failure
                }
                String suffix = note;
                SwingUtilities.invokeLater(() -> setStatus(
                        "Could not read " + BlockIO.WORKSPACE_FILE + ": "
                        + ex.getMessage() + suffix));
            }
            BlockDoc doc = loaded != null ? loaded : new BlockDoc();
            SwingUtilities.invokeLater(() -> {
                undo.clear();
                canvas.setDoc(doc);
                loading = false;
                regenerate();
            });
        });
    }

    private void persist() {
        File dir = projectDir;
        BlockDoc doc = canvas.doc();
        if (dir == null || doc == null || loading) {
            return;
        }
        JSONObject json = doc.toJson();
        RP.post(() -> {
            try {
                org.nmox.studio.core.util.AtomicFiles.writeString(
                        BlockIO.workspaceFile(dir).toPath(), json.toString(2) + "\n");
                // stamp our own write so the pulse can tell it from a
                // foreign edit (the v1.35 self-write-discrimination law)
                selfWrites.noteSync(BlockIO.workspaceFile(dir));
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> setStatus("Save failed: " + ex.getMessage()));
            }
        });
    }

    // ---- external-edit pulse (the studio law v1 deferred) ----

    private void restartPulse(File dir) {
        stopPulse();
        if (dir == null) {
            return;
        }
        pulse = new BlockFilePulse(BlockIO.workspaceFile(dir), this::onWorkspaceFileChanged);
        pulse.start(BlockFilePulse.DEFAULT_INTERVAL_MS);
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.stop();
            pulse = null;
        }
    }

    /**
     * Pulse-thread callback for a {@code .nmoxblocks.json} stamp change.
     * Our own atomic saves are stamped and ignored; a foreign edit
     * reloads the canvas — unless a debounced in-studio save is still
     * pending, in which case the studio's newer intent wins and the
     * override is said out loud instead of silently clobbering either
     * side. Package-private: tests drive it deterministically.
     */
    void onWorkspaceFileChanged(long mtime, long size) {
        if (!selfWrites.isForeign(mtime, size)) {
            return;
        }
        // lane-ordered re-check: our write+stamp pair rides RP, so a pulse
        // tick that lands BETWEEN the atomic move and the stamp classifies
        // our own save as foreign — by the time this posted task runs, any
        // in-flight save has stamped, and a re-stat tells the truth
        RP.post(() -> {
            File dir = projectDir;
            if (dir != null) {
                File f = BlockIO.workspaceFile(dir);
                if (f.isFile() && !selfWrites.isForeign(f.lastModified(), f.length())) {
                    return;
                }
            }
            onForeignEdit();
        });
    }

    private void onForeignEdit() {
        SwingUtilities.invokeLater(() -> {
            if (saver.isRunning()) {
                setStatus("External edit to " + BlockIO.WORKSPACE_FILE
                        + " overridden by newer studio edits");
                return;
            }
            loadForAim();
            setStatus("Reloaded — " + BlockIO.WORKSPACE_FILE + " changed on disk");
        });
    }

    // ---- live preview (v1.80.0): in-memory serve + registry truth ----

    private void togglePreview() {
        if (preview.running()) {
            stopPreview();
            return;
        }
        if (lastResult == null) {
            setStatus("Fix the blocks first — nothing valid to preview");
            return;
        }
        try {
            String url = preview.start();
            org.nmox.studio.rack.service.ServingRegistry.getDefault().register(
                    new org.nmox.studio.rack.service.ServingRegistry.Serving(
                            "block-preview", "BLOCK PREVIEW", url,
                            org.nmox.studio.rack.service.ServingRegistry.Kind.WEB, projectDir));
            previewBtn.setText("Stop Preview");
            setStatus("Previewing at " + url + " — refresh the browser after edits");
            if (openBrowser) {
                try {
                    org.openide.awt.HtmlBrowser.URLDisplayer.getDefault()
                            .showURL(java.net.URI.create(url).toURL());
                } catch (RuntimeException | java.net.MalformedURLException browserless) {
                    // headless / no browser configured: the URL is on the status line
                }
            }
        } catch (IOException ex) {
            setStatus("Preview failed to start: " + ex.getMessage());
        }
    }

    void stopPreview() {
        if (preview.running()) {
            preview.stop();
            org.nmox.studio.rack.service.ServingRegistry.getDefault().deregister("block-preview");
            if (previewBtn != null) {
                previewBtn.setText("Preview");
            }
            setStatus("Preview stopped");
        }
    }

    // ---- undo (JSON snapshots) ----

    private void pushUndo() {
        BlockDoc doc = canvas.doc();
        if (doc != null) {
            if (undo.size() >= UNDO_CAP) {
                undo.removeLast();
            }
            undo.push(doc.toJson().toString());
        }
    }

    private void popUndo() {
        if (undo.isEmpty()) {
            setStatus("Nothing to undo");
            return;
        }
        canvas.setDoc(BlockDoc.fromJson(new JSONObject(undo.pop())));
        regen.restart();
        saver.restart();
    }

    // ---- code pane: the projection + the mapping ----

    private void regenerate() {
        BlockDoc doc = canvas.doc();
        if (doc == null) {
            return;
        }
        List<String> problems = BlockCodegen.validate(doc);
        if (!problems.isEmpty()) {
            lastResult = null;
            StringBuilder sb = new StringBuilder("// Fix these to generate:\n");
            problems.forEach(p -> sb.append("//  - ").append(p).append('\n'));
            codePane.setText(sb.toString());
            setStatus(problems.get(0));
            return;
        }
        lastResult = BlockCodegen.generate(doc);
        codePane.setText(lastResult.code());
        codePane.setCaretPosition(0);
        setStatus(doc.preorder().size() + " pieces → " + doc.root().param("tag") + ".js");
        org.nmox.studio.rack.blockstudio.search.BlockSearchProvider
                .publish(doc.root().param("tag"), doc.preorder().size());
        highlight(canvas.selectedId() == null ? null : doc.find(canvas.selectedId()));
    }

    private void highlight(Block block) {
        codePane.getHighlighter().removeAllHighlights();
        if (block == null || lastResult == null) {
            return;
        }
        int[] range = lastResult.ranges().get(block.id());
        // SET_ATTR/STYLE render inside their parent's open tag and record
        // no range of their own — walk up to the nearest ranged ancestor
        // so clicking them still lights the right lines
        Block cursor = block;
        while (range == null && cursor != null) {
            BlockDoc doc = canvas.doc();
            cursor = doc == null ? null : doc.parentOf(cursor.id());
            if (cursor != null) {
                range = lastResult.ranges().get(cursor.id());
            }
        }
        if (range == null) {
            return;
        }
        try {
            codePane.getHighlighter().addHighlight(range[0], Math.min(range[1],
                    codePane.getDocument().getLength()),
                    new DefaultHighlighter.DefaultHighlightPainter(new Color(90, 110, 160, 90)));
            codePane.setCaretPosition(range[0]);
        } catch (javax.swing.text.BadLocationException ex) {
            // stale range during a rebuild tick: the next regenerate re-highlights
        }
    }

    // ---- params ----

    private void editParamsDialog(Block block) {
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 4));
        java.util.Map<String, JTextField> fields = new java.util.LinkedHashMap<>();
        for (BlockKind.Param p : block.kind().params()) {
            form.add(new JLabel(p.key()));
            JTextField field = new JTextField(block.param(p.key()), 18);
            field.getAccessibleContext().setAccessibleName(block.kind().display() + " " + p.key());
            fields.put(p.key(), field);
            form.add(field);
        }
        DialogDescriptor dd = new DialogDescriptor(form, block.kind().display());
        if (DialogDisplayer.getDefault().notify(dd) == NotifyDescriptor.OK_OPTION) {
            pushUndo();
            fields.forEach((k, f) -> block.setParam(k, f.getText().trim()));
            canvas.refresh();
            regen.restart();
            saver.restart();
        }
    }

    // ---- save to project ----

    private void saveComponent() {
        BlockDoc doc = canvas.doc();
        File dir = projectDir;
        if (doc == null || dir == null) {
            setStatus("Aim a project first");
            return;
        }
        List<String> problems = BlockCodegen.validate(doc);
        if (!problems.isEmpty()) {
            setStatus(problems.get(0));
            return;
        }
        String tag = doc.root().param("tag");
        String code = BlockCodegen.generate(doc).code();
        RP.post(() -> {
            String message;
            try {
                boolean ok = BlockIO.writeComponent(dir, tag, code);
                message = ok
                        ? "Saved src/components/" + tag + ".js"
                        : "Refused: src/components/" + tag + ".js was not generated by Block Studio";
                if (!ok) {
                    DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                            "src/components/" + tag + ".js exists but was not generated by "
                            + "Block Studio — it will not be overwritten. Rename your component "
                            + "or move the file.", NotifyDescriptor.WARNING_MESSAGE));
                }
            } catch (IOException ex) {
                message = "Save failed: " + ex.getMessage();
            }
            String finalMessage = message;
            SwingUtilities.invokeLater(() -> setStatus(finalMessage));
        });
    }

    /**
     * The reverse trip (v1.81.0): pick a Block-Studio-generated file and
     * load it back onto the canvas. Chooser on the EDT, read + parse on
     * the IO lane, doc swap back on the EDT with an undo snapshot. An
     * off-dialect file refuses with the parser's line-numbered message —
     * never a half-import.
     */
    private void openComponent() {
        File dir = projectDir;
        if (dir == null) {
            setStatus("Aim a project first");
            return;
        }
        File components = new File(dir, "src/components");
        File picked = new org.openide.filesystems.FileChooserBuilder(BlockStudioTopComponent.class)
                .setTitle("Open Component")
                .setDefaultWorkingDirectory(components.isDirectory() ? components : dir)
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Block Studio components (*.js)", "js"))
                .setFilesOnly(true)
                .showOpenDialog();
        if (picked == null) {
            return;
        }
        RP.post(() -> {
            try {
                String code = java.nio.file.Files.readString(picked.toPath());
                BlockDoc parsed = BlockParser.parse(code);
                // a parseable file can still fail generation (hand-edited
                // exprs); refuse BEFORE touching the canvas, not after
                List<String> problems = BlockCodegen.validate(parsed);
                if (!problems.isEmpty()) {
                    throw new BlockParser.ParseException(problems.get(0));
                }
                SwingUtilities.invokeLater(() -> {
                    pushUndo();
                    canvas.setDoc(parsed);
                    regenerate();
                    persist();
                    setStatus("Opened " + picked.getName() + " — "
                            + (parsed.preorder().size()) + " pieces");
                });
            } catch (BlockParser.ParseException ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus("Not a Block Studio component: " + ex.getMessage());
                    DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                            picked.getName() + " is not in the Block Studio dialect \u2014 "
                            + ex.getMessage() + ". Only files generated by Block Studio "
                            + "(or edited within its shapes) can be opened as blocks.",
                            NotifyDescriptor.WARNING_MESSAGE));
                });
            } catch (IOException | RuntimeException ex) {
                SwingUtilities.invokeLater(() ->
                        setStatus("Open failed: " + ex.getMessage()));
            }
        });
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    // ---- @ConvertAsProperties plumbing ----

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
    }
}
