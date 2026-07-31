package org.nmox.studio.rack.devices;

import java.util.Map;

/**
 * The words people actually type when they are looking for a device.
 *
 * <p><b>Why a separate list.</b> Every device already has a title and a
 * one-line description, and until v1.215.0 those doubled as the search
 * index. They are bad at that job, because they are written to read
 * well on a shelf rather than to be guessed at. VERITAS is described as
 * a "Test Harness — jest/vitest/mocha…", so searching <i>coverage</i> —
 * the thing VERITAS is most often reached for — found nothing.
 * TYPEGUARD says "tsc" and so could not be found by <i>typescript</i>.
 * NEPTUNE says "databases" and so could not be found by
 * <i>postgres</i>. A measured 24 of 49 ordinary search terms returned
 * an empty result.
 *
 * <p>Rather than bloat the shelf copy with keyword soup, the search
 * words live here: one auditable list, read only by search, never
 * painted on a faceplate. This is a controlled vocabulary — when a term
 * belongs to a device, add it here, and {@code DeviceVocabularyTest}
 * proves the common ones all resolve.
 *
 * <p>Entries hold <em>synonyms and aliases only</em>. Words already in
 * the title or description are not repeated; the matcher searches those
 * too.
 */
public final class DeviceVocabulary {

    private DeviceVocabulary() {
    }

    private static final Map<String, String> WORDS = Map.ofEntries(
            // --- Run & Automate ---
            Map.entry("master", "pipeline orchestrate run everything trigger all sequence chain"),
            Map.entry("reflex", "autorun on save hot reload live rebuild automatically"),
            Map.entry("join", "wait for barrier synchronize all green and gate"),
            Map.entry("rosetta", "polyglot toolchain monorepo mixed languages switch"),
            Map.entry("waypoint", "monorepo package subproject turborepo lerna workspaces"),
            Map.entry("run", "start execute launch main python ruby java binary"),
            Map.entry("debug", "breakpoint step attach inspect devtools debugger"),
            Map.entry("npm-script", "npm run yarn pnpm scripts package.json task"),
            Map.entry("task-runner", "grunt gulp tasks legacy build automation"),
            Map.entry("cmd", "custom arbitrary shell command any command script step"),
            Map.entry("tempo", "schedule cron timer interval periodic every clock repeat"),
            Map.entry("env", "environment variables dotenv secrets config vars NODE_ENV"),

            // --- Build & Verify ---
            Map.entry("package-manager", "dependencies packages modules npm install upgrade outdated cargo pip composer"),
            Map.entry("build", "compile bundle dist production make transpile"),
            Map.entry("test", "coverage unit tests spec suite run tests pytest junit assertions"),
            Map.entry("lint", "code style static analysis warnings ruff clippy rubocop"),
            Map.entry("format", "tidy indent whitespace gofmt black rustfmt style"),
            Map.entry("typecheck", "typescript types type errors mypy ts strict"),
            Map.entry("audit", "vulnerabilities cve advisories security safety exploits"),
            Map.entry("preflight", "ready to ship release check pre-commit checklist verify all"),
            Map.entry("e2e", "end to end integration tests browser tests selenium puppeteer"),
            Map.entry("bench", "benchmark load test performance stress throughput rps latency"),
            Map.entry("bundle-size", "weight kilobytes budget bloat dist size treeshaking"),
            Map.entry("vitals", "performance accessibility a11y seo core web vitals pagespeed best practices audit"),

            // --- Serve & Expose ---
            Map.entry("dev-server", "localhost start server http server preview hot reload"),
            Map.entry("tunnel", "share public url expose webhook demo ngrok"),
            Map.entry("browser", "open browser preview url chrome web page"),
            Map.entry("http", "curl rest api endpoint url status code healthcheck request"),
            Map.entry("ssh", "remote server production box shell over network"),
            Map.entry("beacon", "ssl certificate expiry https monitoring downtime reachable"),
            Map.entry("deploy", "ship release publish production push live"),

            // --- Frameworks ---
            Map.entry("angular", "ng angular cli component service standalone"),
            Map.entry("phoenix", "elixir mix liveview ecto beam"),
            Map.entry("nextjs", "next react vercel ssr app router"),
            Map.entry("vite", "esbuild rollup dev server bundler"),
            Map.entry("astro", "islands static site content collections"),
            Map.entry("sveltekit", "svelte kit runes"),
            Map.entry("nuxt", "vue nitro"),
            Map.entry("artisan", "php laravel composer migration eloquent"),

            // --- Observe ---
            Map.entry("console", "output logs stdout stderr messages"),
            Map.entry("terminal", "shell scrollback copy output ansi"),
            Map.entry("tail", "logs follow watch file log file"),
            Map.entry("blackbox", "history timeline what happened past runs durations slow"),
            Map.entry("sonar", "port in use eaddrinuse kill process listening 3000 8080"),
            Map.entry("oracle", "ai claude llm assistant why did this fail help diagnose explain"),
            Map.entry("repl", "interactive playground scratch irb ghci node repl prompt"),

            // --- Ship & data ---
            Map.entry("docker", "containers compose images volumes dockerfile"),
            Map.entry("git", "branch version control vcs stage diff fetch merge checkout"),
            Map.entry("database", "sql postgres mysql mongo sqlite db schema migration query"),

            // --- Web3 ---
            Map.entry("anvil", "ethereum evm blockchain testnet local chain foundry devnet"),
            Map.entry("gas-budget", "gas ethereum cost optimization snapshot"),
            Map.entry("stellar", "soroban smart contract blockchain rust contract"),
            Map.entry("anchor", "solana rust blockchain smart contract web3 validator")
    );

    /**
     * Search-only synonyms for a device id, or an empty string when the
     * device has none. Never shown in the UI.
     */
    public static String forId(String deviceId) {
        return WORDS.getOrDefault(deviceId, "");
    }

    /** Every device id carrying vocabulary — the gate test walks this. */
    public static java.util.Set<String> coveredIds() {
        return WORDS.keySet();
    }
}
