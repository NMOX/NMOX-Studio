package org.nmox.studio.rack.devices;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.Signal;
import org.nmox.studio.rack.ui.controls.LcdDisplay;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pure half of the URL in-jack fix: a cabled URL is the truth until
 * the EDT has painted it, then the LCD is — so the router-thread action
 * that runs one signal after {@code url} reads what the cable said, and
 * a URL typed by hand later still wins.
 */
class CabledUrlTest {

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** Holds the EDT until released — the router thread outrunning the paint. */
    private static CountDownLatch blockEdt() {
        CountDownLatch release = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
        return release;
    }

    @Test
    @DisplayName("Before the EDT paints, resolve() answers the cabled URL, not the LCD's stale default")
    void cabledUrlWinsUntilPainted() throws Exception {
        LcdDisplay lcd = new LcdDisplay(200, 1);
        lcd.setText("http://localhost:5173");
        CabledUrl cabled = new CabledUrl();
        CountDownLatch release = blockEdt();
        try {
            assertThat(cabled.deliver(Signal.data("http://localhost:4200/"), lcd, r -> { })).isTrue();
            assertThat(cabled.resolve(lcd))
                    .as("the URL the cable delivered, though the LCD still shows the default")
                    .isEqualTo("http://localhost:4200/");
            assertThat(lcd.getText()).as("the paint is still queued").isEqualTo("http://localhost:5173");
        } finally {
            release.countDown();
        }
        flushEdt();
        assertThat(lcd.getText()).isEqualTo("http://localhost:4200/");
        assertThat(cabled.pending()).as("landed: the LCD is the truth again").isNull();
        assertThat(cabled.resolve(lcd)).isEqualTo("http://localhost:4200/");
    }

    @Test
    @DisplayName("Once painted, a URL typed by hand wins over the earlier cable")
    void handEditWinsAfterLanding() throws Exception {
        LcdDisplay lcd = new LcdDisplay(200, 1);
        CabledUrl cabled = new CabledUrl();
        cabled.deliver(Signal.data("http://localhost:4200/"), lcd, r -> { });
        flushEdt();
        lcd.setText("http://localhost:8080/admin");
        assertThat(cabled.resolve(lcd)).isEqualTo("http://localhost:8080/admin");
    }

    @Test
    @DisplayName("A later delivery replaces an unpainted earlier one and keeps its own claim until it lands")
    void laterDeliveryWins() throws Exception {
        LcdDisplay lcd = new LcdDisplay(200, 1);
        CabledUrl cabled = new CabledUrl();
        CountDownLatch release = blockEdt();
        try {
            cabled.deliver(Signal.data("http://localhost:1111"), lcd, r -> { });
            cabled.deliver(Signal.data("http://localhost:2222"), lcd, r -> { });
            assertThat(cabled.resolve(lcd)).isEqualTo("http://localhost:2222");
        } finally {
            release.countDown();
        }
        flushEdt();
        // the first paint landed and must not clear the second's claim early;
        // by now both have landed and the LCD shows the last one
        assertThat(lcd.getText()).isEqualTo("http://localhost:2222");
        assertThat(cabled.pending()).isNull();
    }

    @Test
    @DisplayName("A payload that is not a URL is refused out loud and never becomes the URL")
    void nonUrlRefusesOutLoud() throws Exception {
        LcdDisplay lcd = new LcdDisplay(200, 1);
        lcd.setText("http://localhost:5173");
        CabledUrl cabled = new CabledUrl();
        List<String> refusals = new ArrayList<>();
        assertThat(cabled.deliver(Signal.data("42 requests in 3s"), lcd, refusals::add)).isFalse();
        assertThat(cabled.deliver(Signal.data(null), lcd, refusals::add)).isFalse();
        assertThat(cabled.deliver(Signal.trigger(), lcd, refusals::add)).isFalse();
        flushEdt();
        assertThat(refusals).hasSize(3).allSatisfy(r -> assertThat(r).startsWith(CabledUrl.REFUSAL));
        assertThat(refusals.get(0)).contains("42 requests in 3s");
        assertThat(cabled.pending()).isNull();
        assertThat(cabled.resolve(lcd)).as("the dialed URL survives a bad cable").isEqualTo("http://localhost:5173");
    }
}
