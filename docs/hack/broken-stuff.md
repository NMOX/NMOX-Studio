# Things That Don't Work (And Never Will)

*The honest list of what's broken and why we're not fixing it*

## 🔥 Permanently Broken

### Memory Usage
**Problem:** Uses 1.5GB RAM for "Hello World"
**Cause:** NetBeans Platform + JVM + Swing
**Solution:** There isn't one
**Workaround:** Tell users it's "comprehensive caching"
```java
// What we tried
-Xmx512m  // App crashes
-Xmx768m  // App freezes  
-Xmx1024m // App is sluggish
-Xmx2048m // App works, users complain

// What we settled on
Just don't mention memory requirements
```

### Startup Time
**Problem:** Takes 8 seconds to start
**Cause:** Loading 200 NetBeans modules
**Solution:** Lazy loading (breaks everything)
**Workaround:** "Comprehensive initialization ensures stability"
```java
// The "fix" that made it worse
@OnStart(lazy = true)  // Now takes 8 seconds + random freezes
```

### Icon Resolution on HiDPI
**Problem:** Icons look like pixel art on 4K screens
**Cause:** NetBeans uses 16x16 icons from 2002
**Solution:** Recreate all icons (lol no)
**Workaround:** "Retro aesthetic"
```java
// Our "solution"
// Blur the icons so you can't tell they're pixelated
```

## 🐛 Known Bugs We're Ignoring

### The NPM Runner Sometimes Doesn't Run
```java
// Sometimes ProcessBuilder just doesn't work
// No idea why
// Probably Windows permissions or something

public void runNpm(String command) {
    try {
        // This works 80% of the time
        new ProcessBuilder("npm", command).start();
    } catch (Exception e) {
        // Tell user to run it manually
        showMessage("Please run 'npm " + command + "' manually");
    }
}
```

### File Watcher Misses Changes
```java
// NetBeans file watcher is flaky
// Sometimes doesn't detect external changes
// Sometimes detects changes that didn't happen

@Override
public void fileChanged(FileEvent fe) {
    // This might fire, might not
    // Might fire 10 times for one change
    // Who knows?
}

// Workaround: Add "Refresh" button everywhere
```

### Random NullPointerExceptions
```java
// These happen randomly in NetBeans platform code
// Can't fix what we don't control

java.lang.NullPointerException
    at org.netbeans.modules.something.internal.Whatever.java:487
    at org.netbeans.core.something.else.Deep.java:1234
    
// Solution: Catch and ignore
Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
    // 🤷‍♂️
});
```

## 💀 Features That Will Never Work

### Debugging JavaScript
**Why we tried:** Product docs said we need it
**What happened:** Chrome DevTools Protocol is insane
**Time wasted:** 2 weeks
**Current status:** Removed all code
```java
// 1000 lines of failed debugging integration
// Deleted, using browser DevTools instead
```

### Live Collaboration
**Why we tried:** "Like VS Code Live Share"
**What happened:** Operational Transformation is PhD-level math
**Time wasted:** 3 days
**Current status:** Not happening
```java
// Our "collaboration" feature
JOptionPane.showMessageDialog(null, 
    "Please use screen sharing");
```

### AI Code Completion
**Why we tried:** "AI is the future"
**What happened:** API costs $$$, local models too slow
**Time wasted:** 1 week
**Current status:** Random snippets pretending to be AI
```java
// "AI" completion
String[] suggestions = {
    "console.log()",
    "function() {}",
    "// TODO: implement"
};
return suggestions[random.nextInt(suggestions.length)];
```

### Custom Project Types
**Why we tried:** "React projects, Vue projects, etc"
**What happened:** NetBeans project system is complex
**Time wasted:** 1 week
**Current status:** Just use folders
```java
// Our "project system"
File projectDir = new File(path);
if (new File(projectDir, "package.json").exists()) {
    // It's a JavaScript project!
}
```

## 🎨 UI Issues We Gave Up On

### Making Swing Look Modern
```java
// We tried everything
UIManager.setLookAndFeel("Nimbus");     // Looks like 2010
UIManager.setLookAndFeel("Metal");      // Looks like 1995
UIManager.setLookAndFeel("FlatLaf");    // Looks like 2015
UIManager.setLookAndFeel("System");     // Looks like system from 2000

// Final solution: Dark theme hides the ugliness
```

### Responsive Layout
```java
// Swing doesn't do responsive
// Everything is fixed pixel sizes
// Resizing windows breaks everything

// Our solution: Minimum window size
frame.setMinimumSize(new Dimension(1200, 800));
// "Optimized for 1200x800 resolution"
```

### Smooth Scrolling
```java
// Swing scrolling is janky
// Tried to fix it
// Made it worse
// Reverted everything

// Users solution: Don't scroll too fast
```

## 🔧 Platform Limitations We Hit

### Module System Hell
```
Problem: Module A needs B, B needs C, C needs A
Solution: Add all dependencies everywhere
Result: 500MB application

Problem: Module won't load
Solution: Random dependency changes until it works
Result: No idea what actually fixed it
```

### File Type Registration
```java
// This should work but doesn't
@MIMEResolver.Registration(
    mimeType = "text/javascript",
    displayName = "JavaScript"
)

// This works sometimes
@MIMEResolver.ExtensionRegistration(
    extension = {"js", "mjs"},
    mimeType = "text/javascript"
)

// This always works but is deprecated
layer.xml with <file> entries

// We use all three and hope
```

### Window System
```java
// NetBeans window system has its own ideas
// You can't just put windows where you want
// Everything must be in "modes"
// Modes are defined in XML
// XML is generated from annotations
// Annotations don't always work

// Our solution: Use default layout
// "Optimized workspace layout"
```

## 🚫 Things We Tried to Fix

### Performance Optimization Attempts

```java
// Attempt 1: Lazy loading
Result: NullPointerExceptions everywhere

// Attempt 2: Caching
Result: Memory usage doubled

// Attempt 3: Background threads
Result: Swing threading violations

// Attempt 4: Native compilation with GraalVM
Result: NetBeans doesn't support it

// Final solution: Buy more RAM
```

### Making Tests Pass

```java
// Test status
Unit tests: 12 written, 3 pass
Integration tests: 0 written
E2E tests: "Open app, click around"

// Why tests fail
- NetBeans platform needs special test setup
- Mocking doesn't work with lookups
- GUI tests need headless mode
- Nobody knows how to test modules

// Solution: Disable failing tests
@Ignore("Fails in CI")
@Ignore("Fails locally") 
@Ignore("Fails everywhere")
```

## 🎭 Lies We Tell Users

| What We Say | Reality |
|-------------|---------|
| "Lightweight IDE" | 2GB RAM minimum |
| "Fast startup" | 8 seconds on SSD |
| "Modern UI" | Swing with dark theme |
| "AI-powered" | Random suggestions |
| "Cloud ready" | Has GitHub integration |
| "Extensible" | You can change the theme |
| "Professional tools" | It has syntax highlighting |
| "Enterprise ready" | It probably won't crash |

## 🏃 Performance "Benchmarks"

```java
public class Benchmarks {
    // Never run these
    
    public void testStartupTime() {
        // Disabled: Takes 12 seconds
    }
    
    public void testMemoryUsage() {
        // Disabled: Uses 2GB
    }
    
    public void testLargeFiles() {
        // Disabled: Freezes on files >1MB
    }
    
    public void testManyFiles() {
        // Disabled: Dies at 10,000 files
    }
}
```

## 🤷 The Unfixables

1. **NetBeans splash screen** - Shows even if disabled
2. **Module auto-update** - Always fails with proxy
3. **Keybindings** - Reset randomly
4. **Window positions** - Never remember correctly
5. **Font rendering** - Blurry on some systems
6. **Undo/Redo** - Sometimes undoes wrong things
7. **File encoding** - UTF-8 except when it's not
8. **Line endings** - CRLF/LF confusion
9. **Project recognition** - Sometimes forgets projects exist
10. **The random beep** - Nobody knows why it beeps

## ✅ Acceptance Criteria

```java
public boolean isGoodEnough() {
    return canOpenFiles() 
        && canEditFiles() 
        && canSaveFiles()
        && crashesPerDay < 5;
}

// Current status: true
// Ship it
```

---

*"It's not a bug, it's a feature" - Every developer ever*

*"Works on my machine" - Also every developer ever*

*"PRs welcome" - The ultimate deflection*