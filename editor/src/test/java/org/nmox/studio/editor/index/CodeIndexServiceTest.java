package org.nmox.studio.editor.index;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CodeIndexServiceTest {
    
    private CodeIndexService indexService;
    private Path tempDir;
    
    @BeforeEach
    public void setUp() throws IOException {
        indexService = CodeIndexService.getInstance();
        tempDir = Files.createTempDirectory("codeindex-test");
        // Clear any previous state
        Files.walk(tempDir)
            .filter(Files::isRegularFile)
            .forEach(indexService::clearFileIndex);
    }
    
    @AfterEach
    public void tearDown() throws IOException {
        Files.walk(tempDir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // Ignore
                }
            });
    }
    
    @Test
    public void testIndexJavaFile() throws IOException {
        Path javaFile = tempDir.resolve("Test.java");
        String javaContent = """
            public class Test {
                private String field;
                
                public void method() {
                    System.out.println("Hello");
                }
                
                private static void staticMethod() {
                    // Static method
                }
            }
            """;
        Files.writeString(javaFile, javaContent);
        
        CodeIndexService.FileIndex index = indexService.indexFile(javaFile);
        
        assertNotNull(index);
        assertFalse(index.symbols.isEmpty());
        
        // Check for class symbol
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("Test") && s.type == CodeIndexService.SymbolType.CLASS));
        
        // Check for method symbols
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("method") && s.type == CodeIndexService.SymbolType.METHOD));
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("staticMethod") && s.type == CodeIndexService.SymbolType.METHOD));
        
        // Check for field symbol
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("field") && s.type == CodeIndexService.SymbolType.FIELD));
    }
    
    @Test
    public void testIndexJavaScriptFile() throws IOException {
        Path jsFile = tempDir.resolve("test.js");
        String jsContent = """
            class MyClass {
                constructor() {
                    this.value = 0;
                }
            }
            
            function myFunction() {
                return 42;
            }
            
            const arrowFunc = () => {
                console.log("Arrow function");
            };
            
            let variable = "test";
            const constant = 100;
            """;
        Files.writeString(jsFile, jsContent);
        
        CodeIndexService.FileIndex index = indexService.indexFile(jsFile);
        
        assertNotNull(index);
        assertFalse(index.symbols.isEmpty());
        
        // Check for class
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("MyClass") && s.type == CodeIndexService.SymbolType.CLASS));
        
        // Check for functions
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("myFunction") && s.type == CodeIndexService.SymbolType.FUNCTION));
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("arrowFunc") && s.type == CodeIndexService.SymbolType.FUNCTION));
        
        // Check for variables
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("variable") && s.type == CodeIndexService.SymbolType.VARIABLE));
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("constant") && s.type == CodeIndexService.SymbolType.VARIABLE));
    }
    
    @Test
    public void testIndexPythonFile() throws IOException {
        Path pyFile = tempDir.resolve("test.py");
        String pyContent = """
            class MyClass:
                def __init__(self):
                    self.value = 0
                
                def method(self):
                    return self.value
            
            def function():
                print("Hello")
            
            def another_function(param):
                return param * 2
            """;
        Files.writeString(pyFile, pyContent);
        
        CodeIndexService.FileIndex index = indexService.indexFile(pyFile);
        
        assertNotNull(index);
        assertFalse(index.symbols.isEmpty());
        
        // Check for class
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("MyClass") && s.type == CodeIndexService.SymbolType.CLASS));
        
        // Check for functions
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("__init__") && s.type == CodeIndexService.SymbolType.FUNCTION));
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("method") && s.type == CodeIndexService.SymbolType.FUNCTION));
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("function") && s.type == CodeIndexService.SymbolType.FUNCTION));
        assertTrue(index.symbols.stream()
            .anyMatch(s -> s.name.equals("another_function") && s.type == CodeIndexService.SymbolType.FUNCTION));
    }
    
    @Test
    public void testFindDefinition() throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "public class TestClass {}");
        
        indexService.indexFile(file);
        
        Optional<CodeIndexService.Symbol> symbol = indexService.findDefinition("TestClass");
        assertTrue(symbol.isPresent());
        assertEquals("TestClass", symbol.get().name);
        assertEquals(CodeIndexService.SymbolType.CLASS, symbol.get().type);
    }
    
    @Test
    public void testSearchSymbols() throws IOException {
        Path file1 = tempDir.resolve("Test1.java");
        Path file2 = tempDir.resolve("Test2.java");
        
        Files.writeString(file1, "public class TestClass1 { public void testMethod() {} }");
        Files.writeString(file2, "public class TestClass2 { public void anotherMethod() {} }");
        
        indexService.indexFile(file1);
        indexService.indexFile(file2);
        
        List<CodeIndexService.Symbol> results = indexService.searchSymbols("Test", 10);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(s -> s.name.equals("TestClass1")));
        assertTrue(results.stream().anyMatch(s -> s.name.equals("TestClass2")));
        assertTrue(results.stream().anyMatch(s -> s.name.equals("testMethod")));
    }
    
    @Test
    public void testGetFileSymbols() throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, """
            public class Test {
                public void method1() {}
                public void method2() {}
            }
            """);
        
        indexService.indexFile(file);
        
        List<CodeIndexService.Symbol> symbols = indexService.getFileSymbols(file);
        assertEquals(3, symbols.size()); // class + 2 methods
    }
    
    @Test
    public void testClearFileIndex() throws IOException {
        Path file = tempDir.resolve("TestClear.java");
        Files.writeString(file, "public class TestClear {}");
        
        indexService.indexFile(file);
        assertTrue(indexService.findDefinition("TestClear").isPresent());
        
        indexService.clearFileIndex(file);
        assertFalse(indexService.findDefinition("TestClear").isPresent());
    }
    
    @Test
    public void testIndexProject() throws Exception {
        // Create a small project structure
        Path srcDir = tempDir.resolve("src");
        Files.createDirectory(srcDir);
        
        Files.writeString(srcDir.resolve("Main.java"), "public class Main {}");
        Files.writeString(srcDir.resolve("Helper.java"), "public class Helper {}");
        Files.writeString(srcDir.resolve("script.js"), "function init() {}");
        
        CompletableFuture<CodeIndexService.IndexResult> future = indexService.indexProject(tempDir);
        CodeIndexService.IndexResult result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertEquals(3, result.filesIndexed);
        assertTrue(result.symbolsFound >= 3); // At least one symbol per file
        assertTrue(result.timeMillis >= 0);
    }
    
    @Test
    public void testIndexStats() throws IOException {
        CodeIndexService.IndexStats initialStats = indexService.getStats();
        
        Path file = tempDir.resolve("TestStats.java");
        Files.writeString(file, "public class TestStats { public void methodStats() {} }");
        indexService.indexFile(file);
        
        CodeIndexService.IndexStats stats = indexService.getStats();
        assertTrue(stats.indexedFiles > initialStats.indexedFiles);
        assertTrue(stats.uniqueSymbols >= initialStats.uniqueSymbols);
    }
}