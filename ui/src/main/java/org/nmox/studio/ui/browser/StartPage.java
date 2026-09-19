package org.nmox.studio.ui.browser;

import org.nmox.studio.core.util.PlainText;
import org.openide.util.NbBundle.Messages;

/**
 * What the Browser shows when nothing is serving: a page of this product's own,
 * built here, fetched from nowhere.
 *
 * <p>It used to show Hacker News. A bare {@code ⌥⌘4} loaded
 * {@code https://news.ycombinator.com/} and the tab renamed itself "Hacker
 * News" — a third-party website opening inside a work IDE, and an outbound
 * request the user never asked for, made every time they opened a pane to look
 * at their own running app. A request like that is also a small disclosure: it
 * tells someone else's server that this machine started an IDE, and when.
 *
 * <p>The surrounding logic was already right — a live serving wins
 * (v1.204.0/v1.212.0), and anything routed here by SCOPE or by Run lands on its
 * own URL. Only the empty state was a stranger's page, and an empty state
 * should say what is empty and how to fill it. This one names the door that
 * starts a server, in the reader's own language, and asks the network for
 * nothing.
 *
 * <p>Pure on purpose: it takes its strings and returns a document, so what it
 * paints is a unit test rather than a screenshot. The page is rendered through
 * {@code loadContent} rather than a {@code file:} or {@code data:} URL, so
 * there is no temporary file to clean up and nothing misleading in the address
 * bar — an empty address bar is the truth about a page with no address.
 */
@Messages({
    // the browser TAB's name; the visible heading is StartPage_nothingServing
    "StartPage_title=New Media On X",
    "StartPage_nothingServing=Nothing is serving yet",
    "StartPage_whatHappens=Run your project and its page opens here.",
    "StartPage_door=Run ▸ Run Project, or press GO on a rack device.",
    "StartPage_offline=This page is part of NMOX Studio. It asks the network for nothing."
})
final class StartPage {

    private StartPage() {
    }

    /** The page the Browser shows when no project is serving. */
    static String html() {
        return document(java.util.Locale.getDefault(),
                Bundle.StartPage_title(), Bundle.StartPage_nothingServing(),
                Bundle.StartPage_whatHappens(), Bundle.StartPage_door(), Bundle.StartPage_offline());
    }

    /**
     * The document, from its parts.
     *
     * <p>Every part is escaped even though all five are our own translated
     * chrome: the strings arrive from bundles that fifteen languages write
     * into, and "our own text cannot contain markup" is the assumption behind
     * the class of defect {@code PlainText} exists to close. Escaping costs
     * nothing and removes the assumption.
     */
    static String document(java.util.Locale locale, String title, String heading,
            String whatHappens, String door, String offline) {
        boolean rtl = org.nmox.studio.core.util.TextDirection.isRightToLeft(locale);
        return """
               <!DOCTYPE html>
               <html lang="%s" dir="%s"><head><meta charset="utf-8"/><title>%s</title>
               <style>
                 :root { color-scheme: dark light; }
                 body { margin: 0; display: flex; min-height: 100vh;
                        align-items: center; justify-content: center;
                        font: 15px/1.6 -apple-system, "Segoe UI", system-ui, sans-serif;
                        background: #1b1d1e; color: #d7dadb; }
                 main { max-width: 34rem; padding: 2rem; }
                 h1 { margin: 0 0 .6rem; font-size: 1.35rem; font-weight: 600; color: #f2f4f5; }
                 p { margin: 0 0 .5rem; }
                 .door { color: #9fd3a8; }
                 .offline { margin-top: 1.6rem; font-size: .8rem; color: #7d8486; }
                 @media (prefers-color-scheme: light) {
                   body { background: #f7f8f8; color: #33393b; }
                   h1 { color: #14181a; }
                   .door { color: #2f6f3d; }
                   .offline { color: #767c7e; }
                 }
               </style></head>
               <body><main>
                 <h1>%s</h1>
                 <p>%s</p>
                 <p class="door">%s</p>
                 <p class="offline">%s</p>
               </main></body></html>
               """.formatted(PlainText.escape(locale.getLanguage()), rtl ? "rtl" : "ltr",
                PlainText.escape(title), PlainText.escape(heading),
                PlainText.escape(whatHappens), PlainText.escape(door), PlainText.escape(offline));
    }
}
