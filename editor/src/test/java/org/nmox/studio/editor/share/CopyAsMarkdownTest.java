package org.nmox.studio.editor.share;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CopyAsMarkdownTest {

    @Test
    @DisplayName("the fence tag is the product's mime vocabulary, with GitHub-known exceptions")
    void fenceTags() {
        assertThat(CopyAsMarkdown.fence("text/javascript")).isEqualTo("javascript");
        assertThat(CopyAsMarkdown.fence("text/x-jsx")).isEqualTo("jsx");
        assertThat(CopyAsMarkdown.fence("text/typescript")).isEqualTo("typescript");
        assertThat(CopyAsMarkdown.fence("text/x-python")).isEqualTo("python");
        assertThat(CopyAsMarkdown.fence("text/sh")).isEqualTo("bash");
        assertThat(CopyAsMarkdown.fence(null)).isEqualTo("text");
        assertThat(CopyAsMarkdown.fence("garbage")).isEqualTo("text");
    }

    @Test
    @DisplayName("a block ends the code in exactly one newline before the closing fence, CRLF folded")
    void trailingNewlineLaw() {
        assertThat(CopyAsMarkdown.block("a\n", "text/javascript")).isEqualTo("```javascript\na\n```\n");
        assertThat(CopyAsMarkdown.block("a", "text/javascript")).isEqualTo("```javascript\na\n```\n");
        assertThat(CopyAsMarkdown.block("a\r\nb\r\n\r\n", "text/css")).isEqualTo("```css\na\nb\n```\n");
    }

    @Test
    @DisplayName("code that itself contains a backtick run gets a longer fence (CommonMark)")
    void longerFenceThanAnyRunInside() {
        String withFence = "say(`hi`);\n```\nnested\n```";
        String block = CopyAsMarkdown.block(withFence, "text/javascript");
        assertThat(block).startsWith("````javascript\n").endsWith("\n````\n");
        assertThat(CopyAsMarkdown.longestBacktickRun("a``b````c")).isEqualTo(4);
        assertThat(CopyAsMarkdown.longestBacktickRun("plain")).isZero();
    }

    @Test
    @DisplayName("line counting for the status line")
    void lineCount() {
        assertThat(CopyAsMarkdown.lineCount("")).isZero();
        assertThat(CopyAsMarkdown.lineCount("one")).isEqualTo(1);
        assertThat(CopyAsMarkdown.lineCount("one\ntwo\n")).isEqualTo(2);
        assertThat(CopyAsMarkdown.lineCount("one\ntwo\nthree")).isEqualTo(3);
    }

    @Test
    @DisplayName("the popup action copies the selection or the whole file and says so")
    void actionWiring() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/org/nmox/studio/editor/share/CopyAsMarkdownAction.java"));
        assertThat(src).contains("path = \"Editors/Popup\"")
                .contains("path = \"Menu/Edit\", position = 1370")
                .contains("getSelectedText()")
                .contains("doc.getText(0, doc.getLength())")
                .contains("getSystemClipboard().setContents")
                .contains("CopyAsMarkdown.block(");
    }
}
