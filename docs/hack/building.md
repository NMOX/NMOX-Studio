# How to Actually Build This Thing

*Real instructions from someone who's been up all night fighting NetBeans*

## 🔨 The Truth About Building NetBeans Apps

### Prerequisites (The Real Ones)

```bash
# What they tell you:
- Java 17
- Maven 3.6+
- NetBeans 22

# What you actually need:
- Java 17 (exactly 17, not 18, not 21, EXACTLY 17)
- Maven 3.9.x (3.8 has bugs with NBM)
- NetBeans IDE (to understand WTF is happening)
- Coffee (lots)
- Patience (more than you have)
- A rubber duck (for debugging)
```

### Step 1: Create the Platform App

```bash
# Don't use the archetype, it's broken
# Copy this exact structure or suffer

mkdir nmox-studio
cd nmox-studio

# Create parent POM
cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>org.nmox</groupId>
    <artifactId>nmox-studio-parent</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <properties>
        <netbeans.version>RELEASE220</netbeans.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    
    <modules>
        <module>application</module>
        <module>branding</module>
        <!-- Add modules here as you create them -->
    </modules>
    
    <repositories>
        <repository>
            <id>netbeans</id>
            <url>https://bits.netbeans.org/maven2/</url>
        </repository>
    </repositories>
</project>
EOF
```

### Step 2: The Application Module (The Glue)

```bash
mkdir -p application/src/main/nbm
cd application

cat > pom.xml << 'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.nmox</groupId>
        <artifactId>nmox-studio-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>
    
    <artifactId>nmox-studio-app</artifactId>
    <packaging>nbm-application</packaging>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.netbeans.utilities</groupId>
                <artifactId>nbm-maven-plugin</artifactId>
                <version>14.0</version>
                <extensions>true</extensions>
                <configuration>
                    <brandingToken>nmoxstudio</brandingToken>
                    <etcConfFile>src/main/resources/nmoxstudio.conf</etcConfFile>
                </configuration>
            </plugin>
        </plugins>
    </build>
    
    <dependencies>
        <!-- This is where the magic happens -->
        <!-- NetBeans modules we're stealing -->
        <dependency>
            <groupId>org.netbeans.api</groupId>
            <artifactId>org-openide-windows</artifactId>
            <version>${netbeans.version}</version>
        </dependency>
        <dependency>
            <groupId>org.netbeans.api</groupId>
            <artifactId>org-openide-util</artifactId>
            <version>${netbeans.version}</version>
        </dependency>
        <dependency>
            <groupId>org.netbeans.api</groupId>
            <artifactId>org-netbeans-modules-editor</artifactId>
            <version>${netbeans.version}</version>
        </dependency>
        <!-- Add more as needed, but each one adds 10MB -->
    </dependencies>
</project>
EOF
```

### Step 3: Your First Module (The Actual Code)

```bash
mkdir -p modules/web-editor
cd modules/web-editor

# Module POM
cat > pom.xml << 'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.nmox</groupId>
        <artifactId>nmox-studio-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    
    <artifactId>web-editor</artifactId>
    <packaging>nbm</packaging>
    
    <dependencies>
        <!-- NetBeans APIs -->
        <dependency>
            <groupId>org.netbeans.api</groupId>
            <artifactId>org-openide-filesystems</artifactId>
            <version>${netbeans.version}</version>
        </dependency>
        <!-- Add what you need, prepare for classloader hell -->
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.netbeans.utilities</groupId>
                <artifactId>nbm-maven-plugin</artifactId>
                <version>14.0</version>
                <extensions>true</extensions>
                <configuration>
                    <publicPackages>
                        <publicPackage>org.nmox.studio.web</publicPackage>
                    </publicPackages>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
EOF
```

### Step 4: The Actual Java Code

```java
// This is what AI is good at generating
// modules/web-editor/src/main/java/org/nmox/studio/web/JavaScriptDataObject.java

package org.nmox.studio.web;

import java.io.IOException;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.text.MultiViewEditorElement;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

@Messages({
    "LBL_JavaScript_LOADER=JavaScript Files"
})
@MIMEResolver.ExtensionRegistration(
    displayName = "#LBL_JavaScript_LOADER",
    mimeType = "text/javascript",
    extension = {"js", "mjs", "jsx"}
)
@DataObject.Registration(
    mimeType = "text/javascript",
    iconBase = "org/nmox/studio/web/javascript.png",
    displayName = "#LBL_JavaScript_LOADER",
    position = 300
)
@ActionReferences({
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "System", id = "org.openide.actions.OpenAction"),
        position = 100
    ),
    @ActionReference(
        path = "Loaders/text/javascript/Actions",
        id = @ActionID(category = "Edit", id = "org.openide.actions.CutAction"),
        position = 300
    )
})
public class JavaScriptDataObject extends MultiDataObject {

    public JavaScriptDataObject(FileObject pf, MultiFileLoader loader) 
            throws DataObjectExistsException, IOException {
        super(pf, loader);
        registerEditor("text/javascript", true);
    }

    @Override
    protected int associateLookup() {
        return 1;
    }

    @MultiViewElement.Registration(
        displayName = "#LBL_JavaScript_EDITOR",
        iconBase = "org/nmox/studio/web/javascript.png",
        mimeType = "text/javascript",
        persistenceType = TopComponent.PERSISTENCE_ONLY_OPENED,
        preferredID = "JavaScript",
        position = 1000
    )
    @Messages("LBL_JavaScript_EDITOR=Source")
    public static MultiViewEditorElement createEditor(Lookup lkp) {
        return new MultiViewEditorElement(lkp);
    }
}
```

## 🐛 Common Build Issues (And Actual Fixes)

### Problem: "Cannot find symbol: class ServiceProvider"
```bash
# You forgot the dependency
<dependency>
    <groupId>org.netbeans.api</groupId>
    <artifactId>org-openide-util-lookup</artifactId>
    <version>${netbeans.version}</version>
</dependency>
```

### Problem: "No public packages in module"
```xml
<!-- In your nbm plugin config -->
<publicPackages>
    <publicPackage>org.nmox.studio.*</publicPackage>
</publicPackages>
```

### Problem: Module doesn't load
```xml
<!-- Check your layer.xml -->
<!-- Just kidding, use annotations, layer.xml is deprecated -->
<!-- But sometimes you still need it because NetBeans -->
```

### Problem: Out of Memory
```bash
# In your nbm-maven-plugin config
<additionalArguments>-J-Xmx2048m</additionalArguments>

# Or just accept that NetBeans apps use RAM like it's 1999
```

## 🚀 Actually Running It

```bash
# From root directory
mvn clean install
mvn nbm:run-platform

# If that doesn't work
cd application
mvn nbm:run-platform

# If that doesn't work
mvn nbm:run-platform -e -X > debug.log 2>&1
# Send debug.log to AI and pray

# Nuclear option
rm -rf ~/.m2/repository/org/netbeans
mvn clean install -U
```

## 📦 Creating Installers (Good Luck)

```bash
# This supposedly works
mvn nbm:build-installers

# What actually works
# 1. Build the ZIP distribution
mvn clean package
cd application/target
# 2. There's a .zip file somewhere
# 3. That's your installer
# 4. Tell users to unzip and run bin/nmoxstudio

# For Mac (requires signing)
# Just don't. Seriously.
# Or budget $99/year for Apple Developer Account
# And another week to figure out notarization
```

## 🎯 Pro Tips (From Pain)

1. **Use NetBeans IDE to create modules first**, then copy the structure
2. **Start with one module**, get it working, then add more
3. **Don't fight the platform**, if NetBeans wants something done a certain way, just do it
4. **Copy from existing apps** - look at Gephi, JDownloader, or other NetBeans platform apps
5. **The documentation lies** - when in doubt, check the source code
6. **Layer.xml is dead but not really** - use annotations but keep layer.xml for backup
7. **Module dependencies are hell** - add them one at a time and test
8. **Branding is painful** - just accept the default look for MVP

## 🆘 When All Else Fails

```bash
# The nuclear reset
rm -rf ~/.netbeans
rm -rf ~/.m2/repository
rm -rf */target
mvn clean install -U

# Still broken?
# New project, copy code, try again
# Welcome to NetBeans Platform development
```

---

*Remember: If it compiles, ship it. You can fix it in v2.*