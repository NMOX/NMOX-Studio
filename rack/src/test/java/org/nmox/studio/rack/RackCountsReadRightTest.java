package org.nmox.studio.rack;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nmox.studio.rack.model.RackCard;
import org.nmox.studio.rack.model.RackCompat;
import org.nmox.studio.rack.model.RackShare;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rack's counted sentences read like English at every count (ledger 105).
 *
 * <p>Three sentences rendered {@code {0} devices, {1} cables} for any number,
 * so a one-device rack said "1 devices, 1 cables"; the import manifest also
 * said "0 saved armed or running arrive at rest" on every rack that had none —
 * a clause about nothing, ungrammatical about nothing. The counts take
 * MessageFormat plural branches now (the {@code {n,choice,…}} shape the l10n
 * arc already uses, so pl/ru/uk can carry their three and four forms), and the
 * at-rest clause is said only when there is one.
 */
class RackCountsReadRightTest {

    private Locale before;

    @BeforeEach
    void english() {
        before = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(before);
    }

    private static RackShare.Manifest manifest(int devices, int cables, int atRest) {
        List<RackShare.Device> list = new java.util.ArrayList<>();
        for (int i = 0; i < devices; i++) {
            list.add(new RackShare.Device("tempo", true));
        }
        return new RackShare.Manifest(list, cables, List.of(), List.of(), atRest, "", RackCard.EMPTY);
    }

    private static String imported(int devices, int cables, int atRest) {
        return RackTopComponent.manifestText(manifest(devices, cables, atRest), RackCard.EMPTY,
                new RackCompat.Report(1, false, "", false, List.of(), List.of()));
    }

    @Test
    @DisplayName("one of a thing reads singular, and more than one reads plural — the import manifest")
    void theImportManifestCounts() {
        assertThat(imported(1, 1, 0)).contains("1 device, 1 cable.").doesNotContain("1 devices").doesNotContain("1 cables");
        assertThat(imported(7, 10, 0)).contains("7 devices, 10 cables.");
        assertThat(imported(2, 0, 0)).as("none of a thing is plural in English").contains("2 devices, 0 cables.");
    }

    @Test
    @DisplayName("the at-rest clause is said only when a device really was saved switched on — never a sentence about none")
    void theAtRestClauseSpeaksOnlyWhenItApplies() {
        assertThat(imported(7, 10, 0)).as("a rack with nothing switched on says nothing about it")
                .doesNotContain("at rest");
        assertThat(imported(7, 10, 1)).contains("One device was saved with a switch on; it arrives at rest.");
        assertThat(imported(7, 10, 3)).contains("3 devices were saved with a switch on; they arrive at rest.");
    }

    @Test
    @DisplayName("the sender's page counts the same way, with settings and without")
    void theLeavingPageCounts() {
        org.json.JSONArray devices = new org.json.JSONArray();
        devices.put(new org.json.JSONObject().put("type", "cmd")
                .put("state", new org.json.JSONObject().put("command", "npm test")));
        org.json.JSONObject one = new org.json.JSONObject().put("version", 1)
                .put("devices", devices).put("cables", new org.json.JSONArray());
        assertThat(RackTopComponent.leavingText(RackShare.export(one, null, "2.180.0")))
                .contains("1 device, 0 cables.").contains("Settings that travel:");

        org.json.JSONObject bare = new org.json.JSONObject().put("version", 1)
                .put("devices", new org.json.JSONArray().put(new org.json.JSONObject().put("type", "console")))
                .put("cables", new org.json.JSONArray());
        assertThat(RackTopComponent.leavingText(RackShare.export(bare, null, "2.180.0")))
                .contains("1 device, 0 cables.").contains("No command, path or address travels");
    }
}
