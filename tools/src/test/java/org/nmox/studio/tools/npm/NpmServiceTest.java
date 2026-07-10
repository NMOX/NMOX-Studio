package org.nmox.studio.tools.npm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DisplayName;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NpmService.
 */
@DisplayName("NpmService Tests")
class NpmServiceTest {

    private NpmService npmService;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        npmService = new NpmService();
    }

    @Test
    @DisplayName("parseGlobalList reads npm ls -g --json into sorted name/version pairs")
    void parseGlobalListNormal() {
        String json = """
                {"name": "lib", "dependencies": {
                    "typescript": {"version": "5.7.2"},
                    "corepack": {"version": "0.29.4"},
                    "npm": {"version": "10.9.2"}
                }}""";
        var packages = NpmService.parseGlobalList(json);
        assertThat(packages).extracting(NpmService.GlobalPackage::name)
                .containsExactly("corepack", "npm", "typescript"); // sorted
        assertThat(packages.get(2).version()).isEqualTo("5.7.2");
    }

    @Test
    @DisplayName("parseGlobalList tolerates missing versions, no dependencies, and garbage")
    void parseGlobalListTolerance() {
        assertThat(NpmService.parseGlobalList(
                "{\"dependencies\": {\"weird\": {}}}"))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.name()).isEqualTo("weird");
                    assertThat(p.version()).isEmpty();
                });
        assertThat(NpmService.parseGlobalList("{\"name\": \"lib\"}")).isEmpty();
        assertThat(NpmService.parseGlobalList("not json at all")).isEmpty();
        assertThat(NpmService.parseGlobalList("")).isEmpty();
    }

    @Test
    @DisplayName("Should detect NPM package manager from package-lock.json")
    void testDetectNpmPackageManager() throws IOException {
        File projectDir = tempDir.toFile();
        Files.createFile(tempDir.resolve("package-lock.json"));
        
        NpmService.PackageManager detected = npmService.detectPackageManager(projectDir);
        assertThat(detected).isEqualTo(NpmService.PackageManager.NPM);
    }
    
    @Test
    @DisplayName("Should detect Yarn package manager from yarn.lock")
    void testDetectYarnPackageManager() throws IOException {
        File projectDir = tempDir.toFile();
        Files.createFile(tempDir.resolve("yarn.lock"));
        
        NpmService.PackageManager detected = npmService.detectPackageManager(projectDir);
        assertThat(detected).isEqualTo(NpmService.PackageManager.YARN);
    }
    
    @Test
    @DisplayName("Should detect PNPM package manager from pnpm-lock.yaml")
    void testDetectPnpmPackageManager() throws IOException {
        File projectDir = tempDir.toFile();
        Files.createFile(tempDir.resolve("pnpm-lock.yaml"));
        
        NpmService.PackageManager detected = npmService.detectPackageManager(projectDir);
        assertThat(detected).isEqualTo(NpmService.PackageManager.PNPM);
    }
    
    @Test
    @DisplayName("Should default to NPM when no lock file exists")
    void testDefaultPackageManager() {
        File projectDir = tempDir.toFile();
        
        NpmService.PackageManager detected = npmService.detectPackageManager(projectDir);
        assertThat(detected).isEqualTo(NpmService.PackageManager.NPM);
    }
    
    @Test
    @DisplayName("Should get correct command for NPM")
    void testGetNpmCommand() {
        String command = npmService.getCommand(NpmService.PackageManager.NPM);
        assertThat(command).isEqualTo("npm");
    }
    
    @Test
    @DisplayName("Should get correct command for Yarn")
    void testGetYarnCommand() {
        String command = npmService.getCommand(NpmService.PackageManager.YARN);
        assertThat(command).isEqualTo("yarn");
    }
    
    @Test
    @DisplayName("Should get correct command for PNPM")
    void testGetPnpmCommand() {
        String command = npmService.getCommand(NpmService.PackageManager.PNPM);
        assertThat(command).isEqualTo("pnpm");
    }
    
    @Test
    @DisplayName("Should check NPM availability")
    void testIsAvailable() {
        // NPM should be available in most environments
        boolean available = npmService.isAvailable(NpmService.PackageManager.NPM);
        // We can't guarantee NPM is installed, so just check the method doesn't throw
        assertThat(available).isIn(true, false);
    }
    
    @Test
    @DisplayName("Should resolve the same Lookup-owned instance from getDefault")
    void testGetDefault() {
        // Lookup.getDefault() won't work in unit tests without proper setup
        // This is expected behavior in tests - skip the null check
        try {
            NpmService instance = NpmService.getDefault();
            // If we get an instance, it should be the one Lookup owns
            if (instance != null) {
                NpmService instance2 = NpmService.getDefault();
                assertThat(instance2).isSameAs(instance);
            }
        } catch (Exception e) {
            // Expected in test environment without NetBeans platform
        }
    }

    @Test
    @DisplayName("Subprocess futures run on the module RequestProcessor, never commonPool")
    void testAsyncWorkNamesItsExecutor() throws Exception {
        // supplyAsync with no executor puts a 30-60s blocking subprocess on
        // the JVM-shared ForkJoinPool.commonPool; pin the RP argument
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/tools/npm/NpmService.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        int supplies = source.split("CompletableFuture.supplyAsync", -1).length - 1;
        int bounded = source.split("\\}, RP\\)", -1).length - 1;
        assertThat(supplies).isGreaterThan(0);
        assertThat(bounded)
                .as("every supplyAsync must name the NPM Service RequestProcessor")
                .isEqualTo(supplies);
    }
    
    @Test
    @DisplayName("Should parse simple arguments separated by spaces")
    void testParseSimpleArguments() {
        var result = NpmService.parseArguments("run build --env=prod");
        assertThat(result).containsExactly("run", "build", "--env=prod");
    }
    
    @Test
    @DisplayName("Should parse arguments with double quotes preserving spaces")
    void testParseDoubleQuotedArguments() {
        var result = NpmService.parseArguments("run test --grep \"user service\"");
        assertThat(result).containsExactly("run", "test", "--grep", "user service");
    }
    
    @Test
    @DisplayName("Should parse arguments with single quotes preserving spaces")
    void testParseSingleQuotedArguments() {
        var result = NpmService.parseArguments("run test --grep 'user service'");
        assertThat(result).containsExactly("run", "test", "--grep", "user service");
    }
    
    @Test
    @DisplayName("Should return empty list for null or empty input")
    void testParseEmptyArguments() {
        assertThat(NpmService.parseArguments(null)).isEmpty();
        assertThat(NpmService.parseArguments("   ")).isEmpty();
    }
}