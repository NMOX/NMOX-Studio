package org.nmox.studio.tools.npm;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.search.SearchRoot;
import org.netbeans.api.search.SearchScopeOptions;
import org.netbeans.api.search.provider.SearchListener;
import org.netbeans.spi.search.SearchFilterDefinition;
import org.netbeans.spi.search.SearchInfoDefinition;
import org.netbeans.spi.search.SearchInfoDefinitionFactory;
import org.nmox.studio.core.util.VsCodeExcludes;
import org.nmox.studio.core.util.VsCodeSettingsFile;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * What Edit ▸ Find in Projects walks in a WebProject: the platform's own
 * walk of the project folder with its two default filters (visibility,
 * and the sharability one that keeps it out of what git ignores -
 * {@link WebProjectSharability}), plus one more: the
 * {@code search.exclude} of the project's {@code .vscode/settings.json},
 * laid over its {@code files.exclude} as VS Code lays it
 * ({@link VsCodeExcludes}).
 *
 * <p><b>Why a search definition and not the sharability answer.</b> The
 * sharability query is also read by the platform's git module, which
 * treats "not sharable" as ignored and remembers it for the session (the
 * 3.2 review): a path a repository merely does not want SEARCHED must
 * never be answered there, or it would vanish from Commit. This class is
 * read by the search alone. Read from the RELEASE310 bytecode: the Open
 * Projects and Main Project scopes ask the project's lookup for a
 * {@code SearchInfoDefinition} before building their default walk
 * ({@code AbstractProjectSearchScope.createSingleProjectSearchInfo}), and
 * the default walk is exactly what {@link #defaults()} builds - the
 * project folder is a WebProject's one generic source root.
 *
 * <p><b>One way back.</b> The dialog's "Search in Generated Sources"
 * already drops the sharability filter; here it drops the settings'
 * exclusions too, as VS Code's "Use Exclude Settings and Ignore Files"
 * switches both off together. A search scoped to a folder selected inside
 * the project is built by the platform without asking the project, and is
 * not filtered by these settings.
 *
 * <p><b>Cost and freshness.</b> The settings are read once per search, on
 * the search's own thread when it starts to enumerate (a stat per level,
 * a parse only when the file moved), so an edit is seen by the next
 * search. {@link #canSearch()} and {@link #getSearchRoots()} - which the
 * dialog may ask on the EDT - never read them.
 */
final class WebProjectSearch extends SearchInfoDefinition {

    private final FileObject projectDir;

    WebProjectSearch(FileObject projectDir) {
        this.projectDir = projectDir;
    }

    @Override
    public boolean canSearch() {
        return defaults().canSearch();
    }

    @Override
    public List<SearchRoot> getSearchRoots() {
        return defaults().getSearchRoots();
    }

    @Override
    public Iterator<FileObject> filesToSearch(SearchScopeOptions options, SearchListener listener,
            AtomicBoolean terminated) {
        return walk(options).filesToSearch(options, listener, terminated);
    }

    @Override
    public Iterator<URI> urisToSearch(SearchScopeOptions options, SearchListener listener,
            AtomicBoolean terminated) {
        return walk(options).urisToSearch(options, listener, terminated);
    }

    /** The platform's default walk of the project folder. */
    private SearchInfoDefinition defaults() {
        return SearchInfoDefinitionFactory.createSearchInfo(new FileObject[] {projectDir});
    }

    /** The walk for one search: the defaults, and the settings' exclusions unless everything is asked for. */
    private SearchInfoDefinition walk(SearchScopeOptions options) {
        if (options != null && options.isSearchInGenerated()) {
            return defaults();
        }
        File dir = FileUtil.toFile(projectDir);
        VsCodeExcludes excludes = dir == null ? VsCodeExcludes.NONE : VsCodeSettingsFile.excludesFor(dir);
        if (excludes.searchPatterns().isEmpty()) {
            return defaults();
        }
        List<SearchFilterDefinition> filters = new ArrayList<>(SearchInfoDefinitionFactory.DEFAULT_FILTER_DEFS);
        filters.add(new Excluded(projectDir, excludes));
        return SearchInfoDefinitionFactory.createSearchInfo(new FileObject[] {projectDir},
                filters.toArray(new SearchFilterDefinition[0]));
    }

    /** The settings' exclusions as the search's filter: a folder that matches is not entered. */
    static final class Excluded extends SearchFilterDefinition {

        private final FileObject projectDir;
        private final VsCodeExcludes excludes;

        Excluded(FileObject projectDir, VsCodeExcludes excludes) {
            this.projectDir = projectDir;
            this.excludes = excludes;
        }

        @Override
        public boolean searchFile(FileObject file) {
            if (file.isFolder()) {
                throw new IllegalArgumentException(file + " is a folder");
            }
            return !skipped(file);
        }

        @Override
        public FolderResult traverseFolder(FileObject folder) {
            if (!folder.isFolder()) {
                throw new IllegalArgumentException(folder + " is not a folder");
            }
            return skipped(folder) ? FolderResult.DO_NOT_TRAVERSE : FolderResult.TRAVERSE;
        }

        private boolean skipped(FileObject fo) {
            String relative = FileUtil.getRelativePath(projectDir, fo);
            return relative != null && !relative.isEmpty() && excludes.skipsSearch(relative);
        }
    }
}
