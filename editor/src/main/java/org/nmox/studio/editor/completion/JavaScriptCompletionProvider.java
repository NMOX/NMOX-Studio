package org.nmox.studio.editor.completion;

import java.util.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.openide.util.Exceptions;

public class JavaScriptCompletionProvider implements CompletionProvider {
    
    private static final Map<String, List<JavaScriptMethod>> GLOBAL_OBJECTS = new HashMap<>();
    private static final Set<String> KEYWORDS = new HashSet<>();
    private static final List<JavaScriptSnippet> SNIPPETS = new ArrayList<>();
    
    static {
        // JavaScript keywords
        KEYWORDS.addAll(Arrays.asList(
            "async", "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof", "let",
            "new", "null", "of", "return", "static", "super", "switch", "this", "throw",
            "true", "try", "typeof", "undefined", "var", "void", "while", "with", "yield"
        ));
        
        // Global objects and their methods
        initializeGlobalObjects();
        
        // Common code snippets
        initializeSnippets();
    }
    
    private static void initializeGlobalObjects() {
        // Console methods
        GLOBAL_OBJECTS.put("console", Arrays.asList(
            new JavaScriptMethod("log", "(...data: any[]): void", "Outputs a message to the console"),
            new JavaScriptMethod("error", "(...data: any[]): void", "Outputs an error message"),
            new JavaScriptMethod("warn", "(...data: any[]): void", "Outputs a warning message"),
            new JavaScriptMethod("info", "(...data: any[]): void", "Outputs an informational message"),
            new JavaScriptMethod("debug", "(...data: any[]): void", "Outputs a debug message"),
            new JavaScriptMethod("table", "(tabularData: any): void", "Displays tabular data as a table"),
            new JavaScriptMethod("time", "(label?: string): void", "Starts a timer"),
            new JavaScriptMethod("timeEnd", "(label?: string): void", "Stops a timer"),
            new JavaScriptMethod("clear", "(): void", "Clears the console"),
            new JavaScriptMethod("group", "(label?: string): void", "Creates a new inline group"),
            new JavaScriptMethod("groupEnd", "(): void", "Exits the current inline group")
        ));
        
        // Document methods
        GLOBAL_OBJECTS.put("document", Arrays.asList(
            new JavaScriptMethod("getElementById", "(id: string): HTMLElement | null", "Returns element with specified ID"),
            new JavaScriptMethod("getElementsByClassName", "(className: string): HTMLCollection", "Returns elements with specified class"),
            new JavaScriptMethod("getElementsByTagName", "(tagName: string): HTMLCollection", "Returns elements with specified tag"),
            new JavaScriptMethod("querySelector", "(selector: string): Element | null", "Returns first element matching selector"),
            new JavaScriptMethod("querySelectorAll", "(selector: string): NodeList", "Returns all elements matching selector"),
            new JavaScriptMethod("createElement", "(tagName: string): HTMLElement", "Creates a new element"),
            new JavaScriptMethod("createTextNode", "(text: string): Text", "Creates a new text node"),
            new JavaScriptMethod("addEventListener", "(type: string, listener: Function): void", "Adds event listener"),
            new JavaScriptMethod("removeEventListener", "(type: string, listener: Function): void", "Removes event listener"),
            new JavaScriptMethod("write", "(text: string): void", "Writes HTML to document"),
            new JavaScriptMethod("writeln", "(text: string): void", "Writes HTML with newline")
        ));
        
        // Array methods
        GLOBAL_OBJECTS.put("Array", Arrays.asList(
            new JavaScriptMethod("push", "(...items: T[]): number", "Adds elements to end of array"),
            new JavaScriptMethod("pop", "(): T | undefined", "Removes and returns last element"),
            new JavaScriptMethod("shift", "(): T | undefined", "Removes and returns first element"),
            new JavaScriptMethod("unshift", "(...items: T[]): number", "Adds elements to beginning"),
            new JavaScriptMethod("slice", "(start?: number, end?: number): T[]", "Returns shallow copy of portion"),
            new JavaScriptMethod("splice", "(start: number, deleteCount?: number): T[]", "Changes array contents"),
            new JavaScriptMethod("indexOf", "(searchElement: T): number", "Returns first index of element"),
            new JavaScriptMethod("includes", "(searchElement: T): boolean", "Checks if array includes element"),
            new JavaScriptMethod("find", "(predicate: Function): T | undefined", "Returns first matching element"),
            new JavaScriptMethod("filter", "(predicate: Function): T[]", "Returns filtered array"),
            new JavaScriptMethod("map", "(callbackfn: Function): U[]", "Returns mapped array"),
            new JavaScriptMethod("reduce", "(callbackfn: Function, initialValue?: T): T", "Reduces array to single value"),
            new JavaScriptMethod("forEach", "(callbackfn: Function): void", "Executes function for each element"),
            new JavaScriptMethod("sort", "(compareFn?: Function): T[]", "Sorts array in place"),
            new JavaScriptMethod("reverse", "(): T[]", "Reverses array in place"),
            new JavaScriptMethod("join", "(separator?: string): string", "Joins array elements into string")
        ));
        
        // String methods
        GLOBAL_OBJECTS.put("String", Arrays.asList(
            new JavaScriptMethod("charAt", "(index: number): string", "Returns character at index"),
            new JavaScriptMethod("charCodeAt", "(index: number): number", "Returns Unicode of character"),
            new JavaScriptMethod("concat", "(...strings: string[]): string", "Concatenates strings"),
            new JavaScriptMethod("indexOf", "(searchString: string): number", "Returns index of substring"),
            new JavaScriptMethod("lastIndexOf", "(searchString: string): number", "Returns last index of substring"),
            new JavaScriptMethod("slice", "(start: number, end?: number): string", "Extracts part of string"),
            new JavaScriptMethod("substring", "(start: number, end?: number): string", "Returns substring"),
            new JavaScriptMethod("toLowerCase", "(): string", "Returns lowercase string"),
            new JavaScriptMethod("toUpperCase", "(): string", "Returns uppercase string"),
            new JavaScriptMethod("trim", "(): string", "Removes whitespace from ends"),
            new JavaScriptMethod("split", "(separator: string): string[]", "Splits string into array"),
            new JavaScriptMethod("replace", "(searchValue: string, replaceValue: string): string", "Replaces text"),
            new JavaScriptMethod("match", "(regexp: RegExp): RegExpMatchArray | null", "Matches against regex"),
            new JavaScriptMethod("includes", "(searchString: string): boolean", "Checks if string contains substring"),
            new JavaScriptMethod("startsWith", "(searchString: string): boolean", "Checks if string starts with"),
            new JavaScriptMethod("endsWith", "(searchString: string): boolean", "Checks if string ends with"),
            new JavaScriptMethod("repeat", "(count: number): string", "Repeats string"),
            new JavaScriptMethod("padStart", "(targetLength: number, padString?: string): string", "Pads string start"),
            new JavaScriptMethod("padEnd", "(targetLength: number, padString?: string): string", "Pads string end")
        ));
        
        // Object methods
        GLOBAL_OBJECTS.put("Object", Arrays.asList(
            new JavaScriptMethod("keys", "(obj: any): string[]", "Returns array of object's keys"),
            new JavaScriptMethod("values", "(obj: any): any[]", "Returns array of object's values"),
            new JavaScriptMethod("entries", "(obj: any): [string, any][]", "Returns array of key-value pairs"),
            new JavaScriptMethod("assign", "(target: any, ...sources: any[]): any", "Copies properties to target"),
            new JavaScriptMethod("create", "(proto: object | null): any", "Creates new object with prototype"),
            new JavaScriptMethod("freeze", "(obj: T): T", "Freezes an object"),
            new JavaScriptMethod("seal", "(obj: T): T", "Seals an object"),
            new JavaScriptMethod("defineProperty", "(obj: any, prop: string, descriptor: PropertyDescriptor): any", "Defines property"),
            new JavaScriptMethod("getOwnPropertyNames", "(obj: any): string[]", "Returns all property names"),
            new JavaScriptMethod("hasOwnProperty", "(prop: string): boolean", "Checks for property")
        ));
        
        // Math methods
        GLOBAL_OBJECTS.put("Math", Arrays.asList(
            new JavaScriptMethod("abs", "(x: number): number", "Returns absolute value"),
            new JavaScriptMethod("ceil", "(x: number): number", "Rounds up to nearest integer"),
            new JavaScriptMethod("floor", "(x: number): number", "Rounds down to nearest integer"),
            new JavaScriptMethod("round", "(x: number): number", "Rounds to nearest integer"),
            new JavaScriptMethod("max", "(...values: number[]): number", "Returns largest number"),
            new JavaScriptMethod("min", "(...values: number[]): number", "Returns smallest number"),
            new JavaScriptMethod("pow", "(x: number, y: number): number", "Returns x to power of y"),
            new JavaScriptMethod("sqrt", "(x: number): number", "Returns square root"),
            new JavaScriptMethod("random", "(): number", "Returns random number 0-1"),
            new JavaScriptMethod("sin", "(x: number): number", "Returns sine"),
            new JavaScriptMethod("cos", "(x: number): number", "Returns cosine"),
            new JavaScriptMethod("tan", "(x: number): number", "Returns tangent")
        ));
        
        // JSON methods
        GLOBAL_OBJECTS.put("JSON", Arrays.asList(
            new JavaScriptMethod("parse", "(text: string): any", "Parses JSON string"),
            new JavaScriptMethod("stringify", "(value: any, replacer?: Function, space?: string | number): string", "Converts to JSON string")
        ));
    }
    
    private static void initializeSnippets() {
        SNIPPETS.add(new JavaScriptSnippet("func", "function ${1:name}(${2:params}) {\n    ${3}\n}", "Function declaration"));
        SNIPPETS.add(new JavaScriptSnippet("arrow", "(${1:params}) => {\n    ${2}\n}", "Arrow function"));
        SNIPPETS.add(new JavaScriptSnippet("class", "class ${1:Name} {\n    constructor(${2:params}) {\n        ${3}\n    }\n}", "Class declaration"));
        SNIPPETS.add(new JavaScriptSnippet("for", "for (let ${1:i} = 0; ${1:i} < ${2:array}.length; ${1:i}++) {\n    ${3}\n}", "For loop"));
        SNIPPETS.add(new JavaScriptSnippet("forin", "for (const ${1:key} in ${2:object}) {\n    ${3}\n}", "For-in loop"));
        SNIPPETS.add(new JavaScriptSnippet("forof", "for (const ${1:item} of ${2:array}) {\n    ${3}\n}", "For-of loop"));
        SNIPPETS.add(new JavaScriptSnippet("if", "if (${1:condition}) {\n    ${2}\n}", "If statement"));
        SNIPPETS.add(new JavaScriptSnippet("ife", "if (${1:condition}) {\n    ${2}\n} else {\n    ${3}\n}", "If-else statement"));
        SNIPPETS.add(new JavaScriptSnippet("try", "try {\n    ${1}\n} catch (${2:error}) {\n    ${3}\n}", "Try-catch block"));
        SNIPPETS.add(new JavaScriptSnippet("promise", "new Promise((resolve, reject) => {\n    ${1}\n})", "Promise"));
        SNIPPETS.add(new JavaScriptSnippet("async", "async function ${1:name}(${2:params}) {\n    ${3}\n}", "Async function"));
        SNIPPETS.add(new JavaScriptSnippet("fetch", "fetch('${1:url}')\n    .then(response => response.json())\n    .then(data => {\n        ${2}\n    })\n    .catch(error => console.error(error));", "Fetch API"));
    }
    
    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != CompletionProvider.COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new JavaScriptCompletionQuery(), component);
    }
    
    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        if (typedText.length() == 1) {
            char ch = typedText.charAt(0);
            if (ch == '.' || Character.isLetter(ch)) {
                return CompletionProvider.COMPLETION_QUERY_TYPE;
            }
        }
        return 0;
    }
    
    private static class JavaScriptCompletionQuery extends AsyncCompletionQuery {
        
        @Override
        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
            try {
                JavaScriptContext context = analyzeContext(doc, caretOffset);
                
                if (context.isDotAccess && context.objectName != null) {
                    // Object member access
                    addObjectMemberCompletions(resultSet, context, caretOffset);
                } else {
                    // General completions
                    addKeywordCompletions(resultSet, context, caretOffset);
                    addGlobalObjectCompletions(resultSet, context, caretOffset);
                    addSnippetCompletions(resultSet, context, caretOffset);
                }
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                resultSet.finish();
            }
        }
        
        private JavaScriptContext analyzeContext(Document doc, int offset) throws BadLocationException {
            JavaScriptContext context = new JavaScriptContext();
            
            // Get text before cursor
            int lineStart = Math.max(0, offset - 100);
            String text = doc.getText(lineStart, offset - lineStart);
            
            // Check for dot access
            int lastDot = text.lastIndexOf('.');
            if (lastDot >= 0) {
                context.isDotAccess = true;
                
                // Find object name before dot
                int start = lastDot - 1;
                while (start >= 0 && (Character.isJavaIdentifierPart(text.charAt(start)))) {
                    start--;
                }
                
                if (start < lastDot - 1) {
                    context.objectName = text.substring(start + 1, lastDot);
                    context.prefix = text.substring(lastDot + 1);
                }
            } else {
                // Find current word being typed
                int start = text.length() - 1;
                while (start >= 0 && Character.isJavaIdentifierPart(text.charAt(start))) {
                    start--;
                }
                context.prefix = text.substring(start + 1);
            }
            
            return context;
        }
        
        private void addObjectMemberCompletions(CompletionResultSet resultSet, JavaScriptContext context, int caretOffset) {
            String prefix = context.prefix.toLowerCase();
            
            if (GLOBAL_OBJECTS.containsKey(context.objectName)) {
                for (JavaScriptMethod method : GLOBAL_OBJECTS.get(context.objectName)) {
                    if (method.name.toLowerCase().startsWith(prefix)) {
                        resultSet.addItem(new JavaScriptMethodCompletionItem(
                            method, caretOffset - prefix.length(), prefix.length()));
                    }
                }
            }
        }
        
        private void addKeywordCompletions(CompletionResultSet resultSet, JavaScriptContext context, int caretOffset) {
            String prefix = context.prefix.toLowerCase();
            
            for (String keyword : KEYWORDS) {
                if (keyword.startsWith(prefix)) {
                    resultSet.addItem(new JavaScriptKeywordCompletionItem(
                        keyword, caretOffset - prefix.length(), prefix.length()));
                }
            }
        }
        
        private void addGlobalObjectCompletions(CompletionResultSet resultSet, JavaScriptContext context, int caretOffset) {
            String prefix = context.prefix.toLowerCase();
            
            for (String obj : GLOBAL_OBJECTS.keySet()) {
                if (obj.toLowerCase().startsWith(prefix)) {
                    resultSet.addItem(new JavaScriptObjectCompletionItem(
                        obj, caretOffset - prefix.length(), prefix.length()));
                }
            }
        }
        
        private void addSnippetCompletions(CompletionResultSet resultSet, JavaScriptContext context, int caretOffset) {
            String prefix = context.prefix.toLowerCase();
            
            for (JavaScriptSnippet snippet : SNIPPETS) {
                if (snippet.trigger.startsWith(prefix)) {
                    resultSet.addItem(new JavaScriptSnippetCompletionItem(
                        snippet, caretOffset - prefix.length(), prefix.length()));
                }
            }
        }
    }
    
    private static class JavaScriptContext {
        boolean isDotAccess = false;
        String objectName = null;
        String prefix = "";
    }
    
    static class JavaScriptMethod {
        final String name;
        final String signature;
        final String description;
        
        JavaScriptMethod(String name, String signature, String description) {
            this.name = name;
            this.signature = signature;
            this.description = description;
        }
    }
    
    static class JavaScriptSnippet {
        final String trigger;
        final String code;
        final String description;
        
        JavaScriptSnippet(String trigger, String code, String description) {
            this.trigger = trigger;
            this.code = code;
            this.description = description;
        }
    }
}