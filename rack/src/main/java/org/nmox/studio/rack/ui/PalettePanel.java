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
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * The device shelf: every available device as a mini faceplate.
 * Drag one onto the rack to mount it (or double-click to add at the
 * bottom).
 */
public class PalettePanel extends JPanel {

    public PalettePanel(Rack rack) {
        super(new BorderLayout());
        setBackground(RackStyle.RACK_BG);

        JLabel header = new JLabel("DEVICE SHELF");
        header.setForeground(RackStyle.SILKSCREEN_DIM);
        header.setFont(RackStyle.LABEL_FONT);
        header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        add(header, BorderLayout.NORTH);

        DefaultListModel<DeviceType> model = new DefaultListModel<>();
        for (DeviceType t : DeviceType.values()) {
            model.addElement(t);
        }
        JList<DeviceType> list = new JList<>(model);
        list.setBackground(RackStyle.RACK_BG);
        list.setCellRenderer(new DeviceRenderer());
        list.setDragEnabled(true);
        list.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                DeviceType t = list.getSelectedValue();
                return t == null ? null : new StringSelection(t.getId());
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    DeviceType t = list.getSelectedValue();
                    if (t != null) {
                        rack.addDevice(t.create());
                    }
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(RackStyle.RACK_BG);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(228, 400));
    }

    private static final class DeviceRenderer extends JPanel implements ListCellRenderer<DeviceType> {

        private DeviceType type;
        private boolean selected;

        DeviceRenderer() {
            setPreferredSize(new Dimension(210, 52));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends DeviceType> list, DeviceType value,
                int index, boolean isSelected, boolean cellHasFocus) {
            this.type = value;
            this.selected = isSelected;
            setToolTipText(value.getDescription() + "  (drag onto the rack)");
            return this;
        }

        @Override
        protected void paintComponent(Graphics gr) {
            Graphics2D g = (Graphics2D) gr.create();
            RackStyle.antialias(g);
            int w = getWidth(), h = getHeight();
            g.setColor(selected ? new Color(48, 50, 56) : RackStyle.RACK_BG);
            g.fillRect(0, 0, w, h);
            // mini faceplate card
            g.setColor(RackStyle.FACE_BOTTOM);
            g.fillRoundRect(6, 4, w - 12, h - 8, 8, 8);
            g.setColor(type.getAccent());
            g.fillRoundRect(6, 4, 5, h - 8, 4, 4);
            g.setColor(new Color(0, 0, 0, 120));
            g.drawRoundRect(6, 4, w - 12, h - 8, 8, 8);

            g.setFont(RackStyle.LABEL_FONT);
            g.setColor(RackStyle.SILKSCREEN);
            g.drawString(type.getTitle(), 20, 22);
            g.setFont(RackStyle.TINY_FONT);
            g.setColor(RackStyle.SILKSCREEN_DIM);
            String desc = type.getDescription();
            int dash = desc.indexOf("—");
            g.drawString(dash > 0 ? desc.substring(dash + 1).trim() : desc, 20, 38);
            g.dispose();
        }
    }
}
