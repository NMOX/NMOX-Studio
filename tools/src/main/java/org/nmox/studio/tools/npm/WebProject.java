package org.nmox.studio.tools.npm;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.json.JSONObject;
import org.netbeans.api.annotations.common.StaticResource;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.spi.project.ProjectState;
import org.netbeans.spi.project.ui.LogicalViewProvider;
import org.netbeans.spi.project.ui.support.CommonProjectActions;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import java.awt.Image;
import javax.swing.Action;

public class WebProject implements Project {

    private final FileObject projectDir;
    private final ProjectState state;
    private final Lookup lookup;

    @StaticResource()
    public static final String WEB_PROJECT_ICON = "org/nmox/studio/tools/npm/web-project.png";

    public WebProject(FileObject projectDir, ProjectState state) {
        this.projectDir = projectDir;
        this.state = state;
        this.lookup = Lookups.fixed(new Object[]{
            new Info(),
            new WebProjectLogicalView(this)
        });
    }

    @Override
    public FileObject getProjectDirectory() {
        return projectDir;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    public String getName() {
        return projectDir.getName();
    }

    private final class Info implements ProjectInformation {

        @Override
        public String getName() {
            return getProjectDirectory().getName();
        }

        @Override
        public String getDisplayName() {
            try {
                FileObject packageJson = projectDir.getFileObject("package.json");
                if (packageJson != null) {
                    String content = new String(Files.readAllBytes(FileUtil.toFile(packageJson).toPath()));
                    JSONObject json = new JSONObject(content);
                    if (json.has("name")) {
                        return json.getString("name");
                    }
                }
            } catch (IOException e) {
                // Fall back to directory name
            }
            return getName();
        }

        @Override
        public Icon getIcon() {
            return ImageUtilities.loadImageIcon(WEB_PROJECT_ICON, false);
        }

        @Override
        public Project getProject() {
            return WebProject.this;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener listener) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
        }
    }

    private static class WebProjectLogicalView implements LogicalViewProvider {

        private final WebProject project;

        public WebProjectLogicalView(WebProject project) {
            this.project = project;
        }

        @Override
        public Node createLogicalView() {
            FileObject projectDirectory = project.getProjectDirectory();
            DataFolder projectFolder = DataFolder.findFolder(projectDirectory);
            if (projectFolder != null) {
                Node nodeOfProjectFolder = projectFolder.getNodeDelegate();
                return new ProjectNode(nodeOfProjectFolder, project);
            } else {
                return new AbstractNode(Children.LEAF);
            }
        }

        @Override
        public Node findPath(Node root, Object target) {
            // Not implemented for simplicity
            return null;
        }

        private static class ProjectNode extends FilterNode {

            final WebProject project;

            public ProjectNode(Node node, WebProject project) {
                super(node, new FilterNode.Children(node),
                        new ProxyLookup(new Lookup[]{
                    Lookups.singleton(project),
                    node.getLookup()
                }));
                this.project = project;
            }

            @Override
            public Action[] getActions(boolean arg0) {
                return new Action[]{
                    CommonProjectActions.newFileAction(),
                    CommonProjectActions.copyProjectAction(),
                    CommonProjectActions.deleteProjectAction(),
                    CommonProjectActions.closeProjectAction()
                };
            }

            @Override
            public Image getIcon(int type) {
                return ImageUtilities.loadImage(WEB_PROJECT_ICON);
            }

            @Override
            public Image getOpenedIcon(int type) {
                return getIcon(type);
            }

            @Override
            public String getDisplayName() {
                return project.getName();
            }
        }
    }
}