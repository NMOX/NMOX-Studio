/**
 * A project's {@code .vscode/tasks.json} in Quick Search (⌘I / ⇧⌘P). Read
 * {@code VsCodeTasks} first: it is the pure half — JSONC, the per-OS
 * override, the variables this IDE can fill and the ones it refuses, the
 * working folder judged by {@code core.util.Containment} — and it spawns
 * nothing. {@code VsCodeTaskSearchProvider} lists what it read and, on
 * Enter, refuses out loud or asks Workspace Trust BEFORE spawning through
 * {@code CommandExecutor}, registering the run with {@code LiveRuns} so
 * the toolbar ■ stops it. An {@code npm}-type task goes to
 * {@code tools.npm.NpmService}'s own trust-gated lane instead.
 *
 * <p>{@code .vscode/launch.json} is the sibling pair: {@code VsCodeLaunch}
 * (pure, sharing the tasks half's JSONC, per-OS merge, variables and
 * containment) and {@code VsCodeLaunchSearchProvider}, which spawns
 * nothing itself — it hands the resolved program or page to the editor's
 * debugger through {@code core.spi.DebugLauncher}, after its own trust
 * question on the project.
 *
 * <p>The category is registered in this module's hand {@code layer.xml}
 * under {@code QuickSearch/VsCodeTasks} and {@code QuickSearch/VsCodeLaunches}; the bundle lives here, in a
 * package with no {@code @NbBundle.Messages}, so the two can never clobber
 * each other (the v1.79.0 lesson).
 */
package org.nmox.studio.tools.vscode;
