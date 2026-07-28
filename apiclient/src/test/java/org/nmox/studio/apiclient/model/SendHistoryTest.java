package org.nmox.studio.apiclient.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.api.WorkspaceIO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Send history (v1.197.0): newest first, capped like DB Studio's, and
 * the secrets law held BY CONSTRUCTION — the entry type has no token
 * field, so no serialization path can ever leak one into the
 * committable .nmoxapi.json.
 */
class SendHistoryTest {

    private static ApiModel.Request request() {
        ApiModel.Request r = new ApiModel.Request();
        r.name = "list pets";
        r.method = "GET";
        r.url = "{{baseUrl}}/pets";
        r.params.add(new ApiModel.Pair("limit", "10"));
        r.headers.add(new ApiModel.Pair("Accept", "application/json"));
        r.body = "";
        r.authType = ApiModel.AuthType.BEARER;
        r.authToken = "SECRET-BEARER-TOKEN";
        return r;
    }

    @Test
    @DisplayName("The Entry type structurally cannot hold a token")
    void noTokenFieldExists() {
        assertThat(Arrays.stream(SendHistory.Entry.class.getDeclaredFields())
                .map(Field::getName))
                .as("a token-shaped field on Entry would reopen the leak path")
                .noneMatch(n -> n.toLowerCase().contains("token"))
                .noneMatch(n -> n.toLowerCase().contains("secret"))
                .noneMatch(n -> n.toLowerCase().contains("password"));
    }

    @Test
    @DisplayName("of() snapshots the authored model; restore() comes back tokenless with a fresh id")
    void snapshotAndRestore() {
        ApiModel.Request src = request();
        SendHistory.Entry e = SendHistory.of(1234L, src, 200, 42L);
        assertThat(e.url).isEqualTo("{{baseUrl}}/pets");
        assertThat(e.authType).isEqualTo(ApiModel.AuthType.BEARER);
        assertThat(e.status).isEqualTo(200);
        assertThat(e.params).hasSize(1);
        assertThat(e.headers).hasSize(1);

        ApiModel.Request back = SendHistory.restore(e);
        assertThat(back.id).as("id is the keychain key — must be fresh").isNotEqualTo(src.id);
        assertThat(back.authToken).as("the token never rides history").isEmpty();
        assertThat(back.authType).isEqualTo(ApiModel.AuthType.BEARER);
        assertThat(back.url).isEqualTo(src.url);
        assertThat(back.params.get(0).value).isEqualTo("10");
        // deep copy: mutating the restored request must not touch the entry
        back.params.get(0).value = "999";
        assertThat(e.params.get(0).value).isEqualTo("10");
    }

    @Test
    @DisplayName("record() keeps newest first and prunes past the cap")
    void newestFirstCapped() {
        List<SendHistory.Entry> history = new ArrayList<>();
        for (int i = 0; i < SendHistory.CAP + 7; i++) {
            SendHistory.record(history, SendHistory.of(i, request(), 200, 1));
        }
        assertThat(history).hasSize(SendHistory.CAP);
        assertThat(history.get(0).timestamp)
                .as("newest first").isEqualTo(SendHistory.CAP + 6);
        assertThat(history.get(history.size() - 1).timestamp)
                .as("oldest pruned from the tail").isEqualTo(7);
    }

    @Test
    @DisplayName("History round-trips through .nmoxapi.json with no token anywhere in the bytes")
    void persistenceRoundTripLeaksNothing() {
        ApiModel.Workspace w = new ApiModel.Workspace();
        SendHistory.record(w.history, SendHistory.of(99L, request(), 404, 12L));

        String json = WorkspaceIO.toJson(w);
        assertThat(json).as("the committable file").doesNotContain("SECRET-BEARER-TOKEN");
        assertThat(json).contains("{{baseUrl}}/pets");

        ApiModel.Workspace back = WorkspaceIO.fromJson(json);
        assertThat(back.history).hasSize(1);
        SendHistory.Entry e = back.history.get(0);
        assertThat(e.timestamp).isEqualTo(99L);
        assertThat(e.status).isEqualTo(404);
        assertThat(e.durationMs).isEqualTo(12L);
        assertThat(e.authType).isEqualTo(ApiModel.AuthType.BEARER);
        assertThat(e.headers.get(0).name).isEqualTo("Accept");
    }
}
