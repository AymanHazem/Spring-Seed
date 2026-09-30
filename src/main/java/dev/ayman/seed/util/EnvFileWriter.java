package dev.ayman.seed.util;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;
/**
 * Creates .env / .env.example files and patches application config
 * to import the .env file via spring.config.import.
 */
public class EnvFileWriter
{
    private static final String ENV_CONTENT = """
            # Environment variables for local development
            # This file is excluded from version control — never commit secrets!
            
            # Example:
            # DATABASE_URL=jdbc:postgresql://localhost:5432/mydb
            # DATABASE_USERNAME=myuser
            # DATABASE_PASSWORD=secret
            """;

    private static final String ENV_EXAMPLE_CONTENT = """
            # Copy this file to .env and fill in the values
            
            # DATABASE_URL=jdbc:postgresql://localhost:5432/mydb
            # DATABASE_USERNAME=myuser
            # DATABASE_PASSWORD=secret
            """;

    private static final String SPRING_CONFIG_IMPORT = """
            
            # Load .env file as optional external configuration
            spring.config.import=optional:file:.env[.properties]
            """;

    private static final String SPRING_CONFIG_IMPORT_YAML = "\nspring:\n  config:\n    import: 'optional:file:.env[.properties]'\n";

    private EnvFileWriter() {}

    /**
     * Creates .env and .env.example in projectRoot, then patches the Spring config
     * file.
     *
     * @param projectRoot root directory of the generated project
     * @param useYaml     true if the project uses application.yml, false for
     *                    .properties
     */
    public static void writeEnvFiles(Path projectRoot, boolean useYaml) throws IOException
    {
        ensureEnvIsIgnored(projectRoot);

        // Create .env — never overwrite an existing one, since it may already
        // contain real secrets from a previous run.
        Path envFile = projectRoot.resolve(".env");
        if (!Files.exists(envFile))
        {
            Files.writeString(envFile, ENV_CONTENT, StandardOpenOption.CREATE);
        }

        // Create/refresh .env.example — safe to overwrite, it's just a template
        // with no real values.
        Path envExampleFile = projectRoot.resolve(".env.example");
        Files.writeString(envExampleFile, ENV_EXAMPLE_CONTENT, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        // Patch application config
        patchSpringConfig(projectRoot, useYaml);
    }

    /**
     * Normalize the project's configuration format so that only the selected
     * format is present on disk.
     * If useYaml is true and an application.properties file exists, any real
     * (non-comment, non-blank) lines it contains are converted to YAML and
     * appended to application.yml, then application.properties is removed —
     * so no configuration is lost, and the project ends up with exactly one
     * config file matching the user's choice (no leftover .bak file).
     */
    public static void normalizeConfigFormat(Path projectRoot, boolean useYaml) throws IOException
    {
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Path yaml = resourcesDir.resolve("application.yml");
        Path props = resourcesDir.resolve("application.properties");

        if (useYaml)
        {
            if (Files.exists(props))
            {
                List<String> meaningfulLines = meaningfulPropertyLines(props);
                if (!meaningfulLines.isEmpty())
                {
                    // Migrate real configuration into YAML instead of discarding it.
                    String block = meaningfulLines.stream()
                            .map(EnvFileWriter::propertyLineToYaml)
                            .collect(Collectors.joining("\n", "", "\n"));
                    Files.writeString(yaml, block, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
                Files.delete(props);
            }
            if (!Files.exists(yaml))
            {
                Files.createDirectories(resourcesDir);
                Files.createFile(yaml);
            }
        }
    }

    /**
     * Returns the non-blank, non-comment ("#"/"!" prefix) lines of a .properties
     * file, trimmed.
     */
    private static List<String> meaningfulPropertyLines(Path file) throws IOException
    {
        try (var lines = Files.lines(file))
        {
            return lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#") && !line.startsWith("!"))
                    .toList();
        }
    }

    /**
     * Converts a single "key=value" (or "key:value") .properties line into an
     * equivalent flat YAML mapping entry, e.g. "server.port=8080" becomes
     * server.port: "8080". The value is always double-quoted so arbitrary
     * property values (colons, special characters, etc.) remain valid YAML;
     * Spring's relaxed binding handles quoted scalars the same as unquoted ones.
     */
    private static String propertyLineToYaml(String propertyLine)
    {
        int separator = indexOfKeyValueSeparator(propertyLine);
        String key = (separator >= 0 ? propertyLine.substring(0, separator) : propertyLine).trim();
        String value = separator >= 0 ? propertyLine.substring(separator + 1).trim() : "";
        String escapedValue = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return key + ": \"" + escapedValue + "\"";
    }

    private static int indexOfKeyValueSeparator(String line)
    {
        int eq = line.indexOf('=');
        int colon = line.indexOf(':');
        if (eq < 0)
            return colon;
        if (colon < 0)
            return eq;
        return Math.min(eq, colon);
    }

    /**
     * Appends the given block to the file unless it already contains marker,
     * so calling patchSpringConfig repeatedly (e.g. re-running the wizard
     * against the same output directory) doesn't keep duplicating the block.
     */
    private static void appendIfMissing(Path file, String block) throws IOException
    {
        String existing = Files.readString(file);
        if (existing.contains("spring.config.import"))
            return;
        Files.writeString(file, block, StandardOpenOption.APPEND);
    }

    private static void ensureEnvIsIgnored(Path projectRoot) throws IOException
    {
        Path gitignore = projectRoot.resolve(".gitignore");
        String existing = Files.exists(gitignore) ? Files.readString(gitignore) : "";
        boolean alreadyIgnored = existing.lines()
                .map(String::trim)
                .anyMatch("/.env"::equals);

        if (alreadyIgnored)
            return;

        String leadingNewline = existing.isEmpty() || existing.endsWith("\n") || existing.endsWith("\r")
                ? ""
                : System.lineSeparator();
        Files.writeString(gitignore, leadingNewline + "/.env" + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void patchSpringConfig(Path projectRoot, boolean useYaml) throws IOException
    {
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Path yaml = resourcesDir.resolve("application.yml");
        Path props = resourcesDir.resolve("application.properties");

        // Prefer an existing config file, regardless of the user's format preference,
        // to avoid creating both YAML and .properties in a fresh project.
        if (Files.exists(yaml))
        {
            appendIfMissing(yaml, SPRING_CONFIG_IMPORT_YAML);
            return;
        }
        if (Files.exists(props))
        {
            appendIfMissing(props, SPRING_CONFIG_IMPORT);
            return;
        }

        // If no config file exists yet, create one in the preferred format.
        Files.createDirectories(resourcesDir);
        if (useYaml)
            Files.writeString(yaml, SPRING_CONFIG_IMPORT_YAML, StandardOpenOption.CREATE_NEW);
        else
            Files.writeString(props, SPRING_CONFIG_IMPORT, StandardOpenOption.CREATE_NEW);
    }
}
