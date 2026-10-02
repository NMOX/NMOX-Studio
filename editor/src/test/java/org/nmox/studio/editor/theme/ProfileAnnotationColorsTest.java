package org.nmox.studio.editor.theme;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.netbeans.editor.AnnotationType;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import org.nmox.studio.editor.theme.ProfileAnnotationColors.Colors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A paused debugger's current line is readable in the dark editor: the
 * annotation types take the colours of the profile in use (3.5.2).
 */
class ProfileAnnotationColorsTest {

    private static final Color PASTEL_GREEN = new Color(0xBDE6AA);
    private static final Color DARK_GREEN = new Color(0x283C26);

    /** The platform's own file, in the shape it ships. */
    private static final String PLATFORMS_FILE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE fontscolors PUBLIC "-//NetBeans//DTD Editor Fonts and Colors settings 1.1//EN" "http://www.netbeans.org/dtds/EditorFontsColors-1_1.dtd">
            <fontscolors>
                <fontcolor name="Breakpoint" bgColor="ff4B1919"/>
                <fontcolor name="ClassBreakpoint"/>
                <fontcolor name="CurrentPC" bgColor="ff283C26"/>
                <fontcolor name="todo" foreColor="ffa8c023"/>
                <fontcolor name="JspError" waveUnderlined="red"/>
            </fontscolors>
            """;

    private static AnnotationType type(String name, Color pastel) {
        AnnotationType t = new AnnotationType();
        t.setName(name);
        if (pastel != null) {
            t.setHighlight(pastel);
            t.setUseHighlightColor(true);
        }
        return t;
    }

    @Test
    @DisplayName("the platform's file is read as it ships, without fetching its DTD")
    void thePlatformsFileIsRead() throws Exception {
        Map<String, Colors> colors = ProfileAnnotationColors.parse(
                new ByteArrayInputStream(PLATFORMS_FILE.getBytes(StandardCharsets.UTF_8)));
        assertThat(colors).containsOnlyKeys("Breakpoint", "ClassBreakpoint", "CurrentPC", "todo", "JspError");
        assertThat(colors.get("CurrentPC")).isEqualTo(new Colors(DARK_GREEN, null, null));
        assertThat(colors.get("ClassBreakpoint")).as("named with no colour at all").isEqualTo(new Colors(null, null, null));
        assertThat(colors.get("todo").foreground()).isEqualTo(new Color(0xA8C023));
        assertThat(colors.get("JspError").wave()).isEqualTo(Color.RED);
    }

    @Test
    @DisplayName("colours are hex with or without alpha, or one of the format's names")
    void coloursAreParsed() {
        assertThat(ProfileAnnotationColors.color("ff283C26")).isEqualTo(DARK_GREEN);
        assertThat(ProfileAnnotationColors.color("283C26")).isEqualTo(DARK_GREEN);
        assertThat(ProfileAnnotationColors.color("lightGray")).isEqualTo(Color.LIGHT_GRAY);
        assertThat(ProfileAnnotationColors.color("")).isNull();
        assertThat(ProfileAnnotationColors.color("chartreuse-ish")).as("not a colour: none, never a guess").isNull();
        assertThat(ProfileAnnotationColors.color("ff283C2")).as("seven digits is not a colour").isNull();
    }

    @Test
    @DisplayName("the current line goes from pastel to the profile's dark green")
    void theCurrentLineTakesTheProfilesColour() {
        AnnotationType pc = type("CurrentPC", PASTEL_GREEN);
        Map<String, AnnotationType> types = Map.of("CurrentPC", pc);

        int changed = ProfileAnnotationColors.apply(Map.of("CurrentPC", new Colors(DARK_GREEN, null, null)), types::get);

        assertThat(changed).isEqualTo(1);
        assertThat(pc.getHighlight()).isEqualTo(DARK_GREEN);
        assertThat(pc.isUseHighlightColor()).isTrue();
        assertThat(ProfileAnnotationColors.apply(Map.of("CurrentPC", new Colors(DARK_GREEN, null, null)), types::get))
                .as("a second pass changes nothing").isZero();
    }

    @Test
    @DisplayName("the colours are given without the announcement that makes the platform save the type")
    void nothingIsAnnounced() {
        AnnotationType pc = type("CurrentPC", PASTEL_GREEN);
        java.util.List<String> announced = new java.util.ArrayList<>();
        pc.addPropertyChangeListener(e -> announced.add(e.getPropertyName()));

        ProfileAnnotationColors.apply(Map.of("CurrentPC", new Colors(DARK_GREEN, Color.WHITE, Color.RED)),
                Map.of("CurrentPC", pc)::get);

        // a setter announces, and the announcement is what rewrites the type's
        // file in the user directory while the platform's watcher re-reads it:
        // the staged walk's SEVERE "Premature end of file" (3.5.2)
        assertThat(announced).isEmpty();
        assertThat(pc.getHighlight()).isEqualTo(DARK_GREEN);
        assertThat(pc.getForegroundColor()).isEqualTo(Color.WHITE);
        assertThat(pc.getWaveUnderlineColor()).isEqualTo(Color.RED);
    }

    @Test
    @DisplayName("a type the profile names without a background loses its pastel one")
    void aColourTheProfileDoesNotGiveIsSwitchedOff() {
        AnnotationType classBreakpoint = type("ClassBreakpoint", new Color(0xFC9D9F));
        Map<String, AnnotationType> types = Map.of("ClassBreakpoint", classBreakpoint);

        ProfileAnnotationColors.apply(Map.of("ClassBreakpoint", new Colors(null, null, null)), types::get);

        assertThat(classBreakpoint.isUseHighlightColor())
                .as("the Options dialog's rule: no background in the profile, no highlight").isFalse();
    }

    @Test
    @DisplayName("a type the profile names without a text colour inherits the editor's again")
    void aTextColourTheProfileDoesNotGiveIsInherited() {
        AnnotationType blackOnPastel = type("Breakpoint", PASTEL_GREEN);
        blackOnPastel.setForegroundColor(Color.BLACK);
        blackOnPastel.setInheritForegroundColor(false);
        blackOnPastel.setWaveUnderlineColor(Color.BLUE);
        blackOnPastel.setUseWaveUnderlineColor(true);

        ProfileAnnotationColors.apply(Map.of("Breakpoint", new Colors(DARK_GREEN, null, null)),
                Map.of("Breakpoint", blackOnPastel)::get);

        // black text kept from a light default is unreadable on the dark background just given
        assertThat(blackOnPastel.isInheritForegroundColor()).isTrue();
        assertThat(blackOnPastel.isUseWaveUnderlineColor()).isFalse();
    }

    @Test
    @DisplayName("text and underline colours follow the same rules; an unknown or unnamed type is left alone")
    void foregroundAndWaveAndStrangers() {
        AnnotationType todo = type("todo", null);
        AnnotationType error = type("JspError", null);
        AnnotationType bookmark = type("Bookmark", PASTEL_GREEN);
        Map<String, AnnotationType> types = new HashMap<>();
        types.put("todo", todo);
        types.put("JspError", error);
        types.put("Bookmark", bookmark);
        Map<String, Colors> profile = new LinkedHashMap<>();
        profile.put("todo", new Colors(null, new Color(0xA8C023), null));
        profile.put("JspError", new Colors(null, null, Color.RED));
        profile.put("NoSuchModulesType", new Colors(DARK_GREEN, null, null));

        ProfileAnnotationColors.apply(profile, types::get);

        assertThat(todo.getForegroundColor()).isEqualTo(new Color(0xA8C023));
        assertThat(todo.isInheritForegroundColor()).isFalse();
        assertThat(error.getWaveUnderlineColor()).isEqualTo(Color.RED);
        assertThat(error.isUseWaveUnderlineColor()).isTrue();
        assertThat(bookmark.getHighlight()).as("the profile does not name it").isEqualTo(PASTEL_GREEN);
        assertThat(bookmark.isUseHighlightColor()).isTrue();
    }

    @Test
    @DisplayName("a profile is found by its display name, and the user's own file wins over its defaults")
    void theProfileFolderAndTheUsersFile() throws Exception {
        // the platform's configuration filesystem shows "FlatLaf Dark" for the
        // folder FlatLafDark (a localizing bundle named in the layer); a plain
        // memory filesystem decorates nothing, so this one says the same
        FileObject root = new org.openide.filesystems.MultiFileSystem(FileUtil.createMemoryFileSystem()) {
            @Override
            public org.openide.filesystems.StatusDecorator getDecorator() {
                return new org.openide.filesystems.StatusDecorator() {
                    @Override
                    public String annotateName(String name, java.util.Set<? extends FileObject> files) {
                        return "FlatLafDark".equals(name) ? "FlatLaf Dark" : name;
                    }

                    @Override
                    public String annotateNameHtml(String name, java.util.Set<? extends FileObject> files) {
                        return null;
                    }
                };
            }
        }.getRoot();
        FileObject dark = FileUtil.createFolder(root, "FontsColors/FlatLafDark");
        FileObject defaults = FileUtil.createFolder(dark, "Defaults");
        write(defaults.createData("platform-annotations", "xml"), PLATFORMS_FILE)
                .setAttribute("nbeditor-settings-ColoringType", "annotation");
        write(defaults.createData("platform-tokens", "xml"),
                "<fontscolors><fontcolor name=\"CurrentPC\" bgColor=\"ffffffff\"/></fontscolors>")
                .setAttribute("nbeditor-settings-ColoringType", "token");
        FileUtil.createFolder(root, "FontsColors/NetBeans");

        FileObject fontsColors = root.getFileObject("FontsColors");
        assertThat(ProfileAnnotationColors.profileFolder(fontsColors, "FlatLaf Dark"))
                .as("by the name the Options dialog shows").isEqualTo(dark);
        assertThat(ProfileAnnotationColors.profileFolder(fontsColors, "FlatLafDark"))
                .as("and by the folder's own").isEqualTo(dark);
        assertThat(ProfileAnnotationColors.profileFolder(fontsColors, "No Such Profile")).isNull();

        Map<String, Colors> colors = ProfileAnnotationColors.read(dark);
        assertThat(colors.get("CurrentPC").background())
                .as("only the file marked as annotation colours is read").isEqualTo(DARK_GREEN);

        write(dark.createData("org-netbeans-modules-editor-settings-CustomFontsColors-annotations", "xml"),
                "<fontscolors><fontcolor name=\"CurrentPC\" bgColor=\"ff004400\"/></fontscolors>");
        assertThat(ProfileAnnotationColors.read(dark).get("CurrentPC").background())
                .as("what the Options dialog saved").isEqualTo(new Color(0x004400));
        assertThat(ProfileAnnotationColors.read(dark).get("Breakpoint").background())
                .as("and the defaults it did not touch stand").isEqualTo(new Color(0x4B1919));
    }

    private static FileObject write(FileObject file, String text) throws Exception {
        try (OutputStream out = file.getOutputStream()) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
