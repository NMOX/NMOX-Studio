package org.nmox.studio.infra.api;

import org.json.JSONObject;

/**
 * One planned DigitalOcean API call. Bodies may carry placeholders -
 * <code>${id-of:nodeId}</code> and <code>${ip-of:nodeId}</code> -
 * resolved at execution time from earlier steps' results, which is
 * what lets a single plan create a VPC, place a droplet inside it and
 * point DNS at the droplet's not-yet-known IP.
 *
 * @param method      HTTP method (POST, DELETE)
 * @param path        API path, e.g. /v2/droplets
 * @param body        request body, or null
 * @param nodeId      the designer node this call realizes
 * @param description human line for the dry-run plan
 * @param skipped     true for resources outside the v2 REST API (Spaces
 *                    buckets are S3-protocol); shown in the plan, never sent
 */
public record DoRequest(String method, String path, JSONObject body,
        String nodeId, String description, boolean skipped) {

    public static DoRequest post(String path, JSONObject body, String nodeId, String description) {
        return new DoRequest("POST", path, body, nodeId, description, false);
    }

    public static DoRequest skip(String nodeId, String description) {
        return new DoRequest("SKIP", "", null, nodeId, description, true);
    }
}
