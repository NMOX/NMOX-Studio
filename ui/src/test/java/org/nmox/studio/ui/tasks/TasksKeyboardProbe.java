package org.nmox.studio.ui.tasks;

import javax.swing.JPanel;

/** Reaches the Task Board's package-private column builder for the a11y tests. */
public final class TasksKeyboardProbe {

    private TasksKeyboardProbe() {
    }

    /** The first column of the starter board, built the way the window builds it. */
    public static JPanel firstColumn() {
        TaskBoard board = TasksIO.starterBoard();
        return new TasksTopComponent().columnPanel(0, board.column(0));
    }
}
