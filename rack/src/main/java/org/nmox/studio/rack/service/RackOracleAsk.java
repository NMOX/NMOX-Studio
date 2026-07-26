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
        OracleConversation convo = OracleConversation.forDisclosure(d.title(), d.body());
        // the kind's OWN consent: a grant for one disclosure never
        // authorizes another (the consent-scoping law)
        AskOracleEngine engine = new AskOracleEngine(new OracleClient(),
                OracleKeys::read, c -> OracleConsent.requestKindConsent(d.kind(), d.what()));
        new AskOracleDialog(convo, engine, AskOracleModel.chosen()).open(d.question());
        return true;
    }
}
