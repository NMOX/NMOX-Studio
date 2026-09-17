package org.nmox.studio.ui.browser.fx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fits the repaired simple path's width estimates to the font WebKit is using
 * (v2.173.0).
 *
 * <p>Where WebKit's own text path cannot be switched on (Linux, whose WebKit
 * library decides the path at compile time; unknown builds), WebKit measures
 * each glyph alone and {@link ComplexScripts#measuredWidth} estimates the width
 * that glyph will take once shaped: Arabic letters part of the way from their
 * medial to their final form, other scripts a share of the letter's plain
 * advance. Those constants were measured in the macOS Browser. Linux draws with
 * whatever the distribution installed, and there the same constants were far
 * off: Tamil words 25.6 pixels a word on average, Malayalam 14.4, so shaped
 * phrases ran over the text after them.
 *
 * <p>This class fits the parameter to the font itself, once, from a small corpus
 * of real words per script ({@code shaping-corpus.txt}): the blend toward the
 * final form for Arabic, the share for the others. A fit replaces a constant only
 * when it is sane and clearly better on the corpus, by a quarter of the error and
 * a pixel a word: smaller gains measured in the corpus did not hold on words
 * outside it, where the constants were already right.
 */
final class FontFit {

    /** A fitted parameter: Arabic's blend toward the final form, or a share of the plain advance. */
    record Fit(double factor) {
    }

    /** One corpus word as the fit sees it. For Arabic: medial sum, final sum, laid width. Else: plain sum, unused, laid width. */
    record Row(double a, double b, double laid) {
    }

    static final int MIN_WORDS = 8;
    static final double MIN_SHARE = 0.3;
    static final double MAX_SHARE = 1.8;
    /** A fit must leave at most this share of the constant's error... */
    static final double MUST_REDUCE_TO = 0.75;
    /** ...and gain at least this many pixels a word: small in-corpus gains did not hold on words outside it. */
    static final double MUST_GAIN_PX = 1.0;

    private FontFit() {
    }

    /**
     * The blend {@code t} that makes {@code medial + t (final - medial)} closest to
     * the laid width over the corpus, or {@code fallback} unless the rows are enough,
     * the blend stays in {@code [0, 1]}, and it clearly beats {@code fallback}.
     */
    static Fit arabic(List<Row> rows, double fallback) {
        if (rows.size() < MIN_WORDS) {
            return new Fit(fallback);
        }
        double sxy = 0;
        double sxx = 0;
        for (Row r : rows) {
            double d = r.b() - r.a();
            sxy += (r.laid() - r.a()) * d;
            sxx += d * d;
        }
        if (sxx <= 0) {
            return new Fit(fallback);
        }
        double t = sxy / sxx;
        return t >= 0 && t <= 1 && clearlyBetter(arabicError(rows, t), arabicError(rows, fallback))
                ? new Fit(t) : new Fit(fallback);
    }

    static double arabicError(List<Row> rows, double t) {
        double sum = 0;
        for (Row r : rows) {
            sum += Math.abs(r.a() + t * (r.b() - r.a()) - r.laid());
        }
        return sum / rows.size();
    }

    /**
     * The share {@code s} that makes {@code s * plain} closest to the laid width over
     * the corpus, or {@code fallback} unless the rows are enough, the share stays in
     * {@code [MIN_SHARE, MAX_SHARE]}, and it clearly beats {@code fallback}. A second
     * term for the virama was tried and dropped: fitted on a few dozen words it
     * swung to twenty pixels and made words outside the corpus worse.
     */
    static Fit share(List<Row> rows, double fallback) {
        if (rows.size() < MIN_WORDS) {
            return new Fit(fallback);
        }
        double pp = 0;
        double pw = 0;
        for (Row r : rows) {
            pp += r.a() * r.a();
            pw += r.a() * r.laid();
        }
        if (pp <= 0) {
            return new Fit(fallback);
        }
        double s = pw / pp;
        return s >= MIN_SHARE && s <= MAX_SHARE && clearlyBetter(shareError(rows, s), shareError(rows, fallback))
                ? new Fit(s) : new Fit(fallback);
    }

    static double shareError(List<Row> rows, double share) {
        double sum = 0;
        for (Row r : rows) {
            sum += Math.abs(share * r.a() - r.laid());
        }
        return sum / rows.size();
    }

    static boolean clearlyBetter(double fitted, double constant) {
        return fitted <= MUST_REDUCE_TO * constant && constant - fitted >= MUST_GAIN_PX;
    }

    /** The corpus: block start to its words, read once from the resource. */
    static Map<Integer, List<String>> corpus() {
        return Corpus.WORDS;
    }

    private static final class Corpus {
        static final Map<Integer, List<String>> WORDS = read();

        private static Map<Integer, List<String>> read() {
            Map<Integer, List<String>> words = new TreeMap<>();
            InputStream in = FontFit.class.getResourceAsStream("shaping-corpus.txt");
            if (in == null) {
                return Collections.emptyMap();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                for (String raw; (raw = reader.readLine()) != null; ) {
                    // trim BEFORE deciding it is a comment: the parse trims, so a comment
                    // written with one leading space used to reach Integer.parseInt("#")
                    // and throw away the WHOLE corpus — every script silently back on the
                    // macOS constants, which is the regression this class exists to fix
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split("\\s+");
                    List<String> list = new ArrayList<>();
                    for (int i = 1; i < parts.length; i++) {
                        list.add(parts[i]);
                    }
                    words.put(Integer.parseInt(parts[0], 16), Collections.unmodifiableList(list));
                }
            } catch (IOException | RuntimeException ex) {
                return Collections.emptyMap();
            }
            return Collections.unmodifiableMap(words);
        }
    }
}
