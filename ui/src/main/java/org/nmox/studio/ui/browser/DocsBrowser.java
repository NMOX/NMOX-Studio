package org.nmox.studio.ui.browser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.nmox.studio.core.spi.DocsScene;
import org.nmox.studio.core.util.DocsFixtures;
import org.nmox.studio.core.util.PlainText;
import org.nmox.studio.ui.browser.fx.FxBrowserPanel;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Stages the DevTools picture (v2.164.0): the shop's front page served on
 * loopback, the in-app Browser showing it, and its heading picked in the
 * DOM tree with the page ringing the same element.
 *
 * <p>The page is written here, in the reader's language, and served by the
 * forge script's fixture server from {@link #SITE} under the demo shop. The
 * pick lands through the same handler a click with Pick element armed
 * reaches, so the tree, the highlight and the computed styles are the
 * product's own. The Browser needs JavaFX; the forge script boots on a
 * runtime that has it, and without one the window paints its honest
 * "needs the bundled runtime" panel and this scene never becomes ready.
 */
@ServiceProvider(service = DocsScene.class)
public final class DocsBrowser implements DocsScene {

    /** The scene's name, as its picture is named. */
    public static final String ID = "story-06-devtools-pick";

    /** The page's directory under the demo shop. */
    public static final String SITE = "site";

    /** Where the forge's fixture server answers. */
    public static final String URL = "http://127.0.0.1:3000/";

    /** The shop's name — a name, the same in every language. */
    static final String BRAND = "Meridian Coffee";

    /** body › header.hero › h1, as element-child indices from {@code <html>}. */
    static final List<Integer> HEADING = List.of(1, 0, 0);

    private int phase;
    private long phaseAt;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public File stage(File home, String fixtures, String lang) throws IOException {
        Path site = DocsFixtures.projectDir(home).toPath().resolve(SITE);
        Files.createDirectories(site);
        Files.writeString(site.resolve("index.html"), page(lang,
                DocsFixtures.text(fixtures, lang, "page", "tagline"),
                DocsFixtures.text(fixtures, lang, "page", "heading"),
                DocsFixtures.text(fixtures, lang, "page", "order")), StandardCharsets.UTF_8);
        Files.writeString(site.resolve("style.css"), STYLE, StandardCharsets.UTF_8);
        phase = 0;
        return site.toFile();
    }

    /** The shop front. Its structure is fixed so {@link #HEADING} always names the h1. */
    static String page(String lang, String tagline, String heading, String order) {
        String dir = "he".equals(lang) || "ar".equals(lang) ? "rtl" : "ltr";
        return "<!doctype html>\n"
                + "<html lang=\"" + PlainText.escape(lang) + "\" dir=\"" + dir + "\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <title>" + BRAND + "</title>\n"
                + "  <link rel=\"stylesheet\" href=\"style.css\">\n"
                + "</head>\n"
                + "<body>\n"
                + "  <header class=\"hero\">\n"
                + "    <h1>" + BRAND + "</h1>\n"
                + "    <p>" + PlainText.escape(tagline) + "</p>\n"
                + "  </header>\n"
                + "  <main class=\"card\">\n"
                + "    <h2>" + PlainText.escape(heading) + "</h2>\n"
                + "    <a class=\"order\" href=\"#order\">" + PlainText.escape(order) + "</a>\n"
                + "  </main>\n"
                + "</body>\n"
                + "</html>\n";
    }

    static final String STYLE = """
            body { margin: 0; font-family: Georgia, serif; background: #f6efe6; color: #3b2a22; }
            .hero { background: #4a2f27; color: #e8c9a0; text-align: center; padding: 72px 24px 88px; }
            .hero h1 { font-size: 32px; font-weight: 700; margin: 0 0 16px; }
            .hero p { margin: 0; font-size: 18px; }
            .card { max-width: 560px; margin: -32px auto 0; background: #fff; border-radius: 12px;
                    padding: 24px 32px; box-shadow: 0 2px 8px rgba(0, 0, 0, .12); }
            html[lang="ar"] p, html[lang="ar"] h2, html[lang="ar"] .order {
                font-family: "Geeza Pro", "SF Arabic", "Noto Naskh Arabic", serif; }
            .order { display: inline-block; margin-top: 8px; color: #fff; background: #8a4b2d;
                     padding: 8px 16px; border-radius: 6px; text-decoration: none; }
            """;

    @Override
    public void arrange() {
        phase = 0;
        phaseAt = System.currentTimeMillis();
        WebBrowserTopComponent window = window();
        if (window != null) {
            window.showUrl(URL);
        }
    }

    /** Polled on the EDT: the page loaded, then the pick landed, then a beat for the styles. */
    @Override
    public boolean ready() {
        WebBrowserTopComponent window = window();
        FxBrowserPanel browser = window == null ? null : window.docsBrowser();
        if (browser == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        switch (phase) {
            case 0 -> {
                if (!browser.docsLoaded(URL)) {
                    return false;
                }
                browser.docsPick(HEADING);
                phase = 1;
                phaseAt = now;
                return false;
            }
            case 1 -> {
                if (!browser.docsPicked()) {
                    return false;
                }
                phase = 2;
                phaseAt = now;
                return false;
            }
            default -> {
                return now - phaseAt >= 1_500;
            }
        }
    }

    private static WebBrowserTopComponent window() {
        TopComponent tc = WindowManager.getDefault().findTopComponent("WebBrowserTopComponent");
        return tc instanceof WebBrowserTopComponent w ? w : null;
    }
}
