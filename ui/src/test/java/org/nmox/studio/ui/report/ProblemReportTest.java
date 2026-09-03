package org.nmox.studio.ui.report;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemReportTest {

    @Test
    @DisplayName("The tail keeps the LAST lines, in order, blank lines dropped")
    void tail() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            sb.append("line ").append(i).append(i % 7 == 0 ? "\n\n" : "\n");
        }
        String t = ProblemReport.tail(sb.toString(), 5);
        assertThat(t).isEqualTo("line 96\nline 97\nline 98\nline 99\nline 100");
        assertThat(ProblemReport.tail("", 5)).isEmpty();
        assertThat(ProblemReport.tail(null, 5)).isEmpty();
    }

    @Test
    @DisplayName("Redaction: home path → ~, login → <user>, credential shapes → [redacted]")
    void redact() {
        String log = "INFO: opened /Users/david/NMOX/app (user david)\n"
                + "WARNING: header Authorization: Bearer abcdefghijklmnop\n"
                + "key sk-ant-api03-verysecretvalue123 ghp_0123456789abcdef api_key=hunter2xyz\n"
                + "fine: token count 12";
        String r = ProblemReport.redact(log, "/Users/david", "david");
        assertThat(r).contains("opened ~/NMOX/app (user <user>)");
        assertThat(r).doesNotContain("/Users/david").doesNotContain("abcdefghijklmnop")
                .doesNotContain("sk-ant-").doesNotContain("ghp_0123").doesNotContain("hunter2");
        assertThat(r).contains("[redacted]");
        // a short login never becomes a blanket replacement, and prose survives
        assertThat(ProblemReport.redact("token count 12 by al", "/home/al", "al")).isEqualTo("token count 12 by al");
    }

    @Test
    @DisplayName("The rack's last failed run rides the report — device, command, exit, error lines — or nothing")
    void lastFailure() {
        ProblemReport.LastFailure f = new ProblemReport.LastFailure("VERITAS", "npm test -- --ci", 1,
                java.util.List.of("FAIL src/cart.test.js", "  ● total › applies discount"), 4321);
        String body = ProblemReport.compose("2.65.0", "os", "java", "SEVERE: x", f);
        assertThat(body).contains("**Last failed run**").contains("- Device: VERITAS")
                .contains("- Command: `npm test -- --ci`").contains("- Exit code: 1 after 4321 ms")
                .contains("```\nFAIL src/cart.test.js\n  ● total › applies discount\n```");
        assertThat(body.indexOf("Last failed run")).isLessThan(body.indexOf("Log tail"));
        // a backtick in the command cannot break the markdown code span
        assertThat(ProblemReport.compose("v", "o", "j", "", new ProblemReport.LastFailure("d", "echo `x`", 2,
                java.util.List.of(), 0))).contains("- Command: `echo 'x'`").doesNotContain("after 0 ms");
        assertThat(ProblemReport.compose("v", "o", "j", "", null)).doesNotContain("Last failed run");
    }

    @Test
    @DisplayName("The composed body carries environment first; a clipped URL keeps it and says so")
    void composeAndUrl() {
        String body = ProblemReport.compose("2.64.0", "Mac OS X 15.6 (aarch64)", "25.0.1 (Azul)", "SEVERE: boom");
        assertThat(body).startsWith("**What happened**").contains("- NMOX Studio: 2.64.0")
                .contains("- OS: Mac OS X 15.6 (aarch64)").contains("```\nSEVERE: boom\n```");
        assertThat(ProblemReport.compose(null, null, null, null)).contains("dev build").doesNotContain("Log tail");
        String url = ProblemReport.issueUrl("NMOX Studio 2.64.0: ", body);
        assertThat(url).startsWith(ProblemReport.NEW_ISSUE + "?title=NMOX%20Studio%202.64.0").hasSizeLessThanOrEqualTo(ProblemReport.MAX_URL_CHARS);
        assertThat(ProblemReport.clipped(url)).isFalse();
        String huge = ProblemReport.compose("2.64.0", "os", "java", "x".repeat(20_000));
        String clippedUrl = ProblemReport.issueUrl("t", huge);
        assertThat(clippedUrl).hasSizeLessThanOrEqualTo(ProblemReport.MAX_URL_CHARS);
        assertThat(ProblemReport.clipped(clippedUrl)).isTrue();
        String decoded = URLDecoder.decode(clippedUrl.substring(clippedUrl.indexOf("body=") + 5), StandardCharsets.UTF_8);
        assertThat(decoded).startsWith("**What happened**").contains("- NMOX Studio: 2.64.0").endsWith("for the rest]");
    }
}
