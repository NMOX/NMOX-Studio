package org.nmox.studio.editor.symbols;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import org.nmox.studio.core.spi.ProjectAim;
import org.nmox.studio.editor.symbols.ProjectSymbols.Symbol;
import org.netbeans.spi.jumpto.support.NameMatcher;
import org.netbeans.spi.jumpto.support.NameMatcherFactory;
import org.netbeans.spi.jumpto.symbol.SymbolDescriptor;
import org.netbeans.spi.jumpto.symbol.SymbolProvider;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.Line;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Feeds the platform's Go to Symbol dialog (and its ⌘I bridge — jumpto
 * registers its own quick-search provider over every SymbolProvider)
 * from {@link ProjectSymbols} over the AIMED project. The platform owns
 * the dialog, the matcher, and the threading (computeSymbolNames runs
 * off the EDT and is re-called per keystroke with cancel() between);
 * this class owns only the bounded index and the open-at-line.
 */
@ServiceProvider(service = SymbolProvider.class)
public final class NmoxSymbolProvider implements SymbolProvider {

    private final ProjectSymbols index = new ProjectSymbols();
    private volatile boolean cancelled;

    @Override
    public String name() {
        return "nmox-symbols";
    }

    @Override
    public String getDisplayName() {
        return "NMOX project symbols";
    }

    @Override
    public void computeSymbolNames(Context context, Result result) {
        cancelled = false;
        String text = context.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        ProjectAim aim = Lookup.getDefault().lookup(ProjectAim.class);
        File dir = aim == null ? null : aim.projectDir();
        if (dir == null) {
            result.setMessage("Aim a project to search its symbols.");
            return;
        }
        NameMatcher matcher;
        try {
            matcher = NameMatcherFactory.createNameMatcher(text, context.getSearchType());
        } catch (IllegalArgumentException badPattern) {
            return; // a half-typed regex matches nothing, quietly
        }
        List<Symbol> all = index.refresh(dir.toPath(),
                NmoxSymbolProvider::mimeOf, v -> cancelled);
        List<SymbolDescriptor> hits = new ArrayList<>();
        Path root = dir.toPath();
        for (Symbol s : all) {
            if (cancelled) {
                return;
            }
            if (SymbolMatch.matches(matcher, s.name())) {
                hits.add(new Hit(s, root));
            }
        }
        result.addResult(hits);
        if (index.wasTruncated()) {
            // a silently partial index would read as a complete one
            result.setMessage("Large project — symbols indexed from the first "
                    + ProjectSymbols.MAX_FILES + " files only.");
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public void cleanup() {
        cancelled = true;
    }

    /** The platform's resolvers know every mime the product registered. */
    static String mimeOf(Path file) {
        FileObject fo = FileUtil.toFileObject(FileUtil.normalizeFile(file.toFile()));
        return fo == null ? null : fo.getMIMEType();
    }

    /** One dialog row: name, kind detail, owning file, open-at-line. */
    static final class Hit extends SymbolDescriptor {

        private final Symbol symbol;
        private final Path root;

        Hit(Symbol symbol, Path root) {
            this.symbol = symbol;
            this.root = root;
        }

        @Override
        public Icon getIcon() {
            return null;
        }

        @Override
        public String getSymbolName() {
            String detail = symbol.detail() == null || symbol.detail().isBlank()
                    ? "" : " — " + symbol.detail();
            return symbol.name() + detail;
        }

        @Override
        public String getOwnerName() {
            try {
                return root.relativize(symbol.file()).toString()
                        .replace(File.separatorChar, '/');
            } catch (IllegalArgumentException e) {
                return symbol.file().getFileName().toString();
            }
        }

        @Override
        public String getProjectName() {
            return root.getFileName() == null ? "" : root.getFileName().toString();
        }

        @Override
        public Icon getProjectIcon() {
            return null;
        }

        @Override
        public FileObject getFileObject() {
            return FileUtil.toFileObject(FileUtil.normalizeFile(symbol.file().toFile()));
        }

        @Override
        public int getOffset() {
            return -1; // line-addressed; open() does the honest jump
        }

        @Override
        public void open() {
            FileObject fo = getFileObject();
            if (fo == null) {
                return;
            }
            try {
                LineCookie lc = DataObject.find(fo).getLookup().lookup(LineCookie.class);
                if (lc != null) {
                    lc.getLineSet().getCurrent(Math.max(0, symbol.line()))
                            .show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
                }
            } catch (Exception ex) {
                org.openide.awt.StatusDisplayer.getDefault().setStatusText(
                        "Could not open " + fo.getNameExt() + ": " + ex.getMessage());
            }
        }
    }
}
