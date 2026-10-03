package org.nmox.studio.editor.snippets;

import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

/**
 * Stands in, for {@link SnippetEngineTest}'s one mime type, for the
 * platform module that fills {@code MimeLookup} from the layer
 * ({@code editor.mimelookup.impl}, which is not on a unit test's
 * classpath): it hands the code-template engine the product's processor
 * factory, as the layer row does in the running IDE. Every other mime
 * type gets nothing, so no other test in this fork sees it.
 *
 * <p>The layer row itself is asserted from the generated layer; this
 * proves what the factory does once the engine has it.
 */
public final class SnippetEngineMimeData implements MimeDataProvider {

    /** The mime type the engine test edits. */
    static final String MIME = "text/x-nmox-snippet-test";

    private static final Lookup OURS = Lookups.fixed(new SnippetTemplateProcessor.Factory());

    @Override
    public Lookup getLookup(MimePath mimePath) {
        return MIME.equals(mimePath.getPath()) ? OURS : Lookup.EMPTY;
    }
}
