package org.nmox.studio.infra.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.nmox.studio.infra.model.InfraGraph;
import org.nmox.studio.infra.model.InfraGraph.InfraNode;
import org.nmox.studio.infra.model.NodeKind;

/**
 * The right-hand inspector: edit the selected node's label and
 * properties, see its monthly price and live id. Edits apply
 * immediately and nudge the graph so persistence and the cost total
 * follow along.
 */
public class PropertyPanel extends JPanel {

    private final InfraGraph graph;
    private final JPanel form = new JPanel(new GridBagLayout());
    private final JLabel header = new JLabel("No selection");
    private final JLabel costLabel = new JLabel(" ");
    private InfraNode current;

    public PropertyPanel(InfraGraph graph) {
        super(new BorderLayout());
        this.graph = graph;
        setPreferredSize(new Dimension(250, 400));
        setBackground(new Color(0x17, 0x17, 0x1B));

        header.setForeground(new Color(0xE6, 0xE7, 0xEB));
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        add(header, BorderLayout.NORTH);

        form.setBackground(getBackground());
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(getBackground());
        add(scroll, BorderLayout.CENTER);

        costLabel.setForeground(new Color(0x4E, 0xC9, 0x8B));
        costLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        costLabel.setBorder(BorderFactory.createEmptyBorder(8, 12, 10, 12));
        add(costLabel, BorderLayout.SOUTH);

        show(null);
    }

    public final void show(InfraNode node) {
        this.current = node;
        form.removeAll();
        if (node == null) {
            header.setText("No selection");
            costLabel.setText(" ");
        } else {
            header.setText(node.kind.getDisplayName());
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = 0;
            gc.anchor = GridBagConstraints.WEST;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.insets = new Insets(3, 12, 3, 12);

            addRow(gc, "Name", labelField(node));
            for (NodeKind.Prop prop : node.kind.getProps()) {
                addRow(gc, prop.label(), editorFor(node, prop));
            }
            if (node.doId != null) {
                JLabel live = new JLabel("live: " + node.doId);
                live.setForeground(new Color(0x4E, 0xC9, 0x8B));
                gc.gridwidth = 2;
                form.add(live, gc);
            }
            refreshCost();
        }
        form.revalidate();
        form.repaint();
    }

    private void addRow(GridBagConstraints gc, String label, javax.swing.JComponent editor) {
        JLabel l = new JLabel(label);
        l.setForeground(new Color(0x9A, 0x9D, 0xA4));
        gc.gridx = 0;
        gc.weightx = 0;
        form.add(l, gc);
        gc.gridx = 1;
        gc.weightx = 1;
        form.add(editor, gc);
        gc.gridy++;
    }

    private JTextField labelField(InfraNode node) {
        JTextField field = new JTextField(node.label);
        field.getDocument().addDocumentListener(new SimpleDocListener(() -> {
            node.label = field.getText().trim();
            graph.touch();
        }));
        return field;
    }

    private javax.swing.JComponent editorFor(InfraNode node, NodeKind.Prop prop) {
        String value = node.props.getOrDefault(prop.key(), prop.defaultValue());
        switch (prop.type()) {
            case "choice" -> {
                JComboBox<String> combo = new JComboBox<>(prop.choices().toArray(new String[0]));
                combo.setSelectedItem(value);
                combo.addActionListener(e -> {
                    node.props.put(prop.key(), String.valueOf(combo.getSelectedItem()));
                    graph.touch();
                    refreshCost();
                });
                return combo;
            }
            case "bool" -> {
                JCheckBox box = new JCheckBox("", Boolean.parseBoolean(value));
                box.setBackground(getBackground());
                box.addActionListener(e -> {
                    node.props.put(prop.key(), String.valueOf(box.isSelected()));
                    graph.touch();
                    refreshCost();
                });
                return box;
            }
            default -> {
                JTextField field = new JTextField(value);
                field.getDocument().addDocumentListener(new SimpleDocListener(() -> {
                    node.props.put(prop.key(), field.getText());
                    graph.touch();
                    refreshCost();
                }));
                return field;
            }
        }
    }

    private void refreshCost() {
        if (current != null) {
            costLabel.setText(String.format("≈ $%.2f/mo   (design: $%.2f/mo)",
                    current.monthlyUsd(), graph.totalMonthlyUsd()));
        }
    }

    /** A document listener that just runs a callback on any change. */
    private static final class SimpleDocListener implements javax.swing.event.DocumentListener {

        private final Runnable onChange;

        SimpleDocListener(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            onChange.run();
        }
    }
}
