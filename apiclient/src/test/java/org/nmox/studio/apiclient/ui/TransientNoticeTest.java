package org.nmox.studio.apiclient.ui;

import java.awt.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The response strip's notice/verdict model (v2.85.0): a notice shows for a moment and the verdict comes back. */
class TransientNoticeTest {

    @Test
    @DisplayName("a notice replaces the verdict only until it expires; the verdict is never lost")
    void noticeThenVerdictBack() {
        TransientNotice strip = new TransientNotice();
        strip.verdict("200  ·  15ms  ·  60 B", Color.GREEN);
        long gen = strip.notice("curl command copied");
        assertThat(strip.shown().text()).isEqualTo("curl command copied");
        assertThat(strip.shown().color()).isEqualTo(TransientNotice.NOTICE_GRAY);
        TransientNotice.Shown back = strip.expire(gen);
        assertThat(back.text()).as("the walk's find: the verdict came back").isEqualTo("200  ·  15ms  ·  60 B");
        assertThat(back.color()).isEqualTo(Color.GREEN);
    }

    @Test
    @DisplayName("a stale expiry never clobbers a newer notice or a newer verdict")
    void staleExpiryIsInert() {
        TransientNotice strip = new TransientNotice();
        strip.verdict("200", Color.GREEN);
        long first = strip.notice("curl command copied");
        long second = strip.notice("fetch() snippet copied");
        assertThat(strip.expire(first).text()).as("the first timer fires while the second notice shows").isEqualTo("fetch() snippet copied");
        strip.verdict("Sending…", Color.GRAY);
        assertThat(strip.expire(second).text()).as("a send replaced everything; the old timer changes nothing").isEqualTo("Sending…");
        assertThat(strip.clear().text()).isEqualTo(" ");
    }

    @Test
    @DisplayName("the window routes every verdict and notice through the model and arms the restore timer (wiring)")
    void wiring() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/apiclient/ui/ApiClientTopComponent.java"));
        assertThat(src.split("statusLabel\\.setText\\(").length - 1).as("only show() writes the label").isEqualTo(1);
        assertThat(src).contains("strip.notice(text)").contains("strip.expire(gen)").contains("new javax.swing.Timer(NOTICE_MILLIS");
        assertThat(src.split("verdict\\(").length - 1).as("every verdict site rides verdict()").isGreaterThanOrEqualTo(6);
    }
}
