package org.nmox.studio.dbstudio.engine;

import org.openide.util.NbBundle.Messages;

/**
 * Assembles what DB Studio is willing to send KVASIR about a failed
 * statement — and what it will not.
 *
 * <p>This disclosure is tighter than any other in the product, because
 * a failed statement produced no rows: there is no customer data to
 * withhold. What DOES go is the SQL the user wrote, and the consent
 * line says so in as many words, including that literals inside it go
 * too. Masking them silently would be worse than useless — the error is
 * very often ABOUT a literal ("invalid input syntax for type integer:
 * 'abc'"), so a masked statement would produce a confidently wrong
 * explanation. Honesty over mangling: name it, and let the user decide.
 *
 * <p>Never sent: the connection (host, port, database, user), the
 * password (which is keychain-only and never leaves the OS store), any
 * result rows, and the schema.
 */
@Messages({
    // chrome (shift-2970): the consent dialog shows this line verbatim.
    "SqlErrorDisclosure_what=the SQL statement you ran (including any literal values in it), the database''s error message, and the engine kind ({0}) \u2014 no connection details, no password, and no result rows",
    "SqlErrorDisclosure_unknownEngine=unknown"
})
public final class SqlErrorDisclosure {

    /** Enough SQL to diagnose any hand-written statement. */
    public static final int MAX_SQL_CHARS = 4_000;
    /** Driver errors can embed whole query plans; this is plenty. */
    public static final int MAX_ERROR_CHARS = 2_000;

    private SqlErrorDisclosure() {
    }

    /** A code-point-safe prefix: never splits a surrogate pair. */
    public static String cap(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        int end = text.offsetByCodePoints(0, text.codePointCount(0, max));
        return text.substring(0, end);
    }

    /**
     * The one-line summary the consent dialog shows verbatim. It names
     * the literals explicitly — see the class note on why they are not
     * masked.
     */
    public static String what(String engineKind) {
        return Bundle.SqlErrorDisclosure_what(
                engineKind == null || engineKind.isBlank()
                        ? Bundle.SqlErrorDisclosure_unknownEngine() : engineKind);
    }

    /** The conversation's opening turn. */
    public static String body(String engineKind, String statement, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("A database statement failed and I need the error explained.\n\n")
                .append("Engine: ")
                .append(engineKind == null || engineKind.isBlank() ? "unknown" : engineKind)
                .append('\n');

        String sql = cap(statement, MAX_SQL_CHARS);
        if (!sql.isBlank()) {
            sb.append("\nStatement:\n").append(sql);
            if (statement != null && sql.length() < statement.length()) {
                sb.append("\n[statement truncated]");
            }
            sb.append('\n');
        }

        String message = cap(error, MAX_ERROR_CHARS);
        sb.append("\nError:\n").append(message.isBlank() ? "(no message)" : message);
        if (error != null && message.length() < error.length()) {
            sb.append("\n[error truncated]");
        }
        sb.append("\n\nWhat does this error mean, and how do I fix the statement?");
        return sb.toString();
    }
}
