package org.nmox.studio.ui.tasks;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A fresh board's columns are named in the reader's language.
 *
 * <p>A German walk photographed a fully translated Task Board whose three
 * column headers read To Do / Doing / Done. No l10n gate could see it,
 * because the English never passed through a bundle at all — it was three
 * literals inside {@code TaskBoard.starter()}, a model class. Seed data is
 * chrome until the user edits it.
 *
 * <p>The test drives the real factory with the default locale moved, which
 * is exactly how the product's own live language switch works (v2.103.0:
 * {@code ResourceBundle.getBundle} keys its cache on the current default, so
 * the very next lookup answers the new language).
 */
class StarterBoardSpeaksTest {

    @Test
    @DisplayName("the starter board's columns come from the bundle, not from literals")
    void theColumnsAreTranslated() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            TaskBoard german = TasksIO.starterBoard();
            List<String> names = german.columns().stream().map(TaskBoard.Column::name).toList();
            assertThat(names).as("a German board's headers")
                    .containsExactly("Zu erledigen", "In Arbeit", "Erledigt")
                    .doesNotContain("To Do", "Doing", "Done");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("English still reads English, and the board still has its three columns")
    void englishIsUnchanged() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            TaskBoard english = TasksIO.starterBoard();
            assertThat(english.columns().stream().map(TaskBoard.Column::name).toList())
                    .containsExactly("To Do", "Doing", "Done");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("a board already on disk keeps its own names — they are the user's by then")
    void anExistingBoardIsNeverRenamed() {
        TaskBoard mine = TaskBoard.starter("Backlog", "WIP", "Shipped");
        String json = mine.toJson();
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMAN);
            TaskBoard reloaded = TaskBoard.fromJson(json);
            assertThat(reloaded.columns().stream().map(TaskBoard.Column::name).toList())
                    .as("loading under another language must not re-translate anything")
                    .containsExactly("Backlog", "WIP", "Shipped");
        } finally {
            Locale.setDefault(original);
        }
    }
}
