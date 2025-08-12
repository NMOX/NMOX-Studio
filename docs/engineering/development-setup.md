# Development Setup & Infrastructure

*Complete guide to set up NMOX Studio development environment*

## 🎯 Quick Start for Developers

### Prerequisites
```bash
# Required software
Java 17+ (OpenJDK or Oracle)
Maven 3.8+
Git 2.30+

# Recommended tools
NetBeans IDE 22.0+ (for testing the platform)
IntelliJ IDEA (for development)
VS Code (for documentation editing)
```

### Clone & Build
```bash
# Clone repository
git clone https://github.com/NMOX/NMOX-Studio.git
cd NMOX-Studio

# Build complete distribution
mvn clean package -DskipTests

# Quick compile check
mvn clean compile

# Run the application
cd application/target/nmoxstudio/bin
./nmox-studio                    # Linux/Mac
# or
nmox-studio.exe                  # Windows
```

## 🏗️ Development Environment Setup

### IDE Configuration

#### IntelliJ IDEA Setup
```xml
<!-- Add to .idea/compiler.xml -->
<component name="CompilerConfiguration">
  <option name="DEFAULT_COMPILER" value="Javac" />
  <option name="USE_RELEASE_OPTION" value="true" />
  <bytecodeTargetLevel target="17" />
</component>

<!-- Maven configuration -->
Settings > Build > Build Tools > Maven
- Maven home directory: /path/to/maven
- User settings file: ~/.m2/settings.xml
- JDK for importer: Java 17
```

#### NetBeans IDE Setup (for testing)
```bash
# Install NetBeans 22.0+
# Open project as Maven project
# Set JDK to Java 17
# Enable "Compile on Save"

# For module development
Tools > Netbeans Platforms > Add Platform
# Point to application/target/nmoxstudio
```

### Maven Configuration
```xml
<!-- ~/.m2/settings.xml -->
<settings>
  <profiles>
    <profile>
      <id>nmox-dev</id>
      <properties>
        <netbeans.version>RELEASE220</netbeans.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
      </properties>
    </profile>
  </profiles>
  
  <activeProfiles>
    <activeProfile>nmox-dev</activeProfile>
  </activeProfiles>
</settings>
```

## 🔧 Build System Deep Dive

### Module Build Order
```
1. core/           # Platform services, theme
2. tools/          # NPM integration, project support  
3. editor/         # File type support, completion
4. ui/             # UI components
5. project/        # Project templates
6. application/    # Final packaging
```

### Build Commands
```bash
# Full clean build
mvn clean package

# Skip tests (faster development)
mvn clean package -DskipTests

# Build specific module
cd tools/
mvn clean package

# Run tests only
mvn test

# Install to local repository
mvn clean install

# Create distribution without integrity check
mvn clean package -Dnetbeans.verify.integrity=false
```

### Common Build Issues
```bash
# Issue: "Project depends on packages not accessible at runtime"
# Solution: Add missing NetBeans dependencies to pom.xml

# Issue: "Cannot find symbol" for NetBeans APIs
# Solution: Check NetBeans version in parent pom.xml

# Issue: Tests fail with ClassNotFoundException
# Solution: Run with -DskipTests during development
```

## 🧪 Testing Infrastructure

### Unit Testing Setup
```xml
<!-- Add to module pom.xml -->
<dependencies>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.11.0</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.25.3</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

### Test Structure
```
src/test/java/
├── org/nmox/studio/core/
│   └── ThemeInstallerTest.java
├── org/nmox/studio/tools/npm/
│   ├── NpmServiceTest.java
│   ├── ProjectValidatorTest.java
│   └── WebProjectFactoryTest.java
└── org/nmox/studio/editor/
    └── JavaScriptCompletionTest.java
```

### Running Tests
```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=NpmServiceTest

# Test with coverage
mvn test jacoco:report

# Integration tests
mvn verify
```

## 🚀 Development Workflow

### Git Workflow
```bash
# Feature development
git checkout main
git pull origin main
git checkout -b feature/javascript-syntax-highlighting

# Development cycle
# ... make changes ...
mvn clean test                    # Run tests
git add .
git commit -m "Add JavaScript lexer implementation"

# Push and create PR
git push origin feature/javascript-syntax-highlighting
# Create pull request via GitHub UI
```

### Branch Naming Convention
```
feature/short-description         # New features
bugfix/issue-description         # Bug fixes
hotfix/critical-issue           # Critical production fixes
refactor/component-name         # Code refactoring
docs/section-update             # Documentation updates
```

### Commit Message Format
```
type(scope): short description

Longer description if needed.

- Bullet points for details
- Reference issues: fixes #123

Examples:
feat(editor): add JavaScript syntax highlighting
fix(npm): handle missing package.json gracefully
docs(readme): update build instructions
```

## 🔍 Debugging Setup

### Application Debugging
```bash
# Debug the built application
cd application/target/nmoxstudio/bin
./nmox-studio --jdkhome /path/to/jdk --jdkargs "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000"

# Connect debugger to port 8000
```

### Module Debugging
```java
// Add logging to modules
import java.util.logging.Logger;
import java.util.logging.Level;

public class NpmService {
    private static final Logger LOG = Logger.getLogger(NpmService.class.getName());
    
    public void runCommand(String command) {
        LOG.log(Level.INFO, "Executing NPM command: {0}", command);
        // ... implementation
    }
}
```

### NetBeans Platform Debugging
```bash
# Enable NetBeans logging
echo "org.nmox.studio.level=FINE" >> application/target/nmoxstudio/etc/nmoxstudio.conf

# View logs
tail -f ~/.nmoxstudio/var/log/messages.log
```

## 📊 Performance Monitoring

### Build Performance
```bash
# Time builds
time mvn clean package

# Profile Maven execution
mvn clean package -Dmaven.profile

# Dependency analysis
mvn dependency:analyze
mvn dependency:tree
```

### Runtime Performance
```java
// Add performance monitoring
public class PerformanceMonitor {
    public static void timeOperation(String name, Runnable operation) {
        long start = System.currentTimeMillis();
        operation.run();
        long duration = System.currentTimeMillis() - start;
        Logger.getLogger("performance").info(
            String.format("%s took %d ms", name, duration));
    }
}

// Usage
PerformanceMonitor.timeOperation("NPM command", () -> {
    npmService.runCommand("install");
});
```

### Memory Monitoring
```bash
# Run with memory monitoring
./nmox-studio --jdkargs "-XX:+PrintGCDetails -XX:+PrintGCTimeStamps"

# JVM monitoring
jconsole
jvisualvm
```

## 🔧 Development Tools

### Code Quality Tools
```xml
<!-- Add to parent pom.xml -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.11</version>
  <executions>
    <execution>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

### Static Analysis
```bash
# Run SpotBugs
mvn spotbugs:check

# Run Checkstyle
mvn checkstyle:check

# Run PMD
mvn pmd:check
```

### Documentation Generation
```bash
# Generate Javadoc
mvn javadoc:javadoc

# Generate site documentation
mvn site

# View generated docs
open target/site/index.html
```

## 🐳 Docker Development (Optional)

### Development Container
```dockerfile
# Dockerfile.dev
FROM openjdk:17-jdk

RUN apt-get update && apt-get install -y \
    maven \
    git \
    nodejs \
    npm

WORKDIR /workspace
COPY . .

RUN mvn dependency:go-offline

CMD ["bash"]
```

```bash
# Build and run development container
docker build -f Dockerfile.dev -t nmox-dev .
docker run -it -v $(pwd):/workspace nmox-dev

# Inside container
mvn clean package
```

## 📝 Development Scripts

### Useful Scripts
```bash
# scripts/dev-build.sh
#!/bin/bash
set -e

echo "Building NMOX Studio..."
mvn clean package -DskipTests

echo "Starting application..."
cd application/target/nmoxstudio/bin
./nmox-studio

# scripts/quick-test.sh
#!/bin/bash
set -e

echo "Running unit tests..."
mvn test -pl core,tools,editor

echo "Running integration tests..."
mvn verify -pl application
```

```bash
# Make scripts executable
chmod +x scripts/*.sh

# Run scripts
./scripts/dev-build.sh
./scripts/quick-test.sh
```

## 🔄 Continuous Integration Setup

### GitHub Actions Workflow
```yaml
# .github/workflows/build.yml
name: Build and Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
        
    - name: Run tests
      run: mvn clean test
      
    - name: Build distribution
      run: mvn clean package -DskipTests
```

## 🆘 Troubleshooting

### Common Issues

**Build fails with "cannot find symbol"**
```bash
# Solution: Clean and rebuild
mvn clean
rm -rf ~/.m2/repository/org/nmox
mvn package
```

**Application won't start**
```bash
# Check Java version
java -version

# Check if distribution is complete
ls -la application/target/nmoxstudio/

# Run with debug output
./nmox-studio --jdkargs "-verbose:class"
```

**Out of memory during build**
```bash
# Increase Maven memory
export MAVEN_OPTS="-Xmx2g -XX:MaxMetaspaceSize=512m"
mvn clean package
```

**Tests fail with weird errors**
```bash
# Clean test state
mvn clean
rm -rf target/
mvn test
```

---

**This setup ensures a smooth development experience for all contributors, from first-time builders to power users optimizing performance.**

*Ready to code? Start with: `git clone && mvn clean package && ./scripts/dev-build.sh`*