package org.nmox.studio.infra.ops;

/**
 * One line of the Hub's feed: something that happened, when, about which
 * node. The feed is a record of real events — a request accepted, a host
 * answering for the first time, a deploy step ending — never decoration.
 */
public record OpsEvent(long atMillis, Kind kind, String nodeId, String text) {

    public enum Kind { INFO, OK, FAIL, STEP }
}
