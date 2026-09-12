package org.nmox.studio.rack.projectstudio;

import org.nmox.studio.core.util.Bundles;


/**
 * The New Project catalogue, in the reader's language.
 *
 * <p>Fifteen rows a person reads to choose how to start — the most-used
 * catalogue in the product, and English in every translated build until
 * v2.134.0. {@link ProjectTemplates} keeps the English record and the
 * dialog renders it (the v2.101.0 rule, the v2.132.0 placement).
 *
 * <p>A NAME is only sometimes prose. "Vite + React", "Elixir (mix)" and
 * "Angular (standalone)" are mostly technology, and a name a person types
 * or searches for must survive intact; "TypeScript Library" and "Go
 * Service" end in a word. So a name carries a key when somebody wrote one
 * and is returned unchanged when nobody did — the {@code CatalogText} rule
 * (v2.133.0), which also gives a drop-in template the right behaviour for
 * free. A DESCRIPTION is always prose and always carries a key.
 */
public final class TemplateText {

    private TemplateText() {
    }

    /** The row's title: translated where it is prose, as written where it is a name. */
    public static String name(ProjectTemplates template) {
        return lookup("TemplateName_" + template.name(), template.getDisplayName());
    }

    /** The row's second line: what this template gives you. */
    public static String description(ProjectTemplates template) {
        return lookup("TemplateDesc_" + template.name(), template.getDescription());
    }

    private static String lookup(String key, String english) {
        return Bundles.optional(TemplateText.class, key, english);
    }
}
