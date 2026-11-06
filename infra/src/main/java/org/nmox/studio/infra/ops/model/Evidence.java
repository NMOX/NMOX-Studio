package org.nmox.studio.infra.ops.model;

/**
 * How the Hub knows a relationship exists. The HUD draws the three
 * differently and the inspector names the source, so a line on screen is
 * never mistaken for a measurement it is not.
 */
public enum Evidence {
    /** Seen in a live system: a container listed by the host's docker, a VPC the provider reports. */
    OBSERVED,
    /** Derived from facts that usually mean a relationship: two containers sharing a user-defined network. */
    INFERRED,
    /** Stated by someone: a compose file's {@code depends_on}, a wire drawn in the Infra Designer. */
    DECLARED
}
