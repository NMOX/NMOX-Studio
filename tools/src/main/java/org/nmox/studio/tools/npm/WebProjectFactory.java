package org.nmox.studio.tools.npm;

import java.io.File;
import java.io.IOException;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.ProjectFactory;
import org.netbeans.spi.project.ProjectState;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = ProjectFactory.class)
public class WebProjectFactory implements ProjectFactory {

    public static final String PACKAGE_JSON = "package.json";

    @Override
    public boolean isProject(FileObject projectDirectory) {
        return projectDirectory.getFileObject(PACKAGE_JSON) != null;
    }

    @Override
    public Project loadProject(FileObject projectDirectory, ProjectState state) throws IOException {
        if (isProject(projectDirectory)) {
            return new WebProject(projectDirectory, state);
        }
        return null;
    }

    @Override
    public void saveProject(Project project) throws IOException, ClassCastException {
        // Projects are saved automatically
    }
}