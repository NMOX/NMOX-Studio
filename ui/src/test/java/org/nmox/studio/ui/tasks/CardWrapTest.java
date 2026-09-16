package org.nmox.studio.ui.tasks;

import java.awt.Component;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardWrapTest {

    private static int heightAt(String title, int columnWidth) {
        DefaultListModel<TaskBoard.Card> model = new DefaultListModel<>();
        TaskBoard.Card card = new TaskBoard.Card("id", title, "", 0L);
        model.addElement(card);
        JList<TaskBoard.Card> list = new JList<>(model);
        JScrollPane scroll = new JScrollPane(list);
        scroll.getViewport().setSize(columnWidth, 400);
        Component c = new TasksTopComponent.CardRenderer()
                .getListCellRendererComponent(list, card, 0, false, false);
        return c.getPreferredSize().height;
    }

    @Test
    @DisplayName("a card longer than its column wraps to more lines instead of being cut at the edge")
    void longCardWraps() {
        int one = heightAt("Ship it", 180);
        int long_ = heightAt("Übersetzung des Fehlerkatalogs für die Kassenseite abschließen", 180);
        assertThat(long_).as("a long card is taller than a one-line card").isGreaterThan(one);
    }

    @Test
    @DisplayName("the same card is shorter in a wider column — the wrap follows the width")
    void wrapFollowsWidth() {
        String title = "Übersetzung des Fehlerkatalogs für die Kassenseite abschließen";
        assertThat(heightAt(title, 600)).isLessThan(heightAt(title, 150));
    }
}
