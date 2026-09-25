package org.nmox.studio.editor.standards;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * Makes the editor indent the way the project's {@code .editorconfig}
 * says: {@code indent_style}, {@code indent_size} and {@code tab_width},
 * the settings people write the file for.
 *
 * <p>The platform asks every registered
 * {@link CodeStylePreferences.Provider} in lookup order and takes the
 * first non-null answer ({@code CodeStylePreferences.getPreferences},
 * decompiled from RELEASE310); the Tab key, Enter's auto-indent and
 * re-indent all read indentation through it. This provider sits ahead
 * of the platform's project-aware one (position 100 against its
 * unpositioned registration) and answers only for a file an
 * {@code .editorconfig} says something about indentation for - with an
 * {@link OverlayPreferences} whose base is whatever the next provider
 * would have answered, so a project's own formatting settings and the
 * per-language Options stay underneath for every key the file does not
 * name. Every other document gets null and the platform carries on as
 * if this class did not exist.
 *
 * <p>The ask arrives on the EDT, on every Tab press. The answer is
 * therefore served from memory: on the EDT a file this provider has not
 * resolved yet answers null (the editor's own settings) while the
 * resolution runs on a background lane, and a resolved answer older
 * than {@link #FRESH_MS} is served as it is while a fresh one is
 * fetched - so an edit to {@code .editorconfig} reaches the open editor
 * within a couple of seconds without a single stat on the paint thread.
 * Off the EDT the file is resolved in place. The {@code .editorconfig}
 * files themselves are read through {@link EditorConfig}'s bounded,
 * mtime-cached parse.
 */
@ServiceProvider(service = CodeStylePreferences.Provider.class, position = 100)
public final class EditorConfigCodeStyle implements CodeStylePreferences.Provider {

    private static final Logger LOG = Logger.getLogger(EditorConfigCodeStyle.class.getName());

    /** How long a resolved answer is served without asking the disk again. */
    static final long FRESH_MS = 2_000;

    /** Resolved files kept; past it the map starts over rather than grow. */
    static final int CACHE_CAP = 1_024;

    private static final RequestProcessor RP = new RequestProcessor("EditorConfig indentation", 1, true);

    private static final Map<String, Resolved> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /** The clock; a seam so a test can age an answer without sleeping. */
    static volatile LongSupplier clock = System::currentTimeMillis;

    /** One file's properties and when they were read. */
    private record Resolved(Map<String, String> props, long at) {
    }

    @Override
    public Preferences forDocument(Document doc, String mimeType) {
        File file = fileOf(doc);
        if (file == null) {
            return null;
        }
        return answer(file, () -> nextProvider(p -> p.forDocument(doc, mimeType)), mimeType);
    }

    @Override
    public Preferences forFile(FileObject fo, String mimeType) {
        File file = fo == null ? null : FileUtil.toFile(fo);
        if (file == null) {
            return null;
        }
        return answer(file, () -> nextProvider(p -> p.forFile(fo, mimeType)), mimeType);
    }

    private Preferences answer(File file, java.util.function.Supplier<Preferences> next, String mimeType) {
        Map<String, String> props = propertiesFor(file);
        if (props == null || props.isEmpty()) {
            return null;
        }
        Preferences base = next.get();
        if (base == null) {
            base = MimeLookup.getLookup(mimeType == null ? MimePath.EMPTY : MimePath.parse(mimeType))
                    .lookup(Preferences.class);
        }
        Preferences editor = base;
        Map<String, String> over = EditorConfigIndentation.overrides(props,
                () -> editor == null ? 8 : editor.getInt(EditorConfigIndentation.TAB_SIZE, 8));
        if (over.isEmpty()) {
            return null; // the file speaks, but not about indentation
        }
        return new OverlayPreferences(base, over);
    }

    /**
     * The file's {@code .editorconfig} properties, or null when they are
     * not known yet (only ever on the EDT, and only until the background
     * lane has resolved the file once).
     */
    static Map<String, String> propertiesFor(File file) {
        String key = file.getAbsolutePath();
        Resolved hit = CACHE.get(key);
        long now = clock.getAsLong();
        if (!SwingUtilities.isEventDispatchThread()) {
            if (hit != null && now - hit.at() < FRESH_MS) {
                return hit.props();
            }
            return resolve(file, key);
        }
        if (hit == null || now - hit.at() >= FRESH_MS) {
            refreshLater(file, key);
        }
        return hit == null ? null : hit.props();
    }

    private static void refreshLater(File file, String key) {
        if (IN_FLIGHT.add(key)) {
            RP.post(() -> {
                try {
                    resolve(file, key);
                } finally {
                    IN_FLIGHT.remove(key);
                }
            });
        }
    }

    private static Map<String, String> resolve(File file, String key) {
        Map<String, String> props;
        try {
            props = Map.copyOf(ProjectFormatting.propertiesFor(file));
        } catch (RuntimeException ex) {
            // a hostile or broken .editorconfig must not take the editor down with it
            LOG.log(Level.INFO, "Could not read the formatting settings for " + file, ex);
            props = Map.of();
        }
        if (CACHE.size() >= CACHE_CAP) {
            CACHE.clear();
        }
        CACHE.put(key, new Resolved(props, clock.getAsLong()));
        return props;
    }

    /** What the next provider in the platform's order would answer. */
    private static Preferences nextProvider(java.util.function.Function<CodeStylePreferences.Provider, Preferences> ask) {
        for (CodeStylePreferences.Provider p : Lookup.getDefault().lookupAll(CodeStylePreferences.Provider.class)) {
            if (p instanceof EditorConfigCodeStyle) {
                continue;
            }
            Preferences answer = ask.apply(p);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    /** The file behind a document, the way the platform's own providers find it. */
    static File fileOf(Document doc) {
        if (doc == null) {
            return null;
        }
        Object stream = doc.getProperty(Document.StreamDescriptionProperty);
        FileObject fo = null;
        if (stream instanceof DataObject) {
            // the file edited, not its DataObject's primary: a file takes the
            // sections that match ITS name (one DataObject can own several)
            fo = org.nmox.studio.core.util.EditedFile.of(doc);
        } else if (stream instanceof FileObject f) {
            fo = f;
        }
        return fo == null ? null : FileUtil.toFile(fo);
    }

    /** For the tests: wait until every queued resolution has landed. */
    static void awaitIdle() {
        RP.post(() -> { }).waitFinished();
    }

    /** For the tests: forget every resolved file and restore the clock. */
    static void resetForTest() {
        awaitIdle();
        CACHE.clear();
        IN_FLIGHT.clear();
        clock = System::currentTimeMillis;
    }
}
