package org.nmox.studio.rack.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one GitHub-link ladder (3.2.0): Copy as Markdown with Link, Open on
 * GitHub and Copy GitHub Link in the editor, and the same two rows on
 * Project Studio's tree all ask {@link GitHubLinks#resolve}. Repositories
 * are temp dirs carrying a {@code .git/HEAD} and {@code .git/config} —
 * everything the resolver reads, and nothing it spawns.
 */
class GitHubLinksTest {

    private static Path repo(Path tmp, String originUrl) throws Exception {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.writeString(repo.resolve(".git/config"),
                originUrl == null ? "[core]\n" : "[remote \"origin\"]\n\turl = " + originUrl + "\n");
        Files.createDirectories(repo.resolve("src/components"));
        Files.writeString(repo.resolve("src/App.jsx"), "x\n");
        return repo;
    }

    @Test
    @DisplayName("a file links its blob, repo-relative with forward slashes, with the line fragment")
    void aFileLinksItsBlob(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "git@github.com:NMOX/demo.git");
        GitHubLinks.Link out = GitHubLinks.resolve(repo.resolve("src/App.jsx").toFile(), 3, 14);
        assertThat(out.refusal()).isNull();
        assertThat(out.url()).isEqualTo("https://github.com/NMOX/demo/blob/main/src/App.jsx#L3-L14");
        assertThat(out.relPath()).isEqualTo("src/App.jsx");
        assertThat(out.slug()).isEqualTo("NMOX/demo");
        assertThat(out.ref()).isEqualTo("main");
        assertThat(GitHubLinks.resolve(repo.resolve("src/App.jsx").toFile(), 0, 0).url())
                .as("a tree row has no lines: the whole file")
                .isEqualTo("https://github.com/NMOX/demo/blob/main/src/App.jsx");
    }

    @Test
    @DisplayName("a folder links its tree, and the repository's root folder links tree/<ref> — never a blob")
    void aFolderLinksItsTree(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "https://github.com/NMOX/demo");
        GitHubLinks.Link folder = GitHubLinks.resolve(repo.resolve("src/components").toFile(), 0, 0);
        assertThat(folder.refusal()).isNull();
        assertThat(folder.url()).isEqualTo("https://github.com/NMOX/demo/tree/main/src/components");
        GitHubLinks.Link root = GitHubLinks.resolve(repo.toFile(), 0, 0);
        assertThat(root.refusal()).as("the root is a folder that IS the repository, not outside it").isNull();
        assertThat(root.url()).isEqualTo("https://github.com/NMOX/demo/tree/main");
        assertThat(GitHubLinks.what(root, 0, 0)).as("the root is named by its repository").isEqualTo("NMOX/demo");
        assertThat(GitHubLinks.what(folder, 0, 0)).isEqualTo("src/components");
    }

    @Test
    @DisplayName("not inside a repository refuses by name")
    void notInRepoRefuses(@TempDir Path tmp) throws Exception {
        Path loose = tmp.resolve("loose/f.js");
        Files.createDirectories(loose.getParent());
        Files.writeString(loose, "x");
        assertThat(GitHubLinks.resolve(loose.toFile(), 1, 1).refusal())
                .isEqualTo("f.js is not inside a git repository");
        assertThat(GitHubLinks.resolve(loose.getParent().toFile(), 0, 0).refusal())
                .as("a loose folder too").isEqualTo("loose is not inside a git repository");
    }

    @Test
    @DisplayName("a repository with no origin remote refuses")
    void noOriginRefuses(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, null);
        assertThat(GitHubLinks.resolve(repo.resolve("src/App.jsx").toFile(), 1, 1).refusal())
                .isEqualTo("the repository has no origin remote");
    }

    @Test
    @DisplayName("an origin that is not GitHub refuses and names the origin — a link the product cannot vouch for is not made")
    void nonGitHubOriginRefuses(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "git@gitlab.com:o/r.git");
        assertThat(GitHubLinks.resolve(repo.resolve("src/App.jsx").toFile(), 1, 1).refusal())
                .isEqualTo("origin is not a GitHub remote (git@gitlab.com:o/r.git)");
        assertThat(GitHubLinks.resolve(repo.resolve("src").toFile(), 0, 0).refusal())
                .as("a folder refuses the same way").contains("not a GitHub remote");
    }

    @Test
    @DisplayName("an unreadable HEAD refuses; a detached HEAD links by its short sha")
    void headRungs(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "https://github.com/o/r");
        Files.writeString(repo.resolve(".git/HEAD"), "garbage\n");
        assertThat(GitHubLinks.resolve(repo.resolve("src/App.jsx").toFile(), 1, 1).refusal())
                .isEqualTo("HEAD could not be read");
        Files.writeString(repo.resolve(".git/HEAD"), "0123456789abcdef0123456789abcdef01234567\n");
        assertThat(GitHubLinks.resolve(repo.resolve("src/App.jsx").toFile(), 0, 0).url())
                .isEqualTo("https://github.com/o/r/blob/0123456/src/App.jsx");
    }

    @Test
    @DisplayName("Copy puts the URL on the clipboard and says so; nothing is opened")
    void copyGesture(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "git@github.com:NMOX/demo.git");
        List<String> opened = new ArrayList<>();
        List<String> clip = new ArrayList<>();
        List<String> status = new ArrayList<>();
        GitHubLinks.act(GitHubLinks.Gesture.COPY, repo.resolve("src/App.jsx").toFile(), 7, 7,
                u -> opened.add(u), clip::add, status::add);
        assertThat(opened).isEmpty();
        assertThat(clip).containsExactly("https://github.com/NMOX/demo/blob/main/src/App.jsx#L7");
        assertThat(status).containsExactly("Copied the GitHub link to src/App.jsx#L7 at NMOX/demo@main: "
                + "https://github.com/NMOX/demo/blob/main/src/App.jsx#L7");
    }

    @Test
    @DisplayName("Open hands the URL to the browser, and a browser that declines is said out loud")
    void openGesture(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "git@github.com:NMOX/demo.git");
        List<String> opened = new ArrayList<>();
        List<String> clip = new ArrayList<>();
        List<String> status = new ArrayList<>();
        GitHubLinks.act(GitHubLinks.Gesture.OPEN, repo.resolve("src").toFile(), 0, 0,
                u -> opened.add(u), clip::add, status::add);
        assertThat(opened).containsExactly("https://github.com/NMOX/demo/tree/main/src");
        assertThat(clip).isEmpty();
        assertThat(status).singleElement().asString().startsWith("Opened src on GitHub at NMOX/demo@main");

        status.clear();
        GitHubLinks.act(GitHubLinks.Gesture.OPEN, repo.resolve("src").toFile(), 0, 0,
                u -> false, clip::add, status::add);
        assertThat(status).singleElement().asString()
                .startsWith("Open on GitHub: no browser could open https://github.com/NMOX/demo/tree/main/src");
    }

    @Test
    @DisplayName("a refusal is spoken in the gesture's own name, and neither the browser nor the clipboard is touched")
    void refusalSpeaksAndDoesNothing(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, null);
        List<String> side = new ArrayList<>();
        List<String> status = new ArrayList<>();
        for (GitHubLinks.Gesture g : GitHubLinks.Gesture.values()) {
            GitHubLinks.act(g, repo.resolve("src/App.jsx").toFile(), 1, 1, u -> side.add(u), side::add, status::add);
        }
        assertThat(side).isEmpty();
        assertThat(status).containsExactly("Open on GitHub: the repository has no origin remote",
                "Copy GitHub Link: the repository has no origin remote");
    }

    @Test
    @DisplayName("opening goes to the user's own browser, on the lane, never the in-app one")
    void opensInTheSystemBrowserOffTheEdt() throws Exception {
        String src = Files.readString(Path.of("src/main/java/org/nmox/studio/rack/service/GitHubLinks.java"));
        assertThat(src).contains("ServingLinks::openInSystemBrowser").contains("RP.post(")
                .doesNotContain("EmbeddedBrowser").doesNotContain("ProcessBuilder");
    }

    @Test
    @DisplayName("New Pull Request opens GitHub's compare page for the checked-out branch, slash kept, from anywhere inside")
    void newPullRequest(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "git@github.com:NMOX/demo.git");
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/feature/login form\n");
        List<String> opened = new ArrayList<>();
        List<String> said = new ArrayList<>();
        GitHubLinks.actPullRequest(repo.resolve("src/components").toFile(), u -> opened.add(u), said::add);
        assertThat(opened).containsExactly("https://github.com/NMOX/demo/compare/feature/login%20form?expand=1");
        assertThat(said).containsExactly("Opened the New Pull Request page on GitHub for feature/login form (NMOX/demo)");
    }

    @Test
    @DisplayName("a detached HEAD has no branch to propose; not GitHub and no origin refuse as a link does; nothing opens")
    void newPullRequestRefuses(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "https://github.com/NMOX/demo");
        Files.writeString(repo.resolve(".git/HEAD"), "0123456789abcdef0123456789abcdef01234567\n");
        List<String> opened = new ArrayList<>();
        List<String> said = new ArrayList<>();
        GitHubLinks.actPullRequest(repo.toFile(), u -> opened.add(u), said::add);
        assertThat(said).containsExactly("New Pull Request: HEAD is detached — check out the branch to propose");
        Files.writeString(repo.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.writeString(repo.resolve(".git/config"), "[remote \"origin\"]\n\turl = https://gitlab.com/a/b.git\n");
        GitHubLinks.actPullRequest(repo.toFile(), u -> opened.add(u), said::add);
        assertThat(said.get(1)).startsWith("New Pull Request: origin is not a GitHub remote");
        assertThat(opened).as("a refusal opens nothing").isEmpty();
        GitHubLinks.actPullRequest(repo.toFile(), u -> false, said::add);
        assertThat(said).hasSize(3);
    }

    @Test
    @DisplayName("a name starting with two dots is inside the repository; a token in a non-GitHub origin is never shown")
    void dotDotNamesAndCredentials(@TempDir Path tmp) throws Exception {
        Path repo = repo(tmp, "https://github.com/NMOX/demo");
        Files.createDirectories(repo.resolve("..cache"));
        Files.writeString(repo.resolve("..cache/x.ts"), "x");
        GitHubLinks.Link out = GitHubLinks.resolve(repo.resolve("..cache/x.ts").toFile(), 1, 1);
        assertThat(out.refusal()).isNull();
        assertThat(out.url()).isEqualTo("https://github.com/NMOX/demo/blob/main/..cache/x.ts#L1");
        Files.writeString(repo.resolve(".git/config"),
                "[remote \"origin\"]\n\turl = https://oauth2:glpat-SECRET@gitlab.com/g/p.git\n");
        String refusal = GitHubLinks.resolve(repo.resolve("src/App.jsx").toFile(), 1, 1).refusal();
        assertThat(refusal).contains("gitlab.com/g/p.git").doesNotContain("SECRET").doesNotContain("oauth2");
    }
}
