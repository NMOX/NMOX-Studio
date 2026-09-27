package org.nmox.studio.ui.a11y;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.util.KeyboardAccess;
import org.nmox.studio.ui.irc.IrcKeyboardProbe;
import org.nmox.studio.ui.tasks.TasksKeyboardProbe;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Menus a keyboard can open (3.4, question 3). The census found menus
 * shown only from mouse listeners on components a keyboard never reached:
 * the IRC tree's Add/Edit/Delete Network (the only way to add a network)
 * and the Task Board's column menu (Rename, Move, Set WIP Limit, Delete
 * Column). Each now opens from Shift+F10 and the context-menu key, on the
 * same menu the right-click builds.
 */
class KeyboardMenusTest {

    @Test
    @DisplayName("IRC: Shift+F10 on the network tree opens the menu that adds a network")
    void ircTreeMenuFromTheKeyboard() {
        List<JPopupMenu> shown = new ArrayList<>();
        JTree tree = IrcKeyboardProbe.treeWithShower((menu, at) -> shown.add(menu));

        assertThat(KeyboardAccess.perform(tree, KeyboardAccess.SHIFT_F10)).isTrue();
        assertThat(KeyboardAccess.perform(tree, KeyboardAccess.CONTEXT_MENU)).isTrue();
        assertThat(shown).hasSize(2);
        assertThat(labels(shown.get(0))).as("nothing selected still offers Add Network")
                .contains(IrcKeyboardProbe.addNetworkLabel());
    }

    @Test
    @DisplayName("Task Board: a column header is a Tab stop with a visible ring and Shift+F10 opens its menu")
    void columnHeaderMenuFromTheKeyboard() {
        JPanel column = TasksKeyboardProbe.firstColumn();
        JLabel header = (JLabel) column.getComponent(0);
        assertThat(header.isFocusable()).isTrue();
        assertThat(header.getBorder()).isInstanceOf(KeyboardAccess.FocusRingBorder.class);
        assertThat(header.getComponentPopupMenu()).isNotNull();
        assertThat(KeyboardAccess.perform(header, KeyboardAccess.SHIFT_F10)).isTrue();
        assertThat(labels(header.getComponentPopupMenu())).isNotEmpty();
    }

    private static List<String> labels(JPopupMenu menu) {
        List<String> out = new ArrayList<>();
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem item) {
                out.add(item.getText());
            }
        }
        return out;
    }
}
