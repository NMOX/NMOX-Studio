# NMOX Studio Engineering Documentation

## 🏗️ Engineering Overview

This documentation provides the technical blueprint for building NMOX Studio - a professional web development IDE. It translates product requirements into actionable engineering specifications, architecture decisions, and implementation guidelines.

## 📚 Documentation Structure

### Core Technical Documents
- [System Architecture](./architecture.md) - Technical architecture and design decisions
- [Technical Roadmap](./technical-roadmap.md) - Engineering milestones and deliverables
- [Implementation Guide](./implementation-guide.md) - Detailed technical specifications
- [Performance Engineering](./performance.md) - Performance targets and optimization strategies
- [Testing Strategy](./testing-strategy.md) - Quality assurance and testing approach

### Infrastructure & Operations
- [Scalability Plan](./scalability.md) - Infrastructure and scaling requirements
- [Security Requirements](./security.md) - Security architecture and compliance
- [API Design](./api-design.md) - Plugin API and integration specifications
- [Build & Release](./build-release.md) - CI/CD and release engineering

### Team & Process
- [Team Structure](./team-structure.md) - Engineering organization and roles
- [Technical Debt](./tech-debt.md) - Debt management and refactoring strategy
- [Development Standards](./standards.md) - Coding standards and best practices
- [On-Call Playbook](./on-call.md) - Incident response and operations

## 🎯 Engineering Principles

1. **Performance First** - Every feature must meet performance budgets
2. **Modular Architecture** - Clean separation of concerns via NBM modules
3. **Test-Driven Development** - Minimum 80% code coverage
4. **API Stability** - Backward compatibility for public APIs
5. **Security by Design** - Security considered in every decision

## 📊 Key Technical Metrics

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Startup Time | <3s | 2.8s | ✅ |
| Memory (Idle) | <500MB | 450MB | ✅ |
| Test Coverage | >80% | 85% | ✅ |
| Build Time | <5min | 4min | ✅ |
| P95 Response | <100ms | 95ms | ✅ |

## 🔧 Technology Stack

### Core Platform
- **Platform:** NetBeans RCP 22.0
- **Language:** Java 17 (Temurin)
- **Build:** Maven 3.9.x
- **Modules:** NetBeans Module System (NBM)

### Web Technologies
- **JavaScript:** Chrome V8 via J2V8
- **TypeScript:** Native compiler integration
- **Language Servers:** LSP protocol support
- **Debugging:** Chrome DevTools Protocol

### Infrastructure
- **CI/CD:** GitHub Actions
- **Monitoring:** OpenTelemetry
- **Analytics:** Privacy-first telemetry
- **Distribution:** Platform-specific installers

## 🚀 Quick Links

- [GitHub Repository](https://github.com/NMOX/NMOX-Studio)
- [Build Status](https://github.com/NMOX/NMOX-Studio/actions)
- [Performance Dashboard](https://metrics.nmox.studio/performance)
- [Security Reports](./security/reports/)

## 📧 Engineering Contacts

- **Engineering Lead:** eng-lead@nmox.studio
- **Architecture:** architecture@nmox.studio
- **Security:** security@nmox.studio
- **DevOps:** devops@nmox.studio

---

*Last Updated: January 2025*
*Review Cycle: Bi-weekly*