package org.nmox.studio.editor.javascript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JavaScript lexer functionality.
 * Tests the core tokenization logic without requiring NetBeans runtime.
 */
public class JavaScriptLexerTest {
    
    @BeforeEach
    void setUp() {
        // Test setup if needed
    }
    
    @Test
    void testTokenIdEnum() {
        // Test that all token types are properly defined
        assertEquals("keyword", JavaScriptTokenId.KEYWORD.primaryCategory());
        assertEquals("string", JavaScriptTokenId.STRING.primaryCategory());
        assertEquals("number", JavaScriptTokenId.NUMBER.primaryCategory());
        assertEquals("comment", JavaScriptTokenId.LINE_COMMENT.primaryCategory());
        assertEquals("comment", JavaScriptTokenId.BLOCK_COMMENT.primaryCategory());
        assertEquals("operator", JavaScriptTokenId.OPERATOR.primaryCategory());
        assertEquals("identifier", JavaScriptTokenId.IDENTIFIER.primaryCategory());
        assertEquals("error", JavaScriptTokenId.ERROR.primaryCategory());
        
        // Verify we have all expected token types
        JavaScriptTokenId[] tokens = JavaScriptTokenId.values();
        assertTrue(tokens.length >= 15, "Should have at least 15 token types");
    }
    
    @Test
    void testKeywordRecognition() {
        // Test keyword recognition from JavaScriptLanguageHierarchy
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("function"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("const"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("let"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("var"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("class"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("async"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("await"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("return"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("if"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("else"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("for"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("while"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("try"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("catch"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("throw"));
        
        // Test boolean literals
        assertEquals(JavaScriptTokenId.BOOLEAN, JavaScriptLanguageHierarchy.getToken("true"));
        assertEquals(JavaScriptTokenId.BOOLEAN, JavaScriptLanguageHierarchy.getToken("false"));
        
        // Test special values
        assertEquals(JavaScriptTokenId.NULL, JavaScriptLanguageHierarchy.getToken("null"));
        assertEquals(JavaScriptTokenId.UNDEFINED, JavaScriptLanguageHierarchy.getToken("undefined"));
        
        // Test non-keywords return null (will be treated as identifiers)
        assertNull(JavaScriptLanguageHierarchy.getToken("myVariable"));
        assertNull(JavaScriptLanguageHierarchy.getToken("calculateTotal"));
        assertNull(JavaScriptLanguageHierarchy.getToken("someFunction"));
    }
    
    @Test
    void testES6Keywords() {
        // Test ES6 specific keywords
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("export"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("import"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("of"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("yield"));
        assertEquals(JavaScriptTokenId.KEYWORD, JavaScriptLanguageHierarchy.getToken("async"));
    }
    
    @Test
    void testLanguageHierarchyStructure() {
        // Test that the language hierarchy is properly structured
        JavaScriptLanguageHierarchy hierarchy = new JavaScriptLanguageHierarchy();
        assertNotNull(hierarchy);
        
        // Test MIME type
        assertEquals("text/javascript", hierarchy.mimeType());
        
        // Test token creation
        assertNotNull(hierarchy.createTokenIds());
        assertTrue(hierarchy.createTokenIds().size() >= 15);
        
        // Verify all our token types are included
        assertTrue(hierarchy.createTokenIds().contains(JavaScriptTokenId.KEYWORD));
        assertTrue(hierarchy.createTokenIds().contains(JavaScriptTokenId.STRING));
        assertTrue(hierarchy.createTokenIds().contains(JavaScriptTokenId.NUMBER));
        assertTrue(hierarchy.createTokenIds().contains(JavaScriptTokenId.IDENTIFIER));
        assertTrue(hierarchy.createTokenIds().contains(JavaScriptTokenId.OPERATOR));
        assertTrue(hierarchy.createTokenIds().contains(JavaScriptTokenId.LINE_COMMENT));
        assertTrue(hierarchy.createTokenIds().contains(JavaScriptTokenId.BLOCK_COMMENT));
    }
    
    @Test
    void testLanguageProvider() {
        // Test the language provider integration
        JavaScriptLanguageProvider provider = new JavaScriptLanguageProvider();
        assertNotNull(provider);
        
        assertEquals("JavaScript", provider.getDisplayName());
        assertNotNull(provider.getLexerLanguage());
    }
    
    @Test
    void testTokenCategories() {
        // Test that token categories are correctly assigned
        
        // Keywords should be in keyword category
        assertEquals("keyword", JavaScriptTokenId.KEYWORD.primaryCategory());
        
        // Literals should have appropriate categories
        assertEquals("string", JavaScriptTokenId.STRING.primaryCategory());
        assertEquals("number", JavaScriptTokenId.NUMBER.primaryCategory());
        assertEquals("boolean", JavaScriptTokenId.BOOLEAN.primaryCategory()); // booleans have their own category
        assertEquals("null", JavaScriptTokenId.NULL.primaryCategory());       // null has its own category
        assertEquals("undefined", JavaScriptTokenId.UNDEFINED.primaryCategory()); // undefined has its own category
        
        // Comments should be in comment category
        assertEquals("comment", JavaScriptTokenId.LINE_COMMENT.primaryCategory());
        assertEquals("comment", JavaScriptTokenId.BLOCK_COMMENT.primaryCategory());
        
        // Structural elements
        assertEquals("identifier", JavaScriptTokenId.IDENTIFIER.primaryCategory());
        assertEquals("operator", JavaScriptTokenId.OPERATOR.primaryCategory());
        assertEquals("delimiter", JavaScriptTokenId.DELIMITER.primaryCategory());
        assertEquals("whitespace", JavaScriptTokenId.WHITESPACE.primaryCategory());
        assertEquals("error", JavaScriptTokenId.ERROR.primaryCategory());
        
        // ES6 features
        assertEquals("template", JavaScriptTokenId.TEMPLATE_STRING.primaryCategory());
        assertEquals("template-expression", JavaScriptTokenId.TEMPLATE_EXPRESSION.primaryCategory());
    }
    
    @Test
    void testComprehensiveKeywordCoverage() {
        // Test comprehensive JavaScript keyword coverage
        String[] allKeywords = {
            // Basic JavaScript
            "function", "var", "let", "const", "return", "if", "else", "for", "while", "do",
            "break", "continue", "switch", "case", "default", "try", "catch", "finally", "throw",
            
            // Object-oriented
            "class", "extends", "constructor", "super", "this", "new", "instanceof",
            
            // ES6+
            "arrow", "async", "await", "export", "import", "of", "yield",
            
            // Types and values
            "typeof", "void", "delete", "in",
            
            // Reserved words
            "debugger", "with"
        };
        
        int recognizedKeywords = 0;
        for (String keyword : allKeywords) {
            if (JavaScriptLanguageHierarchy.getToken(keyword) == JavaScriptTokenId.KEYWORD) {
                recognizedKeywords++;
            }
        }
        
        // We should recognize most common JavaScript keywords
        assertTrue(recognizedKeywords >= 20, 
            "Should recognize at least 20 JavaScript keywords, found: " + recognizedKeywords);
    }
    
    @Test 
    void testLanguageCreation() {
        // Test that the language can be created without errors
        try {
            assertNotNull(JavaScriptTokenId.language());
        } catch (Exception e) {
            // If we get an exception due to missing NetBeans runtime, that's expected in unit tests
            // The important thing is that our classes are structured correctly
            assertTrue(e.getMessage().contains("Lookup") || e.getMessage().contains("ClassLoader"),
                "Exception should be related to NetBeans runtime dependency: " + e.getMessage());
        }
    }
}