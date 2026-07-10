package org.nmox.studio.rack.engine;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.windows.OutputEvent;
import org.openide.windows.OutputListener;

/**
 * Makes file:line references in tool output clickable: eslint, tsc,
 * pytest, cargo, go vet - their locations all jump straight to the
 * editor line. One click from "what failed" to "where".
 */
public final class FileLink {

    /** path.ext:12 / path.ext:12:5 / path.ext(12,5) styles. */
    private static final Pattern LOCATION = Pattern.compile(
            "(?<file>[\\w./~\\\\-]+\\.[A-Za-z]{1,12})[(:](?<line>\\d{1,6})(?:[:,](?<col>\\d{1,5}))?\\)?");

    private FileLink() {
    }

    /** A location parsed from one output line. */
    public record Location(File file, int line) {
    }

    /** First existing-file location on the line, resolved against dir. */
    public static Location find(String outputLine, File dir) {
        Matcher m = LOCATION.matcher(outputLine);
        while (m.find()) {
            File candidate = new File(m.group("file"));
            if (!candidate.isAbsolute()) {
                candidate = new File(dir, m.group("file"));
            }
            if (candidate.isFile()) {
                try {
                    return new Location(candidate.getCanonicalFile(),
                            Integer.parseInt(m.group("line")));
                } catch (Exception ignored) {
                    // unparsable line number or path; try the next match
                }
            }
        }
        return null;
    }

    /** An OutputListener that opens the location in the editor. */
    public static OutputListener opener(Location location) {
        return new OutputListener() {
            @Override
            public void outputLineAction(OutputEvent ev) {
                open(location);
            }
        };
    }

    public static void open(Location location) {
        try {
            // tool output paths are often relative or carry '..'; toFileObject
            // silently returns null for non-normalized files (a dead hyperlink)
            FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(location.file()));
            if (fo == null) {
                return;
            }
            LineCookie lines = DataObject.find(fo).getLookup().lookup(LineCookie.class);
            if (lines != null) {
                Line line = lines.getLineSet().getCurrent(
                        Math.max(0, location.line() - 1));
                javax.swing.SwingUtilities.invokeLater(() ->
                        line.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS));
            }
        } catch (Exception ignored) {
            // file vanished or no editor support; the click just does nothing
        }
    }
}
