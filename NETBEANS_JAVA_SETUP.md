# NetBeans RCP Java Setup Guide

This guide documents the lessons learned from setting up a NetBeans Rich Client Platform (RCP) application with Java 23, specifically addressing compatibility issues and configuration requirements.

## Overview

NetBeans Platform 27 (RELEASE270) requires Java 17 or newer, but using Java 23 introduces additional complexity due to the Java Platform Module System (JPMS) restrictions introduced in Java 9+. This guide covers both build-time and runtime configuration needed to successfully run a NetBeans RCP application with Java 23.

## Problem #1: Maven Build Fails with "Unknown packaging: nbm"

### Symptom
```
[ERROR] Unknown packaging: nbm-application @ line 14, column 16
[ERROR] Unknown packaging: nbm @ line 14, column 16
```

### Root Cause
Maven doesn't recognize the custom `nbm` and `nbm-application` packaging types provided by the nbm-maven-plugin because the plugin isn't loaded early enough in Maven's lifecycle.

### Solution
Create a Maven extensions configuration file to load the nbm-maven-plugin as a core extension:

**File: `.mvn/extensions.xml`**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>org.apache.netbeans.utilities</groupId>
        <artifactId>nbm-maven-plugin</artifactId>
        <version>14.4</version>
    </extension>
</extensions>
```

**Also update `pom.xml`:**
```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.netbeans.utilities</groupId>
                <artifactId>nbm-maven-plugin</artifactId>
                <version>14.4</version>
                <extensions>true</extensions>
                <configuration>
                    <brandingToken>nmoxstudio</brandingToken>
                    <cluster>nmoxstudio</cluster>
                </configuration>
            </plugin>
        </plugins>
    </pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.apache.netbeans.utilities</groupId>
            <artifactId>nbm-maven-plugin</artifactId>
            <extensions>true</extensions>
        </plugin>
    </plugins>
</build>
```

## Problem #2: Maven Uses Wrong Java Version

### Symptom
Maven runs with Java 8 (or wrong version) even though project requires Java 23:
```
Java version: 1.8.0_462, vendor: Amazon.com Inc.
```

### Root Cause
Maven uses the `JAVA_HOME` environment variable to determine which JDK to use, not necessarily the one in your PATH.

### Solution
Set `JAVA_HOME` explicitly before running Maven:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home
mvn clean install -Dnetbeans.verify.integrity=false
```

**Tip:** Add this to your shell profile (~/.zshrc or ~/.bashrc) for persistence:
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

## Problem #3: Runtime Error "Cannot run on older versions of Java than Java 17"

### Symptom
Application launches but immediately exits with error:
```
Cannot run on older versions of Java than Java 17.
Please install Java 17 or newer or use --jdkhome
switch to point to its installation directory.
```

### Root Cause
The NetBeans launcher script checks the default system Java, which may not be Java 17+.

### Solution Options

**Option 1: Pass --jdkhome flag at runtime**
```bash
./basics-app/target/nmoxstudio/bin/nmoxstudio --jdkhome /Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home
```

**Option 2: Configure jdkhome in the application config (Temporary - lost on rebuild)**
Edit `basics-app/target/nmoxstudio/etc/nmoxstudio.conf`:
```bash
jdkhome="/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home"
```

**Option 3: Add to nbm-maven-plugin configuration (Permanent)**
This configuration should be added to your `basics-app/pom.xml` to make the setting permanent. However, note that the nbm-maven-plugin doesn't directly support setting `jdkhome` in the generated config file through Maven configuration. Users will need to either:
- Pass `--jdkhome` flag when launching
- Set `JAVA_HOME` environment variable system-wide
- Manually edit the config after building

## Problem #4: Java 23 Module System Restrictions

### Symptom
Application launches with Java 23 but crashes with errors like:
```
java.lang.reflect.InaccessibleObjectException: Unable to make field transient java.net.URLStreamHandler java.net.URL.handler accessible: module java.base does not "opens java.net" to unnamed module
```

```
java.lang.Error: factory already defined
	at java.base/java.net.URL.setURLStreamHandlerFactory
```

### Root Cause
Java 9+ introduced the Java Platform Module System (JPMS) which restricts reflective access to internal JDK classes. NetBeans Platform, being a mature framework that predates JPMS, requires deep reflection access to many internal Java packages.

### Solution
Add `--add-opens` JVM arguments to open required Java modules. These must be configured in your `basics-app/pom.xml`:

```xml
<plugin>
    <groupId>org.apache.netbeans.utilities</groupId>
    <artifactId>nbm-maven-plugin</artifactId>
    <extensions>true</extensions>
    <configuration>
        <brandingToken>nmoxstudio</brandingToken>
        <cluster>nmoxstudio</cluster>
        <additionalArguments>
            <additionalArgument>--add-opens=java.base/java.net=ALL-UNNAMED</additionalArgument>
            <additionalArgument>--add-opens=java.base/java.lang=ALL-UNNAMED</additionalArgument>
            <additionalArgument>--add-opens=java.base/java.lang.reflect=ALL-UNNAMED</additionalArgument>
            <additionalArgument>--add-opens=java.base/java.io=ALL-UNNAMED</additionalArgument>
            <additionalArgument>--add-opens=java.base/java.security=ALL-UNNAMED</additionalArgument>
            <additionalArgument>--add-opens=java.base/java.util=ALL-UNNAMED</additionalArgument>
            <additionalArgument>--add-opens=java.desktop/javax.swing=ALL-UNNAMED</additionalArgument>
        </additionalArguments>
    </configuration>
</plugin>
```

These flags are automatically added to `basics-app/target/nmoxstudio/etc/nmoxstudio.conf` during the build, prefixed with `-J`:
```
default_options="--branding nmoxstudio -J--add-opens=java.base/java.net=ALL-UNNAMED ..."
```

### What Each Flag Does

| Flag | Purpose |
|------|---------|
| `--add-opens=java.base/java.net=ALL-UNNAMED` | Allows NetBeans to manipulate URL stream handlers and network internals |
| `--add-opens=java.base/java.lang=ALL-UNNAMED` | Enables reflection on core language classes |
| `--add-opens=java.base/java.lang.reflect=ALL-UNNAMED` | Required for the reflection framework itself |
| `--add-opens=java.base/java.io=ALL-UNNAMED` | Allows access to I/O internals for file system operations |
| `--add-opens=java.base/java.security=ALL-UNNAMED` | Required for security manager and classloader operations |
| `--add-opens=java.base/java.util=ALL-UNNAMED` | Enables access to collections and utilities internals |
| `--add-opens=java.desktop/javax.swing=ALL-UNNAMED` | Required for Swing UI framework internals |

## Problem #5: Build Integrity Check Failures

### Symptom
```
[ERROR] Some included modules/bundles depend on these codenamebases but they are not included:
[ERROR]    org.netbeans.modules.jemmy
[ERROR]    org.netbeans.modules.jellytools.platform
[ERROR]    org.netbeans.modules.nbjunit
```

### Root Cause
The NetBeans IDE cluster includes some test-only modules that reference testing libraries (jemmy, jellytools) which aren't included in your application.

### Solution
Disable integrity verification (these are optional test dependencies):
```bash
mvn clean install -Dnetbeans.verify.integrity=false
```

**Or** add to your `pom.xml` plugin configuration:
```xml
<plugin>
    <groupId>org.apache.netbeans.utilities</groupId>
    <artifactId>nbm-maven-plugin</artifactId>
    <configuration>
        <verifyIntegrity>false</verifyIntegrity>
    </configuration>
</plugin>
```

## Complete Working Configuration

### Parent POM (`pom.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.nmox</groupId>
    <artifactId>nmoxstudio-parent</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <netbeans.version>RELEASE270</netbeans.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>23</maven.compiler.source>
        <maven.compiler.target>23</maven.compiler.target>
    </properties>

    <repositories>
        <repository>
            <id>netbeans</id>
            <name>NetBeans</name>
            <url>https://netbeans.osuosl.org/content/repositories/releases/</url>
        </repository>
    </repositories>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.netbeans.utilities</groupId>
                    <artifactId>nbm-maven-plugin</artifactId>
                    <version>14.4</version>
                    <extensions>true</extensions>
                    <configuration>
                        <brandingToken>nmoxstudio</brandingToken>
                        <cluster>nmoxstudio</cluster>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                    <configuration>
                        <source>23</source>
                        <target>23</target>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-jar-plugin</artifactId>
                    <version>3.3.0</version>
                </plugin>
            </plugins>
        </pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.apache.netbeans.utilities</groupId>
                <artifactId>nbm-maven-plugin</artifactId>
                <extensions>true</extensions>
            </plugin>
        </plugins>
    </build>
</project>
```

### Application POM (`basics-app/pom.xml`)

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.netbeans.utilities</groupId>
            <artifactId>nbm-maven-plugin</artifactId>
            <extensions>true</extensions>
            <configuration>
                <brandingToken>nmoxstudio</brandingToken>
                <cluster>nmoxstudio</cluster>
                <additionalArguments>
                    <additionalArgument>--add-opens=java.base/java.net=ALL-UNNAMED</additionalArgument>
                    <additionalArgument>--add-opens=java.base/java.lang=ALL-UNNAMED</additionalArgument>
                    <additionalArgument>--add-opens=java.base/java.lang.reflect=ALL-UNNAMED</additionalArgument>
                    <additionalArgument>--add-opens=java.base/java.io=ALL-UNNAMED</additionalArgument>
                    <additionalArgument>--add-opens=java.base/java.security=ALL-UNNAMED</additionalArgument>
                    <additionalArgument>--add-opens=java.base/java.util=ALL-UNNAMED</additionalArgument>
                    <additionalArgument>--add-opens=java.desktop/javax.swing=ALL-UNNAMED</additionalArgument>
                </additionalArguments>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### Maven Extensions (`.mvn/extensions.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>org.apache.netbeans.utilities</groupId>
        <artifactId>nbm-maven-plugin</artifactId>
        <version>14.4</version>
    </extension>
</extensions>
```

## Build and Run Commands

### Building the Application

```bash
# Set JAVA_HOME to Java 23
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home

# Build the entire project
mvn clean install -Dnetbeans.verify.integrity=false
```

### Running the Application

**Option 1: Direct execution**
```bash
./basics-app/target/nmoxstudio/bin/nmoxstudio
```

**Option 2: Via Maven**
```bash
cd basics-app
mvn nbm:run-platform
```

**Option 3: With explicit JDK**
```bash
./basics-app/target/nmoxstudio/bin/nmoxstudio --jdkhome /Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home
```

## Verification Checklist

After configuration, verify your setup:

- [ ] `.mvn/extensions.xml` exists and contains nbm-maven-plugin extension
- [ ] Parent `pom.xml` has nbm-maven-plugin in both pluginManagement and plugins sections
- [ ] Application `pom.xml` has `additionalArguments` configuration with all `--add-opens` flags
- [ ] `maven.compiler.source` and `maven.compiler.target` are set to 23
- [ ] `JAVA_HOME` environment variable points to JDK 23
- [ ] Build succeeds: `mvn clean install -Dnetbeans.verify.integrity=false`
- [ ] Application launches without Java version errors
- [ ] No `InaccessibleObjectException` errors in console

## Common Warnings (Safe to Ignore)

These warnings appear in the console but don't affect functionality:

```
SEVERE: No way to find original stream handler for jar protocol
java.lang.reflect.InaccessibleObjectException: Unable to make field transient java.net.URLStreamHandler java.net.URL.handler accessible
```
This warning appears even with `--add-opens` flags due to timing of when the module system is configured vs. when NetBeans initializes. It's safe to ignore as long as the application continues running.

```
WARNING [org.openide.filesystems.Ordering]: Found same position 100 for both Menu/Window/...
```
These warnings indicate menu item position conflicts between NetBeans default modules and your custom modules. They're cosmetic and don't affect functionality.

## Platform-Specific Notes

### macOS
- JDK installation path: `/Library/Java/JavaVirtualMachines/jdk-23.jdk/Contents/Home`
- Application config: `~/Library/Application Support/nmoxstudio/dev`
- Cache location: `~/Library/Caches/nmoxstudio/dev`

### Linux
- JDK installation path: Varies by distribution (e.g., `/usr/lib/jvm/java-23-openjdk`)
- Application config: `~/.nmoxstudio/dev`
- Cache location: `~/.cache/nmoxstudio/dev`

### Windows
- JDK installation path: `C:\Program Files\Java\jdk-23`
- Application config: `%APPDATA%\nmoxstudio\dev`
- Cache location: `%LOCALAPPDATA%\nmoxstudio\Cache\dev`

## Troubleshooting

### Application Won't Start

1. **Check Java version:**
   ```bash
   java -version
   echo $JAVA_HOME
   ```

2. **Verify launcher is using correct Java:**
   ```bash
   ./basics-app/target/nmoxstudio/bin/nmoxstudio --jdkhome $JAVA_HOME
   ```

3. **Check logs:**
   - macOS: `~/Library/Application Support/nmoxstudio/dev/var/log/messages.log`
   - Linux: `~/.nmoxstudio/dev/var/log/messages.log`
   - Windows: `%APPDATA%\nmoxstudio\dev\var\log\messages.log`

### Maven Build Fails

1. **Verify Maven is using Java 23:**
   ```bash
   mvn --version
   ```

2. **Clean Maven cache if persistent issues:**
   ```bash
   rm -rf ~/.m2/repository/org/netbeans
   ```

3. **Rebuild with verbose output:**
   ```bash
   mvn clean install -X -Dnetbeans.verify.integrity=false
   ```

## Additional Resources

- [NetBeans Platform Developer Guide](https://netbeans.apache.org/tutorials/nbm-quick-start.html)
- [NBM Maven Plugin Documentation](https://bits.netbeans.org/mavenutilities/nbm-maven-plugin/)
- [Java Platform Module System (JPMS)](https://openjdk.org/projects/jigsaw/spec/)
- [Understanding Java --add-opens](https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html#GUID-7BB28E4D-99B3-4078-BDC4-FC24180CE82B)

## Summary

Successfully running a NetBeans RCP application with Java 23 requires:

1. **Build-time configuration**: Maven extensions and plugin configuration
2. **Java 23 compatibility**: `--add-opens` flags to bypass module restrictions
3. **Runtime configuration**: Correct JDK detection via `jdkhome` or `JAVA_HOME`
4. **Understanding**: Knowledge of JPMS and why these configurations are necessary

The configuration may seem complex, but it's necessary due to NetBeans Platform's architecture predating the Java module system. These settings allow the mature, stable NetBeans Platform to work with modern Java versions while maintaining backward compatibility.
