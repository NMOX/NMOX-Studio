package org.nmox.studio.apiclient.search;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.apiclient.model.ApiModel.Collection;
import org.nmox.studio.apiclient.model.ApiModel.Request;
import org.nmox.studio.apiclient.model.ApiModel.Workspace;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Quick Search matcher over saved requests: a needle finds a request
 * by its name, its HTTP method, or a fragment of its URL — case-insensitively
 * — and returns nothing when nothing matches.
 */
class ApiRequestSearchTest {

    private static Request request(String name, String method, String url) {
        Request r = new Request();
        r.name = name;
        r.method = method;
        r.url = url;
        return r;
    }

    private static Workspace fixture() {
        Workspace w = new Workspace();

        Collection users = new Collection();
        users.name = "Users";
        users.requests.add(request("List users", "GET", "{{base_url}}/users"));
        users.requests.add(request("Create user", "POST", "{{base_url}}/users"));

        Collection billing = new Collection();
        billing.name = "Billing";
        billing.requests.add(request("Fetch invoice", "GET", "https://pay.example.com/invoices/42"));

        w.collections.add(users);
        w.collections.add(billing);
        return w;
    }

    @Test
    @DisplayName("matches a request by name (case-insensitive)")
    void byName() {
        List<Request> hits = ApiRequestSearchProvider.match(fixture(), "invoice");
        assertThat(hits).extracting(r -> r.name).containsExactly("Fetch invoice");
    }

    @Test
    @DisplayName("matches requests by HTTP method")
    void byMethod() {
        List<Request> hits = ApiRequestSearchProvider.match(fixture(), "post");
        assertThat(hits).extracting(r -> r.name).containsExactly("Create user");
    }

    @Test
    @DisplayName("matches requests by a fragment of the URL")
    void byUrl() {
        List<Request> hits = ApiRequestSearchProvider.match(fixture(), "pay.example.com");
        assertThat(hits).extracting(r -> r.name).containsExactly("Fetch invoice");

        // the /users path matches both requests in the Users collection
        assertThat(ApiRequestSearchProvider.match(fixture(), "/users"))
                .extracting(r -> r.name)
                .containsExactly("List users", "Create user");
    }

    @Test
    @DisplayName("returns empty for a needle that matches nothing")
    void miss() {
        assertThat(ApiRequestSearchProvider.match(fixture(), "nowhere")).isEmpty();
    }

    @Test
    @DisplayName("a blank needle and a null workspace both yield no hits")
    void degenerate() {
        assertThat(ApiRequestSearchProvider.match(fixture(), "   ")).isEmpty();
        assertThat(ApiRequestSearchProvider.match(null, "get")).isEmpty();
    }
}
