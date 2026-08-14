/**
 * Where processes actually run. The rack's devices never call
 * {@code ProcessBuilder} themselves — they go through
 * {@link org.nmox.studio.rack.engine.CommandExecutor}, which owns the
 * spawn, pumps output line-by-line (bounded — a single endless line
 * cannot eat the heap, v1.112.0), translates the classic walls into
 * human sentences (EADDRINUSE, Node version floors), and guarantees the
 * orphan rule: no child process outlives the IDE.
 *
 * <p><b>The security law to internalize:</b> {@code CommandExecutor}
 * and core's {@code ProcessSupport} are deliberately UN-gated
 * primitives. Whoever calls them must first pass Workspace Trust when
 * the command comes from the project (package.json scripts, forge,
 * node_modules binaries) — see {@code SpawnSiteTrustLedgerTest}, which
 * fails the build until every new spawn site is classified.
 *
 * <p>Also here: {@link org.nmox.studio.rack.engine.FlightRecorder} (the
 * bus every launch/exit is journaled to, off the hot path on its own
 * queue) and the {@code InteractiveProcess} engine behind the REPL.
 */
package org.nmox.studio.rack.engine;
