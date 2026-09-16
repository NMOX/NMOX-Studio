package org.nmox.studio.core.spi;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.openide.util.Lookup;

/**
 * A documentation scene a module can stage for the forge (v2.163.0).
 *
 * <p>The user guide and the tutorials illustrate windows that only mean
 * something with data in them: a Task Board mid-sprint, a database grid
 * holding rows, an infrastructure design with named nodes. Those pictures
 * were photographed once, in English, so every translated document showed an
 * English window under a translated sentence.
 *
 * <p>The forge ({@code DocsShots}, ui module) drives the boot that paints
 * them, and it cannot reach the writers that would create those fixtures:
 * the Task Board's own IO is package-private in its own package, and the
 * Infra Designer, DB Studio and API Studio live in modules the ui module
 * does not depend on. So a scene stages ITSELF — each module registers a
 * provider beside the writers it already owns, and the forge asks by
 * {@link #id()}. This is the {@link ProjectAim} arrangement turned around:
 * there the rack publishes and the studios consume; here each studio
 * publishes and the forge consumes.
 *
 * <p>Forge-only by construction. Nothing on a normal boot looks a provider
 * up, and the forge itself runs only under {@code -Dnmox.shots.dir}, so a
 * provider costs a Lookup registration and nothing else. A scene writes
 * fixtures ONLY under the throwaway home the forge hands it.
 *
 * <p>Fixture CONTENT — the card titles, the rows, the node labels — arrives
 * as the text of {@code docs/i18n/forge-fixtures.json} plus the language
 * being painted, and each provider reads the keys it owns. The content is
 * passed rather than resolved here so this seam holds no words of its own
 * and needs no JSON library: a pure core holds data and contracts, never
 * prose (the v2.101.0 rule, gated by {@code SpiHoldsNoProseTest}).
 */
public interface DocsScene {

    /** Every registered scene; empty when no module publishes one. */
    static List<? extends DocsScene> all() {
        return List.copyOf(Lookup.getDefault().lookupAll(DocsScene.class));
    }

    /** The scene this provider stages, named as its picture is. */
    String id();

    /**
     * Writes this scene's fixtures under {@code home} and returns the
     * directory the forge should aim at, or null when the scene needs no
     * aim. Runs off the paint thread; never touches anything outside
     * {@code home}.
     *
     * @param home     the forge's throwaway user home
     * @param fixtures the text of {@code docs/i18n/forge-fixtures.json}
     * @param lang     the language being painted, e.g. {@code de}
     */
    File stage(File home, String fixtures, String lang) throws IOException;

    /**
     * Puts the window into the state the picture shows, on the paint
     * thread, after the forge has aimed at {@link #stage}'s directory and
     * fronted the window. The default does nothing, which is right for a
     * scene whose fixtures alone tell the story.
     */
    default void arrange() {
    }

    /**
     * False while the scene is still settling, so the forge waits before
     * painting. The default is true: a fixture written before the aim is
     * on screen as soon as the window is.
     */
    default boolean ready() {
        return true;
    }
}
