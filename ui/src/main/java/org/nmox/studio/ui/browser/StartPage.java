package org.nmox.studio.ui.browser;

import org.nmox.studio.core.util.PlainText;
import org.openide.util.NbBundle.Messages;

/**
 * What the Browser shows when it has no page to show: a page of this product's
 * own, built here, fetched from nowhere. There are two such moments, and until
 * now only one of them said anything.
 *
 * <p><b>Nothing is serving.</b> It used to show Hacker News. A bare
 * {@code ⌥⌘4} loaded {@code https://news.ycombinator.com/} and the tab renamed
 * itself "Hacker News" — a third-party website opening inside a work IDE, and
 * an outbound request the user never asked for, made every time they opened a
 * pane to look at their own running app. A request like that is also a small
 * disclosure: it tells someone else's server that this machine started an IDE,
 * and when.
 *
 * <p><b>Something is serving and it does not answer.</b> The address is
 * registered, the load fails at the socket, and the pane went BLANK — no
 * reason, no way forward, in a product whose oldest standing law is that
 * refusals speak. A server that died, one that is still starting, and one
 * bound to an interface this address does not name all look identical from
 * here, so the page says exactly that and names the address that did not
 * answer.
 *
 * <p>The surrounding logic was already right — a live serving wins
 * (v1.204.0/v1.212.0), and anything routed here by SCOPE or by Run lands on its
 * own URL. Only the empty states were a stranger's page and a blank one, and an
 * empty state should say what is empty and how to fill it. Both name a door in
 * the reader's own language, and both ask the network for nothing.
 *
 * <p>Pure on purpose: it takes its strings and returns a document, so what it
 * paints is a unit test rather than a screenshot. The page is rendered through
 * {@code loadContent} rather than a {@code file:} or {@code data:} URL, so
 * there is no temporary file to clean up and nothing misleading in the address
 * bar — an empty address bar is the truth about a page with no address, and the
 * failure page is the one exception, because there the address is both true and
 * the way to retry.
 */
@Messages({
    // the browser TAB's name; the visible heading is StartPage_nothingServing
    "StartPage_title=New Media On X",
    "StartPage_nothingServing=Nothing is serving yet",
    "StartPage_whatHappens=Run your project and its page opens here.",
    "StartPage_door=Run ▸ Run Project, or press GO on a rack device.",
    "StartPage_offline=This page is part of NMOX Studio. It asks the network for nothing.",
    // the other empty state: an address was loaded and nothing answered there
    "StartPage_noAnswerTitle=No answer",
    "StartPage_noAnswer=That address did not answer",
    "StartPage_noAnswerWhy=Nothing is listening there yet. A server that is still starting, "
        + "one that has stopped, and one bound to a different address all look the same from here.",
    // names the toolbar control by the name it carries (FxBrowserPanel_addressField),
    // because a sentence that tells you where to go must name a door that exists
    "StartPage_noAnswerRetry=Press Enter in the Address field above to try again."
})
final class StartPage {

    /**
     * How much of an address the page prints. A serving URL is short, but the
     * address bar takes anything a person can type, and a page of this
     * product's own does not grow without a ceiling.
     */
    static final int URL_CAP = 200;

    private StartPage() {
    }

    /** The page the Browser shows when no project is serving. */
    static String html() {
        return document(java.util.Locale.getDefault(),
                Bundle.StartPage_title(), Bundle.StartPage_nothingServing(),
                Bundle.StartPage_whatHappens(), Bundle.StartPage_door(), Bundle.StartPage_offline());
    }

    /** The page the Browser shows when {@code url} did not answer. */
    static String noAnswerHtml(String url) {
        return noAnswerDocument(java.util.Locale.getDefault(),
                Bundle.StartPage_noAnswerTitle(), Bundle.StartPage_noAnswer(), url,
                Bundle.StartPage_noAnswerWhy(), Bundle.StartPage_noAnswerRetry(),
                Bundle.StartPage_offline());
    }

    /**
     * The nothing-is-serving document, from its parts.
     *
     * <p>Every part is escaped even though all five are our own translated
     * chrome: the strings arrive from bundles that fifteen languages write
     * into, and "our own text cannot contain markup" is the assumption behind
     * the class of defect {@code PlainText} exists to close. Escaping costs
     * nothing and removes the assumption.
     */
    static String document(java.util.Locale locale, String title, String heading,
            String whatHappens, String door, String offline) {
        return page(locale, title, """
                                     <h1>%s</h1>
                                     <p>%s</p>
                                     <p class="door">%s</p>
                                     <p class="offline">%s</p>
                                   """.formatted(PlainText.escape(heading),
                PlainText.escape(whatHappens), PlainText.escape(door),
                PlainText.escape(offline)));
    }

    /**
     * The did-not-answer document, from its parts.
     *
     * <p>The address is the one part that is NOT our own text — it is whatever
     * was loaded — so it is escaped like the rest and clipped to
     * {@link #URL_CAP} code points, never UTF-16 units (a cut between a
     * surrogate pair strands half a character; the v1.287.0 class). It is
     * printed as text and never as a link: a page explaining that an address
     * did not answer must not offer to fetch it, or anything else.
     *
     * <p>It carries {@code dir="ltr"} of its own. In an Arabic or Hebrew window
     * the document runs right to left, and a URL is a run of neutrals around
     * Latin text — under an RTL base direction its {@code http://} draws at the
     * wrong end (measured in v2.181.0 for exactly this shape). An element's own
     * direction is the HTML-native isolate and needs no control characters.
     */
    static String noAnswerDocument(java.util.Locale locale, String title, String heading,
            String url, String why, String retry, String offline) {
        return page(locale, title, """
                                     <h1>%s</h1>
                                     <p class="url" dir="ltr">%s</p>
                                     <p>%s</p>
                                     <p class="door">%s</p>
                                     <p class="offline">%s</p>
                                   """.formatted(PlainText.escape(heading),
                PlainText.escape(clip(url)), PlainText.escape(why),
                PlainText.escape(retry), PlainText.escape(offline)));
    }

    /** {@code url} at no more than {@link #URL_CAP} code points, saying so when it cut. */
    private static String clip(String url) {
        String s = url == null ? "" : url;
        if (s.codePointCount(0, s.length()) <= URL_CAP) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, URL_CAP)) + "…";
    }

    /**
     * The document around a body: doctype, language, direction and the one
     * style sheet both pages share — one fact with one home, so a colour or a
     * dark-mode rule cannot drift between the two empty states.
     *
     * <p>{@code body} is markup this class built from already-escaped parts.
     */
    private static String page(java.util.Locale locale, String title, String body) {
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
                 .url { font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace;
                        font-size: .9rem; overflow-wrap: anywhere; color: #f2f4f5; }
                 .door { color: #9fd3a8; }
                 .offline { margin-top: 1.6rem; font-size: .8rem; color: #7d8486; }
                 @media (prefers-color-scheme: light) {
                   body { background: #f7f8f8; color: #33393b; }
                   h1 { color: #14181a; }
                   .url { color: #14181a; }
                   .door { color: #2f6f3d; }
                   .offline { color: #767c7e; }
                 }
               </style></head>
               <body><main>
               %s</main></body></html>
               """.formatted(PlainText.escape(locale.getLanguage()), rtl ? "rtl" : "ltr",
                PlainText.escape(title), body);
    }
}
