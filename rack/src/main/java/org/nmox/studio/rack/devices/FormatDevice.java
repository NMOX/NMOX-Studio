package org.nmox.studio.rack.devices;

import java.awt.Color;
import java.util.List;
import org.nmox.studio.rack.ui.controls.RackButton;
import org.nmox.studio.rack.ui.controls.RackStyle;
import org.nmox.studio.rack.ui.controls.ToggleSwitch;

/**
 * GLOSS Formatter: the project's own formatter over the whole project
 * — biome when biome.json opts in, prettier otherwise. WRITE mode
 * rewrites files; CHECK mode only verifies (fails when unformatted).
 * On PHP lanes it runs Laravel Pint instead, same two modes.
 */
public class FormatDevice extends CommandDevice {

    private final ToggleSwitch writeSwitch;

    public FormatDevice() {
        super("format", "GLOSS", "CODE FORMATTER", new Color(73, 196, 184), 2);

        RackButton run = place(new RackButton("FORMAT", RackStyle.GO), RackStyle.TRANSPORT_X, 52);
        run.setCommandPreview(this::commandPreview);
        writeSwitch = place(new ToggleSwitch("MODE", true, "WRITE", "CHECK"), 112, 42);
        run.addActionListener(e -> primaryAction());

        param("write", writeSwitch);
    }

    @Override
    protected List<String> buildCommand() {
        // PHP lane: Laravel Pint writes by default; --test only verifies
        if (effectiveKind() == ProjectInspector.ProjectKind.PHP) {
            return writeSwitch.isOn()
                    ? List.of("vendor/bin/pint")
                    : List.of("vendor/bin/pint", "--test");
        }
        // Foundry lane: forge fmt writes by default; --check only verifies
        if (effectiveKind() == ProjectInspector.ProjectKind.FOUNDRY) {
            return writeSwitch.isOn()
                    ? List.of("forge", "fmt")
                    : List.of("forge", "fmt", "--check");
        }
        // Go lane: gofmt ships with the toolchain. WRITE rewrites in
        // place; CHECK runs gofmt -l, whose output lists unformatted
        // files — but gofmt exits ZERO either way (pinned live on go
        // 1.26), so the verdict override below turns any listed file
        // into a FAIL, restoring the exit contract every other
        // formatter honors.
        if (effectiveKind() == ProjectInspector.ProjectKind.GO) {
            return writeSwitch.isOn()
                    ? List.of("gofmt", "-w", ".")
                    : List.of("gofmt", "-l", ".");
        }
        // Rust lane: rustfmt via cargo — the toolchain's own formatter.
        // cargo fmt --check exits 1 when anything needs formatting
        // (pinned live on cargo 1.95).
        if (effectiveKind() == ProjectInspector.ProjectKind.RUST) {
            return writeSwitch.isOn()
                    ? List.of("cargo", "fmt")
                    : List.of("cargo", "fmt", "--check");
        }
        // Deno lane: the runtime ships its own formatter and the
        // workspace may have no node_modules for npx to resolve
        // prettier from at all. deno fmt --check exits 1 when dirty.
        if (ProjectInspector.hasDeno(projectDir())) {
            return writeSwitch.isOn()
                    ? List.of("deno", "fmt")
                    : List.of("deno", "fmt", "--check");
        }
        // Biome lane: a biome.json means the project formats with biome,
        // not prettier — same respect-their-toolchain rule as v1.60.0's
        // package managers. format without --write exits 1 when dirty,
        // which is exactly CHECK mode's contract.
        if (ProjectInspector.hasBiome(projectDir())) {
            return writeSwitch.isOn()
                    ? List.of("npx", "@biomejs/biome", "format", "--write", ".")
                    : List.of("npx", "@biomejs/biome", "format", ".");
        }
        return List.of("npx", "prettier", writeSwitch.isOn() ? "--write" : "--check", ".");
    }

    private volatile boolean goCheckRun;
    private volatile int unformattedFiles;

    @Override
    protected void primaryAction() {
        goCheckRun = effectiveKind() == ProjectInspector.ProjectKind.GO
                && !writeSwitch.isOn();
        unformattedFiles = 0;
        super.primaryAction();
    }

    @Override
    protected void onLine(String line) {
        super.onLine(line);
        if (goCheckRun && !line.isBlank()) {
            unformattedFiles++;
        }
    }

    /** gofmt -l exits 0 even when files need formatting; its OUTPUT is the verdict. */
    @Override
    protected boolean overallSuccess(int exitCode) {
        return super.overallSuccess(exitCode)
                && (!goCheckRun || unformattedFiles == 0);
    }

    /** Test seams for the gofmt -l verdict (no process spawn needed). */
    void beginGoCheckForTest() {
        goCheckRun = true;
        unformattedFiles = 0;
    }

    boolean verdictForTest(int exitCode) {
        return overallSuccess(exitCode);
    }

    void feedLineForTest(String line) {
        onLine(line);
    }
}
