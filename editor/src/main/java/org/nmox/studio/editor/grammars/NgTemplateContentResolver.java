package org.nmox.studio.editor.grammars;

import java.io.File;
import org.nmox.studio.editor.angular.NgTemplates;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MIMEResolver;

/**
 * Suffixless Angular templates → {@code text/x-ng-template} (ledger
 * 73, David's call 2026-08-11: the Angular bet means ALL Angular
 * repos). Angular 21's CLI generates {@code widget.html} beside
 * {@code widget.ts} by default — no {@code .component} in the name for
 * the v1.217.0 declarative resolver to key on — so this resolver
 * decides by CONTENT: an {@code .html} whose same-basename {@code .ts}
 * sibling carries {@code @Component} is a template. The sniff itself
 * lives in {@link NgTemplates}, pure and cached.
 *
 * <p><b>Registration, chosen by scar tissue.</b> v1.217.0 proved live
 * (twice) that a {@code @ServiceProvider} resolver loses to the
 * platform's declarative html claim no matter its position — the
 * LAYER folder's ordered resolvers are consulted first. So this
 * programmatic resolver registers as an {@code .instance} IN that
 * same {@code Services/MIMEResolver} folder, at position 260: after
 * our own cheap name-keyed 250 (a {@code .component.html} never
 * reaches here), before the platform's ext=html at 300.
 */
public final class NgTemplateContentResolver extends MIMEResolver {

    @SuppressWarnings("deprecation") // the mime-declaring ctor IS the .instance contract
    public NgTemplateContentResolver() {
        super("text/x-ng-template");
    }

    @Override
    public String findMIMEType(FileObject fo) {
        if (!"html".equals(fo.getExt())) {
            return null;
        }
        // a non-disk FileObject (jar, in-memory) has no sibling to sniff
        File file = FileUtil.toFile(fo);
        if (file == null) {
            return null;
        }
        return NgTemplates.isAngularTemplate(file) ? "text/x-ng-template" : null;
    }
}
