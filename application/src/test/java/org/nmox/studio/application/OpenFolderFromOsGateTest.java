package org.nmox.studio.application;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opening a folder in NMOX Studio from the operating system, as "Open with
 * Code" does, on all three (3.1.0). Every door ends in the IDE being AIMED
 * at the folder ({@code --aim}, File ▸ Open Folder…'s verb), never in the
 * platform's raw folder tab.
 *
 * <ul>
 *   <li><b>macOS</b> — Finder's Open With, a drop on the Dock icon and
 *       {@code open -a}. The bundle declares folders ({@code public.folder},
 *       Viewer, Alternate) so Finder offers the app; the launcher exports
 *       {@code CFProcessPath} naming the bundle's own executable, without
 *       which the JVM's main bundle is the runtime's embedded Info.plist and
 *       AWT drops every open-documents event before Java sees it (measured on
 *       a probe bundle of the launcher's shape and on the assembled app: no
 *       event reached any handler without it, both launch-time and running
 *       events did with it). The ui module's {@code FinderOpen} aims what
 *       arrives. The plist is linted here with {@code plutil} on a Mac and
 *       parsed as XML everywhere.
 *   <li><b>Linux</b> — the .desktop entry claims {@code inode/directory}
 *       (Open With, never the default: see build-packages.sh) and runs
 *       {@code /usr/bin/nmox %F}, which turns a folder into {@code --aim}.
 *   <li><b>Windows</b> — an unticked installer task writes
 *       {@code Directory\shell} and {@code Directory\Background\shell}
 *       verbs running the launcher with {@code --aim "%V\."}, labelled in
 *       every installer language and taken out on uninstall.
 *       {@code iscc} does not run here: the windows-installer-check workflow
 *       installs, reads the keys, runs the verb's argv and uninstalls; this
 *       gate holds that the workflow does, and the shape.
 * </ul>
 */
class OpenFolderFromOsGateTest {

    private static final Path BUILD_DMG = Path.of("..", "packaging", "macos", "build-dmg.sh");
    private static final Path BUILD_PACKAGES = Path.of("..", "packaging", "linux", "build-packages.sh");
    private static final Path ISS = Path.of("..", "packaging", "windows", "nmox-studio.iss");
    private static final Path WIN_CHECK = Path.of("..", ".github", "workflows", "windows-installer-check.yml");
    private static final Path UI_MANIFEST = Path.of("..", "ui", "src", "main", "nbm", "manifest.mf");
    private static final Path FINDER_INSTALL = Path.of("..", "ui", "src", "main", "java", "org", "nmox",
            "studio", "ui", "actions", "FinderOpenInstall.java");

    private static final String PLIST_OPEN = "cat > \"$BUNDLE/Contents/Info.plist\" <<PLIST\n";
    private static final String LAUNCHER_OPEN = "cat > \"$BUNDLE/Contents/MacOS/nmox-studio\" <<'LAUNCHER'\n";

    @TempDir
    Path tmp;

    private static String read(Path p) throws IOException {
        assertThat(p).as("%s exists", p).exists();
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String heredoc(String script, String open, String close) {
        int from = script.indexOf(open);
        assertThat(from).as("the script writes %s", open.strip()).isNotEqualTo(-1);
        int to = script.indexOf("\n" + close + "\n", from);
        assertThat(to).as("the heredoc %s is closed", close).isGreaterThan(from);
        return script.substring(from + open.length(), to + 1);
    }

    // ---------------------------------------------------------------- macOS

    /** The Info.plist exactly as build-dmg.sh writes it, with its one variable filled. */
    private static String plist() throws IOException {
        return heredoc(read(BUILD_DMG), PLIST_OPEN, "PLIST").replace("${VERSION}", "9.9.9");
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // the DOCTYPE names Apple's DTD by URL: never fetch it
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    /** A plist dict's key/value pairs, in order. */
    private static Map<String, Element> dict(Element dict) {
        Map<String, Element> out = new LinkedHashMap<>();
        String key = null;
        for (Node n = dict.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (!(n instanceof Element e)) {
                continue;
            }
            if (e.getTagName().equals("key")) {
                key = e.getTextContent();
            } else if (key != null) {
                out.put(key, e);
                key = null;
            }
        }
        return out;
    }

    private static List<Element> children(Element e) {
        List<Element> out = new ArrayList<>();
        NodeList kids = e.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element c) {
                out.add(c);
            }
        }
        return out;
    }

    @Test
    @DisplayName("macOS: the bundle declares folders - Viewer, Alternate, public.folder - and nothing else")
    void infoPlistDeclaresFolders() throws Exception {
        Map<String, Element> top = dict((Element) parse(plist()).getDocumentElement()
                .getElementsByTagName("dict").item(0));
        assertThat(top).as("the plist's top-level keys").containsKey("CFBundleExecutable");
        Element types = top.get("CFBundleDocumentTypes");
        assertThat(types).as("Finder offers an app for a folder only when the bundle declares one").isNotNull();
        assertThat(types.getTagName()).isEqualTo("array");
        List<Element> entries = children(types);
        assertThat(entries).as("one document type: folders").hasSize(1);
        Map<String, Element> folder = dict(entries.get(0));
        assertThat(folder.get("CFBundleTypeRole").getTextContent())
                .as("the IDE opens a folder; it never claims to edit Finder's folders").isEqualTo("Viewer");
        assertThat(folder.get("LSHandlerRank").getTextContent())
                .as("Alternate: offered in Open With, never the Mac's default for a folder").isEqualTo("Alternate");
        List<String> uti = children(folder.get("LSItemContentTypes")).stream().map(Element::getTextContent).toList();
        assertThat(uti).containsExactly("public.folder");
    }

    @Test
    @DisplayName("macOS: the generated Info.plist is one plutil accepts")
    @EnabledOnOs(OS.MAC)
    void infoPlistLints() throws Exception {
        Path p = tmp.resolve("Info.plist");
        Files.writeString(p, plist());
        Process lint = new ProcessBuilder("plutil", "-lint", p.toString()).redirectErrorStream(true).start();
        String out = new String(lint.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(lint.waitFor(20, TimeUnit.SECONDS)).isTrue();
        assertThat(lint.exitValue()).as("plutil -lint said: %s", out).isZero();
    }

    @Test
    @DisplayName("macOS: the launcher names the bundle's own executable as CFProcessPath, for the bundled runtime only")
    void launcherExportsCfProcessPathInTheBundledBranch() throws IOException {
        String launcher = heredoc(read(BUILD_DMG), LAUNCHER_OPEN, "LAUNCHER");
        int bundled = launcher.indexOf("if [ \"$PROBE_OK\" = \"0\" ]; then\n");
        int fallback = launcher.indexOf("JDK=$(/usr/libexec/java_home");
        assertThat(bundled).as("the bundled-runtime branch").isNotEqualTo(-1);
        assertThat(fallback).as("the fallback-JDK branch").isGreaterThan(bundled);
        String branch = launcher.substring(bundled, fallback);
        int set = branch.indexOf("    CFProcessPath=\"$DIR/$(basename \"$PRG\")\"\n");
        int export = branch.indexOf("    export CFProcessPath\n");
        int exec = branch.indexOf("exec /bin/sh \"$RES/bin/nmoxstudio\"");
        assertThat(set).as("set to this bundle's executable, found through any link").isNotEqualTo(-1);
        assertThat(export).as("exported, so the JVM two processes down inherits it").isGreaterThan(set);
        assertThat(exec).as("before the platform launcher is started").isGreaterThan(export);
        assertThat(launcher.split("CFProcessPath=", -1)).as("set in exactly one place").hasSize(2);
        assertThat(launcher.substring(fallback)).as("a fallback JDK may be 21, where the IDE cannot take the"
                + " variable back out of its environment, so it never gets it").doesNotContain("CFProcessPath");
    }

    @Test
    @DisplayName("macOS: run by its real path, the launcher hands the platform launcher CFProcessPath = itself")
    @DisabledOnOs(OS.WINDOWS)
    void launcherReallyExportsItself() throws Exception {
        Path contents = tmp.resolve("Apps/NMOX Studio.app/Contents");
        Path macos = Files.createDirectories(contents.resolve("MacOS"));
        Path res = contents.resolve("Resources/nmoxstudio");
        Path launcher = macos.resolve("nmox-studio");
        Files.writeString(launcher, heredoc(read(BUILD_DMG), LAUNCHER_OPEN, "LAUNCHER"));
        executable(launcher);
        Path java = Files.createDirectories(res.resolve("jre/bin")).resolve("java");
        Files.writeString(java, "#!/bin/sh\nexit 0\n");
        executable(java);
        Path record = tmp.resolve("record.txt");
        Files.writeString(Files.createDirectories(res.resolve("bin")).resolve("nmoxstudio"),
                "#!/bin/sh\nprintf '%s\\n' \"${CFProcessPath-<unset>}\" > '" + record + "'\n");
        Process p = new ProcessBuilder(launcher.toString()).directory(tmp.toFile()).redirectErrorStream(true)
                .redirectOutput(tmp.resolve("launcher.out").toFile()).start();
        assertThat(p.waitFor(20, TimeUnit.SECONDS)).as("launcher finished").isTrue();
        assertThat(record).as("the stand-in platform launcher ran").exists();
        String seen = Files.readAllLines(record).get(0);
        assertThat(Path.of(seen).toRealPath()).as("CFProcessPath names the bundle's executable")
                .isEqualTo(launcher.toRealPath());
    }

    @Test
    @DisplayName("macOS: the ui module installs its handler at validate(), the one moment before the platform's")
    void handlerInstallsFirst() throws IOException {
        String manifest = read(UI_MANIFEST);
        assertThat(manifest).as("the ui module names its ModuleInstall")
                .contains("\nOpenIDE-Module-Install: org/nmox/studio/ui/actions/FinderOpenInstall.class\n");
        String src = GateSources.stripComments(read(FINDER_INSTALL));
        assertThat(src).as("NbInstaller.prepare calls validate() only when the class DECLARES it,"
                + " for every module before any restored()")
                .contains("public void validate()").contains("FinderOpen.early();")
                .doesNotContain("void restored()");
    }

    // ---------------------------------------------------------------- Linux

    private static String desktopEntry() throws IOException {
        String body = read(BUILD_PACKAGES);
        int a = body.indexOf("[Desktop Entry]");
        int b = body.indexOf("\nDESKTOP\n", a);
        assertThat(a).as("the .desktop heredoc").isGreaterThan(0);
        return body.substring(a, b + 1);
    }

    @Test
    @DisplayName("Linux: the menu entry offers itself for folders and runs nmox, which aims them")
    void desktopEntryOpensFolders() throws IOException {
        String entry = desktopEntry();
        Map<String, String> keys = new LinkedHashMap<>();
        for (String line : entry.split("\n")) {
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                continue;
            }
            assertThat(line).as("a desktop entry line is Key=Value").contains("=");
            String key = line.substring(0, line.indexOf('='));
            assertThat(keys.put(key, line.substring(line.indexOf('=') + 1)))
                    .as("key %s declared once", key).isNull();
        }
        assertThat(keys.get("MimeType")).as("folders, and the spec's trailing ;").isEqualTo("inode/directory;");
        String exec = keys.get("Exec");
        assertThat(exec).as("a folder is passed to the command that turns it into --aim")
                .matches("/usr/bin/nmox %[FU]");
        String sh = read(BUILD_PACKAGES);
        assertThat(sh).as("and that command is the one the .deb puts there")
                .contains("ln -s /opt/nmox-studio/bin/nmox \"$DEB_STAGE/usr/bin/nmox\"\n");
        assertThat(read(Path.of("..", "packaging", "linux", "nmox")))
                .as("nmox gives a folder the --aim verb").contains("set -- \"$@\" --aim ");
    }

    // ---------------------------------------------------------------- Windows

    private static String section(String iss, String name) {
        int at = iss.indexOf("\n[" + name + "]\n");
        assertThat(at).as("the .iss has a [%s] section", name).isNotEqualTo(-1);
        int next = iss.indexOf("\n[", at + 2);
        return next == -1 ? iss.substring(at) : iss.substring(at, next + 1);
    }

    /** The .iss's logical lines, continuations joined, so one registry entry is one line. */
    private static List<String> logical(String section) {
        return List.of(section.replace("; \\\n    ", "; ").replace(" \\\n    ", " ").split("\n"));
    }

    @Test
    @DisplayName("Windows: an unticked task writes both folder verbs under HKA, --aim \"%V\\.\", removed on uninstall")
    void explorerVerbs() throws IOException {
        String iss = read(ISS);
        assertThat(section(iss, "Tasks"))
                .as("unticked by default, as VS Code's folder task is: a line in a menu every folder shares")
                .contains("\nName: \"contextmenu\"; Description: \"{cm:AddFolderContextMenu}\"; Flags: unchecked\n");
        Matcher exe = Pattern.compile("(?m)^Name: \"\\{group\\}\\\\NMOX Studio\"; Filename: \"\\{app\\}\\\\bin\\\\([^\"]+)\"")
                .matcher(iss);
        assertThat(exe.find()).as("the Start-menu shortcut names the launcher").isTrue();
        String command = "ValueData: \"\"\"{app}\\bin\\" + exe.group(1) + "\"\" --aim \"\"%V\\.\"\"\"; Tasks: contextmenu";
        List<String> registry = logical(section(iss, "Registry"));
        for (String where : List.of("Directory", "Directory\\Background")) {
            String key = "Root: HKA; Subkey: \"Software\\Classes\\" + where + "\\shell\\NMOXStudio";
            List<String> lines = registry.stream().filter(l -> l.startsWith(key + "\"") || l.startsWith(key + "\\"))
                    .toList();
            assertThat(lines).as("%s: label, icon, command", where).hasSize(3);
            assertThat(lines).as("%s: the label speaks the installer's language, and uninstall removes the key", where)
                    .anyMatch(l -> l.startsWith(key + "\"; ValueType: string; ValueName: \"\"; ")
                            && l.endsWith("ValueData: \"{cm:OpenWithNmox}\"; Tasks: contextmenu; Flags: uninsdeletekey"));
            assertThat(lines).as("%s: the shipped icon", where)
                    .anyMatch(l -> l.contains("ValueName: \"Icon\"; ValueData: \"{app}\\nmox-studio.ico\"; Tasks: contextmenu"));
            assertThat(lines).as("%s: the launcher aims the folder", where)
                    .anyMatch(l -> l.startsWith(key + "\\command\"; ") && l.endsWith(command));
            assertThat(lines).as("%s: every entry rides the task", where)
                    .allMatch(l -> l.contains("Tasks: contextmenu"));
        }
    }

    @Test
    @DisplayName("Windows: the task and the Explorer label speak every installer language")
    void explorerVerbSpeaksEveryLanguage() throws IOException {
        String iss = read(ISS);
        String messages = section(iss, "CustomMessages");
        Matcher lang = Pattern.compile("(?m)^Name: \"([a-z]{2})\"; MessagesFile:").matcher(iss);
        int langs = 0;
        List<String> missing = new ArrayList<>();
        for (String key : List.of("AddFolderContextMenu", "OpenWithNmox")) {
            assertThat(messages).as("the fallback for %s", key).contains("\n" + key + "=");
        }
        while (lang.find()) {
            langs++;
            String l = lang.group(1);
            if ("en".equals(l)) {
                continue;
            }
            for (String key : List.of("AddFolderContextMenu", "OpenWithNmox")) {
                Matcher v = Pattern.compile("(?m)^" + l + "\\." + key + "=(.+)$").matcher(messages);
                if (!v.find() || !v.group(1).contains("NMOX Studio")) {
                    missing.add(l + "." + key);
                }
            }
        }
        assertThat(langs).as("the installer's languages").isGreaterThanOrEqualTo(8);
        assertThat(missing).as("a language the wizard speaks, and the folder task does not").isEmpty();
    }

    @Test
    @DisplayName("Windows: the installer check really installs, reads both keys, runs the verb's argv and uninstalls")
    void workflowWalksTheVerb() throws IOException {
        String wf = read(WIN_CHECK);
        int step = wf.indexOf("- name: Verify \"Open with NMOX Studio\" on folders in Explorer\n");
        assertThat(step).as("the workflow step").isNotEqualTo(-1);
        String body = wf.substring(step, wf.indexOf("\n      - name:", step + 1));
        assertThat(body)
                .contains("'HKCU:\\Software\\Classes\\Directory\\shell\\NMOXStudio'")
                .contains("'HKCU:\\Software\\Classes\\Directory\\Background\\shell\\NMOXStudio'")
                .as("default install writes nothing").contains("a default install wrote")
                .as("ticked install").contains("'/TASKS=addtopath,contextmenu'")
                .as("the exact command").contains("'\" --aim \"%V\\.\"'")
                .as("the argv Explorer would hand over, drive root included").contains("GetPathRoot")
                .as("uninstall takes them out").contains("the uninstaller left $k");
        assertThat(wf).as("the workflow runs when the installer changes").contains("- 'packaging/windows/**'\n");
    }

    // ---------------------------------------------------------------- helpers

    private static void executable(Path p) throws IOException {
        if (!File.separator.equals("\\")) {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
