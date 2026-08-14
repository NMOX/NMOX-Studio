/**
 * The Task Board (⌥⌘1): a per-project kanban stored beside the project
 * as {@code .nmoxtasks.json} — the sixth per-project studio file, and
 * the best SMALL example of the whole studio pattern. If you want to
 * learn how every NMOX studio window works, read this package in this
 * order:
 * <ol>
 *   <li>{@link org.nmox.studio.ui.tasks.TaskBoard} — the pure model:
 *       columns, cards, WIP limits, blockers, labels, the time clock.
 *       No Swing; every kanban rule is a plain unit test.</li>
 *   <li>{@link org.nmox.studio.ui.tasks.TasksIO} — load/save with the
 *       persistence laws: atomic writes, corrupt input kept as .bak,
 *       never-clobber.</li>
 *   <li>{@link org.nmox.studio.ui.tasks.BoardStats} — the overview's
 *       numbers, computed from the model (flow, aging, blockers, the
 *       time report with midnight clipping).</li>
 *   <li>{@link org.nmox.studio.ui.tasks.TasksTopComponent} — the
 *       window: a NetBeans {@code TopComponent} whose javadoc lists
 *       every house law it carries, with origins. The disk never
 *       touches the EDT; one RequestProcessor lane orders all IO.</li>
 *   <li>{@link org.nmox.studio.ui.tasks.OverviewPanel} — the dashboard
 *       face; rendering only.</li>
 * </ol>
 * The codebase guide's Flow&nbsp;5 traces a Clock-In click through all
 * of these files.
 */
package org.nmox.studio.ui.tasks;
