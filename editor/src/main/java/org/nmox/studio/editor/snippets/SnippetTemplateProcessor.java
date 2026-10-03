package org.nmox.studio.editor.snippets;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.lib.editor.codetemplates.spi.CodeTemplateInsertRequest;
import org.netbeans.lib.editor.codetemplates.spi.CodeTemplateParameter;
import org.netbeans.lib.editor.codetemplates.spi.CodeTemplateProcessor;
import org.netbeans.lib.editor.codetemplates.spi.CodeTemplateProcessorFactory;
import org.nmox.studio.editor.snippets.SnippetBody.Refused;
import org.nmox.studio.editor.snippets.SnippetTemplates.CodeTemplateText;
import org.nmox.studio.editor.snippets.SnippetTemplates.Live;

/**
 * The half of a project snippet the template text cannot carry, handed
 * to the code-template engine through the door the engine has for it.
 *
 * <p>Two things. A starting text that holds a quote or a backslash,
 * which the engine's hint syntax corrupts (see {@link SnippetTemplates}),
 * is set on its parameter here, before the insert, exactly as the Java
 * editor's own processor sets a variable's name. And a tab stop written
 * with a transform ({@code ${1/(.*)/${1:/upcase}/}}) is kept in step
 * with its source as the source is typed.
 *
 * <p>The engine asks every registered factory about every template it
 * inserts, in every language (this one is registered for all of them,
 * since a snippet with no scope applies everywhere). So the factory
 * answers with a processor that does nothing unless the template being
 * inserted is the one {@link SnippetInsertion} announced on the text
 * component a moment ago, compared by its text.
 */
public final class SnippetTemplateProcessor implements CodeTemplateProcessor {

    private static final Logger LOG = Logger.getLogger(SnippetTemplateProcessor.class.getName());

    /** How long the transformed mirrors of one tab stop may take, together, to follow one change to it. */
    static final long LIVE_BUDGET_NANOS = 100_000_000L;

    /** The client property {@link SnippetInsertion} sets around its insert: the {@link CodeTemplateText} being inserted. */
    static final Object PENDING = new Object();

    private static final CodeTemplateProcessor NOT_OURS = new CodeTemplateProcessor() {
        @Override
        public void updateDefaultValues() {
        }

        @Override
        public void parameterValueChanged(CodeTemplateParameter masterParameter, boolean typingChange) {
        }

        @Override
        public void release() {
        }
    };

    private final CodeTemplateInsertRequest request;
    private final CodeTemplateText snippet;

    private SnippetTemplateProcessor(CodeTemplateInsertRequest request, CodeTemplateText snippet) {
        this.request = request;
        this.snippet = snippet;
    }

    @Override
    public void updateDefaultValues() {
        for (CodeTemplateParameter p : request.getMasterParameters()) {
            String start = snippet.values().get(p.getName());
            if (start != null) {
                p.setValue(start);
            }
        }
    }

    @Override
    public void parameterValueChanged(CodeTemplateParameter masterParameter, boolean typingChange) {
        // one keystroke's worth of time for every mirror of this stop
        // together: a body may hold hundreds, each with its own clock
        long deadline = System.nanoTime() + LIVE_BUDGET_NANOS;
        for (Live live : snippet.lives()) {
            if (!live.source().equals(masterParameter.getName())) {
                continue;
            }
            CodeTemplateParameter target = request.getMasterParameter(live.target());
            if (target == null) {
                continue;
            }
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return; // the mirrors not reached keep their last text
            }
            try {
                String shown = SnippetTransforms.apply(live.transform(), masterParameter.getValue(),
                        Math.min(left, SnippetTransforms.BUDGET_NANOS));
                if (!shown.equals(target.getValue())) {
                    target.setValue(shown);
                }
            } catch (Refused refused) {
                // what is typed now runs the transform past its bounds: the
                // mirror keeps its last text rather than show half a result
                LOG.log(Level.FINE, "A snippet transform was not updated because {0}", refused.getMessage());
            }
        }
    }

    @Override
    public void release() {
    }

    /** Registered for every language: the engine collects factories by the document's mime, and the root is every mime's parent. */
    @MimeRegistration(mimeType = "", service = CodeTemplateProcessorFactory.class)
    public static final class Factory implements CodeTemplateProcessorFactory {

        @Override
        public CodeTemplateProcessor createProcessor(CodeTemplateInsertRequest request) {
            JTextComponent component = request.getComponent();
            Object pending = component == null ? null : component.getClientProperty(PENDING);
            if (pending instanceof CodeTemplateText snippet
                    && snippet.text().equals(request.getParametrizedText())) {
                return new SnippetTemplateProcessor(request, snippet);
            }
            return NOT_OURS;
        }
    }
}
