package dev.ayman.seed.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Creates .env / .env.example files and patches application config
 * to import the .env file via spring.config.import.
 */
public class EnvFileWriter {

    private static final String ENV_CONTENT = "# Environment variables for local development\n" +
            "# This file is excluded from version control — never commit secrets!\n\n" +
            "# Example:\n" +
            "# DATABASE_URL=jdbc:postgresql://localhost:5432/mydb\n" +
            "# DATABASE_USERNAME=myuser\n" +
            "# DATABASE_PASSWORD=secret\n";

    private static final String ENV_EXAMPLE_CONTENT = "# Copy this file to .env and fill in the values\n\n" +
            "# DATABASE_URL=jdbc:postgresql://localhost:5432/mydb\n" +
            "# DATABASE_USERNAME=myuser\n" +
            "# DATABASE_PASSWORD=secret\n";

    private static final String SPRING_CONFIG_IMPORT = "\n# Load .env file as optional external configuration\n" +
            "spring.config.import=optional:file:.env[.properties]\n";

    private static final String SPRING_CONFIG_IMPORT_YAML = "\nspring:\n  config:\n    import: 'optional:file:.env[.properties]'\n";

    private EnvFileWriter() {
    }

    /**
     * Creates .env and .env.example in projectRoot, then patches the Spring config
     * file.
     *
     * @param projectRoot root directory of the generated project
     * @param useYaml     true if the project uses application.yml, false for
     *                    .properties
     */
    public static void writeEnvFiles(Path projectRoot, boolean useYaml) throws IOException {
        // Create .env
        Path envFile = projectRoot.resolve(".env");
        Files.writeString(envFile, ENV_CONTENT, StandardOpenOption.CREATE_NEW);

        // Create .env.example
        Path envExampleFile = projectRoot.resolve(".env.example");
        Files.writeString(envExampleFile, ENV_EXAMPLE_CONTENT, StandardOpenOption.CREATE_NEW);

        // Patch application config
        patchSpringConfig(projectRoot, useYaml);
    }

    /**
     * Normalize the project's configuration format so that only the selected
     * format is present.
     *
     * If useYaml is true and an application.properties file exists, it is
     * removed and an empty application.yml is created (if needed). This ensures
     * that YAML is the single source of truth for configuration.
     */
    public static void normalizeConfigFormat(Path projectRoot, boolean useYaml) throws IOException {
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Path yaml = resourcesDir.resolve("application.yml");
        Path props = resourcesDir.resolve("application.properties");

        if (useYaml) {
            if (Files.exists(props)) {
                // In practice, the generated application.properties is either empty or
                // contains only comments, so it's safe to remove it and switch to YAML.
                Files.delete(props);
            }
            if (!Files.exists(yaml)) {
                Files.createDirectories(resourcesDir);
                Files.createFile(yaml);
            }
        }
    }

    private static void patchSpringConfig(Path projectRoot, boolean useYaml) throws IOException {
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Path yaml = resourcesDir.resolve("application.yml");
        Path props = resourcesDir.resolve("application.properties");

        // Prefer an existing config file, regardless of the user's format preference,
        // to avoid creating both YAML and .properties in a fresh project.
        if (Files.exists(yaml)) {
            Files.writeString(yaml, SPRING_CONFIG_IMPORT_YAML, StandardOpenOption.APPEND);
            return;
        }
        if (Files.exists(props)) {
            Files.writeString(props, SPRING_CONFIG_IMPORT, StandardOpenOption.APPEND);
            return;
        }

        // If no config file exists yet, create one in the preferred format.
        Files.createDirectories(resourcesDir);
        if (useYaml) {
            Files.writeString(yaml, SPRING_CONFIG_IMPORT_YAML, StandardOpenOption.CREATE_NEW);
        } else {
            Files.writeString(props, SPRING_CONFIG_IMPORT, StandardOpenOption.CREATE_NEW);
        }
    }
}
