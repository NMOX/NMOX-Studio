package org.nmox.studio.rack.service;

import java.util.prefs.Preferences;
import org.nmox.studio.rack.engine.KvasirClient;
import org.openide.util.NbPreferences;

/**
 * The editor Ask's model preference: Fast (Haiku, the default) or Deep
 * (Sonnet). A plain preference like the KVASIR device's MODEL knob — a
 * model id is not a secret. The chosen model is fixed for a
 * conversation's whole life (mid-conversation switches would make the
 * transcript lie about who answered what); the choice only steers the
 * NEXT Ask.
 */
public final class AskKvasirModel {

    // userdir-scoped since v2.63.0 (see KvasirConsent.migrated): the remembered
    // depth belongs to an install, not to the machine's JVM-global prefs
    private static final Preferences PREFS = KvasirConsent.migrated(
            NbPreferences.forModule(AskKvasirModel.class),
            Preferences.userNodeForPackage(AskKvasirModel.class));
    private static final String KEY = "kvasir.ask.model";

    /** The two offered depths, label → model id. */
    public static final String[] LABELS = {"Fast (Haiku)", "Deep (Sonnet)"};

    private AskKvasirModel() {
    }

    /** The remembered model id; unknown stored values fall back to Haiku. */
    public static String chosen() {
        return KvasirClient.MODEL_SONNET.equals(PREFS.get(KEY, ""))
                ? KvasirClient.MODEL_SONNET : KvasirClient.MODEL_HAIKU;
    }

    /** The combo index for the remembered choice. */
    public static int chosenIndex() {
        return KvasirClient.MODEL_SONNET.equals(chosen()) ? 1 : 0;
    }

    /** Remembers a combo choice; anything but index 1 means Haiku. */
    public static void remember(int comboIndex) {
        PREFS.put(KEY, comboIndex == 1
                ? KvasirClient.MODEL_SONNET : KvasirClient.MODEL_HAIKU);
    }

    /** Test hook. */
    static void resetForTest() {
        PREFS.remove(KEY);
    }
}
