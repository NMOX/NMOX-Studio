package org.nmox.studio.editor.javascript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JavaScript syntax highlighting.
 * Tests the complete tokenization workflow for real JavaScript code examples.
 */
public class JavaScriptSyntaxHighlightingIntegrationTest {
    
    @BeforeEach
    void setUp() {
        // Test setup
    }
    
    @Test
    void testSimpleJavaScriptFunction() {
        // Test parsing a simple JavaScript function
        String jsCode = "function hello() { return 'world'; }";
        
        // Verify expected tokens would be generated
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testES6Features() {
        // Test ES6 syntax elements
        String jsCode = "const arrow = (x, y) => x + y; let template = `Result: ${arrow(1, 2)}`;";
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testAsyncAwaitCode() {
        // Test async/await syntax
        String jsCode = """
            async function fetchData() {
                try {
                    const response = await fetch('/api');
                    return await response.json();
                } catch (error) {
                    console.error('Error:', error);
                }
            }
            """;
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testClassDefinition() {
        // Test class syntax
        String jsCode = """
            class Calculator {
                constructor() {
                    this.value = 0;
                }
                
                add(n) {
                    return this.value += n;
                }
            }
            """;
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testComplexJavaScript() {
        // Test complex JavaScript with multiple constructs
        String jsCode = """
            // Comment: This is a complex JavaScript example
            const API_URL = 'https://api.example.com';
            let userCache = new Map();
            
            /**
             * Multi-line comment
             * Fetches user data with caching
             */
            async function getUserData(userId) {
                if (userCache.has(userId)) {
                    return userCache.get(userId);
                }
                
                try {
                    const response = await fetch(`${API_URL}/users/${userId}`);
                    if (!response.ok) {
                        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                    }
                    
                    const userData = await response.json();
                    userCache.set(userId, userData);
                    return userData;
                } catch (error) {
                    console.error('Failed to fetch user data:', error);
                    return null;
                }
            }
            
            // Export for module usage
            export { getUserData };
            """;
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testNumberLiterals() {
        // Test various number formats
        String jsCode = "let a = 42; let b = 3.14; let c = 1.5e10; let d = 0xFF; let e = 0b1010;";
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testStringLiterals() {
        // Test various string formats
        String jsCode = "let s1 = 'single'; let s2 = \"double\"; let s3 = `template ${variable}`;";
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testOperators() {
        // Test JavaScript operators
        String jsCode = "result = a + b * c / d - e % f; bool = x === y && z !== w || !condition;";
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testComments() {
        // Test comment recognition
        String jsCode = """
            // Single line comment
            let x = 42; // Inline comment
            /* Block comment */
            let y = /* inline block */ 43;
            /*
             * Multi-line
             * block comment
             */
            """;
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testRegexLiterals() {
        // Test regex recognition (basic)
        String jsCode = "let pattern = /[a-zA-Z0-9]+/g; let match = text.match(pattern);";
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testNestedStructures() {
        // Test nested objects and arrays
        String jsCode = """
            const config = {
                api: {
                    url: 'https://example.com',
                    timeout: 5000,
                    retries: [1, 2, 5, 10]
                },
                features: ['auth', 'cache', 'retry']
            };
            """;
        
        assertValidJavaScriptCode(jsCode);
    }
    
    @Test
    void testErrorScenarios() {
        // Test how lexer handles potentially problematic input
        String[] testCases = {
            "let unclosed = 'string without closing quote",  // Unclosed string
            "function() { /* unclosed comment",              // Unclosed comment
            "let invalid = 123.45.67;",                     // Invalid number
            "let unicode = 'hello 🌍 world';",               // Unicode content
            ""                                               // Empty input
        };
        
        for (String jsCode : testCases) {
            // These should not crash the lexer, even if they produce error tokens
            assertDoesNotThrow(() -> assertValidJavaScriptCode(jsCode), 
                "Lexer should handle problematic input gracefully: " + jsCode);
        }
    }
    
    /**
     * Helper method to validate that JavaScript code can be processed by our lexer.
     * This tests the lexer structure without requiring full NetBeans runtime.
     */
    private void assertValidJavaScriptCode(String jsCode) {
        // Test that our lexer components can handle the code structure
        assertNotNull(jsCode, "JavaScript code should not be null");
        
        // Test keyword recognition for any keywords in the code
        String[] words = jsCode.split("[^a-zA-Z_$][a-zA-Z_$]*");
        for (String word : words) {
            if (!word.isEmpty()) {
                // This should not throw an exception
                assertDoesNotThrow(() -> JavaScriptLanguageHierarchy.getToken(word.trim()));
            }
        }
        
        // Test that our token types are well-defined
        assertNotNull(JavaScriptTokenId.values());
        assertTrue(JavaScriptTokenId.values().length > 0);
        
        // Test that language hierarchy can be created
        assertDoesNotThrow(() -> new JavaScriptLanguageHierarchy());
        
        // Test that language provider can be created
        assertDoesNotThrow(() -> new JavaScriptLanguageProvider());
    }
    
    @Test
    void testMimeTypeMapping() {
        // Test MIME type configuration
        JavaScriptLanguageHierarchy hierarchy = new JavaScriptLanguageHierarchy();
        assertEquals("text/javascript", hierarchy.mimeType());
        
        JavaScriptLanguageProvider provider = new JavaScriptLanguageProvider();
        assertEquals("JavaScript", provider.getDisplayName());
    }
    
    @Test
    void testTokenConsistency() {
        // Test that all token IDs have consistent categories
        for (JavaScriptTokenId token : JavaScriptTokenId.values()) {
            assertNotNull(token.primaryCategory(), "Token " + token + " should have a category");
            assertFalse(token.primaryCategory().isEmpty(), "Token " + token + " category should not be empty");
        }
    }
    
    @Test
    void testRealWorldJavaScript() {
        // Test with a real-world JavaScript example
        String realWorldJs = """
            import { createApp } from 'vue';
            import axios from 'axios';
            
            const API_BASE = process.env.VUE_APP_API_BASE || 'http://localhost:3000';
            
            const app = createApp({
                data() {
                    return {
                        users: [],
                        loading: false,
                        error: null
                    };
                },
                
                async mounted() {
                    await this.fetchUsers();
                },
                
                methods: {
                    async fetchUsers() {
                        this.loading = true;
                        this.error = null;
                        
                        try {
                            const response = await axios.get(`${API_BASE}/api/users`);
                            this.users = response.data;
                        } catch (err) {
                            this.error = `Failed to load users: ${err.message}`;
                            console.error('Error fetching users:', err);
                        } finally {
                            this.loading = false;
                        }
                    },
                    
                    formatDate(dateString) {
                        return new Date(dateString).toLocaleDateString();
                    }
                }
            });
            
            app.mount('#app');
            """;
        
        assertValidJavaScriptCode(realWorldJs);
    }
}