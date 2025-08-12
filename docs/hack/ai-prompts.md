# AI Prompting Strategies for NetBeans Development

*How to get AI to write NetBeans code that actually works*

## 🤖 The Golden Rules

1. **AI doesn't know NetBeans Platform** - It will confidently give you IntelliJ or Eclipse code
2. **Be VERY specific** - "NetBeans Platform 22 using annotations not layer.xml"
3. **Provide examples** - Copy-paste working code as context
4. **Iterate** - First prompt gets you 60%, refine from there
5. **Verify everything** - AI makes up NetBeans APIs that don't exist

## 📝 Prompts That Actually Work

### Creating a Module

```
Create a NetBeans Platform 22 module for editing JavaScript files.

Requirements:
- Use @MIMEResolver.ExtensionRegistration annotation for .js files
- Extend MultiDataObject
- Register with @DataObject.Registration
- Use text/javascript MIME type
- Include @ActionReferences for Open and Edit actions
- Use MultiViewEditorElement for the editor
- Use Maven packaging type "nbm"
- Include proper NetBeans dependencies in pom.xml

Show complete Java code and pom.xml.
```

### Creating a Service

```
Create a NetBeans Platform service using Lookup pattern.

Requirements:
- Create interface extending nothing
- Create implementation with @ServiceProvider annotation
- Use Lookup.getDefault().lookup() to retrieve
- Make it a singleton
- Thread-safe
- NetBeans Platform 22 compatible

Example of how it should be used:
MyService service = Lookup.getDefault().lookup(MyService.class);

Show interface, implementation, and usage example.
```

### Creating an Action

```
Create a NetBeans Platform action that runs NPM commands.

Requirements:
- Use @ActionID, @ActionRegistration, @ActionReference annotations
- Implement ActionListener
- Get current project from context
- Run npm using ProcessBuilder
- Output to NetBeans output window
- Handle errors properly
- Work with NetBeans Platform 22

The action should appear in the project context menu.
Show complete code including annotations.
```

## 🎯 Module-Specific Prompts

### File Type Support

```
Add TypeScript file support to NetBeans Platform app.

Create:
1. DataObject for .ts and .tsx files
2. MIME type registration
3. Syntax highlighting using Lexer
4. Basic completion provider
5. Icon registration

Use NetBeans Platform 22 annotations, not layer.xml.
Include all necessary Maven dependencies.
```

### Project Type

```
Create a custom project type for React applications in NetBeans Platform.

Requirements:
- Implement Project interface
- ProjectFactory with @ServiceProvider
- Logical view with nodes
- Recognize package.json
- Custom icons
- Build/Run actions that execute npm scripts

Use NetBeans Platform 22 APIs.
```

### Tool Window

```
Create a NetBeans Platform TopComponent for NPM package management.

Requirements:
- @TopComponent.Registration annotation
- Appears in left sidebar
- Tree view of dependencies
- Context menu actions
- Refresh button
- Search field
- Opens on IDE start

Use NetBeans Platform 22 with Swing UI.
```

## 🔧 Fixing AI Mistakes

### When AI gives you IntelliJ code:

```
This looks like IntelliJ IDEA plugin code. 
Convert to NetBeans Platform 22:
- Replace AnAction with ActionListener
- Replace ToolWindow with TopComponent
- Replace PsiFile with FileObject
- Use NetBeans Lookup instead of project.getService()
- Use @ServiceProvider instead of plugin.xml

Here's the IntelliJ code: [paste code]
```

### When AI uses deprecated APIs:

```
This uses deprecated NetBeans APIs. Update for NetBeans Platform 22:
- Replace layer.xml with annotations
- Replace SharedClassObject with Lookup
- Replace old Nodes API with new version
- Use new Window System API

Current deprecated code: [paste code]
```

### When AI invents APIs:

```
The class org.netbeans.api.supermagic.WizardBuilder doesn't exist.

What's the correct NetBeans Platform 22 API for:
- Creating wizards
- Multi-step dialogs
- User input validation

Show real API usage with actual class names from NetBeans Platform.
```

## 💡 Getting Complete Solutions

### The Setup-Code-Test Pattern

```
I need to [specific feature] in a NetBeans Platform 22 application.

Setup:
- Show Maven dependencies needed
- Show module structure
- Show any configuration files

Code:
- Show complete Java classes
- Include all imports
- Include all annotations
- Handle exceptions

Test:
- Show how to test this
- Show how to run it
- Show expected output
```

### The Working Example Pattern

```
Here's a working NetBeans Platform module that does something similar:

[paste working code]

Now create a similar module that does [your requirement] instead.
Keep the same structure and annotation pattern.
```

## 🚀 Advanced Prompting

### For Performance Issues

```
This NetBeans Platform code is slow:
[paste code]

Optimize for:
- Lazy loading
- Background threads using RequestProcessor
- Caching with WeakReference
- Proper Swing EDT usage
- NetBeans ProgressHandle for long operations

Show optimized version with same functionality.
```

### For UI Creation

```
Create a NetBeans Platform form for [purpose].

Requirements:
- Use Matisse GUI builder compatible layout
- Follow NetBeans UI guidelines
- Proper Mnemonics
- Accessibility support
- Error validation
- Responsive layout

Generate both:
1. The .form file content
2. The Java code

Make it work with NetBeans Platform 22.
```

### For Debugging Issues

```
NetBeans Platform module throws this error:
[paste stack trace]

Context:
- NetBeans Platform 22
- Java 17
- Module dependencies: [list them]

The code that causes it:
[paste code]

What's wrong and how do I fix it?
```

## 🎨 Templates for Common Tasks

### NPM Integration

```
Create NetBeans Platform code to:
1. Detect if npm is installed
2. Read package.json
3. List available scripts
4. Run a selected script
5. Show output in NetBeans output window
6. Handle errors gracefully

Use ProcessBuilder and NetBeans IOProvider.
```

### Git Integration

```
Integrate JGit into NetBeans Platform module:
1. Add Maven dependency
2. Get current project's git repository
3. Show git status
4. Create commit action
5. Show diff in editor

Use NetBeans Platform 22 APIs and JGit 6.x.
```

### Language Server Integration

```
Connect to TypeScript Language Server from NetBeans Platform:
1. Start tsserver process
2. Communicate via JSON-RPC
3. Request completions
4. Convert to NetBeans CompletionItem
5. Show in editor

Use NetBeans Platform 22 completion APIs.
```

## ⚠️ What NOT to Ask

### Too Vague
❌ "Create a NetBeans IDE"
❌ "Add web development support"
❌ "Make it like VS Code"

### Too Broad
❌ "Create complete JavaScript support"
❌ "Implement full debugging"
❌ "Build entire plugin system"

### Impossible
❌ "Make Swing look modern"
❌ "Reduce memory usage to 50MB"
❌ "Make it faster than native code"

## 🔄 Iteration Strategy

```
Prompt 1: Get basic structure
├── 60% correct
├── 30% needs fixing  
└── 10% completely wrong

Prompt 2: Fix specific issues
├── 85% correct
└── 15% needs tweaking

Prompt 3: Polish and optimize
├── 95% correct
└── 5% manual fixes

Prompt 4: Add error handling
└── 100% shippable (hopefully)
```

## 🎯 Copy-Paste Goldmines

When AI fails, steal from:
- https://github.com/apache/netbeans (the source)
- https://bits.netbeans.org/dev/javadoc/ (official docs)
- https://github.com/gephi/gephi (complex platform app)
- https://github.com/jMonkeyEngine/sdk (game engine IDE)

## 🆘 Emergency Prompts

### When Nothing Works

```
I have a NetBeans Platform 22 module that won't load.

Symptoms:
[paste any errors]

Module info:
- Dependencies: [list]
- Annotations used: [list]
- Module manifest: [paste]

Common causes and fixes?
```

### When You're Lost

```
I need to do [specific task] in NetBeans Platform.

I don't know:
- Which API to use
- Which module to depend on
- If this is even possible

What's the standard NetBeans Platform way to do this?
Show smallest working example.
```

---

*Remember: AI is your junior developer who read the NetBeans book once. Trust but verify.*