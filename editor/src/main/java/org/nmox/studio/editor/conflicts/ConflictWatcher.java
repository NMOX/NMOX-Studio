package org.nmox.studio.editor.conflicts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.nmox.studio.editor.conflicts.MergeConflicts.Block;
import org.nmox.studio.editor.conflicts.MergeConflicts.Resolution;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Merge conflicts, resolved where they are written (3.2.0): in every text
 * editor, a conflict block's current side is tinted one color and its
 * incoming side another, and its {@code <<<<<<<} line carries a warning
 * whose fixes are VS Code's three — Accept Current Change, Accept
 * Incoming Change, Accept Both Changes ({@link ConflictFix}).
 *
 * <p><b>The hook.</b> A highlights-layer factory registered for EVERY mime
 * (the root {@code MimeRegistration}, the {@code GhostText} idiom): a
 * conflicted file can be any kind of file. The first editor built for a
 * document attaches ONE watcher to it (a document property is the guard,
 * the {@code GitSummaryLineHint} idiom), and every view of that document
 * — a split, a clone — shares the watcher's one bag, so one scan feeds
 * the tints and the hints alike.
 *
 * <p><b>Bounded, and off the paint thread.</b> The scan rides a named lane
 * 250 ms after the last keystroke, under {@code doc.render}; a document
 * over {@link #MAX_CHARS} is never read (git does not write conflicts
 * into files that size by hand-resolution, and a log or a dump must not
 * cost a full copy on every pause), and a text with no {@code <<<<<<<}
 * costs one {@code indexOf}. A document that never had a conflict never
 * touches the bag or the hints.
 *
 * <p><b>Colors a theme can change.</b> The three tints are highlight
 * colorings registered in the layer for the NetBeans and FlatLaf profiles
 * (Tools ▸ Options ▸ Fonts &amp; Colors ▸ Highlighting names them), read
 * from the document's {@link FontColorSettings} on every scan; a profile
 * that does not define them gets the same translucent defaults, VS Code's
 * own, which read on a light and a dark editor alike.
 *
 * <p>No {@code removeDocumentListener} anywhere, on purpose (the
 * {@code CssColorHighlighter} idiom): the only strong holders of a watcher
 * are its document and a briefly pending lane task, so it is collected
 * with the document.
 */
@NbBundle.Messages({
    "MergeConflict_hint=Merge conflict: accept the current change, the incoming change, or both",
    "MergeConflict_acceptCurrent=Accept Current Change",
    "MergeConflict_acceptIncoming=Accept Incoming Change",
    "MergeConflict_acceptBoth=Accept Both Changes",
    "MergeConflict_changed=The conflict changed after its hint was shown, so nothing was replaced",
    // the highlight colorings' names in Tools > Options > Fonts & Colors:
    // the keys ARE the coloring names the layer's colors file declares
    "nmox_merge_current=Merge Conflict: Current Change",
    "nmox_merge_incoming=Merge Conflict: Incoming Change",
    "nmox_merge_base=Merge Conflict: Common Ancestor"
})
public final class ConflictWatcher implements DocumentListener {

    /** The hints layer the conflict warnings own; setting it empty clears it. */
    static final String HINTS_LAYER = "nmox-merge-conflicts";

    /** The largest document scanned, in characters. */
    static final int MAX_CHARS = 2 * 1024 * 1024;

    /** At most this many warnings; the tints still cover every block. */
    static final int MAX_HINTS = 500;

    /** The coloring names, as the layer's colors file declares them. */
    static final String CURRENT = "nmox_merge_current";
    static final String INCOMING = "nmox_merge_incoming";
    static final String BASE = "nmox_merge_base";

    // VS Code's merge.currentContentBackground / incomingContentBackground /
    // commonContentBackground: translucent, so one value suits any theme
    private static final Color CURRENT_DEFAULT = new Color(0x40, 0xC8, 0xAE, 0x33);
    private static final Color INCOMING_DEFAULT = new Color(0x40, 0xA6, 0xFF, 0x33);
    private static final Color BASE_DEFAULT = new Color(0x60, 0x60, 0x60, 0x29);

    private static final RequestProcessor RP = new RequestProcessor("nmox-merge-conflicts", 1);

    private final Document doc;
    private final OffsetsBag bag;
    private RequestProcessor.Task pending;
    /** Whether the last scan published anything: only then must a clean one clear it. */
    private boolean published;

    private ConflictWatcher(Document doc) {
        this.doc = doc;
        this.bag = new OffsetsBag(doc);
    }

    /** The document's watcher, attached (and scanned) the first time it is asked for. */
    static ConflictWatcher of(Document doc) {
        synchronized (doc) {
            Object w = doc.getProperty(ConflictWatcher.class);
            if (w instanceof ConflictWatcher watcher) {
                return watcher;
            }
            ConflictWatcher watcher = new ConflictWatcher(doc);
            doc.putProperty(ConflictWatcher.class, watcher);
            doc.addDocumentListener(watcher);
            watcher.schedule(0);
            return watcher;
        }
    }

    OffsetsBag bag() {
        return bag;
    }

    private synchronized void schedule(int delayMillis) {
        if (pending != null) {
            pending.cancel();
        }
        pending = RP.post(this::scan, delayMillis);
    }

    /** One scan: read (bounded), parse, then publish tints and hints. On the lane. */
    void scan() {
        List<Block> blocks = List.of();
        String[] text = {null};
        if (doc.getLength() <= MAX_CHARS) {
            doc.render(() -> {
                try {
                    text[0] = doc.getText(0, doc.getLength());
                } catch (BadLocationException ex) {
                    text[0] = null;
                }
            });
            blocks = MergeConflicts.scan(text[0]);
        }
        if (blocks.isEmpty() && !published) {
            return;
        }
        publish(text[0], blocks);
        published = !blocks.isEmpty();
    }

    private void publish(String text, List<Block> blocks) {
        OffsetsBag fresh = new OffsetsBag(doc);
        AttributeSet current = coloring(CURRENT, CURRENT_DEFAULT);
        AttributeSet incoming = coloring(INCOMING, INCOMING_DEFAULT);
        AttributeSet base = coloring(BASE, BASE_DEFAULT);
        List<ErrorDescription> hints = new ArrayList<>();
        for (Block b : blocks) {
            // the header line and our side; the base marker and the base;
            // their side and the trailer — the ======= line stays plain,
            // the divider it is
            fresh.addHighlight(b.start(), b.oursEnd(), current);
            if (b.hasBase()) {
                fresh.addHighlight(b.oursEnd(), b.baseEnd(), base);
            }
            fresh.addHighlight(b.theirsStart(), b.end(), incoming);
            if (hints.size() < MAX_HINTS) {
                ErrorDescription hint = describeFor(doc, text, b);
                if (hint != null) {
                    hints.add(hint);
                }
            }
        }
        bag.setHighlights(fresh);
        HintsController.setErrors(doc, HINTS_LAYER, hints);
    }

    /** The warning on a block's header line, with its three fixes; null if the document moved. */
    static ErrorDescription describeFor(Document doc, String text, Block b) {
        try {
            String blockText = text.substring(b.start(), b.end());
            List<Fix> fixes = new ArrayList<>();
            for (Resolution r : Resolution.values()) {
                fixes.add(new ConflictFix(doc, doc.createPosition(b.start()), blockText, r));
            }
            return ErrorDescriptionFactory.createErrorDescription(Severity.WARNING,
                    Bundle.MergeConflict_hint(), fixes, doc,
                    doc.createPosition(b.start()), doc.createPosition(b.headerEnd()));
        } catch (BadLocationException ex) {
            // the document shrank after the read; the next edit scans again
            return null;
        }
    }

    /** A coloring from the document's profile, else VS Code's translucent default. */
    private AttributeSet coloring(String name, Color fallback) {
        AttributeSet set = null;
        try {
            Object mime = doc.getProperty("mimeType");
            MimePath path = mime instanceof String m && !m.isEmpty() ? MimePath.parse(m) : MimePath.EMPTY;
            FontColorSettings fcs = MimeLookup.getLookup(path).lookup(FontColorSettings.class);
            set = fcs == null ? null : fcs.getFontColors(name);
        } catch (IllegalArgumentException ex) {
            // a mime the platform will not parse: the default serves
            set = null;
        }
        if (set != null && set.getAttribute(StyleConstants.Background) != null) {
            return AttributesUtilities.createImmutable(
                    StyleConstants.Background, set.getAttribute(StyleConstants.Background));
        }
        return AttributesUtilities.createImmutable(StyleConstants.Background, fallback);
    }

    /** Test barrier: drains the scanning lane. */
    static void awaitQuiet() {
        RP.post(() -> {
        }).waitFinished();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        schedule(250);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        schedule(250);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        // attribute-only change: the text, and so the blocks, are the same
    }

    /**
     * The per-editor hook: every mime (root registration), one layer on the
     * document's shared bag. No position on purpose — the root folder's
     * rows are unpositioned and a lone positioned one makes the platform
     * warn on every boot (the v2.28.0 quieter-boot law, as {@code GhostText}
     * found). The DEFAULT rack sits above the caret row and syntax coloring
     * and below search, occurrences, warnings and the selection, so a
     * selected or found word inside a conflict still shows as one.
     */
    @MimeRegistration(mimeType = "", service = HighlightsLayerFactory.class)
    public static final class Factory implements HighlightsLayerFactory {
        @Override
        public HighlightsLayer[] createLayers(Context context) {
            ConflictWatcher watcher = of(context.getDocument());
            return new HighlightsLayer[]{
                HighlightsLayer.create("nmox-merge-conflicts", ZOrder.DEFAULT_RACK, true, watcher.bag())
            };
        }
    }
}
