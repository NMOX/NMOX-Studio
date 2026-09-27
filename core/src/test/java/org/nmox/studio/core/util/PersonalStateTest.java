package org.nmox.studio.core.util;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** One person's state about one project lives outside the project (3.4). */
class PersonalStateTest {

    @AfterEach
    void restore() {
        PersonalState.setBaseForTest(null);
    }

    @Test
    @DisplayName("a document round-trips, keyed by project and studio, outside the project")
    void roundTrip(@TempDir Path base, @TempDir File project, @TempDir File other) throws Exception {
        PersonalState.setBaseForTest(base);
        assertThat(PersonalState.read(project, "api")).as("nothing yet").isNull();

        PersonalState.write(project, "api", "{\"a\":1}");
        PersonalState.write(project, "db", "{\"b\":2}");
        PersonalState.write(other, "api", "{\"c\":3}");

        assertThat(PersonalState.read(project, "api")).isEqualTo("{\"a\":1}");
        assertThat(PersonalState.read(project, "db")).isEqualTo("{\"b\":2}");
        assertThat(PersonalState.read(other, "api")).isEqualTo("{\"c\":3}");
        assertThat(PersonalState.fileFor(project, "api"))
                .as("never inside the project the team commits")
                .startsWith(base);
        assertThat(PersonalState.fileFor(project, "api").startsWith(project.toPath())).isFalse();
        assertThat(project.list()).as("the project directory is untouched").isEmpty();
    }

    @Test
    @DisplayName("the same project spelled two ways is one key")
    void keyNormalizes(@TempDir File project) {
        File dotted = new File(project, "sub/..");
        assertThat(PersonalState.key(dotted)).isEqualTo(PersonalState.key(project));
    }

    @Test
    @DisplayName("without a platform user directory, tests never write into real IDE state")
    void defaultBaseOutsidePlatformIsTemp() {
        PersonalState.setBaseForTest(null);
        assertThat(PersonalState.base().toString())
                .startsWith(Path.of(System.getProperty("java.io.tmpdir")).toString());
    }
}
