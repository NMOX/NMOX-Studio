package org.nmox.studio.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebFileSupport.
 */
@DisplayName("WebFileSupport Tests")
class WebFileSupportTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    @DisplayName("Should recognize HTML MIME type")
    void testHtmlMimeType() {
        String mimeType = WebFileSupport.HTML_MIME_TYPE;
        assertThat(mimeType).isEqualTo("text/html");
    }
    
    @Test
    @DisplayName("Should recognize CSS MIME type")
    void testCssMimeType() {
        String mimeType = WebFileSupport.CSS_MIME_TYPE;
        assertThat(mimeType).isEqualTo("text/css");
    }
    
    @Test
    @DisplayName("Should recognize JavaScript MIME type")
    void testJavaScriptMimeType() {
        String mimeType = WebFileSupport.JAVASCRIPT_MIME_TYPE;
        assertThat(mimeType).isEqualTo("text/javascript");
    }
    
    @Test
    @DisplayName("Should recognize TypeScript MIME type")
    void testTypeScriptMimeType() {
        String mimeType = WebFileSupport.TYPESCRIPT_MIME_TYPE;
        assertThat(mimeType).isEqualTo("text/typescript");
    }
    
    @Test
    @DisplayName("Should recognize JSON MIME type")
    void testJsonMimeType() {
        String mimeType = WebFileSupport.JSON_MIME_TYPE;
        assertThat(mimeType).isEqualTo("application/json");
    }
    
    @Test
    @DisplayName("Should recognize HTML file extension")
    void testHtmlExtension() throws IOException {
        File htmlFile = tempDir.resolve("test.html").toFile();
        Files.createFile(htmlFile.toPath());
        FileObject fo = FileUtil.toFileObject(htmlFile);
        
        assertThat(fo).isNotNull();
        assertThat(fo.getExt()).isEqualTo("html");
    }
    
    @Test
    @DisplayName("Should recognize CSS file extension")
    void testCssExtension() throws IOException {
        File cssFile = tempDir.resolve("styles.css").toFile();
        Files.createFile(cssFile.toPath());
        FileObject fo = FileUtil.toFileObject(cssFile);
        
        assertThat(fo).isNotNull();
        assertThat(fo.getExt()).isEqualTo("css");
    }
    
    @Test
    @DisplayName("Should recognize JavaScript file extensions")
    void testJavaScriptExtensions() throws IOException {
        File jsFile = tempDir.resolve("script.js").toFile();
        Files.createFile(jsFile.toPath());
        FileObject fo = FileUtil.toFileObject(jsFile);
        
        assertThat(fo).isNotNull();
        assertThat(fo.getExt()).isEqualTo("js");
        
        File mjsFile = tempDir.resolve("module.mjs").toFile();
        Files.createFile(mjsFile.toPath());
        FileObject mfo = FileUtil.toFileObject(mjsFile);
        
        assertThat(mfo).isNotNull();
        assertThat(mfo.getExt()).isEqualTo("mjs");
    }
    
    @Test
    @DisplayName("Should recognize TypeScript file extensions")
    void testTypeScriptExtensions() throws IOException {
        File tsFile = tempDir.resolve("app.ts").toFile();
        Files.createFile(tsFile.toPath());
        FileObject fo = FileUtil.toFileObject(tsFile);
        
        assertThat(fo).isNotNull();
        assertThat(fo.getExt()).isEqualTo("ts");
        
        File tsxFile = tempDir.resolve("component.tsx").toFile();
        Files.createFile(tsxFile.toPath());
        FileObject txfo = FileUtil.toFileObject(tsxFile);
        
        assertThat(txfo).isNotNull();
        assertThat(txfo.getExt()).isEqualTo("tsx");
    }
    
    @Test
    @DisplayName("Should recognize JSX file extension")
    void testJsxExtension() throws IOException {
        File jsxFile = tempDir.resolve("component.jsx").toFile();
        Files.createFile(jsxFile.toPath());
        FileObject fo = FileUtil.toFileObject(jsxFile);
        
        assertThat(fo).isNotNull();
        assertThat(fo.getExt()).isEqualTo("jsx");
    }
    
    @Test
    @DisplayName("Should recognize JSON file extension")
    void testJsonExtension() throws IOException {
        File jsonFile = tempDir.resolve("data.json").toFile();
        Files.createFile(jsonFile.toPath());
        FileObject fo = FileUtil.toFileObject(jsonFile);
        
        assertThat(fo).isNotNull();
        assertThat(fo.getExt()).isEqualTo("json");
    }
}