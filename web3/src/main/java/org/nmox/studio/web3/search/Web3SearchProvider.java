package org.nmox.studio.web3.search;

import java.io.File;
import java.util.List;
import org.netbeans.spi.quicksearch.SearchProvider;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.nmox.studio.web3.io.Web3WorkspaceIO;
import org.nmox.studio.web3.model.ContractArtifact;
import org.nmox.studio.web3.model.DeploymentRecord;
import org.nmox.studio.web3.ui.Web3StudioTopComponent;

/**
 * Quick Search (⌘I) over Contract Studio: type a contract name, a
 * deployment address, or a network name and Enter opens the tab with
 * the hit selected. Matching is delegated to the pure
 * {@link Web3SearchIndex}; the index is a snapshot {@link #publish}ed
 * by Contract Studio whenever its artifacts or deployments change — a
 * search NEVER scans the disk or talks to a node. Before the tab has
 * published anything (it opens at startup, so this is rare), the
 * provider falls back to the deployments committed in
 * {@code .nmoxweb3.json}; contract hits appear once a scan has run.
 */
public class Web3SearchProvider implements SearchProvider {

    private static volatile Web3SearchIndex published;

    /**
     * Called by Contract Studio (on the EDT) with its current artifacts
     * and address book. The index copies both, so the caller's lists
     * stay its own.
     */
    public static void publish(List<ContractArtifact> artifacts,
            List<DeploymentRecord> deployments) {
        published = new Web3SearchIndex(artifacts, deployments);
    }

    /** Test seam: forget the published snapshot. */
    static void reset() {
        published = null;
    }

    /** The index to search: the published snapshot, else deployments from disk. */
    static Web3SearchIndex currentIndex() {
        Web3SearchIndex index = published;
        if (index != null) {
            return index;
        }
        return new Web3SearchIndex(List.of(),
                Web3WorkspaceIO.load(projectDir()).deployments());
    }

    @Override
    public void evaluate(SearchRequest request, SearchResponse response) {
        String text = request.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        for (Web3SearchIndex.Hit hit : currentIndex().matches(text)) {
            if (!response.addResult(() -> open(hit), hit.label())) {
                return;
            }
        }
    }

    private static void open(Web3SearchIndex.Hit hit) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            org.openide.windows.TopComponent tc = org.openide.windows.WindowManager
                    .getDefault().findTopComponent("Web3StudioTopComponent");
            if (tc == null) {
                return;
            }
            tc.open();
            tc.requestActive();
            if (tc instanceof Web3StudioTopComponent studio) {
                if (hit.kind() == Web3SearchIndex.Kind.DEPLOYMENT
                        && hit.deployment() != null) {
                    studio.selectDeployment(hit.deployment());
                } else {
                    studio.selectContract(hit.contractName());
                }
            }
        });
    }

    /**
     * The aimed project's directory, resolved the same way Contract
     * Studio does: the rack's target if it's up, else the user's home.
     * Guarded so tests and a stripped platform (no rack) fall back
     * cleanly.
     */
    private static File projectDir() {
        try {
            File dir = org.nmox.studio.rack.service.RackService.getDefault()
                    .getRack().getProjectDir();
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // rack unavailable (tests, stripped platform)
        }
        return new File(System.getProperty("user.home"));
    }
}
