package org.nmox.studio.rack.devices;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nmox.studio.rack.engine.DiagnosticsBus;

/**
 * Reads slither's machine report into rack findings — PURITY's slither
 * lane (ledger 12). Pure: no process, no Swing, no bus.
 *
 * <p><b>Why {@code --json <file>} and not SARIF, and not {@code --json -}.</b>
 * Measured on slither 0.11.6 against a Foundry project: {@code --json -}
 * writes the whole report as ONE stdout line (9,360 bytes for three
 * findings), which would ride the executor's per-line cap (200k chars —
 * a real contract suite exceeds it and the JSON arrives truncated, i.e.
 * unparseable) and paint raw JSON into the Output tab where a person reads.
 * With a file target slither keeps printing its human report on stderr and
 * writes the machine report beside it, so the Output tab stays readable
 * and the machine read is bounded here. JSON over SARIF because it is
 * slither's own documented schema ({@code success}, {@code error},
 * {@code results.detectors[].impact}) and carries the detector's IMPACT
 * verbatim — SARIF folds High and Medium into one level, and the impact is
 * exactly what decides error versus warning.
 *
 * <p>Measured too: slither refuses to overwrite an existing report file
 * ({@code "exists already, the overwrite is prevented"}), so every run
 * writes into a fresh directory of its own.
 */
final class SlitherReport {

    /** The same hint the Environment Doctor shows beside slither (parity-tested). */
    static final String INSTALL_HINT = "pip3 install slither-analyzer";

    /** Ceiling on the report read; past it the report is refused, never truncated. */
    static final long MAX_REPORT_BYTES = 8L * 1024 * 1024;

    /** The LCD refusal is clipped so a long error cannot push the verdict off the panel. */
    private static final int MAX_REFUSAL_CHARS = 120;

    /**
     * One parsed run. {@code refusal} is null when the report was read; when
     * it is set the run produced no trustworthy findings and nothing should
     * be published (a failed run is not an all-clear).
     */
    record Result(List<DiagnosticsBus.Problem> problems, int errors, int warnings, String refusal) {
        Result {
            problems = List.copyOf(problems);
        }

        static Result refused(String why) {
            return new Result(List.of(), 0, 0, why);
        }

        /** The E/W line PURITY's count LCD shows, the same shape as every other linter. */
        String lcd() {
            return "E:" + errors + " W:" + warnings;
        }
    }

    private SlitherReport() {
    }

    /**
     * slither's impact ladder is High / Medium / Low / Informational /
     * Optimization. High and Medium are defects a person must fix before
     * shipping a contract; the rest are advice. The bus has two levels, so
     * the first two are errors and everything else is a warning — including
     * an impact name this version of slither does not know yet (advice is
     * the safe default for an unrecognised label; it never hides a finding).
     */
    static boolean isErrorImpact(String impact) {
        if (impact == null) {
            return false;
        }
        String i = impact.trim().toLowerCase(Locale.ROOT);
        return "high".equals(i) || "medium".equals(i);
    }

    /** Reads the report file with a byte ceiling, then parses it. */
    static Result read(Path report, File baseDir) {
        if (report == null || !Files.isRegularFile(report)) {
            return Result.refused("NO SLITHER REPORT — SEE THE OUTPUT TAB");
        }
        byte[] bytes;
        try (InputStream in = Files.newInputStream(report)) {
            // one probe byte past the cap: a file that grows after a size
            // check still cannot be read whole
            bytes = in.readNBytes((int) MAX_REPORT_BYTES + 1);
        } catch (IOException ex) {
            return Result.refused(clip("SLITHER REPORT UNREADABLE — " + ex.getMessage()));
        }
        if (bytes.length > MAX_REPORT_BYTES) {
            return Result.refused("SLITHER REPORT OVER 8 MB — NOT PARSED");
        }
        return parse(new String(bytes, StandardCharsets.UTF_8), baseDir);
    }

    /** Parses slither's {@code --json} report; relative paths resolve against {@code baseDir}. */
    static Result parse(String json, File baseDir) {
        JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException ex) {
            return Result.refused("SLITHER REPORT IS NOT JSON — SEE THE OUTPUT TAB");
        }
        if (!root.optBoolean("success", false)) {
            String error = root.optString("error", "");
            String first = error.isBlank() ? "no reason given" : firstLine(error);
            return Result.refused(clip("SLITHER FAILED — " + first));
        }
        JSONObject results = root.optJSONObject("results");
        JSONArray detectors = results == null ? null : results.optJSONArray("detectors");
        List<DiagnosticsBus.Problem> problems = new ArrayList<>();
        int errors = 0;
        int warnings = 0;
        if (detectors != null) {
            for (int i = 0; i < detectors.length(); i++) {
                JSONObject finding = detectors.optJSONObject(i);
                if (finding == null) {
                    continue;
                }
                boolean error = isErrorImpact(finding.optString("impact", null));
                if (error) {
                    errors++;
                } else {
                    warnings++;
                }
                JSONObject mapping = primaryMapping(finding.optJSONArray("elements"));
                File file = mapping == null ? null : resolve(mapping, baseDir);
                int line = mapping == null ? 0 : firstLineNumber(mapping);
                if (file != null && line > 0) {
                    problems.add(new DiagnosticsBus.Problem(file, line, message(finding), error));
                }
            }
        }
        return new Result(problems, errors, warnings, null);
    }

    /** The first element that points into the project's own source (dependencies last). */
    private static JSONObject primaryMapping(JSONArray elements) {
        if (elements == null) {
            return null;
        }
        JSONObject dependencyFallback = null;
        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.optJSONObject(i);
            JSONObject mapping = element == null ? null : element.optJSONObject("source_mapping");
            if (mapping == null || firstLineNumber(mapping) <= 0) {
                continue;
            }
            if (!mapping.optBoolean("is_dependency", false)) {
                return mapping;
            }
            if (dependencyFallback == null) {
                dependencyFallback = mapping;
            }
        }
        return dependencyFallback;
    }

    private static int firstLineNumber(JSONObject mapping) {
        JSONArray lines = mapping.optJSONArray("lines");
        return lines == null || lines.isEmpty() ? 0 : lines.optInt(0, 0);
    }

    /** The absolute path when it names a real file here, else the relative one under the project. */
    private static File resolve(JSONObject mapping, File baseDir) {
        String absolute = mapping.optString("filename_absolute", "");
        if (!absolute.isEmpty()) {
            File f = new File(absolute);
            if (f.isFile()) {
                return f;
            }
        }
        String relative = mapping.optString("filename_relative", "");
        if (!relative.isEmpty() && baseDir != null) {
            File f = new File(baseDir, relative);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /** "reentrancy-eth (High): Reentrancy in Vault.withdraw() (src/Vault.sol#11-16)". */
    private static String message(JSONObject finding) {
        String check = finding.optString("check", "slither");
        String impact = finding.optString("impact", "");
        String description = firstLine(finding.optString("description", ""));
        if (description.endsWith(":")) {
            description = description.substring(0, description.length() - 1);
        }
        return check + (impact.isEmpty() ? "" : " (" + impact + ")")
                + (description.isEmpty() ? "" : ": " + description);
    }

    private static String firstLine(String text) {
        String trimmed = text.strip();
        int nl = trimmed.indexOf('\n');
        return (nl < 0 ? trimmed : trimmed.substring(0, nl)).strip();
    }

    private static String clip(String text) {
        if (text.length() <= MAX_REFUSAL_CHARS) {
            return text;
        }
        int end = text.offsetByCodePoints(0, text.codePointCount(0, MAX_REFUSAL_CHARS - 1));
        return text.substring(0, end) + "…";
    }
}
