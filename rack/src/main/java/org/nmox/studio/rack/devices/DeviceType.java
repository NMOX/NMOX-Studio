package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.function.Supplier;
import org.nmox.studio.rack.model.RackDevice;

/**
 * Catalog of every device that can be racked. The palette lists these;
 * patch persistence stores devices by their type id.
 */
public enum DeviceType {

    MASTER("master", "MAESTRO", "Master Control — fire whole pipelines", new Color(240, 196, 25), MasterControlDevice::new),
    NPM_SCRIPT("npm-script", "NPM-9000", "Script Sequencer — run package.json scripts", new Color(203, 56, 55), NpmScriptDevice::new),
    PACKAGE_MANAGER("package-manager", "CRATE", "Package Manager — install & update deps", new Color(214, 121, 41), PackageManagerDevice::new),
    BUILD("build", "FORGE", "Build Engine — vite/webpack/rollup & co", new Color(232, 166, 35), BuildDevice::new),
    TEST("test", "VERITAS", "Test Harness — jest/vitest/mocha...", new Color(99, 197, 70), TestDevice::new),
    DEV_SERVER("dev-server", "SURGE", "Dev Server — start/stop local serving", new Color(64, 156, 255), DevServerDevice::new),
    LINT("lint", "PURITY", "Lint Filter — eslint/stylelint", new Color(168, 110, 221), LintDevice::new),
    FORMAT("format", "GLOSS", "Code Formatter — prettier", new Color(73, 196, 184), FormatDevice::new),
    GIT("git", "TIMELINE", "Git Sequencer — status/pull/commit/push", new Color(222, 78, 54), GitDevice::new),
    AUDIT("audit", "SENTRY", "Security Analyzer — npm audit meters", new Color(188, 42, 48), AuditDevice::new),
    DEPLOY("deploy", "LAUNCHPAD", "Deploy Output — armed deploys only", new Color(231, 52, 99), DeployDevice::new),
    HTTP("http", "PING", "Request Probe — HTTP smoke tests", new Color(96, 180, 100), HttpDevice::new),
    BROWSER("browser", "SCOPE", "Browser Link — open URLs on trigger", new Color(54, 174, 222), BrowserDevice::new),
    CONSOLE("console", "MONITOR", "Output Console — watch any OUT jack", new Color(80, 200, 120), ConsoleDevice::new),
    ENV("env", "ATMOS", "Env Mixer — NODE_ENV/CI/custom vars", new Color(120, 144, 220), EnvDevice::new);

    private final String id;
    private final String title;
    private final String description;
    private final Color accent;
    private final Supplier<RackDevice> factory;

    DeviceType(String id, String title, String description, Color accent, Supplier<RackDevice> factory) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.accent = accent;
        this.factory = factory;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Color getAccent() {
        return accent;
    }

    public RackDevice create() {
        return factory.get();
    }

    public static DeviceType byId(String id) {
        for (DeviceType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
