package org.nmox.studio.rack.service;

import java.util.prefs.Preferences;
import org.nmox.studio.rack.engine.KvasirClient;
import org.nmox.studio.rack.engine.KvasirProvider;
import org.nmox.studio.rack.engine.KvasirProvider.Depth;
import org.openide.util.NbPreferences;

/**
 * The editor Ask's depth preference: Fast (the default) or Deep. A plain
 * preference like the KVASIR device's MODEL knob — a depth is not a
 * secret. The depth is provider-independent: it stores {@code fast} or
 * {@code deep}, and {@link #chosen()} answers with the configured
 * provider's id for it (Haiku/Sonnet, GPT-5 mini/GPT-5, Gemini
 * Flash/Pro), so switching providers keeps the user's depth. The chosen
 * model is fixed for a conversation's whole life (mid-conversation
 * switches would make the transcript lie about who answered what); the
 * choice only steers the NEXT Ask.
 */
@org.openide.util.NbBundle.Messages({
    "AskKvasirModel_fast=Fast ({0})",
    "AskKvasirModel_deep=Deep ({0})"
})
public final class AskKvasirModel {

    // userdir-scoped since v2.63.0 (see KvasirConsent.migrated): the remembered
    // depth belongs to an install, not to the machine's JVM-global prefs
    private static final Preferences PREFS = KvasirConsent.migrated(
            NbPreferences.forModule(AskKvasirModel.class),
            Preferences.userNodeForPackage(AskKvasirModel.class));
    private static final String KEY = "kvasir.ask.model";
    /** The provider-neutral tokens stored since v2.96.0. */
    static final String FAST = "fast";
    static final String DEEP = "deep";

    private AskKvasirModel() {
    }

    /** The two offered depths as combo labels, named in the configured provider's words. */
    public static String[] labels() {
        KvasirProvider p = KvasirProvider.configured();
        return new String[] {
            Bundle.AskKvasirModel_fast(p.depthLabel(Depth.FAST)),
            Bundle.AskKvasirModel_deep(p.depthLabel(Depth.DEEP))};
    }

    /**
     * The remembered depth. A pre-v2.96.0 preference stored the Anthropic
     * model id itself; the Sonnet id still reads as Deep so an old choice
     * survives. Unknown values fall back to Fast.
     */
    public static Depth chosenDepth() {
        String stored = PREFS.get(KEY, "");
        return DEEP.equals(stored) || KvasirClient.MODEL_SONNET.equals(stored)
                ? Depth.DEEP : Depth.FAST;
    }

    /** The remembered depth as the configured provider's model id. */
    public static String chosen() {
        return KvasirProvider.configured().model(chosenDepth());
    }

    /** The combo index for the remembered choice. */
    public static int chosenIndex() {
        return chosenDepth() == Depth.DEEP ? 1 : 0;
    }

    /** Remembers a combo choice; anything but index 1 means Fast. */
    public static void remember(int comboIndex) {
        PREFS.put(KEY, comboIndex == 1 ? DEEP : FAST);
    }

    /** Test hook. */
    static void resetForTest() {
        PREFS.remove(KEY);
    }
}
