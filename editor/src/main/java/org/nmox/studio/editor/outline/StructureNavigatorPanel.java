package org.nmox.studio.editor.outline;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.netbeans.spi.navigator.NavigatorPanel;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;

/**
 * The Navigator window's structure view for every language NMOX lexes.
 * TextMate gives colour but no parse tree, so the outline comes from
 * {@link OutlineModel}'s heuristics; this panel is the thin, idiomatic
 * shell around it - a tree that mirrors the active file and jumps the
 * editor to a symbol when you click it.
 *
 * Registered for the mimes whose families OutlineModel actually knows
 * how to read; anything else keeps the platform's own navigator (Java,
 * XML) or simply shows nothing.
 */
@NavigatorPanel.Registrations({
    @NavigatorPanel.Registration(mimeType = "text/javascript", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/typescript", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-vue", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-svelte", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-astro", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/css", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-scss", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-less", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-markdown", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-json", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-yaml", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-toml", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-ini", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/html", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-python", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-rust", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-go", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-elixir", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-clojure", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-erlang", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-haskell", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-ocaml", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-r", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-perl", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-julia", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-fsharp", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-crystal", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-zig", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-solidity", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/coffeescript", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-java", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-kotlin", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-scala", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-csharp", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-swift", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-c", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-cpp", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-dart", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-groovy", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-php5", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/sh", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-graphql", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-sql", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-makefile", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-protobuf", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/markdown", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-nim", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-d", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-racket", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-elm", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-rescript", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-purescript", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-vlang", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-fortran", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-scheme", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-odin", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-haxe", position = 100, displayName = "#LBL_Structure"),
    @NavigatorPanel.Registration(mimeType = "text/x-janet", position = 100, displayName = "#LBL_Structure")
})
public final class StructureNavigatorPanel implements NavigatorPanel {

    private static final RequestProcessor RP = new RequestProcessor("nmox-outline", 1, true);

    private JComponent component;
    private JTree tree;
    private JLabel empty;
    private JScrollPane scroll;

    private Lookup.Result<DataObject> result;
    private final org.openide.util.LookupListener contextListener = ev -> refreshFromContext();
    private DataObject current;
    private Document document;
    private final DocumentListener docListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { scheduleRebuild(); }
        @Override public void removeUpdate(DocumentEvent e) { scheduleRebuild(); }
        @Override public void changedUpdate(DocumentEvent e) { }
    };
    private Timer debounce;

    @Override
    public String getDisplayName() {
        return "Structure";
    }

    @Override
    public String getDisplayHint() {
        return "Outline of the current file";
    }

    @Override
    public JComponent getComponent() {
        if (component == null) {
            tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
            tree.setRootVisible(false);
            tree.setShowsRootHandles(true);
            tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            tree.setCellRenderer(new OutlineCellRenderer());
            tree.addTreeSelectionListener(e -> navigateToSelection());

            empty = new JLabel("No structure to show", JLabel.CENTER);
            empty.setEnabled(false);
            empty.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            scroll = new JScrollPane(tree);
            scroll.setBorder(BorderFactory.createEmptyBorder());

            component = new JPanel(new BorderLayout());
            component.add(scroll, BorderLayout.CENTER);

            debounce = new Timer(300, e -> rebuild());
            debounce.setRepeats(false);
        }
        return component;
    }

    @Override
    public void panelActivated(Lookup context) {
        getComponent();
        result = context.lookupResult(DataObject.class);
        result.addLookupListener(contextListener);
        refreshFromContext();
    }

    @Override
    public void panelDeactivated() {
        if (result != null) {
            result.removeLookupListener(contextListener);
            result = null;
        }
        detachDocument();
        current = null;
        if (debounce != null) {
            debounce.stop();
        }
    }

    @Override
    public Lookup getLookup() {
        return Lookup.EMPTY;
    }

    private void refreshFromContext() {
        DataObject dob = result == null ? null
                : result.allInstances().stream().findFirst().orElse(null);
        if (dob == current) {
            return;
        }
        detachDocument();
        current = dob;
        if (dob == null) {
            showEmpty();
            return;
        }
        EditorCookie ec = dob.getLookup().lookup(EditorCookie.class);
        document = ec == null ? null : ec.getDocument();
        if (document != null) {
            document.addDocumentListener(docListener);
        }
        rebuild();
    }

    private void detachDocument() {
        if (document != null) {
            document.removeDocumentListener(docListener);
            document = null;
        }
    }

    private void scheduleRebuild() {
        if (debounce != null) {
            debounce.restart();
        }
    }

    /** Reads the document off the EDT, parses, then swaps the tree in on the EDT. */
    private void rebuild() {
        final DataObject dob = current;
        final Document doc = document;
        if (dob == null) {
            showEmpty();
            return;
        }
        final String mime = mimeOf(dob);
        RP.post(() -> {
            final String text = textOf(doc);
            final java.util.List<OutlineModel.Item> items =
                    text == null ? java.util.List.of() : OutlineModel.extract(mime, text);
            SwingUtilities.invokeLater(() -> {
                if (dob != current) {
                    return; // context moved on while we parsed
                }
                applyItems(items);
            });
        });
    }

    private void applyItems(java.util.List<OutlineModel.Item> items) {
        if (items.isEmpty()) {
            showEmpty();
            return;
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        java.util.Deque<DefaultMutableTreeNode> stack = new java.util.ArrayDeque<>();
        java.util.Deque<Integer> depths = new java.util.ArrayDeque<>();
        stack.push(root);
        depths.push(-1);
        for (OutlineModel.Item item : items) {
            while (depths.size() > 1 && depths.peek() >= item.depth()) {
                stack.pop();
                depths.pop();
            }
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(item);
            stack.peek().add(node);
            stack.push(node);
            depths.push(item.depth());
        }
        tree.setModel(new DefaultTreeModel(root));
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        if (component.getComponent(0) != scroll) {
            component.removeAll();
            component.add(scroll, BorderLayout.CENTER);
            component.revalidate();
            component.repaint();
        }
    }

    private void showEmpty() {
        if (component != null && (component.getComponentCount() == 0
                || component.getComponent(0) != empty)) {
            component.removeAll();
            component.add(empty, BorderLayout.CENTER);
            component.revalidate();
            component.repaint();
        }
    }

    private void navigateToSelection() {
        TreePath path = tree.getSelectionPath();
        if (path == null || current == null) {
            return;
        }
        Object obj = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(obj instanceof OutlineModel.Item item)) {
            return;
        }
        LineCookie lc = current.getLookup().lookup(LineCookie.class);
        if (lc == null) {
            return;
        }
        try {
            Line line = lc.getLineSet().getCurrent(item.line());
            line.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
        } catch (IndexOutOfBoundsException ignored) {
            // the document shrank under us between parse and click
        }
    }

    private static String mimeOf(DataObject dob) {
        FileObject fo = dob.getPrimaryFile();
        return fo == null ? null : fo.getMIMEType();
    }

    private static String textOf(Document doc) {
        if (doc == null) {
            return null;
        }
        final String[] holder = new String[1];
        doc.render(() -> {
            try {
                holder[0] = doc.getText(0, doc.getLength());
            } catch (BadLocationException ex) {
                holder[0] = null;
            }
        });
        return holder[0];
    }

    /** Phosphor-badged renderer: a kind-coloured glyph, the name, and a
     * dim detail (heading level, sql object kind) when present. */
    private static final class OutlineCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object value, boolean sel,
                boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(t, value, sel, expanded, leaf, row, focus);
            Object obj = ((DefaultMutableTreeNode) value).getUserObject();
            if (obj instanceof OutlineModel.Item item) {
                setIcon(new BadgeIcon(item.kind()));
                if (item.detail() != null) {
                    setText("<html>" + escape(item.name())
                            + " <font color='#7a7a7a'>" + escape(item.detail()) + "</font></html>");
                } else {
                    setText(item.name());
                }
            }
            return this;
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /** A small rounded badge bearing the kind's glyph in its colour. */
    private static final class BadgeIcon implements Icon {

        private static final int SIZE = 14;
        private final OutlineKind kind;

        BadgeIcon(OutlineKind kind) {
            this.kind = kind;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = kind.color();
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 48));
            g2.fillRoundRect(x, y, SIZE, SIZE, 5, 5);
            g2.setColor(base);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            String s = String.valueOf(kind.glyph());
            int sw = g2.getFontMetrics().stringWidth(s);
            int ascent = g2.getFontMetrics().getAscent();
            g2.drawString(s, x + (SIZE - sw) / 2, y + ascent
                    + (SIZE - g2.getFontMetrics().getHeight()) / 2);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return SIZE + 4;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
