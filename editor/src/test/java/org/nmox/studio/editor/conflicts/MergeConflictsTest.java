package org.nmox.studio.editor.conflicts;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.editor.conflicts.MergeConflicts.Block;
import org.nmox.studio.editor.conflicts.MergeConflicts.Resolution;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conflict parser and VS Code's three choices, against the shapes git
 * really writes and the ones it does not.
 */
class MergeConflictsTest {

    private static final String SIMPLE = """
            before
            <<<<<<< HEAD
            ours 1
            ours 2
            =======
            theirs 1
            >>>>>>> feature/login
            after
            """;

    @Test
    @DisplayName("a git conflict block: its sections, its labels, and where it ends")
    void simpleBlock() {
        List<Block> blocks = MergeConflicts.scan(SIMPLE);
        assertThat(blocks).hasSize(1);
        Block b = blocks.get(0);
        assertThat(SIMPLE.substring(b.start(), b.headerEnd())).isEqualTo("<<<<<<< HEAD");
        assertThat(SIMPLE.substring(b.oursStart(), b.oursEnd())).isEqualTo("ours 1\nours 2\n");
        assertThat(SIMPLE.substring(b.theirsStart(), b.theirsEnd())).isEqualTo("theirs 1\n");
        assertThat(SIMPLE.substring(b.end())).isEqualTo("after\n");
        assertThat(b.oursLabel()).isEqualTo("HEAD");
        assertThat(b.theirsLabel()).isEqualTo("feature/login");
        assertThat(b.hasBase()).isFalse();
        assertThat(b.endsAtEof()).isFalse();
    }

    @Test
    @DisplayName("current keeps ours, incoming keeps theirs, both keeps ours then theirs")
    void threeChoices() {
        Block b = MergeConflicts.scan(SIMPLE).get(0);
        assertThat(MergeConflicts.apply(SIMPLE, b, Resolution.CURRENT))
                .isEqualTo("before\nours 1\nours 2\nafter\n");
        assertThat(MergeConflicts.apply(SIMPLE, b, Resolution.INCOMING))
                .isEqualTo("before\ntheirs 1\nafter\n");
        assertThat(MergeConflicts.apply(SIMPLE, b, Resolution.BOTH))
                .isEqualTo("before\nours 1\nours 2\ntheirs 1\nafter\n");
    }

    @Test
    @DisplayName("CRLF: markers are found and the kept lines keep their CRLF")
    void crlf() {
        String text = "a\r\n<<<<<<< HEAD\r\nx\r\n=======\r\ny\r\n>>>>>>> other\r\nb\r\n";
        Block b = MergeConflicts.scan(text).get(0);
        assertThat(b.oursLabel()).isEqualTo("HEAD");
        assertThat(b.theirsLabel()).isEqualTo("other");
        assertThat(MergeConflicts.apply(text, b, Resolution.INCOMING)).isEqualTo("a\r\ny\r\nb\r\n");
        assertThat(MergeConflicts.apply(text, b, Resolution.BOTH)).isEqualTo("a\r\nx\r\ny\r\nb\r\n");
    }

    @Test
    @DisplayName("diff3: the base section is found and never kept by any choice")
    void diff3() {
        String text = """
                <<<<<<< HEAD
                ours
                ||||||| merged common ancestors
                base
                =======
                theirs
                >>>>>>> topic
                """;
        Block b = MergeConflicts.scan(text).get(0);
        assertThat(b.hasBase()).isTrue();
        assertThat(text.substring(b.oursStart(), b.oursEnd())).isEqualTo("ours\n");
        assertThat(text.substring(b.baseStart(), b.baseEnd())).isEqualTo("base\n");
        assertThat(MergeConflicts.apply(text, b, Resolution.CURRENT)).isEqualTo("ours\n");
        assertThat(MergeConflicts.apply(text, b, Resolution.INCOMING)).isEqualTo("theirs\n");
        assertThat(MergeConflicts.apply(text, b, Resolution.BOTH)).isEqualTo("ours\ntheirs\n");
    }

    @Test
    @DisplayName("two adjacent blocks are two blocks, each resolved alone")
    void adjacentBlocks() {
        String text = "<<<<<<< HEAD\na\n=======\nb\n>>>>>>> x\n<<<<<<< HEAD\nc\n=======\nd\n>>>>>>> x\n";
        List<Block> blocks = MergeConflicts.scan(text);
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(1).start()).isEqualTo(blocks.get(0).end());
        assertThat(MergeConflicts.apply(text, blocks.get(1), Resolution.INCOMING))
                .isEqualTo("<<<<<<< HEAD\na\n=======\nb\n>>>>>>> x\nd\n");
    }

    @Test
    @DisplayName("markers that are not at a line start are text, not a conflict")
    void notAtLineStart() {
        String text = """
                const s = "<<<<<<< HEAD";
                const t = `
                  =======
                  >>>>>>> x`;
                """;
        assertThat(MergeConflicts.scan(text)).isEmpty();
    }

    @Test
    @DisplayName("a block that ends the file without a newline keeps the file without one")
    void atEofWithoutNewline() {
        String text = "top\n<<<<<<< HEAD\nours\n=======\ntheirs\n>>>>>>> x";
        Block b = MergeConflicts.scan(text).get(0);
        assertThat(b.endsAtEof()).isTrue();
        assertThat(b.end()).isEqualTo(text.length());
        assertThat(MergeConflicts.apply(text, b, Resolution.CURRENT)).isEqualTo("top\nours");
        assertThat(MergeConflicts.apply(text, b, Resolution.BOTH)).isEqualTo("top\nours\ntheirs");
    }

    @Test
    @DisplayName("a setext heading's ======= outside any block is prose")
    void setextHeadingOutsideABlock() {
        String text = """
                Title
                =======

                <<<<<<< HEAD
                one
                =======
                two
                >>>>>>> x
                """;
        List<Block> blocks = MergeConflicts.scan(text);
        assertThat(blocks).hasSize(1);
        assertThat(text.substring(blocks.get(0).oursStart(), blocks.get(0).oursEnd())).isEqualTo("one\n");
        assertThat(MergeConflicts.scan("Title\n=======\n\nbody\n")).isEmpty();
    }

    @Test
    @DisplayName("exactly seven: eight marker characters, or a glued label, is not a marker")
    void exactlySeven() {
        assertThat(MergeConflicts.scan("<<<<<<<< HEAD\na\n=======\nb\n>>>>>>> x\n")).isEmpty();
        assertThat(MergeConflicts.scan("<<<<<<<HEAD\na\n=======\nb\n>>>>>>> x\n")).isEmpty();
        // an eight-character rule inside a block is content, not a separator
        String text = "<<<<<<< HEAD\n========\n=======\nb\n>>>>>>> x\n";
        Block b = MergeConflicts.scan(text).get(0);
        assertThat(text.substring(b.oursStart(), b.oursEnd())).isEqualTo("========\n");
        // a bare header with no label is a marker, the label empty
        assertThat(MergeConflicts.scan("<<<<<<<\na\n=======\nb\n>>>>>>>\n").get(0).oursLabel()).isEmpty();
    }

    @Test
    @DisplayName("malformed blocks are refused whole, never guessed at")
    void malformedRefused() {
        // unterminated: the file ends inside the block
        assertThat(MergeConflicts.scan("<<<<<<< HEAD\na\n=======\nb\n")).isEmpty();
        // a trailer before any separator
        assertThat(MergeConflicts.scan("<<<<<<< HEAD\na\n>>>>>>> x\n")).isEmpty();
        // two separators
        assertThat(MergeConflicts.scan("<<<<<<< HEAD\na\n=======\nb\n=======\nc\n>>>>>>> x\n")).isEmpty();
        // a base after the separator
        assertThat(MergeConflicts.scan("<<<<<<< HEAD\na\n=======\n||||||| base\nb\n>>>>>>> x\n")).isEmpty();
        // a refused block does not hide a good one after it
        List<Block> after = MergeConflicts.scan(
                "<<<<<<< HEAD\na\n>>>>>>> x\n<<<<<<< HEAD\nc\n=======\nd\n>>>>>>> y\n");
        assertThat(after).hasSize(1);
        assertThat(after.get(0).theirsLabel()).isEqualTo("y");
    }

    @Test
    @DisplayName("a block nested in another (a recursive merge's conflicted base) is refused whole")
    void nestedRefused() {
        String text = """
                <<<<<<< HEAD
                a
                ||||||| merged common ancestors
                <<<<<<< Temporary merge branch 1
                b
                =======
                c
                >>>>>>> Temporary merge branch 2
                =======
                d
                >>>>>>> topic
                tail
                """;
        assertThat(MergeConflicts.scan(text)).isEmpty();
    }

    @Test
    @DisplayName("a text with no header is answered empty, and null is too")
    void nothingToFind() {
        assertThat(MergeConflicts.scan("=======\n>>>>>>> x\n")).isEmpty();
        assertThat(MergeConflicts.scan(null)).isEmpty();
        assertThat(MergeConflicts.scan("")).isEmpty();
    }

    @Test
    @DisplayName("an empty side resolves to nothing, and both keeps the other side alone")
    void emptySide() {
        String text = "<<<<<<< HEAD\n=======\nonly theirs\n>>>>>>> x\nz\n";
        Block b = MergeConflicts.scan(text).get(0);
        assertThat(MergeConflicts.apply(text, b, Resolution.CURRENT)).isEqualTo("z\n");
        assertThat(MergeConflicts.apply(text, b, Resolution.BOTH)).isEqualTo("only theirs\nz\n");
    }
}
