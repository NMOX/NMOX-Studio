package org.nmox.studio.ui.tasks;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ⌘I reach into the Task Board (v1.324.0). The Task Board walk in
 * shipped 1.323.0 could exercise the chord, drag-and-drop, rename, WIP
 * colour and plain rendering, but the Quick Search POPUP does not
 * respond to programmatic typing in this automation (a known-good term
 * returned empty too), so the reach was unproven live. This drives the
 * provider's seamed match through a plain sink — the same shape the
 * apiclient/dbstudio providers pin — so the matching is proven at the
 * level the walk could not reach.
 */
class TasksSearchTest {

    @Test
    @DisplayName("a card title matches case-insensitively, and the hit names its column")
    void matchesTitleWithColumn() {
        TaskBoard b = TaskBoard.starter();
        b.addCard(0, "Ship the kanban", "");
        b.addCard(1, "review PR", "");
        List<String> labels = new ArrayList<>();
        new TasksSearchProvider().evaluate("SHIP", b,
                (a, l) -> { labels.add(l); return true; });
        assertThat(labels).containsExactly("Ship the kanban — To Do (Tasks)");
    }

    @Test
    @DisplayName("a substring anywhere in the title matches; the column travels with it")
    void substringAndColumnTravel() {
        TaskBoard b = TaskBoard.starter();
        b.addCard(1, "drag me to Done", "");
        List<String> labels = new ArrayList<>();
        new TasksSearchProvider().evaluate("drag me", b,
                (a, l) -> { labels.add(l); return true; });
        assertThat(labels)
                .as("the exact term the shipped-app walk typed — proven here"
                        + " because the popup could not be driven by automation")
                .containsExactly("drag me to Done — Doing (Tasks)");
    }

    @Test
    @DisplayName("under two characters, or no board, yields nothing")
    void guards() {
        TaskBoard b = TaskBoard.starter();
        b.addCard(0, "a card", "");
        List<String> labels = new ArrayList<>();
        new TasksSearchProvider().evaluate("a", b,
                (a, l) -> { labels.add(l); return true; });
        assertThat(labels).as("single char is below the floor").isEmpty();
        new TasksSearchProvider().evaluate("card", null,
                (a, l) -> { labels.add(l); return true; });
        assertThat(labels).as("no board, no results").isEmpty();
    }

    @Test
    @DisplayName("the sink returning false stops the walk (SPI back-pressure)")
    void stopsWhenSinkFull() {
        TaskBoard b = TaskBoard.starter();
        b.addCard(0, "match one", "");
        b.addCard(0, "match two", "");
        List<String> labels = new ArrayList<>();
        new TasksSearchProvider().evaluate("match", b,
                (a, l) -> { labels.add(l); return false; });
        assertThat(labels)
                .as("the SPI contract: addResult=false means the response is"
                        + " full — stop, do not keep offering")
                .hasSize(1);
    }
}
