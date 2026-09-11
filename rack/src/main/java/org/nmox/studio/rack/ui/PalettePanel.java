package org.nmox.studio.rack.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.TransferHandler;
import org.nmox.studio.rack.devices.DeviceCatalog;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * The device shelf: every available device as a mini faceplate.
 * Drag one onto the rack to mount it (or double-click to add at the
 * bottom).
 */
@org.openide.util.NbBundle.Messages({
    "PalettePanel_header=DEVICE SHELF",
    "PalettePanel_searchTooltip=Filter devices by name or task",
    "PalettePanel_searchPlaceholder=Search devices…",
    "PalettePanel_listName=Device shelf",
    "PalettePanel_addFailed=Could not add {0}: {1}",
    "PalettePanel_hint=<html>Drag a device onto the rack &middot; Tab flips the rack &middot; drag or click jacks to patch cables</html>",
    "PalettePanel_entryTooltip=<html><b>{0}</b> — {1}<br><i>{2}</i><br>(drag onto the rack; right-click a racked device for the full recipe)</html>"
})
public class PalettePanel extends JPanel {

    public PalettePanel(Rack rack) {
        super(new BorderLayout());
        setBackground(RackStyle.RACK_BG);

        JLabel header = new JLabel(Bundle.PalettePanel_header());
        header.setForeground(RackStyle.SILKSCREEN_DIM);
        header.setFont(RackStyle.LABEL_FONT);
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));

        javax.swing.JTextField search = new javax.swing.JTextField();
        search.setToolTipText(Bundle.PalettePanel_searchTooltip());
        search.putClientProperty("JTextField.placeholderText", Bundle.PalettePanel_searchPlaceholder());
        search.setBackground(new java.awt.Color(34, 34, 38));
        search.setForeground(RackStyle.SILKSCREEN);
        search.setCaretColor(RackStyle.SILKSCREEN);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(10, 10, 12)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JPanel north = new JPanel(new BorderLayout());
        north.setBackground(RackStyle.RACK_BG);
        north.add(header, BorderLayout.NORTH);
        north.add(search, BorderLayout.SOUTH);
        north.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        add(north, BorderLayout.NORTH);

        DefaultListModel<Object> model = new DefaultListModel<>();
        Runnable refilter = () -> {
            String query = search.getText().trim();
            model.clear();
            java.util.List<DeviceCatalog.Entry> catalog = DeviceCatalog.all();
            for (DeviceType.PaletteCategory category : DeviceType.PaletteCategory.values()) {
                java.util.List<DeviceCatalog.Entry> matches = new java.util.ArrayList<>();
                for (DeviceCatalog.Entry t : catalog) {
                    // Same matcher as Quick Search (v1.215.0), and the
                    // same vocabulary: typing "coverage" here shows
                    // VERITAS even though its shelf copy never says it.
                    if (t.category() == category
                            && (query.isEmpty()
                            || org.nmox.studio.core.search.SearchTerms.matches(query,
                                    t.title(), DeviceText.description(t), t.keywords(),
                                    category.label))) {
                        matches.add(t);
                    }
                }
                if (!matches.isEmpty()) {
                    model.addElement(category.label);
                    matches.forEach(model::addElement);
                }
            }
        };
        refilter.run();
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refilter.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refilter.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refilter.run();
            }
        });
        JList<Object> list = new JList<>(model);
        list.getAccessibleContext().setAccessibleName(Bundle.PalettePanel_listName());
        list.setBackground(RackStyle.RACK_BG);
        list.setCellRenderer(new DeviceRenderer());
        // setDragEnabled throws HeadlessException by spec; headless JVMs
        // (unit tests) have nothing to drag anyway — double-click still adds
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            list.setDragEnabled(true);
        }
        list.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                return list.getSelectedValue() instanceof DeviceCatalog.Entry t
                        ? new StringSelection(t.id()) : null;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (list.getSelectedValue() instanceof DeviceCatalog.Entry t) {
                        // a third-party device's build() (or a stale entry whose
                        // module was just uninstalled) can throw — one bad
                        // plugin must not blow up the palette, matching the
                        // drop path's guard
                        try {
                            rack.addDevice(t.create());
                        } catch (Exception | LinkageError ex) {
                            org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                                    Bundle.PalettePanel_addFailed(t.title(), String.valueOf(ex)));
                        }
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(RackStyle.RACK_BG);
        add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel(Bundle.PalettePanel_hint());
        hint.setForeground(RackStyle.SILKSCREEN_DIM);
        hint.setFont(RackStyle.TINY_FONT);
        hint.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        add(hint, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(228, 400));
    }

    private static final class DeviceRenderer extends JPanel implements ListCellRenderer<Object> {

        private DeviceCatalog.Entry type;
        private String headerText;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends Object> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            if (value instanceof DeviceCatalog.Entry t) {
                this.type = t;
                this.headerText = null;
                this.selected = isSelected;
                setPreferredSize(new Dimension(210, 52));
                String firstRecipeLine = t.usage().split("\\n")[0];
                setToolTipText(Bundle.PalettePanel_entryTooltip(t.title(), DeviceText.description(t), firstRecipeLine));
            } else {
                this.type = null;
                this.headerText = String.valueOf(value);
                this.selected = false;
                setPreferredSize(new Dimension(210, 24));
                setToolTipText(null);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics gr) {
            Graphics2D g = (Graphics2D) gr.create();
            RackStyle.antialias(g);
            int w = getWidth(), h = getHeight();
            g.setColor(selected ? new Color(48, 50, 56) : RackStyle.RACK_BG);
            g.fillRect(0, 0, w, h);
            if (headerText != null) {
                // section header: etched divider + caps label
                g.setColor(RackStyle.SILKSCREEN_DIM);
                g.setFont(RackStyle.TINY_FONT);
                String text = headerText.toUpperCase(java.util.Locale.ROOT);
                g.drawString(text, 10, h - 8);
                int tw = g.getFontMetrics().stringWidth(text);
                g.setColor(new Color(255, 255, 255, 26));
                g.drawLine(16 + tw, h - 11, w - 12, h - 11);
                g.dispose();
                return;
            }
            // mini faceplate card
            g.setColor(RackStyle.FACE_BOTTOM);
            g.fillRoundRect(6, 4, w - 12, h - 8, 8, 8);
            g.setColor(type.accent());
            g.fillRoundRect(6, 4, 5, h - 8, 4, 4);
            g.setColor(new Color(0, 0, 0, 120));
            g.drawRoundRect(6, 4, w - 12, h - 8, 8, 8);

            g.setFont(RackStyle.LABEL_FONT);
            g.setColor(RackStyle.SILKSCREEN);
            g.drawString(type.title(), 20, 22);
            g.setFont(RackStyle.TINY_FONT);
            g.setColor(RackStyle.SILKSCREEN_DIM);
            // fit to the card, never hard-clip: a painted line that simply
            // stops leaves the reader unable to tell a short description
            // from a cut one. German runs ~40% longer than English here and
            // the first German shelf photograph read "…wenn alle Spuren bes".
            // The budget gate keeps our own glosses short; this keeps a
            // drop-in device's authored words honest too.
            g.drawString(fitTo(g.getFontMetrics(), DeviceText.gloss(type), w - 32), 20, 38);
            g.dispose();
        }
    }

    /**
     * {@code text} if it fits in {@code room} pixels, else its head plus an
     * ellipsis that does fit — the v1.282.0 LCD rule, applied to the shelf.
     */
    static String fitTo(java.awt.FontMetrics fm, String text, int room) {
        if (fm.stringWidth(text) <= room) {
            return text;
        }
        String ellipsis = "\u2026";
        int budget = room - fm.stringWidth(ellipsis);
        if (budget <= 0) {
            return ellipsis;
        }
        int end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) > budget) {
            // step by code POINTS: a cut between surrogates is not a character
            end = text.offsetByCodePoints(end, -1);
        }
        return text.substring(0, end).stripTrailing() + ellipsis;
    }
}
