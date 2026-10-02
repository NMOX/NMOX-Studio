package org.nmox.studio.editor.theme;

import java.awt.Color;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.netbeans.editor.AnnotationType;
import org.netbeans.editor.AnnotationTypes;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileUtil;
import org.openide.util.RequestProcessor;
import org.openide.windows.OnShowing;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * The editor's annotations take the colours of the colour profile in use.
 *
 * <p>A debugger's current line, a breakpoint, a bookmark: each is an
 * annotation type, defined with a pastel background meant for a white
 * editor and no text colour of its own. The platform's dark profile has its
 * own set (the current line is a dark green there), but an annotation type
 * does not look its colours up. They are PUSHED onto it, by the Options
 * dialog, when the user changes the profile there
 * ({@code ColorModel.setAnnotations}, read from its bytecode).
 *
 * <p>This product never goes through that dialog: it runs the dark look and
 * feel and selects the dark profile through its layer. So tokens and
 * highlights came out dark and annotations stayed pastel, and a paused
 * debugger showed its current line as light text on pale green, the least
 * readable line on the screen at the moment it mattered most. The first
 * staged walk on Windows photographed it (3.5.2); it is the same on every
 * system.
 *
 * <p>Once the main window shows, this does what the dialog would have done:
 * it reads the annotation colours of the profile in use, the profile's
 * defaults and then the user's own changes from the Options dialog, and
 * gives them to the annotation types, with the dialog's own rules. A type
 * the profile does not name is left alone. The settings storage's API is
 * for the platform's friends, so the two files are read where the platform
 * registers them.
 *
 * <p><b>Given, not set.</b> The first cut called the types' setters, as the
 * dialog does. A setter also SAVES the type: the platform rewrites that
 * type's file in the user directory in place, while its folder watcher
 * re-reads the folder on another thread, and the walk logged a SEVERE
 * "Premature end of file" from a file caught half-written, on a first
 * launch, one save per setter for every type the profile names. So the
 * colours are stored on the type
 * without the announcement ({@code putProp}): nothing is written, nothing
 * races, and the same few dozen values are given again at the next start.
 * The cost is stated: a type already painted in an editor that was open
 * before this ran keeps its old colour in that editor until it is reopened.
 */
@OnShowing
public final class ProfileAnnotationColors implements Runnable {

    private static final Logger LOG = Logger.getLogger(ProfileAnnotationColors.class.getName());
    private static final RequestProcessor RP = new RequestProcessor("nmox-annotation-colors", 1, true);

    /** What a profile says about one annotation type; a null colour means "none". */
    record Colors(Color background, Color foreground, Color wave) {
    }

    @Override
    public void run() {
        RP.post(() -> {
            try {
                Map<String, Colors> colors = ofCurrentProfile();
                if (colors.isEmpty()) {
                    return;
                }
                AnnotationTypes types = AnnotationTypes.getTypes();
                // the folder is read here, off the paint thread; and a type
                // is only asked for by a name the folder has, because asking
                // for any other logs a stack trace
                java.util.Set<String> known = new java.util.HashSet<>();
                for (java.util.Iterator<String> it = types.getAnnotationTypeNames(); it.hasNext();) {
                    known.add(it.next());
                }
                EventQueue.invokeLater(() -> {
                    int changed = apply(colors, name -> known.contains(name) ? types.getType(name) : null);
                    LOG.log(Level.FINE, "annotation colours of the profile in use: {0} types set", changed);
                });
            } catch (RuntimeException | LinkageError ex) {
                // colours are a refinement; an editor without them still works
                LOG.log(Level.INFO, "annotation colours not applied", ex);
            }
        });
    }

    /** The annotation colours of the profile the editor is using, or nothing. */
    static Map<String, Colors> ofCurrentProfile() {
        FileObject editors = FileUtil.getConfigFile("Editors");
        FileObject fontsColors = FileUtil.getConfigFile("Editors/FontsColors");
        if (editors == null || fontsColors == null) {
            return Map.of();
        }
        Object current = editors.getAttribute("currentFontColorProfile");
        FileObject profile = profileFolder(fontsColors, current instanceof String s ? s : "NetBeans");
        return profile == null ? Map.of() : read(profile);
    }

    /**
     * The folder of the profile with this display name. The attribute that
     * selects a profile holds its DISPLAY name ("FlatLaf Dark"); the folder
     * is "FlatLafDark".
     */
    static FileObject profileFolder(FileObject fontsColors, String displayName) {
        for (FileObject folder : fontsColors.getChildren()) {
            if (!folder.isFolder()) {
                continue;
            }
            if (folder.getName().equals(displayName) || displayed(folder).equals(displayName)) {
                return folder;
            }
        }
        return null;
    }

    private static String displayed(FileObject folder) {
        try {
            return folder.getFileSystem().getDecorator().annotateName(folder.getName(),
                    Collections.singleton(folder));
        } catch (FileStateInvalidException | RuntimeException ex) {
            return folder.getName();
        }
    }

    /** The profile's defaults, then the user's own file over them. */
    static Map<String, Colors> read(FileObject profile) {
        Map<String, Colors> out = new LinkedHashMap<>();
        FileObject defaults = profile.getFileObject("Defaults");
        if (defaults != null) {
            for (FileObject f : defaults.getChildren()) {
                if ("annotation".equals(f.getAttribute("nbeditor-settings-ColoringType"))) {
                    readInto(out, f);
                }
            }
        }
        for (FileObject f : profile.getChildren()) {
            if (f.isData() && f.getNameExt().endsWith("-annotations.xml")) {
                readInto(out, f); // what the Options dialog saved
            }
        }
        return out;
    }

    private static void readInto(Map<String, Colors> out, FileObject file) {
        // these files are a few kilobytes; one read as large as a megabyte is not one of them
        if (file.getSize() > 1_048_576L) {
            LOG.log(Level.INFO, "annotation colours: {0} is too large to be a colour file", file.getPath());
            return;
        }
        try (InputStream in = file.getInputStream()) {
            out.putAll(parse(in));
        } catch (IOException | SAXException | ParserConfigurationException ex) {
            LOG.log(Level.INFO, "annotation colours: " + file.getPath() + " could not be read", ex);
        }
    }

    /** One {@code <fontcolor>} per annotation type; a type with no colour attribute has none. */
    static Map<String, Colors> parse(InputStream xml) throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // the files name a DTD at netbeans.org, so a DOCTYPE is expected;
        // nothing here fetches it, or any other entity
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
        NodeList nodes = builder.parse(xml).getElementsByTagName("fontcolor");
        Map<String, Colors> out = new LinkedHashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String name = e.getAttribute("name");
            if (!name.isBlank()) {
                out.put(name, new Colors(color(e.getAttribute("bgColor")), color(e.getAttribute("foreColor")),
                        color(e.getAttribute("waveUnderlined"))));
            }
        }
        return out;
    }

    /** {@code ff283C26}, {@code 283C26} or one of the names the format allows; anything else is no colour. */
    static Color color(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.strip();
        Color named = switch (t.toLowerCase(Locale.ROOT)) {
            case "black" -> Color.BLACK;
            case "blue" -> Color.BLUE;
            case "cyan" -> Color.CYAN;
            case "darkgray" -> Color.DARK_GRAY;
            case "gray" -> Color.GRAY;
            case "green" -> Color.GREEN;
            case "lightgray" -> Color.LIGHT_GRAY;
            case "magenta" -> Color.MAGENTA;
            case "orange" -> Color.ORANGE;
            case "pink" -> Color.PINK;
            case "red" -> Color.RED;
            case "white" -> Color.WHITE;
            case "yellow" -> Color.YELLOW;
            default -> null;
        };
        if (named != null) {
            return named;
        }
        if ((t.length() == 6 || t.length() == 8) && t.chars().allMatch(c -> Character.digit(c, 16) >= 0)) {
            long argb = Long.parseLong(t, 16);
            return t.length() == 8 ? new Color((int) argb, true) : new Color((int) argb);
        }
        return null;
    }

    /**
     * Gives each named type its colours, by the Options dialog's rules: a
     * colour the profile gives is used; one it does not give is switched
     * off, so a pastel default does not survive into a dark editor. Returns
     * how many types changed.
     */
    static int apply(Map<String, Colors> colors, Function<String, AnnotationType> types) {
        int changed = 0;
        for (Map.Entry<String, Colors> e : colors.entrySet()) {
            AnnotationType type = types.apply(e.getKey());
            if (type == null) {
                continue; // a type no installed module defines
            }
            Colors c = e.getValue();
            boolean touched = false;
            touched |= give(type, AnnotationType.PROP_USE_HIGHLIGHT_COLOR, type.isUseHighlightColor(),
                    c.background() != null);
            touched |= give(type, AnnotationType.PROP_HIGHLIGHT_COLOR, type.getHighlight(), c.background());
            touched |= give(type, AnnotationType.PROP_INHERIT_FOREGROUND_COLOR, type.isInheritForegroundColor(),
                    c.foreground() == null);
            touched |= give(type, AnnotationType.PROP_FOREGROUND_COLOR, type.getForegroundColor(), c.foreground());
            touched |= give(type, AnnotationType.PROP_USE_WAVEUNDERLINE_COLOR, type.isUseWaveUnderlineColor(),
                    c.wave() != null);
            touched |= give(type, AnnotationType.PROP_WAVEUNDERLINE_COLOR, type.getWaveUnderlineColor(), c.wave());
            if (touched) {
                changed++;
            }
        }
        return changed;
    }

    /** Stores a flag where the type keeps it, quietly; true when it changed. */
    private static boolean give(AnnotationType type, String property, boolean now, boolean wanted) {
        if (now == wanted) {
            return false;
        }
        type.putProp(property, wanted ? Boolean.TRUE : Boolean.FALSE);
        return true;
    }

    /** Stores a colour the profile gives; a colour it does not give is left as it is, switched off by its flag. */
    private static boolean give(AnnotationType type, String property, Color now, Color wanted) {
        if (wanted == null || wanted.equals(now)) {
            return false;
        }
        type.putProp(property, wanted);
        return true;
    }

    /** The platform instantiates this through {@code @OnShowing}. */
    public ProfileAnnotationColors() {
    }
}
