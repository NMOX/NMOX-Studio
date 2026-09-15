package org.nmox.studio.web3.engine;

/**
 * Reassembles WebSocket text frames into messages, BOUNDED. The JDK
 * listener hands text over in parts; an endpoint the user added can send
 * a message of any size, and buffering it whole is the v1.99.0 OOM class
 * one transport over. Past {@link #MAX_MESSAGE_CHARS} the message is
 * refused exactly once and the rest of it is discarded as it arrives —
 * nothing past the cap is ever held.
 *
 * <p>Not thread-safe; the listener delivers one part at a time.
 */
public final class WsFrames {

    /** No subscription notification the Watch pane decodes comes near this. */
    public static final int MAX_MESSAGE_CHARS = 1024 * 1024;

    /** What one appended part produced. */
    public enum Kind {
        /** More parts to come. */
        PARTIAL,
        /** A whole message, in {@link Result#text()}. */
        MESSAGE,
        /** This message crossed the cap: refuse it (reported once). */
        OVERSIZE,
        /** A later part of a message already refused. */
        DISCARDED
    }

    /** One append's outcome; {@code text} is non-null only for {@link Kind#MESSAGE}. */
    public record Result(Kind kind, String text) {
    }

    private static final Result PARTIAL = new Result(Kind.PARTIAL, null);
    private static final Result OVERSIZE = new Result(Kind.OVERSIZE, null);
    private static final Result DISCARDED = new Result(Kind.DISCARDED, null);

    private final int maxChars;
    private final StringBuilder buffer = new StringBuilder();
    private boolean discarding;

    public WsFrames() {
        this(MAX_MESSAGE_CHARS);
    }

    public WsFrames(int maxChars) {
        if (maxChars < 1) {
            throw new IllegalArgumentException("maxChars must be at least 1");
        }
        this.maxChars = maxChars;
    }

    /** Feeds one part; {@code last} marks the end of the message. */
    public Result append(CharSequence part, boolean last) {
        if (discarding) {
            discarding = !last;
            return DISCARDED;
        }
        if ((long) buffer.length() + part.length() > maxChars) {
            buffer.setLength(0);
            buffer.trimToSize();
            discarding = !last;
            return OVERSIZE;
        }
        buffer.append(part);
        if (!last) {
            return PARTIAL;
        }
        String text = buffer.toString();
        buffer.setLength(0);
        return new Result(Kind.MESSAGE, text);
    }
}
