package org.nmox.studio.infra.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.TransferHandler;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.NodeKind;

/**
 * The node palette, Node-RED style: category-grouped entries shaped
 * like miniature nodes. Drag onto the canvas to place (or double-click).
 */
public class InfraPalette extends JPanel {

    /** List entries: a header (null kind) or a draggable node kind. */
    private record Entry(NodeKind kind, String header) {
    }

    public InfraPalette(InfraGraph graph) {
        super(new BorderLayout());
        setBackground(new Color(0x17, 0x17, 0x1B));
        setPreferredSize(new Dimension(190, 400));

        DefaultListModel<Entry> model = new DefaultListModel<>();
        NodeKind.Category last = null;
        for (NodeKind kind : NodeKind.values()) {
            if (kind.getCategory() != last) {
                last = kind.getCategory();
                model.addElement(new Entry(null, last.name()));
            }
            model.addElement(new Entry(kind, null));
        }

        JList<Entry> list = new JList<>(model);
        list.setBackground(getBackground());
        list.setCellRenderer(new Renderer());
        list.setDragEnabled(true);
        list.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                Entry entry = list.getSelectedValue();
                return entry == null || entry.kind() == null ? null
                        : new StringSelection(entry.kind().name());
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Entry entry = list.getSelectedValue();
                if (e.getClickCount() == 2 && entry != null && entry.kind() != null) {
                    graph.addNode(entry.kind(), 120 + (int) (Math.random() * 80),
                            80 + (int) (Math.random() * 120));
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(getBackground());
        add(scroll, BorderLayout.CENTER);
    }

    private static final class Renderer extends JPanel implements ListCellRenderer<Entry> {

        private Entry entry;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> list, Entry value,
                int index, boolean isSelected, boolean cellHasFocus) {
            this.entry = value;
            this.selected = isSelected;
            setPreferredSize(new Dimension(180, value.kind() == null ? 26 : 34));
            setToolTipText(value.kind() == null ? null
                    : value.kind().getDisplayName() + " — drag onto the canvas");
            return this;
        }

        @Override
        protected void paintComponent(Graphics gr) {
            Graphics2D g = (Graphics2D) gr.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(selected ? new Color(0x26, 0x26, 0x2D) : new Color(0x17, 0x17, 0x1B));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (entry.kind() == null) {
                g.setColor(new Color(0x7A, 0x7D, 0x85));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
                g.drawString(entry.header(), 10, getHeight() - 9);
            } else {
                Color base = entry.kind().getCategory().color;
                g.setColor(base);
                g.fillRoundRect(10, 4, getWidth() - 24, getHeight() - 9, 8, 8);
                g.setColor(base.darker());
                g.fillRoundRect(10, 4, 20, getHeight() - 9, 8, 8);
                g.setColor(new Color(0, 0, 0, 130));
                g.drawRoundRect(10, 4, getWidth() - 24, getHeight() - 9, 8, 8);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                g.drawString(entry.kind().getDisplayName(), 36, getHeight() / 2 + 4);
                // port nubs make it read as a node
                g.setColor(new Color(0xD6, 0xD7, 0xDB));
                g.fillRoundRect(6, getHeight() / 2 - 4, 7, 7, 2, 2);
                g.fillRoundRect(getWidth() - 13, getHeight() / 2 - 4, 7, 7, 2, 2);
            }
            g.dispose();
        }
    }
}
