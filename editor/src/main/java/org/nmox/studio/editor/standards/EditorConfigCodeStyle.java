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
 * the settings people write the file for - and draw its right-margin line
 * where the project states one ({@code max_line_length}, or the first of
 * {@code .vscode/settings.json}'s {@code editor.rulers}), and wrap its
 * lines when the project says to ({@code editor.wordWrap};
 * {@link EditorConfigMargin} for both).
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
 *
 * <p><b>The margin is read once, so it is told.</b> Indentation is asked
 * for on every Tab press; the right-margin column is read by the editor's
 * view when it is built, and again only when the document says its
 * {@code text-limit-width} property changed. So when a resolution finds
 * that what a file's project says about the margin is not what was last
 * answered, the open documents of that file are told ({@link #retell(Document)}):
 * the view asks again and gets the answer now in memory. And because
 * nothing else re-asks about a file nobody is typing in, an editor that
 * gains focus is asked about ({@link #onFocus}) - which is how an edit to
 * {@code settings.json} or {@code .editorconfig} reaches the editor one
 * comes back to. Nothing here writes a preference: the user's own
 * right margin is untouched, and every other file keeps it.
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
        watchFocus();
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
        Map<String, String> over = new java.util.LinkedHashMap<>(EditorConfigIndentation.overrides(props,
                () -> editor == null ? 8 : editor.getInt(EditorConfigIndentation.TAB_SIZE, 8)));
        over.putAll(view(props));
        if (over.isEmpty()) {
            return null; // the file speaks, but not about anything the editor reads here
        }
        return new OverlayPreferences(base, over);
    }

    /** What the properties say that an editor's VIEW reads once: the right margin, and whether lines wrap. */
    static Map<String, String> view(Map<String, String> props) {
        return EditorConfigMargin.overrides(props);
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
        Resolved before = CACHE.put(key, new Resolved(props, clock.getAsLong()));
        // the view read its answer when it was built (or was answered null
        // on the EDT a moment ago): if that answer is no longer the one in
        // memory, the open documents of this file are told to ask again
        if (!view(props).equals(before == null ? Map.of() : view(before.props()))) {
            tellOpenDocuments.accept(file);
        }
        return props;
    }

    /** Tells the open documents of a file that what their view reads has changed; a seam for tests. */
    static volatile java.util.function.Consumer<File> tellOpenDocuments = EditorConfigCodeStyle::retellOpenDocuments;

    private static void retellOpenDocuments(File file) {
        SwingUtilities.invokeLater(() -> retellAmong(org.netbeans.api.editor.EditorRegistry.componentList(), file));
    }

    /** Tells the documents of {@code file} among {@code editors}, and no other. */
    static void retellAmong(Iterable<? extends javax.swing.text.JTextComponent> editors, File file) {
        for (javax.swing.text.JTextComponent c : editors) {
            Document doc = c.getDocument();
            if (file.equals(fileOf(doc))) {
                retell(doc);
            }
        }
    }

    /**
     * Makes {@code doc} announce its view properties again. On an editor
     * document each is computed on every read (from the code-style
     * preferences, so from this provider), and putting ANY value fires the
     * property's change without storing it ({@code BaseDocument}'s lazy
     * property map, read from RELEASE310): the view re-reads, and gets
     * what is in memory now. On a plain document the same line stores back
     * the value it just read.
     */
    static void retell(Document doc) {
        for (String property : VIEW_PROPERTIES) {
            Object now = doc.getProperty(property);
            if (now != null) {
                doc.putProperty(property, now);
            }
        }
    }

    /** The document properties an editor's view reads once and then only on a change. */
    static final java.util.List<String> VIEW_PROPERTIES =
            java.util.List.of(EditorConfigMargin.TEXT_LIMIT_WIDTH, EditorConfigMargin.TEXT_LINE_WRAP);

    private static final java.util.concurrent.atomic.AtomicBoolean WATCHING_FOCUS =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Starts asking about an editor when it gains focus. Installed by the
     * first document this provider is asked about - never at boot - and
     * kept for the life of the IDE: one listener, on the editor registry.
     */
    private static void watchFocus() {
        if (WATCHING_FOCUS.compareAndSet(false, true)) {
            org.netbeans.api.editor.EditorRegistry.addPropertyChangeListener(evt -> {
                if (org.netbeans.api.editor.EditorRegistry.FOCUS_GAINED_PROPERTY.equals(evt.getPropertyName())) {
                    javax.swing.text.JTextComponent c = org.netbeans.api.editor.EditorRegistry.lastFocusedComponent();
                    onFocus(c == null ? null : c.getDocument());
                }
            });
        }
    }

    /**
     * An editor gained focus: if what is known about its file has aged, a
     * fresh read is queued (and {@link #retell(Document)} follows when the margin
     * moved). On the EDT, so from memory.
     */
    static void onFocus(Document doc) {
        File file = fileOf(doc);
        if (file != null) {
            propertiesFor(file);
        }
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
        tellOpenDocuments = EditorConfigCodeStyle::retellOpenDocuments;
    }
}
