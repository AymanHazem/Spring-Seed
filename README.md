

<h1 align="center">🌱 Spring Seed</h1>

<p align="center">
  <strong>Plant your next Spring Boot application faster - right from your terminal.</strong>
</p>


---

**Spring Seed** is a fast, interactive CLI that initialize production-ready Spring Boot projects from your terminal. It connects to [start.spring.io](https://start.spring.io) in real time, walks you through every project option — type, language, version, dependencies, packaging, metadata — and generates a fully configured project in seconds. No browser needed.

Built as a single GraalVM native binary, Spring Seed starts instantly and runs anywhere.

---

## Features

| Feature | Description |
|---|---|
| **Interactive Wizard** | Step-by-step TUI guides you through project type, language, Spring Boot version, metadata, packaging, and Java version. |
| **Fuzzy Dependency Search** | Real-time fuzzy matching across the full Spring Initializr dependency catalog. Type to search, number to toggle. |
| **Live Metadata** | Always up-to-date — fetches live metadata from `start.spring.io` with intelligent local caching (1-hour TTL). |
| **YAML Config** | Optionally generates `application.yml` instead of `.properties`, normalized on disk. |
| **`.env` Bootstrapping** | Generates `.env` and `.env.example` files with auto-patched `spring.config.import` for seamless environment management. |
| **Git Ready** | One-step `git init` + initial commit so your project is version-controlled from the start. |
| **GraalVM Native Image** | Ships as a single, ahead-of-time compiled binary — instant startup, zero JVM overhead. |
| **Cross-platform** | Runs on Linux, macOS, and Windows (via WSL). |

---

## Quick Start

### Prerequisites

| Requirement | Why |
|---|---|
| Linux / macOS / WSL | Supported platforms |
| Internet access | Fetches metadata & project ZIP from `start.spring.io` |

> [!NOTE]
> Spring Seed does not have an official release yet. For now, you need to build from source.

### Install from Source

```bash
# 1. Clone the repository
git clone https://github.com/AymanHazem/Spring-Seed.git
cd Spring-Seed

# 2. Build the native binary (requires GraalVM JDK 25 + native-image)
mvn -Pnative clean package

# 3. Install globally
sudo cp target/seed /usr/local/bin/seed
sudo chmod +x /usr/local/bin/seed
```

Verify the installation:

```bash
seed --version
# seed 1.0.0
```

---

## Usage

### Launch the Interactive Wizard

```bash
seed
```

This starts the full guided experience:

```
   ███████╗██████╗ ██████╗ ██╗███╗   ██╗ ██████╗     ███████╗███████╗███████╗██████╗
   ██╔════╝██╔══██╗██╔══██╗██║████╗  ██║██╔════╝     ██╔════╝██╔════╝██╔════╝██╔══██╗
   ███████╗██████╔╝██████╔╝██║██╔██╗ ██║██║  ███╗    ███████╗█████╗  █████╗  ██║  ██║
   ╚════██║██╔═══╝ ██╔══██╗██║██║╚██╗██║██║   ██║    ╚════██║██╔══╝  ██╔══╝  ██║  ██║
   ███████║██║     ██║  ██║██║██║ ╚████║╚██████╔╝    ███████║███████╗███████╗██████╔╝
   ╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝ ╚═════╝     ╚══════╝╚══════╝╚══════╝╚═════╝
```

The wizard walks you through these steps:

| Step | What You Choose |
|---|---|
| 1 | **Project Type** — Maven or Gradle |
| 2 | **Language** — Java, Kotlin, or Groovy |
| 3 | **Spring Boot Version** — latest stable or snapshot |
| 4 | **Dependencies** — fuzzy search & multi-select |
| 5 | **Project Metadata** — group, artifact, name, description, package |
| 6 | **Packaging** — JAR or WAR |
| 7 | **Java Version** — 17, 21, 25, etc. |
| 8 | **Output Directory** — where to scaffold the project |
| 9 | **Config Format** — YAML or Properties |
| 10 | **`.env` Files** — generate environment templates |
| 11 | **Git Init** — initialize a repository with first commit |

### CLI Options

```bash
seed                 # Start the interactive wizard
seed --refresh       # Force re-fetch metadata (bypass cache)
seed --version       # Print version
seed --help          # Show help
```

### Dependency Search

The dependency selector supports real-time fuzzy search across the entire Spring Initializr catalog:

```
  ╔══════════════════════════════════════════╗
  ║      Dependency Search & Selection       ║
  ╚══════════════════════════════════════════╝
  Type to search  │  [number] to toggle  │  [Enter] to finish

  ─────────────────────────────────────────────────────────
   1.  ✔ Spring Web [web] · Web
       Build web, including RESTful, applications using Spring MVC…
   2.  ○ Spring Data JPA [data-jpa] · SQL
       Persist data in SQL stores with Java Persistence API…
   3.  ○ Lombok [lombok] · Developer Tools
       Java annotation library which helps to reduce boilerplate code…
  ─────────────────────────────────────────────────────────
  Selected:  web

  Search: _
```

**Controls:**
- Type to filter results in real time
- Enter a number to toggle a dependency on/off
- Press `Enter` with empty input or type `:done` to confirm
- Type `:clear` to reset selections

---

## `.env` Support

When you opt into `.env` generation, Seed creates:

| File | Purpose |
|---|---|
| `.env` | Local environment variables (git-ignored) |
| `.env.example` | Sanitized template for teammates to copy |

Seed also patches your Spring config to auto-load the `.env` file:

```yaml
# application.yml
spring:
  config:
    import: 'optional:file:.env[.properties]'
```

---

## Cache Behavior

Spring Seed caches Spring Initializr metadata locally to minimize network requests:

| Setting | Value |
|---|---|
| Cache file | `~/.cache/seed/metadata.json` |
| TTL | **1 hour** |
| Force refresh | `seed --refresh` |

---

## Build from Source

### Prerequisites

| Tool | Version |
|---|---|
| JDK | 25 (GraalVM distribution recommended) |
| `native-image` | Included with GraalVM |
| Maven | 3.9+ |
| Git | Any recent version |

```bash
# Verify your toolchain
java -version
mvn -version
native-image --version

# Build the native binary
mvn -Pnative clean package

# The binary is at: target/seed
```

> [!TIP]
> You can also build a standard fat JAR (no GraalVM required) and run it with `java -jar`:
> ```bash
> mvn clean package
> java -jar target/seed-1.0.0.jar
> ```

---

## Architecture

```
dev.ayman.seed
├── SeedCli.java                    # Entry point (Picocli @Command)
├── model/
│   ├── InitializrMetadata.java     # Root metadata model
│   ├── DependencyGroup.java        # Dependency category group
│   ├── Dependency.java             # Individual dependency
│   ├── Option.java                 # Generic selectable option
│   └── ProjectType.java            # Build system type
├── service/
│   ├── MetadataService.java        # Fetches & caches start.spring.io metadata
│   └── ProjectGeneratorService.java # Downloads & extracts project ZIP
├── wizard/
│   ├── WizardRunner.java           # Orchestrates the full interactive flow
│   ├── DependencySelector.java     # Fuzzy search + multi-select for deps
│   ├── MetadataForm.java           # Prompts for project metadata fields
│   ├── ProjectConfig.java          # Holds all user selections
│   └── SelectionPrompt.java        # Generic single-select prompt
└── util/
    ├── EnvFileWriter.java          # .env / .env.example generation
    ├── FuzzyMatcher.java           # Fuzzy string matching algorithm
    └── GitInitializer.java         # git init + initial commit
```

### Tech Stack

| Layer | Technology |
|---|---|
| CLI framework | [Picocli](https://picocli.info/) |
| Terminal UI | [Lanterna](https://github.com/mabe02/lanterna) |
| JSON parsing | [Jackson](https://github.com/FasterXML/jackson) |
| ANSI colors | [Jansi](https://github.com/fusesource/jansi) |
| Native compilation | [GraalVM Native Image](https://www.graalvm.org/native-image/) |
| Language | Java 25 |
| Build tool | Apache Maven |

---

<p align="center">
  <strong>Built with ☕ and 🌱 by <a href="https://github.com/AymanHazem">Ayman Hazem</a></strong>
</p>
