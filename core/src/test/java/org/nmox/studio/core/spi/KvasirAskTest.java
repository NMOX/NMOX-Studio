package org.nmox.studio.core.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.core.spi.KvasirAsk.Disclosure;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seam's guarantees to every studio that reaches KVASIR through it.
 * A {@link Disclosure} is normalized once, here, so no consumer has to
 * defend against its own nulls — and so the CONSENT dialog, which
 * quotes {@code kind} and {@code what}, can never render "null".
 */
class KvasirAskTest {

    @Test
    @DisplayName("A fully-specified disclosure passes through untouched")
    void fullDisclosureUnchanged() {
        Disclosure d = new Disclosure("api.response", "GET · 500",
                "the response status and body", "Status: 500", "why?");
        assertThat(d.kind()).isEqualTo("api.response");
        assertThat(d.title()).isEqualTo("GET · 500");
        assertThat(d.what()).isEqualTo("the response status and body");
        assertThat(d.body()).isEqualTo("Status: 500");
        assertThat(d.question()).isEqualTo("why?");
    }

    @Test
    @DisplayName("A null or blank kind becomes its own named bucket, never null")
    void kindIsAlwaysNamed() {
        // the kind scopes a CONSENT GRANT — a null would either crash the
        // preference key or, worse, silently share one grant across flows
        assertThat(new Disclosure(null, "t", "w", "b", "q").kind()).isEqualTo("unknown");
        assertThat(new Disclosure("  ", "t", "w", "b", "q").kind()).isEqualTo("unknown");
    }

    @Test
    @DisplayName("A null title still names the window")
    void titleFallsBack() {
        assertThat(new Disclosure("k", null, "w", "b", "q").title()).isEqualTo("KVASIR");
        assertThat(new Disclosure("k", "", "w", "b", "q").title()).isEqualTo("KVASIR");
    }

    @Test
    @DisplayName("Null what/body become empty, so the consent dialog never prints null")
    void textFieldsNeverNull() {
        Disclosure d = new Disclosure("k", "t", null, null, "q");
        assertThat(d.what()).isEmpty();
        assertThat(d.body()).isEmpty();
    }

    @Test
    @DisplayName("A missing question gets a NEUTRAL default, not the code flow's wording")
    void questionDefaultIsNeutral() {
        // v1.172.0: falling through to the engine's default said "Explain
        // what this code does" — wrong words for a response or a query
        String q = new Disclosure("api.response", "t", "w", "b", null).question();
        assertThat(q).isNotBlank().doesNotContain("code");
        assertThat(new Disclosure("k", "t", "w", "b", "   ").question()).isEqualTo(q);
    }

    @Test
    @DisplayName("An empty body survives normalization for the provider to refuse")
    void emptyBodyIsPreserved() {
        // the provider's own guard turns this into "did not start"; the
        // record must not invent content to paper over it
        assertThat(new Disclosure("k", "t", "w", "", "q").body()).isEmpty();
    }
}
