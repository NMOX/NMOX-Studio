package org.nmox.studio.apiclient.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The API Studio data model: a workspace holds collections of saved
 * requests and named environments of variables. Everything here is a
 * plain mutable bean - the UI edits it directly and {@code WorkspaceIO}
 * serializes it to {@code .nmoxapi.json} beside the project.
 */
public final class ApiModel {

    private ApiModel() {
    }

    /** One header or query parameter: a name, a value, and an on/off. */
    public static final class Pair {

        public String name;
        public String value;
        public boolean enabled = true;

        public Pair() {
        }

        public Pair(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /** How a request authenticates. NONE, or a bearer token, or basic. */
    public enum AuthType {
        NONE, BEARER, BASIC
    }

    /** A single assertion run against the response after a request. */
    public static final class Assertion {

        public enum Kind {
            STATUS_IS, TIME_UNDER_MS, BODY_CONTAINS, JSON_HAS_PATH, HEADER_PRESENT
        }

        public Kind kind = Kind.STATUS_IS;
        public String target = "200";

        public Assertion() {
        }

        public Assertion(Kind kind, String target) {
            this.kind = kind;
            this.target = target;
        }
    }

    /** A saved request: everything needed to fire it and judge the result. */
    public static final class Request {

        public String name = "New request";
        public String method = "GET";
        public String url = "";
        public final List<Pair> params = new ArrayList<>();
        public final List<Pair> headers = new ArrayList<>();
        public String body = "";
        public AuthType authType = AuthType.NONE;
        public String authToken = "";  // bearer token, or "user:password" for basic
        public final List<Assertion> tests = new ArrayList<>();
    }

    /** A named group of requests - Postman's "collection". */
    public static final class Collection {

        public String name = "New collection";
        public final List<Request> requests = new ArrayList<>();
    }

    /** A named set of variables; {@code {{var}}} resolves against the active one. */
    public static final class Environment {

        public String name = "New environment";
        public final Map<String, String> variables = new LinkedHashMap<>();
    }

    /** The persisted root: collections, environments, and the active one. */
    public static final class Workspace {

        public final List<Collection> collections = new ArrayList<>();
        public final List<Environment> environments = new ArrayList<>();
        public String activeEnvironment = "";

        public Environment active() {
            for (Environment e : environments) {
                if (e.name.equals(activeEnvironment)) {
                    return e;
                }
            }
            return null;
        }

        /** A first-run workspace: one collection, one empty environment. */
        public static Workspace starter() {
            Workspace w = new Workspace();
            Collection c = new Collection();
            c.name = "My API";
            Request r = new Request();
            r.name = "Health check";
            r.url = "{{base_url}}/health";
            r.tests.add(new Assertion(Assertion.Kind.STATUS_IS, "200"));
            c.requests.add(r);
            w.collections.add(c);
            Environment local = new Environment();
            local.name = "Local";
            local.variables.put("base_url", "http://localhost:3000");
            w.environments.add(local);
            w.activeEnvironment = "Local";
            return w;
        }
    }
}
