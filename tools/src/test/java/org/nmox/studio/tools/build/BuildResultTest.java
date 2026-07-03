package org.nmox.studio.tools.build;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The build-result value object: it faithfully carries the outcome,
 * captured streams and timing, starts with an empty message list and
 * zeroed statistics, and lets the parser append messages and bump
 * counters after the fact.
 */
class BuildResultTest {

    @Test
    @DisplayName("A fresh result exposes its constructor arguments and starts with no messages")
    void carriesConstructorArguments() {
        BuildResult result = new BuildResult(true, "all good", "", 1234L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEqualTo("all good");
        assertThat(result.getErrorOutput()).isEmpty();
        assertThat(result.getDuration()).isEqualTo(1234L);
        assertThat(result.getMessages()).isEmpty();
        assertThat(result.getStatistics()).isNotNull();
    }

    @Test
    @DisplayName("A failed result reports failure and preserves its error stream")
    void carriesFailure() {
        BuildResult result = new BuildResult(false, "", "boom", 7L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorOutput()).isEqualTo("boom");
        assertThat(result.getOutput()).isEmpty();
        assertThat(result.getDuration()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Appended messages accumulate in the order they were added")
    void messagesAccumulateInOrder() {
        BuildResult result = new BuildResult(true, "", "", 0L);

        result.addMessage(new BuildResult.BuildMessage(
                BuildResult.BuildMessage.Type.ERROR, "first"));
        result.addMessage(new BuildResult.BuildMessage(
                BuildResult.BuildMessage.Type.WARNING, "second"));

        assertThat(result.getMessages()).hasSize(2);
        assertThat(result.getMessages().get(0).getMessage()).isEqualTo("first");
        assertThat(result.getMessages().get(0).getType())
                .isEqualTo(BuildResult.BuildMessage.Type.ERROR);
        assertThat(result.getMessages().get(1).getType())
                .isEqualTo(BuildResult.BuildMessage.Type.WARNING);
    }

    @Test
    @DisplayName("The short message constructor leaves location fields at their neutral defaults")
    void shortMessageHasNeutralLocation() {
        BuildResult.BuildMessage message = new BuildResult.BuildMessage(
                BuildResult.BuildMessage.Type.INFO, "just info");

        assertThat(message.getType()).isEqualTo(BuildResult.BuildMessage.Type.INFO);
        assertThat(message.getMessage()).isEqualTo("just info");
        assertThat(message.getFile()).isNull();
        assertThat(message.getLine()).isZero();
        assertThat(message.getColumn()).isZero();
    }

    @Test
    @DisplayName("The full message constructor records file, line and column")
    void fullMessageCarriesLocation() {
        BuildResult.BuildMessage message = new BuildResult.BuildMessage(
                BuildResult.BuildMessage.Type.ERROR, "type error",
                "src/app.ts", 42, 13);

        assertThat(message.getFile()).isEqualTo("src/app.ts");
        assertThat(message.getLine()).isEqualTo(42);
        assertThat(message.getColumn()).isEqualTo(13);
        assertThat(message.getType()).isEqualTo(BuildResult.BuildMessage.Type.ERROR);
        assertThat(message.getMessage()).isEqualTo("type error");
    }

    @Test
    @DisplayName("Statistics start at zero and every counter round-trips through its setter")
    void statisticsRoundTrip() {
        BuildResult.BuildStatistics stats = new BuildResult(true, "", "", 0L).getStatistics();

        assertThat(stats.getFilesProcessed()).isZero();
        assertThat(stats.getErrors()).isZero();
        assertThat(stats.getWarnings()).isZero();
        assertThat(stats.getOutputSize()).isZero();
        assertThat(stats.getOriginalSize()).isZero();
        assertThat(stats.getCompressionRatio()).isZero();

        stats.setFilesProcessed(9);
        stats.setErrors(2);
        stats.setWarnings(3);
        stats.setOutputSize(1024L);
        stats.setOriginalSize(4096L);
        stats.setCompressionRatio(0.25);

        assertThat(stats.getFilesProcessed()).isEqualTo(9);
        assertThat(stats.getErrors()).isEqualTo(2);
        assertThat(stats.getWarnings()).isEqualTo(3);
        assertThat(stats.getOutputSize()).isEqualTo(1024L);
        assertThat(stats.getOriginalSize()).isEqualTo(4096L);
        assertThat(stats.getCompressionRatio()).isEqualTo(0.25);
    }

    @Test
    @DisplayName("Every message type constant is distinct and enumerable")
    void messageTypesAreDistinct() {
        assertThat(BuildResult.BuildMessage.Type.values())
                .containsExactly(
                        BuildResult.BuildMessage.Type.ERROR,
                        BuildResult.BuildMessage.Type.WARNING,
                        BuildResult.BuildMessage.Type.INFO,
                        BuildResult.BuildMessage.Type.SUCCESS);
        assertThat(BuildResult.BuildMessage.Type.valueOf("SUCCESS"))
                .isEqualTo(BuildResult.BuildMessage.Type.SUCCESS);
    }
}
