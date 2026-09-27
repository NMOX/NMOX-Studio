package org.nmox.studio.dbstudio.engine;

import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;
import org.openide.util.NbBundle.Messages;

/**
 * The sentence a connection's failure needs when this machine holds no
 * password for it (3.4, question 1).
 *
 * <p>{@code .nmoxdb.json} is committed and shared; passwords never are —
 * each person's lives in their own OS keychain. So a teammate's clone
 * opens every server connection with no password at all, and until 3.4
 * the only thing it said was the driver's own error ({@code password
 * authentication failed for user "app"}, {@code Access denied for user},
 * a MongoDB auth exception), which reads as a wrong password rather than
 * as a missing one. When the connection names a user and no password is
 * stored here, the driver's words are kept but led by the fact that
 * explains them.
 */
@Messages({
    // {0} is the driver's own error, kept whole
    "MissingPassword_noneStored=No password for this connection is stored on this machine — passwords stay in each person’s own keychain, never in the shared .nmoxdb.json. Edit the connection to enter yours. The server said: {0}"
})
public final class MissingPassword {

    private MissingPassword() {
    }

    /** Whether this spec will connect with no stored password where one is plainly wanted. */
    static boolean applies(ConnectionSpec spec, char[] password) {
        return spec.engine() != DbEngine.SQLITE
                && spec.user() != null && !spec.user().isBlank()
                && (password == null || password.length == 0);
    }

    /** The error to show: {@code error} as it is, or led by the missing-password fact. */
    static String explain(ConnectionSpec spec, char[] password, String error) {
        if (error == null || !applies(spec, password)) {
            return error;
        }
        return Bundle.MissingPassword_noneStored(error);
    }
}
