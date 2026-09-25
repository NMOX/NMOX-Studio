package org.nmox.studio.rack.projectstudio;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The places a terminal line points at (3.2): the forms real tools print,
 * and the look-alikes that must never be followed.
 */
class TerminalLinksTest {

    @TempDir
    Path tmp;

    private static TerminalLinks.Link only(String line) {
        List<TerminalLinks.Link> links = TerminalLinks.find(line);
        assertThat(links).as("links on: " + line).hasSize(1);
        return links.get(0);
    }

    @Test
    @DisplayName("tsc: src/app.ts:42:7 - error TS2322")
    void tsc() {
        TerminalLinks.Link l = only("src/app.ts:42:7 - error TS2322: Type 'string' is not assignable to type 'number'.");
        assertThat(l.path()).isEqualTo("src/app.ts");
        assertThat(l.line()).isEqualTo(42);
        assertThat(l.column()).isEqualTo(7);
        assertThat(l.start()).isZero();
        assertThat(l.end()).isEqualTo("src/app.ts:42:7".length());
    }

    @Test
    @DisplayName("tsc's older form: src/app.ts(42,7): error")
    void tscParens() {
        TerminalLinks.Link l = only("src/app.ts(42,7): error TS2304: Cannot find name 'x'.");
        assertThat(l.path()).isEqualTo("src/app.ts");
        assertThat(l.line()).isEqualTo(42);
        assertThat(l.column()).isEqualTo(7);
        assertThat(l.end()).isEqualTo("src/app.ts(42,7)".length());
    }

    @Test
    @DisplayName("eslint unix/compact: /abs/x.js:3:5: message")
    void eslint() {
        TerminalLinks.Link l = only("/home/me/proj/src/x.js:3:5: 'a' is defined but never used. [no-unused-vars]");
        assertThat(l.path()).isEqualTo("/home/me/proj/src/x.js");
        assertThat(l.line()).isEqualTo(3);
        assertThat(l.column()).isEqualTo(5);
    }

    @Test
    @DisplayName("node / jest stack frame: at fn (/abs/path.js:10:5)")
    void nodeFrame() {
        String line = "    at Object.<anonymous> (/Users/me/app/src/sum.test.js:10:5)";
        TerminalLinks.Link l = only(line);
        assertThat(l.path()).isEqualTo("/Users/me/app/src/sum.test.js");
        assertThat(l.line()).isEqualTo(10);
        assertThat(l.column()).isEqualTo(5);
        assertThat(line.substring(l.start(), l.end())).isEqualTo("/Users/me/app/src/sum.test.js:10:5");
    }

    @Test
    @DisplayName("node frame without a function: at /abs/path.js:10:5")
    void bareNodeFrame() {
        assertThat(only("    at /srv/app/index.js:7:13").path()).isEqualTo("/srv/app/index.js");
    }

    @Test
    @DisplayName("an ES module frame: file:///abs/x.mjs:3:1 (the whole URL is the click target)")
    void esmFrame() {
        String line = "    at file:///Users/me/app/main.mjs:3:1";
        TerminalLinks.Link l = only(line);
        assertThat(l.path()).isEqualTo("/Users/me/app/main.mjs");
        assertThat(l.line()).isEqualTo(3);
        assertThat(line.substring(l.start(), l.end())).isEqualTo("file:///Users/me/app/main.mjs:3:1");
    }

    @Test
    @DisplayName("pytest: tests/test_x.py:12: AssertionError")
    void pytest() {
        TerminalLinks.Link l = only("tests/test_x.py:12: AssertionError");
        assertThat(l.path()).isEqualTo("tests/test_x.py");
        assertThat(l.line()).isEqualTo(12);
        assertThat(l.column()).isZero();
    }

    @Test
    @DisplayName("go: ./main.go:3:5: undefined: x")
    void go() {
        TerminalLinks.Link l = only("./main.go:3:5: undefined: x");
        assertThat(l.path()).isEqualTo("./main.go");
        assertThat(l.line()).isEqualTo(3);
        assertThat(l.column()).isEqualTo(5);
    }

    @Test
    @DisplayName("rustc: --> src/main.rs:3:5")
    void rustc() {
        TerminalLinks.Link l = only("  --> src/main.rs:3:5");
        assertThat(l.path()).isEqualTo("src/main.rs");
        assertThat(l.line()).isEqualTo(3);
        assertThat(l.column()).isEqualTo(5);
    }

    @Test
    @DisplayName("a Python traceback: File \"x.py\", line 12, in <module> (a quoted path may hold spaces)")
    void pythonTraceback() {
        String line = "  File \"/Users/me/my app/x.py\", line 12, in <module>";
        TerminalLinks.Link l = only(line);
        assertThat(l.path()).isEqualTo("/Users/me/my app/x.py");
        assertThat(l.line()).isEqualTo(12);
        assertThat(line.substring(l.start(), l.end())).isEqualTo("File \"/Users/me/my app/x.py\", line 12");
    }

    @Test
    @DisplayName("Python's own frames name no file: <stdin>, <frozen importlib._bootstrap>")
    void pythonPseudoFiles() {
        assertThat(TerminalLinks.find("  File \"<stdin>\", line 1, in <module>")).isEmpty();
        assertThat(TerminalLinks.find("  File \"<frozen importlib._bootstrap>\", line 241, in _call")).isEmpty();
    }

    @Test
    @DisplayName("a Windows path keeps its drive: C:\\proj\\src\\app.ts:4:2")
    void windowsDrive() {
        TerminalLinks.Link l = only("C:\\proj\\src\\app.ts:4:2 - error TS1005");
        assertThat(l.path()).isEqualTo("C:\\proj\\src\\app.ts");
        assertThat(l.line()).isEqualTo(4);
    }

    @Test
    @DisplayName("a Next.js route keeps its brackets: app/[slug]/page.tsx:12:5")
    void bracketedSegment() {
        assertThat(only("./app/[slug]/page.tsx:12:5").path()).isEqualTo("./app/[slug]/page.tsx");
    }

    @Test
    @DisplayName("a file named in Devanagari is one link: its vowel signs stay with their letters")
    void devanagariFileName() {
        TerminalLinks.Link l = only("\u091f\u093e\u0938\u094d\u0915.js:3:1 - error");
        assertThat(l.path()).isEqualTo("\u091f\u093e\u0938\u094d\u0915.js");
        assertThat(l.start()).isZero();
    }

    @Test
    @DisplayName("an option's value is not part of its path: --file=src/a.ts:3")
    void optionValue() {
        assertThat(only("error in --file=src/a.ts:3").path()).isEqualTo("src/a.ts");
    }

    @Test
    @DisplayName("two links on one line are both found, left to right")
    void twoOnALine() {
        List<TerminalLinks.Link> links = TerminalLinks.find("a.ts:1:2 imported from b.ts:3:4");
        assertThat(links).extracting(TerminalLinks.Link::path).containsExactly("a.ts", "b.ts");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Local:   http://localhost:5173/",
        "listening on http://x:8080",
        "https://example.com/assets/app.js:3",
        "clone user@host:src/app.js:3",
        "see //cdn.example.com/lib.js:3",
        "curl http://127.0.0.1:8080/index.html",
        "server on localhost:3000",
        "Time: 12:30:45",
        "at 12:30:45.123Z",
        "bound 127.0.0.1:8080",
        "version 1.2.3:4",
        "src/app.ts:abc",
        "src/app.ts:0:1",
        "src/app.ts:12abc",
        "src/app.ts",
        "package.json(",
        "see Makefile:12",
        "a.ts(4",
        "a.ts(4,)",
        "12345678 bytes: done.",
        ""
    })
    @DisplayName("look-alikes are never links: URLs, host:port, clock times, versions, a line 0")
    void notLinks(String line) {
        assertThat(TerminalLinks.find(line)).as(line).isEmpty();
    }

    @Test
    @DisplayName("a host:port with a dot is syntactically a file; the resolver, not the scanner, refuses it")
    void hostWithADotIsLeftToTheDisk() {
        assertThat(only("connecting to example.com:443").path()).isEqualTo("example.com");
    }

    @Test
    @DisplayName("a line number longer than any file is not one")
    void absurdLineNumber() {
        assertThat(TerminalLinks.find("a.ts:123456789:1")).isEmpty();
    }

    @Test
    @DisplayName("the click picks the link under it; off every link, none")
    void atColumn() {
        String line = "a.ts:1:2 imported from b.ts:3:4";
        assertThat(TerminalLinks.at(line, 0).path()).isEqualTo("a.ts");
        assertThat(TerminalLinks.at(line, 7).path()).isEqualTo("a.ts");
        assertThat(TerminalLinks.at(line, 8)).isNull();
        assertThat(TerminalLinks.at(line, line.indexOf("b.ts")).path()).isEqualTo("b.ts");
        assertThat(TerminalLinks.at(line, line.length() - 1).path()).isEqualTo("b.ts");
    }

    @Test
    @DisplayName("where on the line is unknown: the only link, or none rather than a guess between two")
    void atUnknownColumn() {
        assertThat(TerminalLinks.at("  --> src/main.rs:3:5", -1).path()).isEqualTo("src/main.rs");
        assertThat(TerminalLinks.at("a.ts:1:2 imported from b.ts:3:4", -1)).isNull();
    }

    @Test
    @DisplayName("a hostile line is scanned only so far, and quickly")
    void boundedAndLinear() {
        String hostile = "a".repeat(200_000) + ".ts:1:1";
        long t0 = System.nanoTime();
        assertThat(TerminalLinks.find(hostile)).isEmpty(); // the link sits past the read bound
        String dots = ".:1".repeat(100_000);
        assertThat(TerminalLinks.find(dots)).isEmpty();
        assertThat((System.nanoTime() - t0) / 1_000_000).as("ms").isLessThan(2_000);
    }

    @Test
    @DisplayName("resolving: absolute as printed, ~/ under home, relative under the aimed project, none without one")
    void candidates() {
        File project = tmp.resolve("proj").toFile();
        File home = tmp.resolve("home").toFile();
        String abs = tmp.resolve("elsewhere/x.ts").toString();
        assertThat(TerminalLinks.candidate(abs, project, home)).isEqualTo(new File(abs));
        assertThat(TerminalLinks.candidate("~/notes/a.md", project, home)).isEqualTo(new File(home, "notes/a.md"));
        assertThat(TerminalLinks.candidate("./src/../src/app.ts", project, home))
                .isEqualTo(new File(project, "src/app.ts"));
        assertThat(TerminalLinks.candidate("src/app.ts", null, home)).isNull();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("a file:// frame is percent-decoded: a folder with a space, or not ASCII, is the folder on disk")
    void fileUrlsDecode() {
        java.util.List<TerminalLinks.Link> found = TerminalLinks.find(
                "    at main (file:///Users/d/My%20Project/caf%C3%A9.mjs:3:1)");
        org.assertj.core.api.Assertions.assertThat(found).singleElement()
                .extracting(TerminalLinks.Link::path).isEqualTo("/Users/d/My Project/café.mjs");
        org.assertj.core.api.Assertions.assertThat(TerminalLinks.percentDecoded("/a/100%/b.js"))
                .as("a malformed escape is kept").isEqualTo("/a/100%/b.js");
    }
}
