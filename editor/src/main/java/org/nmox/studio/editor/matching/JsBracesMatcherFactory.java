package org.nmox.studio.editor.matching;

import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.bracesmatching.BracesMatcher;
import org.netbeans.spi.editor.bracesmatching.BracesMatcherFactory;
import org.netbeans.spi.editor.bracesmatching.MatcherContext;
import org.netbeans.spi.editor.bracesmatching.support.BracesMatcherSupport;

/**
 * Highlights the matching (), [], {} for the brace under the caret -
 * the platform's character matcher does the searching, we just declare
 * the pairs for our MIME types.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/javascript", service = BracesMatcherFactory.class),
    @MimeRegistration(mimeType = "text/typescript", service = BracesMatcherFactory.class)
})
public class JsBracesMatcherFactory implements BracesMatcherFactory {

    @Override
    public BracesMatcher createMatcher(MatcherContext context) {
        return BracesMatcherSupport.defaultMatcher(context, -1, -1);
    }
}
