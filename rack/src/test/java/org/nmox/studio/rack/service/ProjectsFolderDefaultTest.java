package org.nmox.studio.rack.service;

import java.io.File;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectsFolderDefaultTest {

    @Test
    @DisplayName("the platform's dialogs default to ~/NMOX, not a ~/NetBeansProjects the product never uses")
    void defaultsToTheWorkspace() {
        assertThat(ProjectsFolderDefault.choose(null, "/home/ada"))
                .isEqualTo(new File("/home/ada", "NMOX").getAbsolutePath());
        assertThat(ProjectsFolderDefault.choose("  ", "/home/ada"))
                .isEqualTo(new File("/home/ada", "NMOX").getAbsolutePath());
    }

    @Test
    @DisplayName("a -Dnetbeans.projects.dir on the command line is left alone")
    void commandLineWins() {
        assertThat(ProjectsFolderDefault.choose("/work/projects", "/home/ada")).isNull();
    }

    @Test
    @DisplayName("it runs at startup")
    void registered() throws Exception {
        String layer = new String(ProjectsFolderDefault.class.getClassLoader()
                .getResourceAsStream("META-INF/namedservices/Modules/Start/java.lang.Runnable")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(layer).contains(ProjectsFolderDefault.class.getName());
    }
}
