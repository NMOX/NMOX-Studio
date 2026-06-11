package org.nmox.studio.rack.projectstudio;

import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.nmox.studio.rack.devices.DeviceType;
import org.nmox.studio.rack.model.Rack;
import org.nmox.studio.rack.model.RackDevice;
import org.nmox.studio.rack.model.RackIO;

/**
 * Ready-made infra wirings. Each preset builds a throwaway rack
 * programmatically and serializes it through RackIO, so a preset can
 * never drift from the real patch schema - if a port or param is
 * renamed, the preset breaks at build time, not in a user's hands.
 */
public enum RackPresets {

    DEV_LOOP("Dev Loop",
            "Dev server auto-opens the browser; saves run the tests") {
        @Override
        void wire(Rack rack) {
            RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "false", "filter", "1"));
            RackDevice server = add(rack, DeviceType.DEV_SERVER, null);
            RackDevice browser = add(rack, DeviceType.BROWSER, null);
            RackDevice test = add(rack, DeviceType.TEST, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(server.getPort("url"), browser.getPort("url"));
            rack.connect(server.getPort("ready"), browser.getPort("open"));
            rack.connect(reflex.getPort("changed"), test.getPort("run"));
            rack.connect(test.getPort("out"), console.getPort("in"));
        }
    },

    TDD_LOOP("TDD Loop",
            "REFLEX armed: every save runs the tests, tally on the console") {
        @Override
        void wire(Rack rack) {
            RackDevice reflex = add(rack, DeviceType.REFLEX, Map.of("armed", "true", "filter", "1"));
            RackDevice test = add(rack, DeviceType.TEST, null);
            RackDevice lint = add(rack, DeviceType.LINT, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(reflex.getPort("changed"), test.getPort("run"));
            rack.connect(test.getPort("out"), console.getPort("in"));
            rack.connect(test.getPort("ok"), lint.getPort("run"));
            rack.connect(lint.getPort("out"), console.getPort("in"));
        }
    },

    CI_LANE("CI Lane",
            "MAESTRO fires install → build → test, output on the console") {
        @Override
        void wire(Rack rack) {
            RackDevice master = add(rack, DeviceType.MASTER, null);
            RackDevice deps = add(rack, DeviceType.PACKAGE_MANAGER, null);
            RackDevice build = add(rack, DeviceType.BUILD, null);
            RackDevice test = add(rack, DeviceType.TEST, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(master.getPort("trig1"), deps.getPort("run"));
            rack.connect(deps.getPort("ok"), build.getPort("run"));
            rack.connect(build.getPort("ok"), test.getPort("run"));
            rack.connect(test.getPort("out"), console.getPort("in"));
        }
    },

    UPTIME_WATCH("Uptime Watch",
            "While the dev server runs, TEMPO clocks PING health checks") {
        @Override
        void wire(Rack rack) {
            RackDevice server = add(rack, DeviceType.DEV_SERVER, null);
            RackDevice browser = add(rack, DeviceType.BROWSER, null);
            // 30s health-check clock, gated by the server's RUNNING state
            RackDevice tempo = add(rack, DeviceType.TEMPO, Map.of("rate", "2", "running", "false"));
            RackDevice ping = add(rack, DeviceType.HTTP, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(server.getPort("url"), browser.getPort("url"));
            rack.connect(server.getPort("ready"), browser.getPort("open"));
            rack.connect(server.getPort("running"), tempo.getPort("enable"));
            rack.connect(server.getPort("url"), ping.getPort("url"));
            rack.connect(tempo.getPort("tick"), ping.getPort("send"));
            rack.connect(ping.getPort("body"), console.getPort("in"));
        }
    },

    SHIP_LANE("Ship Lane",
            "Prod build → security scan → armed deploy, with console trail") {
        @Override
        void wire(Rack rack) {
            RackDevice master = add(rack, DeviceType.MASTER, null);
            RackDevice build = add(rack, DeviceType.BUILD, Map.of("prod", "true"));
            RackDevice audit = add(rack, DeviceType.AUDIT, null);
            RackDevice deploy = add(rack, DeviceType.DEPLOY, null);
            RackDevice console = add(rack, DeviceType.CONSOLE, null);
            rack.connect(master.getPort("trig1"), build.getPort("run"));
            rack.connect(build.getPort("ok"), audit.getPort("run"));
            rack.connect(audit.getPort("ok"), deploy.getPort("run"));
            rack.connect(deploy.getPort("out"), console.getPort("in"));
        }
    };

    private final String displayName;
    private final String description;

    RackPresets(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    abstract void wire(Rack rack);

    /** Serializes this preset to patch JSON, loadable via RackIO. */
    public JSONObject buildPatch() {
        return buildPatchFrom(this::wire);
    }

    // ---- shared patch-building machinery (also used by templates) ----

    /** Builds a patch JSON by wiring a throwaway rack. */
    public static JSONObject buildPatchFrom(Consumer<Rack> wiring) {
        Rack rack = new Rack();
        try {
            wiring.accept(rack);
            return RackIO.toJson(rack);
        } finally {
            rack.shutdown();
        }
    }

    /**
     * Creates a device, applies state BEFORE racking it (so stateful
     * devices like REFLEX don't act on the throwaway rack), then mounts it.
     */
    public static RackDevice add(Rack rack, DeviceType type, Map<String, String> state) {
        RackDevice device = type.create();
        if (state != null) {
            device.applyState(state);
        }
        rack.addDevice(device);
        return device;
    }
}
