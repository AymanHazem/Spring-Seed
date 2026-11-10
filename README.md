<h1 align="center">🌱 Spring Seed</h1>

<p align="center">
  <strong>Scaffold Spring Boot projects directly from your terminal.</strong>
</p>

---

**Spring Seed** is an interactive CLI tool for Linux, macOS, and WSL that scaffolds Spring Boot projects with [Spring Initializr](https://start.spring.io). Choose your build system, language, Spring Boot version, dependencies, and project metadata without opening a browser. Seed downloads the project, prepares your configuration, and optionally sets up `.env` files and Git.

---
## Features

| Feature | Description |
|---|---|
| **Non-interactive / Flags mode** | Scaffold projects in one command (e.g. `seed init my-app -d web,data-jpa --java 21`). |
| **Automatic update checks** | Lightweight 24-hour background check notifying you when a new release is available, or manually via `seed update`. |
| **Interactive wizard** | Numbered prompts for project type, language, Spring Boot version, metadata, packaging, and Java version. |
| **Fuzzy dependency search** | Search dependency names, IDs, and descriptions, with typo-tolerant matching and multi-selection. |
| **Live Initializr metadata** | Fetch available options from `start.spring.io`, with a one-hour local cache and fallback to valid cached metadata if a refresh fails. |
| **Custom output location** | Use `--path` to choose a parent directory; Seed creates a project folder named after your artifact. |
| **Overwrite protection** | Refuse to extract a project into a non-empty target directory. |
| **YAML configuration** | Choose `application.yml` instead of `application.properties`; generated property entries are migrated to YAML. |
| **`.env` bootstrapping** | Create `.env` and `.env.example`, ignore the local `.env` in Git, and configure Spring to load it. |
| **Optional Git setup** | Initialize a repository and create an `Initial commit`. |

---
## Quick Start

### Prerequisites

- Linux (x86_64 / arm64), macOS (Apple Silicon / Intel), or WSL
- Internet access
- Git installed and configured (optional, only for Git initialization)

No Java installation is needed: Seed ships as a self-contained native binary.

### Install

```bash
curl -fsSL https://raw.githubusercontent.com/AymanHazem/Spring-Seed/main/install.sh | sh
```

The installer detects your OS and CPU, downloads the matching binary from the latest [GitHub Release](https://github.com/AymanHazem/Spring-Seed/releases), verifies its SHA-256 checksum, installs it to `~/.local/bin/seed`, and adds that directory to your `PATH` if needed.

Options (set as environment variables on the `sh` side of the pipe):

```bash
# Install a specific version
curl -fsSL https://raw.githubusercontent.com/AymanHazem/Spring-Seed/main/install.sh | SEED_VERSION=v1.1.0 sh

# Install somewhere else, and don't touch shell rc files
curl -fsSL https://raw.githubusercontent.com/AymanHazem/Spring-Seed/main/install.sh | SEED_INSTALL_DIR="$HOME/bin" SEED_NO_MODIFY_PATH=1 sh
```

To uninstall, delete the binary and the metadata cache (`rm ~/.local/bin/seed && rm -rf ~/.cache/seed`), then remove the `# Added by Spring Seed installer` line from your shell rc file.

### Install from Source

```bash
# 1. Clone the repository
git clone https://github.com/AymanHazem/Spring-Seed.git
cd Spring-Seed

# 2. Build the native binary (requires GraalVM JDK 25 + native-image).
# Native compilation can take several minutes.
mvn -Pnative clean package

# 3. Install globally
sudo cp target/seed /usr/local/bin/seed
sudo chmod +x /usr/local/bin/seed
```

Building also requires Maven 3.9+ and GraalVM's native compiler toolchain for your platform. The resulting binary does not require a JVM at runtime.

Verify the installation:

```bash
seed --version
# seed 1.1.2
```

---

## Demo

https://github.com/user-attachments/assets/5fcc9d53-3d83-452a-86fc-dced7e001a88

## CLI Options

```bash
seed                                       # Start the interactive wizard
seed init my-app -d web,data-jpa --java 21 # One-shot generation (non-interactive)
seed update                                # Check for updates from GitHub Releases
seed -p ./projects                         # Create ./projects/<artifact>
seed --path /path/to/parent                # Choose a parent directory for the project
seed --refresh                             # Force re-fetch metadata (bypass the fresh cache)
seed --version                             # Print version
seed --help                                # Show help
```

### Non-Interactive Mode (One-Shot Project Generation)

Scaffold a project without prompts using `seed init` (or aliases `new`, `create`, or root options):

```bash
# Quick start: artifact name + dependencies
seed init order-service -d web,data-jpa,postgresql,lombok

# Full options example
seed init ecommerce-api \
  --dependencies web,data-jpa,postgresql,security,lombok \
  --type maven \
  --java 21 \
  --language java \
  --group-id com.mycompany \
  --name "E-Commerce Microservice" \
  --project-version 1.0.0-SNAPSHOT \
  --path ./test-projects \
  --git \
  --env

# Specify Java version, build tool, and metadata
seed init auth-service -d web,security -t maven-project -j 21 -g com.mycompany

# Use build tool shortcuts ('maven' or 'gradle')
seed init payments-api -d web -t maven -j 21

# Use standard aliases or direct command
seed new inventory-service -d web,mysql
seed my-quick-app -d web,devtools --java 21

# Enable Git & .env file generation
seed init notification-service -d web,mail --git --env

# Use properties instead of YAML (YAML is default)
seed init legacy-service -d web --properties
```

#### Flags Reference

| Flag | Short | Default | Description |
|---|---|---|---|
| `<artifact>` | | *metadata default* | Project artifact name (creates `./<artifact>`). |
| `-d`, `--dependencies`, `--deps` | `-d` | | Comma-separated dependencies (e.g. `web,data-jpa,postgresql`). |
| `-t`, `--type`, `--build` | `-t` | *metadata default* | Project type (`gradle-project`, `gradle-project-kotlin`, `maven-project`, or shortcuts `maven`, `gradle`, `gradle-kotlin`, `gradle-kts`). |
| `-j`, `--java`, `--java-version` | `-j` | *metadata default* | Java version (`17`, `21`, `25`, etc.). |
| `-b`, `--boot`, `--boot-version` | `-b` | *metadata default* | Spring Boot version. |
| `-l`, `--lang`, `--language` | `-l` | *metadata default* | Language (`java`, `kotlin`, `groovy`). |
| `-g`, `--group`, `--group-id` | `-g` | *metadata default* | Group ID. |
| `-n`, `--name` | `-n` | same as artifact | Project name. |
| `--description` | | *metadata default* | Project description. |
| `--package-name` | | `<groupId>.<artifact>` | Package name. |
| `--packaging` | | *metadata default* | Packaging (`jar`, `war`). |
| `-v`, `--project-version` | `-v` | *metadata default* | Project version. |
| `-p`, `--path` | `-p` | `.` (current directory) | Parent directory for the generated project. |
| `--yaml` | | enabled | Generate `application.yml` instead of `.properties`. |
| `--properties`, `--no-yaml` | | | Generate `application.properties` instead of `application.yml`. |
| `--env` / `--no-env` | | `false` | Generate `.env` and `.env.example` templates. |
| `--git` / `--no-git` | | `false` | Initialize a Git repository with an initial commit. |

> [!TIP]
> **Typo tolerance**: If you misspell a dependency (e.g. `-d postgres`), Seed automatically detects it and suggests the correct ID:
> `✗ Error: Unknown dependency: 'postgres'. Did you mean 'postgresql'?`

### Output directory behavior

**`--path` specifies the parent directory, not the final project directory.** For an artifact named `my-app`:

| Command | Project location |
|---|---|
| `seed` | `./my-app` |
| `seed -p ./projects` | `./projects/my-app` |
| `seed --path /workspace` | `/workspace/my-app` |

Relative paths are resolved from your current working directory. Missing parent directories are created during extraction. The parent may already contain other files; Seed writes into the artifact-named subdirectory.

> [!IMPORTANT]
> The final project directory must be new or empty. If it already contains files, Seed refuses to extract the project rather than overwriting them. Choose another artifact name or parent directory. Artifact names must be a single directory name: `/`, `\`, `.` and `..` are not allowed as paths.

### Wizard flow

1. Select a project type offered by Initializr, such as Maven or Gradle.
2. Choose the language and Spring Boot version.
3. Search for and select dependencies.
4. Enter the group, artifact, application name, description, project version, and package name.
5. Choose packaging and the generated project's Java version.
6. Review the resolved output directory.
7. Choose whether to use YAML, create `.env` files, and initialize Git.

Press **Enter** to accept defaults in single-choice and metadata prompts. The YAML, `.env`, and Git questions default to **Yes**; enter `n` or `no` to skip them.

The Java version selected in the wizard applies to the **generated application**. Seed itself requires JDK 25 when built or run as a JAR.

### Dependency search

The initial list features Web, Lombok, and DevTools when those IDs are available. Up to **12 results** are displayed per search; refine your query to find more specific matches. After toggling a dependency, the search resets to the featured list while retaining your selections.

**Controls:**
- Type a search query and press `Enter` to filter dependencies
- Enter a displayed number to toggle a dependency on/off
- Press `Enter` with empty input or type `:done` to confirm
- Type `:clear` to reset selections

### Automatic update checks

Seed periodically checks for new releases on GitHub in the background:
- **Periodic check**: Runs asynchronously once every 24 hours without slowing down command execution, caching state in `~/.cache/seed/update-check.json`. If a new version is available, a friendly upgrade banner is displayed.
- **Manual check**: Run `seed update` (or `seed update --force` to bypass cache) anytime to verify your version against GitHub Releases.
- **Opt-out**: Disable background checks in scripts or CI environments with `SEED_NO_UPDATE_CHECK=1` or `CI=true`.
---
## Configuration and `.env` Support

Choosing YAML converts generated property entries into `src/main/resources/application.yml` and removes `application.properties`. This format choice is applied whether or not you enable `.env` generation.

When `.env` generation is enabled, Seed prepares:

| File | Purpose |
|---|---|
| `.env` | Local configuration with commented example values. |
| `.env.example` | A template to share with teammates. |
| `.gitignore` | Adds `/.env` so the local file is excluded from Git. |

It also adds an optional Spring configuration import in the selected format:

```yaml
# src/main/resources/application.yml
spring:
  config:
    import: 'optional:file:.env[.properties]'
```

```properties
# src/main/resources/application.properties
spring.config.import=optional:file:.env[.properties]
```

> [!NOTE]
> Spring reads this `.env` file as a **Java properties file**, not as a shell script. Use `KEY=value` entries without `export`. The import is relative to the application's working directory, so run the generated application from its project root. A missing `.env` is allowed because the import is optional.

The supplied database variables are examples, not automatic datasource configuration. Reference your own values in Spring configuration, for example:

```properties
# .env
DATABASE_URL=jdbc:postgresql://localhost:5432/mydb
DATABASE_USERNAME=myuser
DATABASE_PASSWORD=replace-me
```

```yaml
# Add to the existing spring mapping in application.yml.
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
```

Keep real credentials in `.env`, and share only placeholder values in `.env.example`.

---
## Metadata Cache

| Setting | Value |
|---|---|
| Cache file | `~/.cache/seed/metadata.json` |
| Freshness period | **1 hour**, based on the cache file's modification time |
| Force refresh | `seed --refresh` or `seed -r` |

Seed validates metadata before using it. A fresh, valid cache avoids a metadata request. An expired, invalid, or missing cache triggers a fetch; `--refresh` also triggers a fetch regardless of age.

If a refresh fails and valid cached metadata is available, Seed falls back to that cache, even with `--refresh`. If neither the fetch nor the cache is usable, it reports an error.

**Caching does not make project generation offline:** downloading the project ZIP still requires access to `start.spring.io`.

---
### Project structure

```text
src/
├── main/
│   ├── java/dev/ayman/seed/
│   │   ├── SeedCli.java                  # Picocli entry point and CLI dispatch
│   │   ├── cli/
│   │   │   ├── InitCommand.java          # Non-interactive CLI command (aliases: new, create)
│   │   │   ├── NonInteractiveRunner.java # Scaffolds projects via CLI options
│   │   │   ├── ProjectOptions.java       # Picocli options mixin for project generation
│   │   │   └── UpdateCommand.java        # Manual update check command
│   │   ├── model/                        # Initializr metadata and update models
│   │   ├── service/
│   │   │   ├── MetadataService.java      # Fetch, validate, cache, and fallback
│   │   │   ├── ProjectGeneratorService.java # Download and extract project ZIPs
│   │   │   └── UpdateService.java        # GitHub Releases check, SemVer, and 24h caching
│   │   ├── wizard/
│   │   │   ├── WizardRunner.java         # Interactive flow and output resolution
│   │   │   ├── DependencySelector.java   # Dependency search and multi-selection
│   │   │   ├── MetadataForm.java         # Project metadata prompts
│   │   │   ├── ProjectConfig.java        # Selected project configuration
│   │   │   ├── SelectionPrompt.java      # Single-choice and yes/no prompts
│   │   │   └── WizardCancelledException.java # End-of-input cancellation
│   │   └── util/
│   │       ├── EnvFileWriter.java        # Config format, .env, and .gitignore setup
│   │       ├── FuzzyMatcher.java         # Dependency relevance ranking
│   │       ├── GitInitializer.java       # Repository and initial commit setup
│   │       └── TerminalUi.java           # Console color/NO_COLOR setup and summary output
│   └── resources/META-INF/native-image/  # GraalVM reflection configuration
└── test/java/dev/ayman/seed/              # JUnit tests
```

Seed uses **Picocli** for CLI parsing, **Jackson** for JSON, **Jansi** for terminal styling, and Java's HTTP client for Initializr requests.

---
## License

Spring Seed is available under the [MIT License](LICENSE). Author: [Ayman Hazem](https://github.com/AymanHazem).
