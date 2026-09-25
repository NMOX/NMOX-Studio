package org.nmox.studio.editor.blame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code git blame --porcelain} read the way git writes it: the facts of a
 * commit appear once, the first time it is seen, and every later line of the
 * same commit carries the bare header. The fixture below is the shape of a
 * real run (captured from {@code git blame --porcelain --no-textconv -- run.sh}
 * in this repository), cut down.
 */
class BlamePorcelainTest {

    static final String A = "417180783790a176c265ee7fbf9cf8fc1deacae5";
    static final String B = "6bc133a7c0e9b85d2f40b5c611f9fa2e0a7debc1";
    static final String ZERO = "0000000000000000000000000000000000000000";

    static final String REAL = String.join("\n",
            A + " 1 1 1",
            "author Ada Lovelace",
            "author-mail <ada@example.org>",
            "author-time 1753057899",
            "author-tz -0600",
            "committer Ada Lovelace",
            "committer-mail <ada@example.org>",
            "committer-time 1753057899",
            "committer-tz -0600",
            "summary First light",
            "boundary",
            "filename run.sh",
            "\t#!/bin/bash",
            B + " 2 2 3",
            "author Grace Hopper",
            "author-mail <grace@example.org>",
            "author-time 1790238807",
            "author-tz -0600",
            "committer GitHub",
            "committer-mail <noreply@github.com>",
            "committer-time 1790238807",
            "committer-tz -0600",
            "summary Fix the parser",
            "previous 1ac059cef179ef14a8238a2eca859084aba9ec4f run.sh",
            "filename run.sh",
            "\t#",
            B + " 3 3",
            "\t# a content line that looks like a header:",
            B + " 4 4",
            "\t" + A + " 9 9 9",
            ZERO + " 5 5 1",
            "author Not Committed Yet",
            "author-mail <not.committed.yet>",
            "author-time 1790300000",
            "author-tz -0600",
            "committer Not Committed Yet",
            "committer-mail <not.committed.yet>",
            "committer-time 1790300000",
            "committer-tz -0600",
            "summary Version of run.sh from run.sh",
            "previous " + B + " run.sh",
            "filename run.sh",
            "\techo edited",
            B + " 7 6 1",
            "filename run.sh",
            "\tthe same commit again, in a later group",
            "");

    @Test
    @DisplayName("a commit's facts are read once and every later line of it points at them")
    void shorthandRecordsReuseTheFirstHeader() {
        BlamePorcelain.Blame blame = BlamePorcelain.parse(REAL);
        assertThat(blame.size()).isEqualTo(6);
        BlamePorcelain.Line first = blame.at(2);
        assertThat(first.author()).isEqualTo("Grace Hopper");
        assertThat(first.summary()).isEqualTo("Fix the parser");
        assertThat(first.authorTime()).isEqualTo(1790238807L);
        // lines 3 and 4 carried the bare header only
        assertThat(blame.at(3)).isSameAs(first);
        assertThat(blame.at(4)).isSameAs(first);
        // a later, separate group of the same commit
        assertThat(blame.at(6)).isSameAs(first);
        assertThat(blame.at(1).author()).isEqualTo("Ada Lovelace");
        assertThat(blame.at(1).summary()).isEqualTo("First light");
    }

    @Test
    @DisplayName("a content line shaped like a header is content: it follows the TAB")
    void contentIsNeverAHeader() {
        BlamePorcelain.Blame blame = BlamePorcelain.parse(REAL);
        assertThat(blame.at(4).sha()).isEqualTo(B);
        assertThat(blame.at(9)).isNull();
    }

    @Test
    @DisplayName("the all-zero id is the working tree's own change, not a commit")
    void uncommittedLine() {
        BlamePorcelain.Line line = BlamePorcelain.parse(REAL).at(5);
        assertThat(line.uncommitted()).isTrue();
        assertThat(BlamePorcelain.parse(REAL).at(2).uncommitted()).isFalse();
        assertThat(BlamePorcelain.parse(REAL).at(1).uncommitted())
                .as("a boundary commit is still a commit")
                .isFalse();
    }

    @Test
    @DisplayName("an author or summary carrying markup is kept exactly as written")
    void markupIsData() {
        String porcelain = A + " 1 1 1\n"
                + "author <html><img src='http://evil/x'>\n"
                + "author-time 1\n"
                + "summary <html><b>pwned</b>\n"
                + "\tline\n";
        BlamePorcelain.Line line = BlamePorcelain.parse(porcelain).at(1);
        assertThat(line.author()).isEqualTo("<html><img src='http://evil/x'>");
        assertThat(line.summary()).isEqualTo("<html><b>pwned</b>");
    }

    @Test
    @DisplayName("nothing, garbage and a truncated tail are answers, never throws")
    void hostileInput() {
        assertThat(BlamePorcelain.parse(null).size()).isZero();
        assertThat(BlamePorcelain.parse("").at(1)).isNull();
        assertThat(BlamePorcelain.parse("not porcelain\n\tat all\n").size()).isZero();
        assertThat(BlamePorcelain.parse(A + " 1 99999999999 1\n\tx\n").size()).isZero();
        // cut mid-record: the covered lines are still answered
        String cut = REAL.substring(0, REAL.indexOf(B + " 3 3"));
        BlamePorcelain.Blame blame = BlamePorcelain.parse(cut + B + " 3 3\n");
        assertThat(blame.at(2).author()).isEqualTo("Grace Hopper");
        assertThat(blame.at(3)).isNull();
        assertThat(blame.at(0)).isNull();
    }

    @Test
    @DisplayName("a CRLF pipe reads the same, and the year 2038 is not a parse failure")
    void crlfAndLongTimes() {
        String crlf = A + " 1 1 1\r\nauthor Ada\r\nauthor-time 4102444800\r\nsummary s\r\n\tx\r\n";
        BlamePorcelain.Line line = BlamePorcelain.parse(crlf).at(1);
        assertThat(line.author()).isEqualTo("Ada");
        assertThat(line.authorTime()).isEqualTo(4102444800L);
        assertThat(line.summary()).isEqualTo("s");
        assertThat(line.shortSha()).isEqualTo("41718078");
    }
}
