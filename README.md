# Seed 🌱

Plant your next Spring Boot application faster with a powerful, interactive CLI.

## Why Seed?

Seed is built for developers who prefer working directly from the terminal.
It provides a faster, more flexible experience than browser-based generators,
with smart dependency search and optional environment bootstrapping.

## Features

- Interactive project wizard in terminal
- Real-Time and Up-to-Date Spring Initializr metadata
- Dependency picker with fuzzy search
- Interactive prompts for type, language, Spring Boot version, metadata, packaging, and Java version
- Ability to Generate YAML configuration mode (`application.yml`)
- Optional `.env` and `.env.example` generation
- Ability to initialize Git repository 

---

## Prerequisites

### For Users (Running the Binary)

- Linux / macOS (WSL works on Windows)

- Internet access


### For Developers (Building from Source)

- JDK 25

- GraalVM (Java 25) + native-image

- Maven 3.9+

---

## Run Seed in Your Terminal

Build and install the native binary once, then run `seed` from anywhere:
 - Seed doesn't have an official release yet,
 - For now, you'll need to build and install it manually. Sorry for the extra steps!

### 1. Verify your toolchain

```bash
java -version
mvn -version
git --version
native-image --version
```

### 2. Build the native binary

```bash
mvn -Pnative clean package
```

### 3. Install it globally

```bash
sudo cp target/seed /usr/local/bin/seed
sudo chmod +x /usr/local/bin/seed
```

You can now run `seed` from anywhere.
---

## CLI Usage

### Tool usage 

```bash
# Start the interactive tool
seed

# Regenerate metadata cache from network
seed --refresh

# Show command help
seed --help

# Show installed version
seed --version
```

---

## .env Support
When you opt into `.env` generation, your project will include:

- `.env`  local environment values (git-ignored)
- `.env.example`  sanitized template for your team

Spring will automatically load `.env` if present, via a config import line added to your `application.yml`:
```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```
---


## Cache Behavior

- Metadata cache file: `~/.cache/seed/metadata.json`
- Cache TTL: **1 hour**
- Use `--refresh` to force online metadata retrieval

