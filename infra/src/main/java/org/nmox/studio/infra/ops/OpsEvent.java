package org.nmox.studio.infra.ops;

/**
 * One line of the Hub's feed: something that happened, when, about which
 * node. The feed is a record of real events — a request accepted, a host
 * answering for the first time, a deploy step ending — never decoration.
 */
/**
 * @param overLink true when the event is something the host itself answered over ssh — a probe's
 *     answer, a deploy step's end. The HUD runs a pulse along the link for these and for nothing
 *     else: a provider's listing never crossed that link, and a host that stopped answering
 *     crossed nothing at all.
 */
public record OpsEvent(long atMillis, Kind kind, String nodeId, String text, boolean overLink) {

    public enum Kind { INFO, OK, FAIL, STEP }

    public OpsEvent(long atMillis, Kind kind, String nodeId, String text) {
        this(atMillis, kind, nodeId, text, false);
    }
}
