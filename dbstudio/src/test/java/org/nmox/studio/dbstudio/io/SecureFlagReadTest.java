package org.nmox.studio.dbstudio.io;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How {@code secure} is read from a {@code .nmoxdb.json} someone else
 * wrote, measured (review of 3.9.0). Since 3.9.0 the flag decides whether
 * a MySQL or MariaDB connection is encrypted, so what a hand-edited value
 * turns into is worth knowing rather than assuming.
 */
class SecureFlagReadTest {

    private static boolean secureOf(String secureMember) {
        String json = "{\"version\":1,\"connections\":[{\"id\":\"a\",\"name\":\"n\",\"engine\":\"MYSQL\","
                + "\"host\":\"db.example.com\",\"port\":3306,\"database\":\"shop\",\"user\":\"u\""
                + secureMember + "}]}";
        List<ConnectionSpec> specs = DbWorkspaceIO.fromJson(json);
        assertThat(specs).as(json).hasSize(1);
        return specs.get(0).secure();
    }

    @Test
    @DisplayName("absent, null and anything that is not true read as off; true reads as on, as a boolean or as the word")
    void whatASavedSecureValueTurnsInto() {
        assertThat(secureOf("")).as("no key: every file written before the flag existed").isFalse();
        assertThat(secureOf(",\"secure\":true")).isTrue();
        assertThat(secureOf(",\"secure\":false")).isFalse();
        assertThat(secureOf(",\"secure\":\"true\"")).as("the word, as a string").isTrue();
        assertThat(secureOf(",\"secure\":\"TRUE\"")).isTrue();
        assertThat(secureOf(",\"secure\":\"false\"")).isFalse();
        assertThat(secureOf(",\"secure\":null")).isFalse();
        // recorded, not blessed: a hand-written 1 or "yes" is read as OFF, which for
        // MySQL/MariaDB is a cleartext connection the dialog shows as an unticked box
        assertThat(secureOf(",\"secure\":1")).isFalse();
        assertThat(secureOf(",\"secure\":\"yes\"")).isFalse();
    }
}
