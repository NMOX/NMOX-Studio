package org.nmox.studio.infra.ops.ui;

/**
 * The level of abstraction the HUD is showing. FLEET is the physical
 * picture (this machine and every host, services as satellites); HOST
 * drills into one server; SERVICES is the logical picture (every service,
 * grouped by where it runs, with its dependencies). A node that exists in
 * two views keeps its identity, so moving between them is a motion, not
 * a cut.
 */
public record HudView(Level level, String hostId) {

    public enum Level { FLEET, HOST, SERVICES }

    public static final HudView FLEET = new HudView(Level.FLEET, null);
    public static final HudView SERVICES = new HudView(Level.SERVICES, null);

    public static HudView host(String hostId) {
        return new HudView(Level.HOST, hostId);
    }

    /** Where Escape goes: a host view returns to the fleet; the top views stay. */
    public HudView up() {
        return level == Level.HOST ? FLEET : this;
    }
}
