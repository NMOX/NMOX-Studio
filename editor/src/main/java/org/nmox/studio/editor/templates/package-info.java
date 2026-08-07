/**
 * The everyday web file types for the New File wizard (v1.286.0, the
 * project-starter persona walk).
 *
 * <p>On a JavaScript project — in a web IDE — the New File wizard
 * offered no JavaScript file at all: the ClientSide category held only
 * the css-prep module's Sass and LESS entries, HTML and CSS hid under
 * "Other", and JS/TS/JSON existed nowhere. The tell was in
 * {@code WebProjectRecommendedTemplates}: its privileged list has named
 * {@code Templates/ClientSide/javascript.js} and
 * {@code Templates/ClientSide/json.json} since it was written — paths
 * no shipped module ever registered, so the "float the everyday files
 * to the top" feature pointed at nothing. These registrations create
 * the exact paths that list already promises.
 *
 * <p>Content: the JS/TS templates are deliberately EMPTY — the value is
 * the type, the extension, and the name in the wizard, not a guess at
 * the user's module shape. JSON is the one exception: an empty file is
 * not valid JSON, so it starts as {@code {}}.
 */
@NbBundle.Messages({
    "JSTemplate=JavaScript File",
    "TSTemplate=TypeScript File",
    "JSONTemplate=JSON File"
})
@TemplateRegistrations({
    @TemplateRegistration(folder = "ClientSide", position = 100,
            displayName = "#JSTemplate",
            content = "javascript.js",
            requireProject = false),
    @TemplateRegistration(folder = "ClientSide", position = 110,
            displayName = "#TSTemplate",
            content = "typescript.ts",
            requireProject = false),
    @TemplateRegistration(folder = "ClientSide", position = 120,
            displayName = "#JSONTemplate",
            content = "json.json",
            requireProject = false)
})
package org.nmox.studio.editor.templates;

import org.netbeans.api.templates.TemplateRegistration;
import org.netbeans.api.templates.TemplateRegistrations;
import org.openide.util.NbBundle;
