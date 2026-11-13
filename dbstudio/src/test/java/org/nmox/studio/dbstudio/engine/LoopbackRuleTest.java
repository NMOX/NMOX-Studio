package org.nmox.studio.dbstudio.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.dbstudio.model.ConnectionSpec;
import org.nmox.studio.dbstudio.model.DbEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What counts as "this machine" for MySQL 8's RSA key fetch (3.9.0), read
 * with a hostile eye two weeks on.
 *
 * <p>{@code allowPublicKeyRetrieval} lets whoever answers the connection
 * hand over the key the password is then encrypted with, so the one who
 * answers can read the password. On the loopback nobody else can answer;
 * anywhere else the driver refuses by default, and so must the product.
 * The shipped rule was {@code startsWith("127.")}, and a host name may
 * start with anything its owner likes.
 */
class LoopbackRuleTest {

    private static ConnectionSpec mysql(String host) {
        return new ConnectionSpec("id", "t", DbEngine.MYSQL, host, 3306, "db", "u", null, false);
    }

    @Test
    @DisplayName("a NAME that begins 127. is not this machine: no key fetch for 127.0.0.1.evil.example")
    void aNameThatStartsLike127IsNotTheLoopback() {
        for (String remote : new String[]{
            "127.0.0.1.evil.example", "127.evil.example", "127.0.0.1.nip.io", "127.0.0.256",
            "127.0.0.1,evil.example", "127.0.0.1:3306,evil.example", "127.0.0.1@evil.example",
            "127.0.0.1/x?a=", "127.", "127.0.0", "127.0.0.1.", "localhost.evil.example",
            "localhost.", "localhost,evil.example", "::1,evil.example", "0.0.0.0", "10.0.0.5"}) {
            assertThat(DbClient.isLoopbackHost(remote))
                    .as("not the loopback, whatever it starts with: %s", remote).isFalse();
        }
        // and the option really is absent from what the driver is handed
        assertThat(DbClient.connectionUrl(mysql("127.0.0.1.evil.example")))
                .doesNotContain("allowPublicKeyRetrieval");
        assertThat(DbClient.connectionUrl(mysql("127.evil.example")))
                .doesNotContain("allowPublicKeyRetrieval");
    }

    @Test
    @DisplayName("the loopback's own spellings still are: localhost, ::1, [::1] and every 127.a.b.c")
    void theRealLoopbackSpellingsStillAre() {
        for (String local : new String[]{"localhost", "LOCALHOST", "::1", "[::1]",
            "127.0.0.1", "127.0.0.2", "127.255.255.255", "127.10.0.99"}) {
            assertThat(DbClient.isLoopbackHost(local)).as(local).isTrue();
        }
    }

    @Test
    @DisplayName("a bare ::1 makes a URL the MariaDB driver can read, and it dials ::1")
    void bareIpv6LoopbackIsAUrlTheDriverAccepts() throws Exception {
        // the 3.9.0 entry names ::1 as a host that may fetch the key; as shipped
        // the URL was jdbc:mariadb://::1:3306/db and the driver refused to parse
        // it ("Incorrect port value"), so that host never connected at all
        for (String host : new String[]{"::1", "[::1]"}) {
            org.mariadb.jdbc.Configuration conf =
                    org.mariadb.jdbc.Configuration.parse(DbClient.connectionUrl(mysql(host)));
            assertThat(conf.addresses()).as(host).hasSize(1);
            assertThat(conf.addresses().get(0).host).as(host).isEqualTo("::1");
            assertThat(conf.addresses().get(0).port).isEqualTo(3306);
            assertThat(conf.allowPublicKeyRetrieval()).isTrue();
        }
        // a name with one colon is not an IPv6 literal and is left as typed
        assertThat(DbClient.connectionUrl(mysql("db.example.com")))
                .startsWith("jdbc:mariadb://db.example.com:3306/db?");
        // PostgreSQL keeps the URL it always had: pgJDBC reads the bare form itself
        assertThat(DbClient.connectionUrl(new ConnectionSpec("id", "t", DbEngine.POSTGRES,
                "::1", 5432, "db", "u", null, false))).startsWith("jdbc:postgresql://::1:5432/db?");
    }
}
