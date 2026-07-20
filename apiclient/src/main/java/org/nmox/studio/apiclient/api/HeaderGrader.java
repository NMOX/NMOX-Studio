package org.nmox.studio.apiclient.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Grades a response against the web's security-header standards - the
 * checks a 2026 web developer runs at securityheaders.com, built into
 * every send: HSTS, CSP, X-Content-Type-Options, anti-clickjacking
 * (frame-ancestors or X-Frame-Options), Referrer-Policy,
 * Permissions-Policy, and Cross-Origin-Opener-Policy. Deterministic
 * and value-aware, so the verdicts are testable claims, not vibes.
 */
public final class HeaderGrader {

    private HeaderGrader() {
    }

    public enum Verdict {
        PASS, WARN, MISS
    }

    public record Check(String standard, Verdict verdict, String detail) {
    }

    public record Report(String grade, List<Check> checks) {

        public Report {
            checks = List.copyOf(checks); // graded is graded — the report can't be edited
        }
    }

    public static Report grade(Map<String, List<String>> headers) {
        List<Check> checks = new ArrayList<>();
        String hsts = first(headers, "strict-transport-security");
        if (hsts == null) {
            checks.add(new Check("Strict-Transport-Security", Verdict.MISS,
                    "absent — add max-age=31536000; includeSubDomains"));
        } else if (maxAge(hsts) < 15_552_000) {
            checks.add(new Check("Strict-Transport-Security", Verdict.WARN,
                    "max-age under 180 days: " + hsts));
        } else {
            checks.add(new Check("Strict-Transport-Security", Verdict.PASS, hsts));
        }

        // CSP is legitimately sent as MULTIPLE header fields (each an
        // independently-enforced policy). Reading only the first one
        // mis-graded split CSP in both directions: frame-ancestors in
        // the second field looked absent (false MISS), unsafe-inline in
        // the second field looked clean (false PASS). Grade the union.
        String csp = joined(headers, "content-security-policy");
        if (csp == null) {
            checks.add(new Check("Content-Security-Policy", Verdict.MISS,
                    "absent — the single most effective XSS defense"));
        } else if (csp.contains("unsafe-inline") || csp.contains("unsafe-eval")) {
            checks.add(new Check("Content-Security-Policy", Verdict.WARN,
                    "present but allows unsafe-inline/unsafe-eval"));
        } else {
            checks.add(new Check("Content-Security-Policy", Verdict.PASS, "present"));
        }

        String xcto = first(headers, "x-content-type-options");
        checks.add("nosniff".equalsIgnoreCase(String.valueOf(xcto).trim())
                ? new Check("X-Content-Type-Options", Verdict.PASS, "nosniff")
                : new Check("X-Content-Type-Options",
                        xcto == null ? Verdict.MISS : Verdict.WARN,
                        xcto == null ? "absent — add: nosniff" : "unexpected value: " + xcto));

        boolean frameAncestors = csp != null && csp.contains("frame-ancestors");
        String xfo = first(headers, "x-frame-options");
        if (frameAncestors) {
            checks.add(new Check("Clickjacking (frame-ancestors)", Verdict.PASS,
                    "CSP frame-ancestors present"));
        } else if (xfo != null && (xfo.equalsIgnoreCase("DENY") || xfo.equalsIgnoreCase("SAMEORIGIN"))) {
            checks.add(new Check("Clickjacking (X-Frame-Options)", Verdict.PASS, xfo));
        } else {
            checks.add(new Check("Clickjacking protection", Verdict.MISS,
                    "no CSP frame-ancestors and no X-Frame-Options"));
        }

        String referrer = first(headers, "referrer-policy");
        if (referrer == null) {
            checks.add(new Check("Referrer-Policy", Verdict.MISS,
                    "absent — add: strict-origin-when-cross-origin"));
        } else if (referrer.toLowerCase(Locale.ROOT).contains("unsafe-url")) {
            checks.add(new Check("Referrer-Policy", Verdict.WARN, "unsafe-url leaks full URLs"));
        } else {
            checks.add(new Check("Referrer-Policy", Verdict.PASS, referrer));
        }

        checks.add(first(headers, "permissions-policy") != null
                ? new Check("Permissions-Policy", Verdict.PASS, "present")
                : new Check("Permissions-Policy", Verdict.WARN,
                        "absent — declare the features you don't use"));

        checks.add(first(headers, "cross-origin-opener-policy") != null
                ? new Check("Cross-Origin-Opener-Policy", Verdict.PASS, "present")
                : new Check("Cross-Origin-Opener-Policy", Verdict.WARN,
                        "absent — same-origin isolates your window"));

        return new Report(letter(checks), checks);
    }

    /** Every value of a repeatable header, comma-joined; null when absent. */
    private static String joined(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)
                    && !e.getValue().isEmpty()) {
                return String.join(", ", e.getValue());
            }
        }
        return null;
    }

    /**
     * The FIRST value only — correct for the single-value headers, and
     * for HSTS by spec: RFC 6797 §8.1 says a UA processes only the
     * first STS header field it receives.
     */
    private static String first(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)
                    && !e.getValue().isEmpty()) {
                return e.getValue().get(0);
            }
        }
        return null;
    }

    static long maxAge(String hsts) {
        for (String part : hsts.split(";")) {
            String p = part.strip().toLowerCase(Locale.ROOT);
            if (p.startsWith("max-age=")) {
                try {
                    return Long.parseLong(p.substring("max-age=".length()).trim());
                } catch (NumberFormatException ex) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** PASS=2, WARN=1, MISS=0 over 7 checks → a letter, deterministic. */
    static String letter(List<Check> checks) {
        int score = 0;
        for (Check c : checks) {
            score += switch (c.verdict()) {
                case PASS -> 2;
                case WARN -> 1;
                case MISS -> 0;
            };
        }
        if (score >= 13) {
            return "A";
        }
        if (score >= 11) {
            return "B";
        }
        if (score >= 8) {
            return "C";
        }
        if (score >= 5) {
            return "D";
        }
        return "F";
    }
}
