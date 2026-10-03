package org.nmox.studio.editor.vscode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Build;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Change;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Fit;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Location;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Outcome;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Plan;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Row;
import org.nmox.studio.editor.vscode.VsCodeUserSettings.Target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pure half of Import VS Code Settings: where the file is on each
 * system, what each recognised setting becomes here, that nothing the
 * product does not recognise ever leaves the plan, and that Apply writes
 * exactly the chosen rows.
 */
class VsCodeUserSettingsTest {

    private static final List<String> FONTS = List.of("Menlo", "Fira Code", "Monospaced", "Courier New");

    private static Plan plan(String json) throws VsCodeUserSettings.Unreadable {
        return VsCodeUserSettings.plan(json, FONTS);
    }

    private static Row row(Plan plan, String key) {
        return plan.rows().stream().filter(r -> r.key().equals(key)).findFirst().orElseThrow(
                () -> new AssertionError("no row for " + key + " in " + plan.rows()));
    }

    private static Path settingsUnder(Path base, String folder) {
        return base.resolve(folder).resolve("User").resolve("settings.json");
    }

    // ------------------------------------------------------------------ where

    @Test
    @DisplayName("macOS looks under ~/Library/Application Support, for Code, Insiders and VSCodium in that order")
    void macCandidates() {
        List<Location> c = VsCodeUserSettings.candidates("Mac OS X", "/Users/ada", name -> null);
        Path base = Path.of("/Users/ada", "Library", "Application Support");
        assertThat(c).extracting(Location::build).containsExactly(Build.CODE, Build.INSIDERS, Build.CODIUM);
        assertThat(c).extracting(Location::file).containsExactly(settingsUnder(base, "Code"),
                settingsUnder(base, "Code - Insiders"), settingsUnder(base, "VSCodium"));
    }

    @Test
    @DisplayName("Windows looks under %APPDATA%, and under the home's AppData/Roaming when it is not set")
    void windowsCandidates() {
        String appData = "C:\\Users\\ada\\AppData\\Roaming";
        List<Location> c = VsCodeUserSettings.candidates("Windows 11", "C:\\Users\\ada",
                name -> name.equals("APPDATA") ? appData : null);
        assertThat(c.get(0).file()).isEqualTo(settingsUnder(Path.of(appData), "Code"));
        List<Location> noEnv = VsCodeUserSettings.candidates("Windows 11", "C:\\Users\\ada", name -> null);
        assertThat(noEnv.get(2).file()).isEqualTo(settingsUnder(Path.of("C:\\Users\\ada", "AppData", "Roaming"),
                "VSCodium"));
    }

    @Test
    @DisplayName("elsewhere $XDG_CONFIG_HOME wins when it is an absolute path, else ~/.config")
    void linuxCandidates() {
        String xdg = Path.of("/").toAbsolutePath().resolve("xdg").toString();
        assertThat(VsCodeUserSettings.candidates("Linux", "/home/ada", name -> name.equals("XDG_CONFIG_HOME") ? xdg
                : null).get(0).file()).isEqualTo(settingsUnder(Path.of(xdg), "Code"));
        assertThat(VsCodeUserSettings.candidates("Linux", "/home/ada", name -> "relative/dir").get(0).file())
                .as("a relative XDG_CONFIG_HOME is invalid by the spec and ignored")
                .isEqualTo(settingsUnder(Path.of("/home/ada", ".config"), "Code"));
        assertThat(VsCodeUserSettings.candidates("Linux", "/home/ada", name -> null).get(1).file())
                .isEqualTo(settingsUnder(Path.of("/home/ada", ".config"), "Code - Insiders"));
    }

    @Test
    @DisplayName("only the places that have a file are found, in the order tried")
    void foundKeepsOrder() {
        List<Location> c = VsCodeUserSettings.candidates("Linux", "/home/ada", name -> null);
        Set<Path> present = Set.of(c.get(2).file(), c.get(1).file());
        assertThat(VsCodeUserSettings.found(c, present::contains)).extracting(Location::build)
                .containsExactly(Build.INSIDERS, Build.CODIUM);
    }

    // ------------------------------------------------------------------ what

    @Test
    @DisplayName("JSON with comments and trailing commas is read; anything else is unreadable and says nothing of the file")
    void jsoncAndUnreadable() throws Exception {
        Plan p = plan("""
                // my settings
                {
                  /* indentation */ "editor.tabSize": 2,
                  "editor.insertSpaces": true,
                }
                """);
        assertThat(p.rows()).extracting(Row::key).containsExactly("editor.tabSize", "editor.insertSpaces");
        assertThatThrownBy(() -> plan("{\"github.token\": \"ghp_SECRETVALUE\""))
                .isInstanceOf(VsCodeUserSettings.Unreadable.class)
                .hasMessageNotContaining("ghp_SECRETVALUE").hasMessageNotContaining("github.token");
        assertThatThrownBy(() -> plan("[1, 2]")).isInstanceOf(VsCodeUserSettings.Unreadable.class);
    }

    @Test
    @DisplayName("tab size sets tab width and indentation; a separate indent size takes the indentation")
    void indentation() throws Exception {
        Plan p = plan("{\"editor.tabSize\": 4, \"editor.insertSpaces\": false}");
        assertThat(row(p, "editor.tabSize").fit()).isEqualTo(Fit.EXACT);
        assertThat(row(p, "editor.tabSize").changes()).containsExactly(new Change(Target.TAB_SIZE, 4),
                new Change(Target.INDENT, 4));
        assertThat(row(p, "editor.insertSpaces").changes()).containsExactly(new Change(Target.EXPAND_TABS, false));
        assertThat(row(p, "editor.tabSize").here()).startsWith("Tab width and indentation 4 columns,");
        assertThat(row(plan("{\"editor.tabSize\": 1}"), "editor.tabSize").here())
                .startsWith("Tab width and indentation 1 column,");

        Plan apart = plan("{\"editor.tabSize\": 8, \"editor.indentSize\": 2}");
        assertThat(row(apart, "editor.tabSize").changes()).containsExactly(new Change(Target.TAB_SIZE, 8));
        assertThat(row(apart, "editor.indentSize").changes()).containsExactly(new Change(Target.INDENT, 2));

        Plan defaulted = plan("{\"editor.tabSize\": 3, \"editor.indentSize\": \"tabSize\"}");
        assertThat(defaulted.rows()).extracting(Row::key).containsExactly("editor.tabSize");

        for (String bad : List.of("0", "33", "2.5", "\"four\"", "true")) {
            Row r = row(plan("{\"editor.tabSize\": " + bad + "}"), "editor.tabSize");
            assertThat(r.fit()).as(bad).isEqualTo(Fit.NONE);
            assertThat(r.applicable()).as(bad).isFalse();
        }
        assertThat(row(plan("{\"editor.detectIndentation\": true}"), "editor.detectIndentation").fit())
                .isEqualTo(Fit.NONE);
    }

    @Test
    @DisplayName("word wrap on/off maps to the platform's line wrap; a wrap column has no counterpart")
    void wordWrap() throws Exception {
        assertThat(row(plan("{\"editor.wordWrap\": \"on\"}"), "editor.wordWrap").changes())
                .containsExactly(new Change(Target.LINE_WRAP, "words"));
        assertThat(row(plan("{\"editor.wordWrap\": \"off\"}"), "editor.wordWrap").changes())
                .containsExactly(new Change(Target.LINE_WRAP, "none"));
        for (String inexact : List.of("wordWrapColumn", "bounded", "sometimes")) {
            assertThat(row(plan("{\"editor.wordWrap\": \"" + inexact + "\"}"), "editor.wordWrap").fit())
                    .as(inexact).isEqualTo(Fit.NONE);
        }
    }

    @Test
    @DisplayName("auto save: off and onFocusChange are exact; afterDelay is exact only in whole minutes")
    void autoSave() throws Exception {
        Row off = row(plan("{\"files.autoSave\": \"off\"}"), "files.autoSave");
        assertThat(off.fit()).isEqualTo(Fit.EXACT);
        assertThat(off.changes()).containsExactly(new Change(Target.AUTOSAVE_ACTIVE, false),
                new Change(Target.AUTOSAVE_ON_FOCUS_LOST, false));

        Row focus = row(plan("{\"files.autoSave\": \"onFocusChange\"}"), "files.autoSave");
        assertThat(focus.fit()).isEqualTo(Fit.EXACT);
        assertThat(focus.changes()).contains(new Change(Target.AUTOSAVE_ON_FOCUS_LOST, true));

        assertThat(row(plan("{\"files.autoSave\": \"onWindowChange\"}"), "files.autoSave").fit()).isEqualTo(Fit.NEAR);

        Row defaultDelay = row(plan("{\"files.autoSave\": \"afterDelay\"}"), "files.autoSave");
        assertThat(defaultDelay.fit()).as("VS Code's default delay is one second").isEqualTo(Fit.NEAR);
        assertThat(defaultDelay.changes()).extracting(Change::target)
                .as("the interval is left alone: one second has no counterpart in minutes")
                .doesNotContain(Target.AUTOSAVE_MINUTES);
        assertThat(defaultDelay.here()).contains("1000 ms");

        Plan minutes = plan("{\"files.autoSave\": \"afterDelay\", \"files.autoSaveDelay\": 120000}");
        Row twoMinutes = row(minutes, "files.autoSave");
        assertThat(twoMinutes.fit()).isEqualTo(Fit.EXACT);
        assertThat(twoMinutes.changes()).contains(new Change(Target.AUTOSAVE_ACTIVE, true),
                new Change(Target.AUTOSAVE_MINUTES, 2));
        assertThat(minutes.rows()).extracting(Row::key).as("the delay is read with the mode, not as a row of its own")
                .doesNotContain("files.autoSaveDelay");

        assertThat(row(plan("{\"files.autoSave\": \"afterDelay\", \"files.autoSaveDelay\": 1500}"), "files.autoSave")
                .fit()).isEqualTo(Fit.NEAR);
        assertThat(row(plan("{\"files.autoSaveDelay\": 60000}"), "files.autoSaveDelay").fit()).isEqualTo(Fit.NONE);
    }

    @Test
    @DisplayName("format on save, minimap, sticky scroll and trailing whitespace go to this product's own toggles")
    void toggles() throws Exception {
        Plan p = plan("""
                {"editor.formatOnSave": false, "editor.minimap.enabled": false,
                 "editor.stickyScroll.enabled": true, "files.trimTrailingWhitespace": true}
                """);
        assertThat(row(p, "editor.formatOnSave").changes()).containsExactly(new Change(Target.FORMAT_ON_SAVE, false));
        assertThat(row(p, "editor.minimap.enabled").changes()).containsExactly(new Change(Target.MINIMAP, false));
        assertThat(row(p, "editor.stickyScroll.enabled").changes())
                .containsExactly(new Change(Target.STICKY_SCROLL, true));
        assertThat(row(p, "files.trimTrailingWhitespace").changes())
                .containsExactly(new Change(Target.TRIM_ON_SAVE, "always"));
        assertThat(row(plan("{\"files.trimTrailingWhitespace\": false}"), "files.trimTrailingWhitespace").changes())
                .containsExactly(new Change(Target.TRIM_ON_SAVE, "never"));
        assertThat(row(plan("{\"editor.minimap.enabled\": \"yes\"}"), "editor.minimap.enabled").fit())
                .isEqualTo(Fit.NONE);
    }

    @Test
    @DisplayName("rulers: one is exact, several keep the first, none hides the line; renderWhitespace all or none")
    void rulersAndWhitespace() throws Exception {
        assertThat(row(plan("{\"editor.rulers\": [100]}"), "editor.rulers").changes()).containsExactly(
                new Change(Target.RULER_WIDTH, 100), new Change(Target.RULER_VISIBLE, true));
        Row two = row(plan("{\"editor.rulers\": [{\"column\": 80, \"color\": \"#f00\"}, 120]}"), "editor.rulers");
        assertThat(two.fit()).isEqualTo(Fit.NEAR);
        assertThat(two.changes()).contains(new Change(Target.RULER_WIDTH, 80));
        assertThat(row(plan("{\"editor.rulers\": []}"), "editor.rulers").changes())
                .containsExactly(new Change(Target.RULER_VISIBLE, false));
        assertThat(row(plan("{\"editor.rulers\": [\"x\"]}"), "editor.rulers").fit()).isEqualTo(Fit.NONE);

        assertThat(row(plan("{\"editor.renderWhitespace\": \"none\"}"), "editor.renderWhitespace").fit())
                .isEqualTo(Fit.EXACT);
        assertThat(row(plan("{\"editor.renderWhitespace\": \"all\"}"), "editor.renderWhitespace").fit())
                .isEqualTo(Fit.NEAR);
        assertThat(row(plan("{\"editor.renderWhitespace\": \"trailing\"}"), "editor.renderWhitespace").fit())
                .isEqualTo(Fit.NONE);
    }

    @Test
    @DisplayName("fonts, themes, final newline and eol are recognised and answered, never applied")
    void answeredNotApplied() throws Exception {
        Plan p = plan("""
                {"editor.fontFamily": "'Not A Font', 'fira code', monospace", "editor.fontSize": 14,
                 "editor.fontLigatures": true, "workbench.colorTheme": "Monokai", "workbench.iconTheme": "seti",
                 "files.insertFinalNewline": true, "files.eol": "\\n"}
                """);
        assertThat(p.rows()).hasSize(7).allSatisfy(r -> {
            assertThat(r.fit()).as(r.key()).isEqualTo(Fit.NONE);
            assertThat(r.applicable()).as(r.key()).isFalse();
        });
        assertThat(row(p, "editor.fontFamily").here()).as("the machine's own spelling of the first installed family")
                .contains("Fira Code");
        assertThat(row(p, "workbench.colorTheme").value()).isEqualTo("Monokai");
    }

    @Test
    @DisplayName("the first installed family of a CSS list, quotes stripped, monospace as Java's Monospaced")
    void firstInstalledFamily() {
        assertThat(VsCodeUserSettings.firstInstalled("'JetBrains Mono', \"Menlo\", monospace", FONTS)).isEqualTo("Menlo");
        assertThat(VsCodeUserSettings.firstInstalled("Nope, monospace", FONTS)).isEqualTo("Monospaced");
        assertThat(VsCodeUserSettings.firstInstalled("FIRA CODE", FONTS)).isEqualTo("Fira Code");
        assertThat(VsCodeUserSettings.firstInstalled("Nope, 'Also Nope'", FONTS)).isNull();
        assertThat(VsCodeUserSettings.firstInstalled(", ,", FONTS)).isNull();
        assertThat(VsCodeUserSettings.firstInstalled(null, FONTS)).isNull();
    }

    /** A user file the way real ones are: tokens, proxies, environments, language blocks. */
    private static final String SECRETS = """
            {
              "github.token": "ghp_SECRETVALUE",
              "http.proxy": "http://ada:hunter2@proxy.example:8080",
              "python.defaultInterpreterPath": "/Users/ada/private/venv/bin/python",
              "[typescript]": {"editor.tabSize": 8, "secret.inside": "TOPSECRET_BLOCK"},
              "terminal.integrated.env.osx": {"AWS_SECRET_ACCESS_KEY": "AKIA_TERMINAL_SECRET"},
              "terminal.integrated.fontSize": 13,
              "editor.tabSize": 2
            }
            """;

    private static final List<String> NEVER = List.of("ghp_SECRETVALUE", "github.token", "hunter2", "http.proxy",
            "proxy.example", "defaultInterpreterPath", "/Users/ada/private", "TOPSECRET_BLOCK", "secret.inside",
            "[typescript]", "AKIA_TERMINAL_SECRET", "AWS_SECRET_ACCESS_KEY", "env.osx");

    @Test
    @DisplayName("a setting this does not recognise is counted, and its name and value appear nowhere: rows, footer, status, log")
    void noLeak() throws Exception {
        List<String> logged = new ArrayList<>();
        Handler tap = new Handler() {
            @Override
            public void publish(LogRecord r) {
                logged.add(String.valueOf(r.getMessage()) + " " + java.util.Arrays.toString(r.getParameters())
                        + " " + (r.getThrown() == null ? "" : r.getThrown()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger root = Logger.getLogger("");
        Level before = root.getLevel();
        root.addHandler(tap);
        root.setLevel(Level.ALL);
        try {
            Plan p = plan(SECRETS);
            assertThat(p.others()).as("github.token, http.proxy, the interpreter path and the [typescript] block")
                    .isEqualTo(4);
            assertThat(p.rows()).extracting(Row::key).containsExactly("editor.tabSize", "terminal.integrated.*");
            assertThat(row(p, "terminal.integrated.*").value()).contains("2 settings");

            List<String> said = new ArrayList<>();
            for (Row r : p.rows()) {
                said.add(r.key());
                said.add(r.value());
                said.add(r.here());
                said.add(r.changes().toString());
            }
            ImportVsCodeSettingsAction.Prepared prepared = new ImportVsCodeSettingsAction.Prepared(List.of(),
                    List.of(), new Location(Build.CODE, Path.of("settings.json")), p, null);
            said.add(ImportVsCodeSettingsSheet.footer(prepared));
            said.add(ImportVsCodeSettingsSheet.heading(prepared));
            Outcome done = VsCodeUserSettings.apply(p.rows(), new Recorder());
            said.add(ImportVsCodeSettingsSheet.said(done));
            for (String secret : NEVER) {
                assertThat(said).as(secret).noneMatch(s -> s.contains(secret));
                assertThat(logged).as(secret).noneMatch(s -> s.contains(secret));
            }
        } finally {
            root.removeHandler(tap);
            root.setLevel(before);
        }
    }

    /** Records every write; refuses the targets it is told to. */
    static final class Recorder implements VsCodeUserSettings.Homes {
        final List<Change> writes = new ArrayList<>();
        final Set<Target> refused = EnumSet.noneOf(Target.class);
        final List<Set<Target>> settled = new ArrayList<>();

        @Override
        public void write(Change change) {
            if (refused.contains(change.target())) {
                throw new IllegalStateException("no home");
            }
            writes.add(change);
        }

        @Override
        public void settled(Set<Target> written) {
            settled.add(Set.copyOf(written));
        }
    }

    @Test
    @DisplayName("Apply writes the chosen applicable rows; a row whose home is missing is named and the rest still apply")
    void applyWritesChosenRows() throws Exception {
        Plan p = plan("""
                {"editor.tabSize": 2, "files.autoSave": "onFocusChange", "editor.minimap.enabled": false,
                 "workbench.colorTheme": "Monokai"}
                """);
        Recorder homes = new Recorder();
        homes.refused.add(Target.AUTOSAVE_ACTIVE);
        Outcome o = VsCodeUserSettings.apply(p.rows(), homes);
        assertThat(o.applied()).containsExactly("editor.tabSize", "editor.minimap.enabled");
        assertThat(o.failed()).containsExactly("files.autoSave");
        assertThat(homes.writes).containsExactly(new Change(Target.TAB_SIZE, 2), new Change(Target.INDENT, 2),
                new Change(Target.MINIMAP, false));
        assertThat(homes.settled).hasSize(1);
        assertThat(homes.settled.get(0)).containsExactlyInAnyOrder(Target.TAB_SIZE, Target.INDENT, Target.MINIMAP);

        Recorder again = new Recorder();
        VsCodeUserSettings.apply(p.rows(), again);
        VsCodeUserSettings.apply(p.rows(), again);
        assertThat(again.writes).hasSize(10);
        assertThat(again.writes.subList(0, 5)).as("applying twice writes the same values again: harmless")
                .isEqualTo(again.writes.subList(5, 10));

        Recorder nothing = new Recorder();
        assertThat(VsCodeUserSettings.apply(List.of(), nothing).applied()).isEmpty();
        assertThat(nothing.settled).as("nothing written, nobody told").isEmpty();
    }
}
