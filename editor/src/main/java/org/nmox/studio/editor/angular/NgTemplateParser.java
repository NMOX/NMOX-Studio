package org.nmox.studio.editor.angular;

import java.util.List;

import javax.swing.event.ChangeListener;

import org.netbeans.modules.csl.api.Error;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;

/**
 * The minimal parser that makes CSL's native gestures reachable on
 * Angular templates (Angular-top arc, 2026-08-11). CSL routes ⌘B /
 * Navigate ▸ Go to Declaration through {@code DeclarationFinder}, and a
 * finder is only ever consulted WITH a {@link ParserResult} — a CSL
 * language without a parser silently no-ops the whole gesture family,
 * which is exactly what v1.219.0 measured and worked around with a
 * custom mime action. This parser does no analysis at all: its result
 * is the snapshot itself (the finder needs only text + file), and it
 * reports no diagnostics so the editor gains no new error badges.
 */
public final class NgTemplateParser extends Parser {

    private NgResult result;

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent event) {
        result = new NgResult(snapshot);
    }

    @Override
    public Result getResult(Task task) {
        return result;
    }

    @Override
    public void addChangeListener(ChangeListener changeListener) {
        // results never change after parse; nothing to notify
    }

    @Override
    public void removeChangeListener(ChangeListener changeListener) {
    }

    /** A snapshot-only result: no structure, no diagnostics. */
    public static final class NgResult extends ParserResult {

        NgResult(Snapshot snapshot) {
            super(snapshot);
        }

        @Override
        public List<? extends Error> getDiagnostics() {
            return List.of();
        }

        @Override
        protected void invalidate() {
        }
    }
}
