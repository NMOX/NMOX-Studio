package org.nmox.studio.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Windows installer speaks the languages it can (v2.105.0).
 *
 * <p>The setup wizard is the first text a user ever sees, before any
 * preference of theirs exists to consult, and it is the one surface a later
 * release cannot correct — an installer that already ran, ran in whatever
 * language it had. It had English.
 *
 * <p>The translations are Inno Setup's own bundled {@code .isl} files, not
 * anything written here, and the three strings we contribute resolve through
 * {@code {cm:…}} so they come from those same files. Five of the IDE's
 * thirteen languages ship no official {@code .isl}: they are named below as
 * a deliberate absence rather than left to be rediscovered, because
 * vendoring an unreviewed community file would put text nobody here can read
 * in front of every user of that language.
 *
 * <p>That this compiles is proven by the {@code windows-installer-check}
 * workflow on a real Windows runner — {@code iscc} does not run on this
 * machine, and this gate does not pretend otherwise. What it holds is the
 * shape: the list matches what Inno ships, our own strings stay translated,
 * and the deliberate absences stay written down.
 */
class InstallerLanguagesTest {

    private static final Path ISS = Path.of("..", "packaging", "windows", "nmox-studio.iss");

    /** Every language Inno Setup 6 ships an official message file for. */
    private static final Set<String> INNO_SHIPS = Set.of(
            "Default", "Armenian", "BrazilianPortuguese", "Bulgarian", "Catalan", "Corsican",
            "Czech", "Danish", "Dutch", "Finnish", "French", "German", "Hebrew", "Hungarian",
            "Icelandic", "Italian", "Japanese", "Norwegian", "Polish", "Portuguese", "Russian",
            "Slovak", "Slovenian", "Spanish", "Turkish", "Ukrainian");

    /** No official .isl exists for these, so the wizard is English for them. */
    private static final Set<String> NO_OFFICIAL_ISL = Set.of("id", "tl", "vi", "zh", "hi");

    private static String iss() throws IOException {
        return Files.readString(ISS, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("every declared message file is one Inno Setup actually ships")
    void everyMessageFileIsReal() throws IOException {
        Matcher m = Pattern.compile("MessagesFile: \"compiler:(?:Languages\\\\)?(\\w+)\\.isl\"")
                .matcher(iss());
        List<String> declared = new ArrayList<>();
        while (m.find()) {
            declared.add(m.group(1));
        }
        assertThat(declared).as("the installer's language list").hasSizeGreaterThanOrEqualTo(8);
        assertThat(INNO_SHIPS).as("a message file Inno does not ship fails the compile on the "
                + "Windows runner, hours after the merge — catch it here").containsAll(declared);
    }

    @Test
    @DisplayName("the wizard covers every IDE language that has an official translation")
    void coversWhatItCan() throws IOException {
        String iss = iss();
        Set<String> declared = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^Name: \"([a-z]{2})\"; MessagesFile:").matcher(iss);
        while (m.find()) {
            declared.add(m.group(1));
        }
        List<String> missing = new ArrayList<>();
        for (String lang : uiLanguages()) {
            if (!declared.contains(lang) && !NO_OFFICIAL_ISL.contains(lang)) {
                missing.add(lang);
            }
        }
        assertThat(missing)
                .as("an IDE language with an official Inno translation the installer does not use")
                .isEmpty();
        // and the absences stay honest: a language that GAINS an official
        // file should be added, not left in a list that says it cannot be
        assertThat(NO_OFFICIAL_ISL).as("the written absences").hasSize(5);
        for (String lang : NO_OFFICIAL_ISL) {
            assertThat(uiLanguages()).as("%s is blessed absent but is not an IDE language", lang)
                    .contains(lang);
        }
    }

    @Test
    @DisplayName("our own three strings resolve from the chosen language, not from English here")
    void ourStringsAreTranslatedToo() throws IOException {
        String iss = iss();
        assertThat(iss).contains("{cm:CreateDesktopIcon}")
                .contains("{cm:AdditionalIcons}")
                .contains("{cm:LaunchProgram,NMOX Studio}");
        // the literals they replaced would have stayed English in every wizard
        assertThat(iss).doesNotContain("Create a &desktop icon")
                .doesNotContain("Additional icons:")
                .doesNotContain("Description: \"Launch NMOX Studio\"");
    }

    /** The languages the IDE's chrome speaks, English aside — one vocabulary. */
    private static List<String> uiLanguages() throws IOException {
        Path src = Path.of("..", "core", "src", "main", "java", "org", "nmox", "studio",
                "core", "util", "UiLocale.java");
        String body = Files.readString(src, StandardCharsets.UTF_8);
        String list = body.substring(body.indexOf("SUPPORTED = List.of("));
        list = list.substring(0, list.indexOf(";"));
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("new Choice\\(\"([a-z]{2})\"").matcher(list);
        while (m.find()) {
            if (!"en".equals(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }
}
