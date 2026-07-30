package org.nmox.studio.ui.irc.protocol;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * The IRCv3 {@code server-time} capability's timestamp, decoded: when a
 * server (or a bouncer replaying history) attaches
 * {@code @time=2026-07-30T12:34:56.789Z} to a message, the transcript
 * should show WHEN THE MESSAGE HAPPENED, not when our socket read it —
 * otherwise replayed backlog all wears the reconnect minute. This class
 * turns that tag value into a local wall-clock time; a missing or
 * malformed value yields {@link Optional#empty()} so callers fall back
 * to "now" instead of crashing on a sloppy bridge.
 *
 * <p>Pure time-in/time-out (the zone is a parameter, defaulted to the
 * system zone) so the parse rules are unit-testable without a clock.
 */
public final class ServerTime {

    private ServerTime() {
    }

    /** The tag value as a local wall-clock time, or empty when absent/garbled. */
    public static Optional<LocalTime> localTime(String tagValue, ZoneId zone) {
        if (tagValue == null || tagValue.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalTime.ofInstant(Instant.parse(tagValue), zone));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    /** {@link #localTime(String, ZoneId)} in the system default zone. */
    public static Optional<LocalTime> localTime(String tagValue) {
        return localTime(tagValue, ZoneId.systemDefault());
    }
}
