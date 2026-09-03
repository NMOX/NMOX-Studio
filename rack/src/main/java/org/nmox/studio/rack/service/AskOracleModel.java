package org.nmox.studio.rack.service;

import java.util.prefs.Preferences;
import org.nmox.studio.rack.engine.OracleClient;
import org.openide.util.NbPreferences;

/**
 * The editor Ask's model preference: Fast (Haiku, the default) or Deep
 * (Sonnet). A plain preference like the ORACLE device's MODEL knob — a
 * model id is not a secret. The chosen model is fixed for a
 * conversation's whole life (mid-conversation switches would make the
 * transcript lie about who answered what); the choice only steers the
 * NEXT Ask.
 */
public final class AskOracleModel {

    // userdir-scoped since v2.63.0 (see OracleConsent.migrated): the remembered
    // depth belongs to an install, not to the machine's JVM-global prefs
    private static final Preferences PREFS = OracleConsent.migrated(
            NbPreferences.forModule(AskOracleModel.class),
            Preferences.userNodeForPackage(AskOracleModel.class));
    private static final String KEY = "oracle.ask.model";

    /** The two offered depths, label → model id. */
    public static final String[] LABELS = {"Fast (Haiku)", "Deep (Sonnet)"};

    private AskOracleModel() {
    }

    /** The remembered model id; unknown stored values fall back to Haiku. */
    public static String chosen() {
        return OracleClient.MODEL_SONNET.equals(PREFS.get(KEY, ""))
                ? OracleClient.MODEL_SONNET : OracleClient.MODEL_HAIKU;
    }

    /** The combo index for the remembered choice. */
    public static int chosenIndex() {
        return OracleClient.MODEL_SONNET.equals(chosen()) ? 1 : 0;
    }

    /** Remembers a combo choice; anything but index 1 means Haiku. */
    public static void remember(int comboIndex) {
        PREFS.put(KEY, comboIndex == 1
                ? OracleClient.MODEL_SONNET : OracleClient.MODEL_HAIKU);
    }

    /** Test hook. */
    static void resetForTest() {
        PREFS.remove(KEY);
    }
}
