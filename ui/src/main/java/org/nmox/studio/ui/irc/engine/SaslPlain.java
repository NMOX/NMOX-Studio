package org.nmox.studio.ui.irc.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The SASL PLAIN payload, rendered the way the IRCv3 {@code sasl}
 * capability wants it on the wire: the mechanism's
 * {@code authzid NUL authcid NUL password} triple, base64-encoded, then
 * split into {@code AUTHENTICATE} lines of at most {@link #CHUNK} bytes
 * — and, per spec, a final bare {@code +} line when the last chunk came
 * out exactly full (that is how the server knows the payload ended
 * rather than the pipe stalling). An empty payload is a single
 * {@code +}. Pure strings-in/strings-out so the chunking rules are
 * exhaustively testable; the engine feeds each returned string to one
 * {@code AUTHENTICATE} line and never keeps the password afterwards.
 */
final class SaslPlain {

    /** The spec's per-AUTHENTICATE ceiling for base64 payload bytes. */
    static final int CHUNK = 400;

    private SaslPlain() {
    }

    /**
     * The AUTHENTICATE line payloads for one PLAIN authentication.
     * The authorization identity is left empty (the conventional "act
     * as myself"), the account is both omitted-authzid's implied value
     * and the authcid.
     */
    static List<String> chunks(String account, String password) {
        String raw = "\0" + account + "\0" + password;
        String b64 = Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        List<String> out = new ArrayList<>();
        if (b64.isEmpty()) {
            out.add("+");
            return out;
        }
        for (int i = 0; i < b64.length(); i += CHUNK) {
            out.add(b64.substring(i, Math.min(b64.length(), i + CHUNK)));
        }
        if (b64.length() % CHUNK == 0) {
            out.add("+"); // a full final chunk needs the explicit end mark
        }
        return out;
    }
}
