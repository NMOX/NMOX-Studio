package org.nmox.studio.core.util;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TreeTextTest {

    private static Path fixture(Path tmp) throws Exception {
        Path root = tmp.resolve("react-walk");
        Files.createDirectories(root.resolve("src/components"));
        Files.createDirectories(root.resolve("node_modules/react"));
        Files.createDirectories(root.resolve("public"));
        Files.writeString(root.resolve("src/App.jsx"), "");
        Files.writeString(root.resolve("src/components/Button.jsx"), "");
        Files.writeString(root.resolve("node_modules/react/index.js"), "");
        Files.writeString(root.resolve("index.html"), "");
        Files.writeString(root.resolve("package.json"), "");
        Files.writeString(root.resolve("README.md"), "");
        Files.writeString(root.resolve(".nmoxrack.json"), "{}");
        Files.writeString(root.resolve(".nmoxtasks.json"), "{}");
        return root;
    }

    @Test
    @DisplayName("directories first in case-folded order, heavy dirs named but never entered, box-drawing exact")
    void rendersTheReadmeTree(@TempDir Path tmp) throws Exception {
        TreeText.Result r = TreeText.render(fixture(tmp), 4, 200);
        assertThat(r.elided()).isZero();
        assertThat(r.text()).isEqualTo(String.join("\n",
                "react-walk/",
                "├── node_modules/ …",
                "├── public/",
                "├── src/",
                "│   ├── components/",
                "│   │   └── Button.jsx",
                "│   └── App.jsx",
                "├── index.html",
                "├── package.json",
                "└── README.md") + "\n");
        assertThat(r.text()).doesNotContain("react/");
        assertThat(r.text()).as("the IDE's own workspace files are the product's, not the project's").doesNotContain(".nmox");
    }

    @Test
    @DisplayName("the depth cap marks the floor and the entry cap counts what it left out — never a silent drop")
    void caps(@TempDir Path tmp) throws Exception {
        Path root = fixture(tmp);
        TreeText.Result shallow = TreeText.render(root, 2, 200);
        assertThat(shallow.text()).contains("│   ├── components/ …").doesNotContain("Button.jsx");
        TreeText.Result few = TreeText.render(root, 4, 3);
        assertThat(few.elided()).isPositive();
        assertThat(few.text()).endsWith("… (" + Plural.of(few.elided(), "more entry", "more entries") + " not shown)\n");
        assertThat(few.text().lines().count()).isEqualTo(1 + 3 + 1);
    }

    @Test
    @DisplayName("a symlinked directory is listed as a file, never followed (a loop would be an unbounded read)")
    void symlinkNotFollowed(@TempDir Path tmp) throws Exception {
        Path root = fixture(tmp);
        try {
            Files.createSymbolicLink(root.resolve("loop"), root);
        } catch (UnsupportedOperationException | java.io.IOException noSymlinks) {
            return; // a platform without symlinks has nothing to prove here
        }
        TreeText.Result r = TreeText.render(root, 6, 500);
        assertThat(r.text()).contains("── loop\n").doesNotContain("loop/");
    }
}
