package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.io.File;
import java.util.List;
import org.nmox.studio.rack.ui.controls.Knob;
import org.nmox.studio.rack.ui.controls.LcdDisplay;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;

/**
 * CRATE Package Manager: dependency installation and maintenance.
 * INSTALL is the primary action (fired by the RUN jack). The DEPS LCD
 * shows the declared dependency counts straight from package.json and
 * refreshes after every operation.
 */
public class PackageManagerDevice extends CommandDevice {

    private static final String[] MANAGERS = {"auto", "npm", "yarn", "pnpm"};

    private final Knob managerKnob;
    private final LcdDisplay depsLcd;

    public PackageManagerDevice() {
        super("package-manager", "CRATE", "PACKAGE MANAGER", new Color(214, 121, 41), 2);

        RackButton install = place(new RackButton("INSTALL", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        install.setCommandPreview(this::commandPreview);
        RackButton stop = place(new RackButton("STOP", RackStyle.STOP), RackStyle.TRANSPORT_STOP_X, 52);
        managerKnob = place(new Knob("ENGINE", MANAGERS, 0), 180, 40);
        RackButton update = place(new RackButton("UPDATE", RackStyle.MUTATE), 254, 52);
        RackButton outdated = place(new RackButton("CHECK", RackStyle.QUERY), 318, 52);
        depsLcd = place(new LcdDisplay(96, 1), 382, 52);
        depsLcd.setText("—");
        depsLcd.setToolTipText("dependencies + devDependencies declared in package.json");

        install.addActionListener(e -> installAll());
        update.addActionListener(e -> launch(cmd("update")));
        outdated.addActionListener(e -> launch(cmd("outdated")));
        stop.addActionListener(e -> stopProcess());

        param("manager", managerKnob);
    }

    private String manager() {
        String selected = MANAGERS[managerKnob.getSelectedIndex()];
        return "auto".equals(selected) ? "npm" : selected;
    }

    private boolean isAuto() {
        return managerKnob.getSelectedIndex() == 0;
    }

    /**
     * In AUTO the verb maps onto whatever toolchain the project uses;
     * an explicit npm/yarn/pnpm selection always talks Node.
     */
    private List<String> cmd(String verb) {
        ProjectInspector.ProjectKind kind = isAuto()
                ? effectiveKind()
                : ProjectInspector.ProjectKind.NODE;
        return cmdFor(kind, verb);
    }

    private List<String> cmdFor(ProjectInspector.ProjectKind kind, String verb) {
        return switch (kind) {
            case RUST -> switch (verb) {
                case "update" -> List.of("cargo", "update");
                case "outdated" -> List.of("cargo", "update", "--dry-run");
                default -> List.of("cargo", "fetch");
            };
            case ELIXIR -> switch (verb) {
                case "update" -> List.of("mix", "deps.update", "--all");
                case "outdated" -> List.of("mix", "hex.outdated");
                default -> List.of("mix", "deps.get");
            };
            case ERLANG -> switch (verb) {
                case "update" -> List.of("rebar3", "upgrade", "--all");
                default -> List.of("rebar3", "get-deps");
            };
            case CLOJURE -> List.of("clojure", "-P");
            case SWIFT -> switch (verb) {
                case "update" -> List.of("swift", "package", "update");
                case "outdated" -> List.of("swift", "package", "show-dependencies");
                default -> List.of("swift", "package", "resolve");
            };
            case DOTNET -> switch (verb) {
                case "outdated" -> List.of("dotnet", "list", "package", "--outdated");
                default -> List.of("dotnet", "restore");
            };
            case DART -> switch (verb) {
                case "update" -> List.of("dart", "pub", "upgrade");
                case "outdated" -> List.of("dart", "pub", "outdated");
                default -> List.of("dart", "pub", "get");
            };
            case SCALA -> List.of("sbt", "update");
            case HASKELL -> List.of("stack", "build", "--only-dependencies");
            case ZIG -> List.of("zig", "build", "--fetch");
            case OCAML -> List.of("dune", "build");
            case CRYSTAL -> switch (verb) {
                case "update" -> List.of("shards", "update");
                case "outdated" -> List.of("shards", "outdated");
                default -> List.of("shards", "install");
            };
            case GO -> switch (verb) {
                case "update" -> List.of("go", "get", "-u", "./...");
                case "outdated" -> List.of("go", "list", "-u", "-m", "all");
                default -> List.of("go", "mod", "download");
            };
            case MAVEN -> switch (verb) {
                case "update", "outdated" -> List.of("mvn", "-q", "versions:display-dependency-updates");
                default -> List.of("mvn", "-q", "dependency:resolve");
            };
            case GRADLE -> List.of("gradle", "--quiet", "dependencies");
            case PYTHON -> switch (verb) {
                case "update" -> List.of("pip", "install", "--upgrade", "-r", "requirements.txt");
                case "outdated" -> List.of("pip", "list", "--outdated");
                default -> new java.io.File(ProjectInspector.kindDir(projectDir(),
                        ProjectInspector.ProjectKind.PYTHON), "requirements.txt").isFile()
                        ? List.of("pip", "install", "-r", "requirements.txt")
                        : List.of("pip", "install", "-e", ".");
            };
            case RUBY -> switch (verb) {
                case "update" -> List.of("bundle", "update");
                case "outdated" -> List.of("bundle", "outdated");
                default -> List.of("bundle", "install");
            };
            case PHP -> switch (verb) {
                case "update" -> List.of("composer", "update");
                case "outdated" -> List.of("composer", "outdated");
                default -> List.of("composer", "install");
            };
            default -> List.of(manager(),
                    "update".equals(verb) && "yarn".equals(manager()) ? "upgrade" : verb);
        };
    }

    /** Single launches run where the effective toolchain's manifest lives. */
    @Override
    protected java.io.File commandDir() {
        ProjectInspector.ProjectKind kind = isAuto()
                ? effectiveKind() : ProjectInspector.ProjectKind.NODE;
        return ProjectInspector.kindDir(projectDir(), kind);
    }

    /**
     * INSTALL bootstraps the whole repo: in AUTO with no ROSETTA
     * override, a mixed project sequences every toolchain's install,
     * each in its own manifest directory - one button press readies
     * frontend and backend alike.
     */
    private void installAll() {
        if (!isAuto() || (getRack() != null && getRack().getToolchainOverride() != null)) {
            launch(cmd("install"));
            return;
        }
        var kinds = ProjectInspector.detectKinds(projectDir());
        if (kinds.size() <= 1) {
            launch(cmd("install"));
            return;
        }
        List<Step> steps = new java.util.ArrayList<>();
        for (var entry : kinds.entrySet()) {
            steps.add(new Step(cmdFor(entry.getKey(), "install"), entry.getValue()));
        }
        launchSequence(steps);
    }

    @Override
    protected void primaryAction() {
        installAll();
    }

    @Override
    protected List<String> buildCommand() {
        return cmd("install");
    }

    @Override
    protected void onAttached() {
        refreshDepsLcd();
    }

    @Override
    public void projectChanged(File dir) {
        refreshDepsLcd();
    }

    /** Installs and updates change package.json; re-read the truth. */
    @Override
    protected void onFinished(int exitCode) {
        refreshDepsLcd();
    }

    private void refreshDepsLcd() {
        int[] counts = ProjectInspector.dependencyCounts(projectDir());
        var kinds = ProjectInspector.detectKinds(projectDir());
        // a present-but-unparseable package.json is a fact worth stating,
        // not a silent shrug - it breaks every AUTO knob downstream
        boolean broken = counts == null && new File(
                ProjectInspector.kindDir(projectDir(), ProjectInspector.ProjectKind.NODE),
                "package.json").isFile();
        onEdt(() -> {
            depsLcd.setText(broken ? "PACKAGE.JSON UNREADABLE"
                    : counts != null && kinds.size() <= 1
                    ? counts[0] + "+" + counts[1] + " DEPS"
                    : kinds.size() > 1 ? kinds.size() + " TOOLCHAINS"
                    : !kinds.isEmpty() ? kinds.keySet().iterator().next().manifest()
                    : "NO PROJECT");
            depsLcd.setToolTipText(broken
                    ? "package.json exists but does not parse — fix the JSON"
                    : counts == null
                    ? "No package.json in the project"
                    : counts[0] + " dependencies, " + counts[1] + " devDependencies");
        });
    }
}
