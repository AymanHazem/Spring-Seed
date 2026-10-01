<h1 align="center">🌱 Spring Seed</h1>

<p align="center">
  <strong>Plant your next Spring Boot application faster — right from your terminal.</strong>
</p>

---

**Spring Seed** is an interactive CLI for scaffolding Spring Boot projects with [Spring Initializr](https://start.spring.io). Choose your build system, language, Spring Boot version, dependencies, and project metadata without opening a browser. Seed downloads the project, prepares your configuration, and optionally sets up `.env` files and Git.

Run it as a standalone **GraalVM native executable** or a **Java JAR**.

## Features

| Feature | Description |
|---|---|
| **Interactive wizard** | Numbered prompts for project type, language, Spring Boot version, metadata, packaging, and Java version. |
| **Fuzzy dependency search** | Search dependency names, IDs, and descriptions, with typo-tolerant matching and multi-selection. |
| **Live Initializr metadata** | Fetch available options from `start.spring.io`, with a one-hour local cache and fallback to valid cached metadata if a refresh fails. |
| **Custom output location** | Use `--path` to choose a parent directory; Seed creates a project folder named after your artifact. |
| **Overwrite protection** | Refuse to extract a project into a non-empty target directory. |
| **YAML configuration** | Choose `application.yml` instead of `application.properties`; generated property entries are migrated to YAML. |
| **`.env` bootstrapping** | Create `.env` and `.env.example`, ignore the local `.env` in Git, and configure Spring to load it. |
| **Optional Git setup** | Initialize a repository and create an `Initial commit`. |

## Quick Start

### Prerequisites

- Linux / macOS / WSL
- Internet access

> [!NOTE]
> For now, build Spring Seed from source using the instructions below.

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
# seed 1.0.0
```

---

## Demo

https://github.com/user-attachments/assets/5fcc9d53-3d83-452a-86fc-dced7e001a88

### CLI Options

```bash
seed                         # Start the interactive wizard
seed -p ./projects           # Create ./projects/<artifact>
seed --path /path/to/parent  # Choose a parent directory for the project
seed --refresh              # Force re-fetch metadata (bypass the fresh cache)
seed --version              # Print version
seed --help                 # Show help
```

**Controls:**
- Type a search query and press `Enter` to filter dependencies
- Enter a displayed number to toggle a dependency on/off
- Press `Enter` with empty input or type `:done` to confirm
- Type `:clear` to reset selections

### Output directory behavior

**`--path` specifies the parent directory, not the final project directory.** For an artifact named `my-app`:

| Command | Project location |
|---|---|
| `seed` | `~/my-app` |
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

### Spring Boot versions and Gradle

For Gradle projects, Seed hides SNAPSHOT and milestone Spring Boot versions when other versions are available, as a workaround for upstream Initializr Gradle/BOM resolution failures. Maven keeps the full version list.

If Initializr returns a generation error, Seed displays the server's error message. Gradle BOM resolution failures also include a suggestion to try Maven or retry later.

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

## Git Setup

When enabled, Seed runs `git init`, `git add .`, and `git commit -m "Initial commit"` in the generated project directory. `.env` is ignored before these commands run.

Git must be installed and a commit identity must be configured. Check your identity before choosing Git initialization:

```bash
git config user.name
git config user.email
```

If either value is missing, configure Git or answer `n` to the Git prompt. A Git setup failure is reported as an error; the generated project files remain on disk.

## Metadata Cache

| Setting | Value |
|---|---|
| Metadata endpoint | `https://start.spring.io/metadata/client` |
| Cache file | `~/.cache/seed/metadata.json` |
| Freshness period | **1 hour**, based on the cache file's modification time |
| Force refresh | `seed --refresh` or `seed -r` |

Seed validates metadata before using it. A fresh, valid cache avoids a metadata request. An expired, invalid, or missing cache triggers a fetch; `--refresh` also triggers a fetch regardless of age.

If a refresh fails and valid cached metadata is available, Seed falls back to that cache, even with `--refresh`. If neither the fetch nor the cache is usable, it reports an error.

**Caching does not make project generation offline:** downloading the project ZIP still requires access to `start.spring.io`.

## Development

```bash
# Run the unit tests.
mvn test

# Build the executable JAR, including tests.
mvn clean package

# Build the native executable, including tests.
mvn -Pnative clean package
```

> [!TIP]
> You can also build a standard fat JAR (requires JDK 25, but no GraalVM) and run it with `java -jar`:
> ```bash
> mvn clean package
> java -jar target/seed-1.0.0.jar
> ```
> The JAR accepts the same CLI options, including `--path` and `--refresh`.

The test suite covers metadata parsing and validation, cache behavior, CLI options, prompts, dependency search, configuration generation, output-path resolution, and project ZIP extraction.

### Project structure

```text
src/
├── main/
│   ├── java/dev/ayman/seed/
│   │   ├── SeedCli.java                  # Picocli entry point and exit handling
│   │   ├── model/                        # Initializr metadata models
│   │   ├── service/
│   │   │   ├── MetadataService.java      # Fetch, validate, cache, and fallback
│   │   │   └── ProjectGeneratorService.java # Download and extract project ZIPs
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
│   │       └── GitInitializer.java       # Repository and initial commit setup
│   └── resources/META-INF/native-image/  # GraalVM reflection configuration
└── test/java/dev/ayman/seed/              # JUnit tests
```

Seed uses **Picocli** for CLI parsing, **Jackson** for JSON, **Jansi** for terminal styling, and Java's HTTP client for Initializr requests.

### Troubleshooting

- **Target directory is not empty:** choose a different artifact name or parent directory. Seed does not merge projects into existing directories.
- **Git initialization fails:** verify that Git is installed and `user.name` / `user.email` are configured. You can initialize Git manually in the generated project afterward.
- **Generation fails with a Gradle BOM error:** try Maven or retry later; the generation service may be unable to resolve the requested version.
- **Need a stack trace:** run the JAR with `java -Dseed.debug=true -jar target/seed-1.0.0.jar`.

## License

Spring Seed is available under the [MIT License](LICENSE).

---

<p align="center">
  <strong>Built with ☕ and 🌱 by <a href="https://github.com/AymanHazem">Ayman Hazem</a></strong>
</p>
