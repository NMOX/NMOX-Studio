package org.nmox.studio.infra.ops.remote;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which image a rollback returns to, decided from what a host printed — a host's word, read as one. */
class RollbackTest {

    @Test
    @DisplayName("the listing line names the app's repository and nothing a slug could not be")
    void imagesCommand() {
        assertThat(Rollback.imagesCommand("shop")).isEqualTo("docker images nmox/shop --format '{{.Tag}}'");
        for (String bad : new String[]{null, "", "Shop", "shop;id", "shop x", "$(id)", "-x", "a".repeat(41)}) {
            assertThatThrownBy(() -> Rollback.imagesCommand(bad)).as(String.valueOf(bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("only an image this product built has a stamp")
    void stampOf() {
        assertThat(Rollback.stampOf("nmox/shop:1762400000000", "shop")).isEqualTo("1762400000000");
        assertThat(Rollback.stampOf("nmox/shop:latest", "shop")).isNull();
        assertThat(Rollback.stampOf("nmox/other:5", "shop")).isNull();
        assertThat(Rollback.stampOf("postgres:16", "shop")).isNull();
        assertThat(Rollback.stampOf("nmox/shop:5;id", "shop")).isNull();
        assertThat(Rollback.stampOf(null, "shop")).isNull();
        assertThat(Rollback.stampOf("nmox/shop:5", null)).isNull();
    }

    @Test
    @DisplayName("stamps are read newest first, as numbers of any length; everything else a host prints is ignored")
    void stamps() {
        assertThat(Rollback.stamps(List.of("latest", "900", "1762400000000", " 1000 ", "<none>", "900", "5; rm -rf /",
                "", "1762300000000", "abc", "12a")))
                .containsExactly("1762400000000", "1762300000000", "1000", "900");
        // 1000 is later than 900 though it sorts before it as text
        assertThat(Rollback.compare("1000", "900")).isPositive();
        assertThat(Rollback.compare("0900", "900")).isZero();
    }

    @Test
    @DisplayName("the choice is the newest stamp OLDER than what runs now")
    void previous() {
        List<String> held = List.of("300", "latest", "100", "200");
        assertThat(Rollback.previous("300", held)).isEqualTo("200");
        assertThat(Rollback.previous("200", held)).as("already rolled back once: the one before that").isEqualTo("100");
        assertThat(Rollback.previous("100", held)).as("nothing older").isNull();
        assertThat(Rollback.previous("250", held)).as("the running image need not be in the listing").isEqualTo("200");
        assertThat(Rollback.previous(null, held)).as("nothing of ours runs now").isNull();
        assertThat(Rollback.previous("300", List.of("300"))).isNull();
        assertThat(Rollback.previous("300", List.of())).isNull();
    }
}
