package org.nmox.studio.rack.sharing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.nmox.studio.core.util.PlainTables;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.rack.gallery.RackGallery;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;

/**
 * The Rack Gallery: every rack this install can mount, on one shelf — the
 * community racks that ship with the product, the built-in presets, the
 * starters, and the user's own — each with what it is for, what it needs and
 * how it is wired, BEFORE it mounts. Until v2.179.0 a preset was a name in a
 * popup menu with a tooltip; a rack is a workflow, and a workflow chosen by
 * name alone is chosen blind.
 *
 * <p>The window only chooses. What happens next — the replace question, the
 * manifest and dry run for a file, the mount — is the rack window's, through
 * the doors it already has; this returns what the reader picked.
 *
 * <p>Threads: the shelf is listed off the EDT (it reads the drop-in
 * directory) and so is each rack's tool lookup; both land newest-wins.
 */
@Messages({
    "RackGalleryDialog_title=Rack Gallery",
    "RackGalleryDialog_search=Find:",
    "RackGalleryDialog_searchHint=A job, a tool, a device or a project kind — “tests”, “cargo”, “lighthouse”",
    "RackGalleryDialog_listName=Racks",
    "RackGalleryDialog_detailName=About the selected rack",
    "RackGalleryDialog_loading=Reading the shelf…",
    "RackGalleryDialog_nothing=No rack matches. Clear the search to see the whole shelf.",
    "RackGalleryDialog_mount=Mount",
    "RackGalleryDialog_remove=Remove from My Racks…",
    "RackGalleryDialog_importFile=Import from File…",
    "RackGalleryDialog_importClipboard=Import from Clipboard",
    "RackGalleryDialog_close=Close"
})
public final class RackGalleryDialog {

    /** What the reader asked for. */
    public enum Action { MOUNT, REMOVE, IMPORT_FILE, IMPORT_CLIPBOARD }

    /** {@code entry} is null for the two import doors. */
    public record Result(Action action, RackGallery.Entry entry) {
    }

    private static final RequestProcessor SHELF_RP = new RequestProcessor("nmox-rack-gallery", 1);

    private RackGalleryDialog() {
    }

    /** EDT. Shows the gallery for the project the rack is aimed at; empty when closed. */
    public static Optional<Result> ask(File projectDir) {
        DefaultListModel<RackGallery.Entry> model = new DefaultListModel<>();
        JList<RackGallery.Entry> rackList = new JList<>(model);
        rackList.getAccessibleContext().setAccessibleName(Bundle.RackGalleryDialog_listName());
        rackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // a rack's name is its author's text: the renderer never parses markup
        rackList.setCellRenderer(PlainTables.plain(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof RackGallery.Entry entry) {
                    setText(PlainText.plain(GalleryText.row(entry)));
                }
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return this;
            }
        }));

        JTextArea detailArea = new JTextArea(Bundle.RackGalleryDialog_loading(), 18, 44);
        detailArea.getAccessibleContext().setAccessibleName(Bundle.RackGalleryDialog_detailName());
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JTextField searchField = new JTextField(24);
        searchField.setToolTipText(PlainText.plain(Bundle.RackGalleryDialog_searchHint()));
        JLabel searchLabel = new JLabel(Bundle.RackGalleryDialog_search());
        searchLabel.setLabelFor(searchField);

        JButton mountButton = new JButton(Bundle.RackGalleryDialog_mount());
        JButton removeButton = new JButton(Bundle.RackGalleryDialog_remove());
        mountButton.setEnabled(false);
        removeButton.setEnabled(false);

        // the whole shelf as listed; the list shows the part of it the search keeps
        java.util.concurrent.atomic.AtomicReference<List<RackGallery.Entry>> shelf =
                new java.util.concurrent.atomic.AtomicReference<>(List.of());
        Runnable refill = () -> {
            RackGallery.Entry kept = rackList.getSelectedValue();
            model.clear();
            for (RackGallery.Entry entry : RackGallery.filter(shelf.get(), searchField.getText())) {
                model.addElement(entry);
            }
            if (model.isEmpty()) {
                detailArea.setText(Bundle.RackGalleryDialog_nothing());
            } else if (kept != null && model.contains(kept)) {
                rackList.setSelectedValue(kept, true);
            } else {
                rackList.setSelectedIndex(0);
            }
        };
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refill.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refill.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refill.run();
            }
        });
        rackList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            RackGallery.Entry entry = rackList.getSelectedValue();
            mountButton.setEnabled(entry != null);
            removeButton.setEnabled(entry != null && entry.source() == RackGallery.Source.YOURS);
            if (entry == null) {
                return;
            }
            detailArea.setText(GalleryText.detail(entry, null));
            detailArea.setCaretPosition(0);
            if (!entry.card().requires().isEmpty()) {
                // a PATH lookup per tool: off the EDT, and only the rack still selected gets the answer
                SHELF_RP.post(() -> {
                    List<String> missing = RackGallery.missingTools(entry.card());
                    java.awt.EventQueue.invokeLater(() -> {
                        if (entry == rackList.getSelectedValue()) {
                            detailArea.setText(GalleryText.detail(entry, missing));
                            detailArea.setCaretPosition(0);
                        }
                    });
                });
            }
        });

        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        searchRow.add(searchLabel, BorderLayout.LINE_START);
        searchRow.add(searchField, BorderLayout.CENTER);
        JPanel left = new JPanel(new BorderLayout());
        left.add(searchRow, BorderLayout.PAGE_START);
        left.add(new JScrollPane(rackList), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(330, 440));
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setPreferredSize(new Dimension(470, 440));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, detailScroll);
        split.setResizeWeight(0.4);
        split.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        SHELF_RP.post(() -> {
            List<RackGallery.Entry> listed = RackGallery.entries(projectDir);
            java.awt.EventQueue.invokeLater(() -> {
                shelf.set(listed);
                refill.run();
            });
        });

        Object importFile = Bundle.RackGalleryDialog_importFile();
        Object importClipboard = Bundle.RackGalleryDialog_importClipboard();
        Object close = Bundle.RackGalleryDialog_close();
        DialogDescriptor descriptor = new DialogDescriptor(split, Bundle.RackGalleryDialog_title(), true,
                new Object[]{mountButton, removeButton, importFile, importClipboard, close},
                close, DialogDescriptor.DEFAULT_ALIGN, null, null);
        Object chosen = DialogDisplayer.getDefault().notify(descriptor);
        RackGallery.Entry selected = rackList.getSelectedValue();
        if (chosen == mountButton && selected != null) {
            return Optional.of(new Result(Action.MOUNT, selected));
        }
        if (chosen == removeButton && selected != null) {
            return Optional.of(new Result(Action.REMOVE, selected));
        }
        if (importFile.equals(chosen)) {
            return Optional.of(new Result(Action.IMPORT_FILE, null));
        }
        if (importClipboard.equals(chosen)) {
            return Optional.of(new Result(Action.IMPORT_CLIPBOARD, null));
        }
        return Optional.empty();
    }
}
