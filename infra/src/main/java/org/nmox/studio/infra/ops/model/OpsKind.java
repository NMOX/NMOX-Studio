package org.nmox.studio.infra.ops.model;

/**
 * What a node on the Ops Hub's topology stands for. Three kinds carry the
 * first vertical slice: the machine the IDE runs on (where every ssh link
 * starts), a host reachable over ssh (a droplet, or a server the user
 * already has), and a service observed running on a host (a container).
 */
public enum OpsKind {
    /** The machine NMOX Studio runs on — the origin of every ssh link. */
    WORKSTATION,
    /** A server reachable over ssh: created by a provider or added by hand. */
    HOST,
    /** A service observed on a host — today, a running container. */
    SERVICE
}
