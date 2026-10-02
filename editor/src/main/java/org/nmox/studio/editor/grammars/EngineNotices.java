package org.nmox.studio.editor.grammars;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.modules.OnStart;

/**
 * Keeps the regex engine's notes about vendored grammars out of the
 * session log (3.5.5).
 *
 * <p>The editor's engine compiles TextMate patterns with joni, and joni
 * remarks on style: an unescaped {@code ]} in a character class, a nested
 * repeat it simplifies ({@code (?:\s+)?} is {@code \s*}). TM4E logs each
 * remark as a WARNING, once for every time a pattern is compiled, and a
 * grammar's patterns are compiled again for every rule that reaches them.
 * Opening one C++ file wrote 1,088 such lines. None is a failure, none is
 * anything the person at the keyboard can act on, and the log they bury is
 * the one Report a Problem attaches.
 *
 * <p>The logger is set to SEVERE, which is the level a refusal would need
 * anyway: a pattern joni cannot compile is thrown, not logged, and
 * {@code GrammarRegexesCompileGateTest} fails the build on one. A level
 * someone set on purpose ({@code -J-D<logger>.level=WARNING} on the command
 * line, which the platform applies before any module starts) is left alone,
 * so a maintainer bumping a grammar can read the notes.
 */
@OnStart
public final class EngineNotices implements Runnable {

    /** The class TM4E logs joni's remarks under. */
    static final String LOGGER = "org.eclipse.tm4e.core.internal.oniguruma.OnigRegExp";

    /**
     * Held for the life of the module: the log manager keeps loggers
     * weakly, and one nobody holds is collected with the level it was given.
     */
    private static Logger held;

    @Override
    public void run() {
        quiet();
    }

    static synchronized Logger quiet() {
        if (held == null) {
            held = Logger.getLogger(LOGGER);
        }
        if (held.getLevel() == null) {
            held.setLevel(Level.SEVERE);
        }
        return held;
    }
}
