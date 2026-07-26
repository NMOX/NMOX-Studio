package org.nmox.studio.rack.service;

import org.nmox.studio.core.spi.OracleAsk;
import org.nmox.studio.rack.engine.AskOracleEngine;
import org.nmox.studio.rack.engine.OracleClient;
import org.nmox.studio.rack.engine.OracleConversation;
import org.openide.util.lookup.ServiceProvider;

/**
 * The rack's {@link OracleAsk} provider: studios reach ORACLE through
 * this adapter without compiling against rack (ledger 30, the
 * {@link RackProjectAim} shape).
 *
 * <p>No disclosure logic lives here. The caller hands over text it has
 * already capped and redacted; this class only wires it to the same
 * engine, key source, model preference and conversation window the
 * editor's Ask and the device's EXPLAIN use — so every ORACLE flow
 * passes the identical key and consent gates, and there is exactly one
 * place where a send can happen.
 */
@ServiceProvider(service = OracleAsk.class)
public final class RackOracleAsk implements OracleAsk {

    @Override
    public boolean explain(Disclosure d) {
        if (d == null || d.body().isBlank()) {
            return false;
        }
        // Ask BEFORE opening the window. The engine gates every send too
        // (that law is mutation-proven and stays), but consent-inside-the-
        // send meant the conversation window appeared first and rendered
        // "Thinking…" while the prompt was still on screen — the UI
        // claiming work that consent had not yet allowed, and a declined
        // ask leaving an orphaned window behind. Live-caught 2026-07-26.
        // Once granted this returns immediately, so the per-send gate
        // below is unchanged in behaviour and still defends every turn.
        if (!OracleConsent.requestKindConsent(d.kind(), d.what())) {
            return false;
        }
        OracleConversation convo = OracleConversation.forDisclosure(d.title(), d.body());
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(),
                OracleKeys::read, c -> OracleConsent.requestKindConsent(d.kind(), d.what()));
        new AskOracleDialog(convo, engine, AskOracleModel.chosen()).open(d.question());
        return true;
    }
}
