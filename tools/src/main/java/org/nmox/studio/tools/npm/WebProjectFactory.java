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

    /** Every manifest the rack understands makes a real platform project. */
    private static final String[] MANIFESTS = {
        "package.json", "Cargo.toml", "go.mod", "mix.exs", "rebar.config",
        "deps.edn", "project.clj", "Package.swift", "pom.xml", "build.gradle",
        "build.gradle.kts", "pyproject.toml", "requirements.txt", "Gemfile",
        "composer.json", "angular.json", "bun.lock", "bunfig.toml", "deno.json", "deno.jsonc",
        "foundry.toml"};

    @Override
    public boolean isProject(FileObject projectDirectory) {
        for (String manifest : MANIFESTS) {
            if (projectDirectory.getFileObject(manifest) != null) {
                return true;
            }
        }
        return false;
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