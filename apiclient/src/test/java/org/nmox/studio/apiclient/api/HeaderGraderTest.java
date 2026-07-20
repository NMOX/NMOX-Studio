package org.nmox.studio.apiclient.api;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The security-header grader: value-aware verdicts and a deterministic
 * letter, so the Standards tab makes claims a developer can act on.
 */
class HeaderGraderTest {

    @Test
    @DisplayName("A fully-hardened response grades A with every check passing")
    void hardenedGradesA() {
        var report = HeaderGrader.grade(Map.of(
                "Strict-Transport-Security", List.of("max-age=31536000; includeSubDomains"),
                "Content-Security-Policy", List.of("default-src 'self'; frame-ancestors 'none'"),
                "X-Content-Type-Options", List.of("nosniff"),
                "Referrer-Policy", List.of("strict-origin-when-cross-origin"),
                "Permissions-Policy", List.of("camera=(), microphone=()"),
                "Cross-Origin-Opener-Policy", List.of("same-origin")));
        assertThat(report.grade()).isEqualTo("A");
        assertThat(report.checks()).allMatch(c -> c.verdict() == HeaderGrader.Verdict.PASS);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> report.checks().add(new HeaderGrader.Check("x", HeaderGrader.Verdict.PASS, "y")))
                .as("graded is graded — the report is immutable")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Bare responses grade F; every miss names its fix")
    void bareGradesF() {
        var report = HeaderGrader.grade(Map.of());
        assertThat(report.grade()).isEqualTo("F");
        assertThat(report.checks())
                .filteredOn(c -> c.verdict() == HeaderGrader.Verdict.MISS)
                .allMatch(c -> !c.detail().isBlank());
    }

    @Test
    @DisplayName("Value-aware: short HSTS warns, unsafe-inline CSP warns, wrong nosniff warns")
    void valueAware() {
        var report = HeaderGrader.grade(Map.of(
                "Strict-Transport-Security", List.of("max-age=3600"),
                "Content-Security-Policy", List.of("default-src * 'unsafe-inline'"),
                "X-Content-Type-Options", List.of("sniff-away")));
        assertThat(report.checks())
                .filteredOn(c -> c.standard().startsWith("Strict-Transport"))
                .allMatch(c -> c.verdict() == HeaderGrader.Verdict.WARN);
        assertThat(report.checks())
                .filteredOn(c -> c.standard().startsWith("Content-Security"))
                .allMatch(c -> c.verdict() == HeaderGrader.Verdict.WARN);
        assertThat(report.checks())
                .filteredOn(c -> c.standard().startsWith("X-Content-Type"))
                .allMatch(c -> c.verdict() == HeaderGrader.Verdict.WARN);
    }

    @Test
    @DisplayName("CSP frame-ancestors satisfies clickjacking without X-Frame-Options")
    void frameAncestorsCounts() {
        var report = HeaderGrader.grade(Map.of(
                "Content-Security-Policy", List.of("default-src 'self'; frame-ancestors 'self'")));
        assertThat(report.checks())
                .filteredOn(c -> c.standard().startsWith("Clickjacking"))
                .allMatch(c -> c.verdict() == HeaderGrader.Verdict.PASS);
    }

    @Test
    @DisplayName("max-age parses through junk; header names match case-insensitively")
    void parsingRobustness() {
        assertThat(HeaderGrader.maxAge("includeSubDomains; max-age=100")).isEqualTo(100);
        assertThat(HeaderGrader.maxAge("nonsense")).isZero();
        var report = HeaderGrader.grade(Map.of(
                "sTrIcT-tRaNsPoRt-SeCuRiTy", List.of("max-age=31536000")));
        assertThat(report.checks())
                .filteredOn(c -> c.standard().startsWith("Strict-Transport"))
                .allMatch(c -> c.verdict() == HeaderGrader.Verdict.PASS);
    }

    @Test
    @DisplayName("Split CSP: frame-ancestors in the SECOND header field still counts (no false MISS)")
    void splitCspSecondFieldFrameAncestors() {
        var report = HeaderGrader.grade(Map.of(
                "Content-Security-Policy",
                List.of("default-src 'self'", "frame-ancestors 'none'")));
        assertThat(report.checks())
                .filteredOn(c -> c.standard().startsWith("Clickjacking"))
                .allMatch(c -> c.verdict() == HeaderGrader.Verdict.PASS);
    }

    @Test
    @DisplayName("Split CSP: unsafe-inline in the SECOND header field still warns (no false PASS)")
    void splitCspSecondFieldUnsafeInline() {
        var report = HeaderGrader.grade(Map.of(
                "Content-Security-Policy",
                List.of("default-src 'self'", "script-src 'unsafe-inline'")));
        assertThat(report.checks())
                .filteredOn(c -> c.standard().equals("Content-Security-Policy"))
                .allMatch(c -> c.verdict() == HeaderGrader.Verdict.WARN);
    }
}
