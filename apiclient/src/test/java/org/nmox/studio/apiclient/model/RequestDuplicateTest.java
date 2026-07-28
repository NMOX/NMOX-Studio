package org.nmox.studio.apiclient.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Assertion;
import org.nmox.studio.apiclient.model.ApiModel.AuthType;
import org.nmox.studio.apiclient.model.ApiModel.Pair;
import org.nmox.studio.apiclient.model.ApiModel.Request;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The duplicate-and-tweak contract (v1.194.0): a copy carries every
 * authored field, shares NOTHING mutable with its source, and mints a
 * fresh id — the id is the keychain key, and two requests sharing one
 * would silently share (and cross-delete) an auth secret.
 */
class RequestDuplicateTest {

    private static Request sample() {
        Request r = new Request();
        r.name = "Login";
        r.method = "POST";
        r.url = "{{baseUrl}}/login";
        Pair p = new Pair("verbose", "true");
        p.enabled = false;
        r.params.add(p);
        r.headers.add(new Pair("X-Trace", "on"));
        r.body = "{\"user\":\"sam\"}";
        r.authType = AuthType.BEARER;
        r.authToken = "in-memory-secret";
        r.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "201"));
        return r;
    }

    @Test
    @DisplayName("Every authored field rides along, including the in-memory token")
    void copiesEveryAuthoredField() {
        Request src = sample();
        Request copy = Request.duplicate(src);
        assertThat(copy.name).isEqualTo("Login (copy)");
        assertThat(copy.method).isEqualTo("POST");
        assertThat(copy.url).isEqualTo("{{baseUrl}}/login");
        assertThat(copy.params).hasSize(1);
        assertThat(copy.params.get(0).name).isEqualTo("verbose");
        assertThat(copy.params.get(0).enabled).isFalse();
        assertThat(copy.headers.get(0).value).isEqualTo("on");
        assertThat(copy.body).isEqualTo(src.body);
        assertThat(copy.authType).isEqualTo(AuthType.BEARER);
        assertThat(copy.authToken).isEqualTo("in-memory-secret");
        assertThat(copy.tests).hasSize(1);
        assertThat(copy.tests.get(0).target).isEqualTo("201");
    }

    @Test
    @DisplayName("The copy gets a FRESH id — the keychain key must never be shared")
    void mintsAFreshId() {
        Request src = sample();
        Request copy = Request.duplicate(src);
        assertThat(copy.id).isNotBlank().isNotEqualTo(src.id);
    }

    @Test
    @DisplayName("Deep copy: editing the duplicate never reaches back into the source")
    void sharesNothingMutable() {
        Request src = sample();
        Request copy = Request.duplicate(src);
        copy.params.get(0).value = "false";
        copy.headers.add(new Pair("X-Extra", "1"));
        copy.tests.get(0).target = "500";
        assertThat(src.params.get(0).value).isEqualTo("true");
        assertThat(src.headers).hasSize(1);
        assertThat(src.tests.get(0).target).isEqualTo("201");
    }
}
