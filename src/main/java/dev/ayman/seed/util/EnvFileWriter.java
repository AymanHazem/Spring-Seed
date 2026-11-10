package dev.ayman.seed.util;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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
            # This file is excluded from version control. Never commit secrets.
            
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

    private static final String SPRING_CONFIG_IMPORT_YAML = """
            # Load .env file as optional external configuration
            spring.config.import: "optional:file:.env[.properties]"
            """;

    private static final String IMPORT_KEY = "spring.config.import";

    private EnvFileWriter() {}

    public static void writeEnvFiles(Path projectRoot, boolean useYaml) throws IOException
    {
        ensureEnvIsIgnored(projectRoot);

        // Preserve an existing .env because it may hold real secrets.
        Path envFile = projectRoot.resolve(".env");
        if (!Files.exists(envFile))
        {
            Files.writeString(envFile, ENV_CONTENT, StandardOpenOption.CREATE);
        }

        Path envExampleFile = projectRoot.resolve(".env.example");
        Files.writeString(envExampleFile, ENV_EXAMPLE_CONTENT, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        patchSpringConfig(projectRoot, useYaml);
    }

    /**
     * Migrates application.properties entries into application.yml when YAML is selected
     * and deletes application.properties so only one config file remains.
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
     * Converts a "key=value" property line to YAML, double-quoting values so
     * special characters remain valid under Spring relaxed binding.
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
        int sep = -1;
        if (eq >= 0 && colon >= 0)
            sep = Math.min(eq, colon);
        else if (eq >= 0)
            sep = eq;
        else if (colon >= 0)
            sep = colon;

        if (sep < 0)
        {
            for (int i = 0; i < line.length(); i++)
            {
                if (Character.isWhitespace(line.charAt(i)))
                    return i;
            }
        }
        return sep;
    }

    /**
     * Checks if the given YAML content defines spring.config.import
     * (flat key, nested mapping, mixed, or indexed array key e.g. spring.config.import[0]).
     * Ignores comments (#) and block scalars (| or >).
     */
    public static boolean yamlDefinesImport(String yaml)
    {
        if (yaml == null || yaml.isBlank())
            return false;

        List<IndentEntry> stack = new ArrayList<>();
        boolean inBlockScalar = false;
        int blockScalarIndent = -1;

        String[] lines = yaml.split("\\R");
        for (String rawLine : lines)
        {
            if (rawLine.isBlank())
                continue;

            int indent = getIndent(rawLine);
            String trimmed = rawLine.trim();

            if (inBlockScalar)
            {
                if (indent > blockScalarIndent)
                    continue;
                inBlockScalar = false;
                blockScalarIndent = -1;
            }

            if (trimmed.startsWith("#"))
                continue;

            if (trimmed.equals("---") || trimmed.equals("..."))
            {
                stack.clear();
                continue;
            }

            if (trimmed.startsWith("-"))
                continue;

            int colonIdx = trimmed.indexOf(':');
            if (colonIdx <= 0)
                continue;

            String keyPart = trimmed.substring(0, colonIdx).trim();
            if ((keyPart.startsWith("\"") && keyPart.endsWith("\"")) ||
                (keyPart.startsWith("'") && keyPart.endsWith("'")))
            {
                keyPart = keyPart.substring(1, keyPart.length() - 1);
            }

            while (!stack.isEmpty() && stack.getLast().indent >= indent)
            {
                stack.removeLast();
            }

            String currentPath = stack.isEmpty()
                    ? keyPart
                    : stack.getLast().path + "." + keyPart;

            if (isImportKey(currentPath))
                return true;

            String valuePart = trimmed.substring(colonIdx + 1).trim();
            if (valuePart.startsWith("|") || valuePart.startsWith(">"))
            {
                inBlockScalar = true;
                blockScalarIndent = indent;
            }
            else
            {
                stack.add(new IndentEntry(indent, currentPath));
            }
        }

        return false;
    }

    public static boolean propertiesDefinesImport(String properties)
    {
        if (properties == null || properties.isBlank())
            return false;

        String[] lines = properties.split("\\R");
        for (String line : lines)
        {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!"))
                continue;

            int sep = indexOfKeyValueSeparator(trimmed);
            String key = (sep >= 0 ? trimmed.substring(0, sep) : trimmed).trim();
            if (isImportKey(key))
                return true;
        }
        return false;
    }

    private static boolean isImportKey(String path)
    {
        return IMPORT_KEY.equals(path) || path.startsWith(IMPORT_KEY + "[");
    }

    private static int getIndent(String line)
    {
        int count = 0;
        while (count < line.length() && (line.charAt(count) == ' ' || line.charAt(count) == '\t'))
            count += (line.charAt(count) == '\t' ? 2 : 1);
        return count;
    }

    private record IndentEntry(int indent, String path) {}

    private static void appendToYamlIfMissing(Path file, String block) throws IOException
    {
        String existing = Files.readString(file);
        if (yamlDefinesImport(existing))
            return;

        // If multi-document YAML (contains "---"), prepend the block to document 1
        if (existing.lines().anyMatch(l -> l.trim().equals("---")))
        {
            String newContent = block.stripLeading() + (existing.startsWith("\n") ? "" : "\n") + existing;
            Files.writeString(file, newContent);
        }
        else
        {
            String formatted = formatBlockToAppend(existing, block);
            Files.writeString(file, formatted, StandardOpenOption.APPEND);
        }
    }

    private static void appendToPropsIfMissing(Path file, String block) throws IOException
    {
        String existing = Files.readString(file);
        if (propertiesDefinesImport(existing))
            return;

        String formatted = formatBlockToAppend(existing, block);
        Files.writeString(file, formatted, StandardOpenOption.APPEND);
    }

    private static String formatBlockToAppend(String existing, String block)
    {
        if (existing.isEmpty())
            return block;
        StringBuilder sb = new StringBuilder();
        if (!existing.endsWith("\n") && !existing.endsWith("\r"))
            sb.append(System.lineSeparator());
        if (!existing.endsWith("\n\n") && !existing.endsWith("\r\n\r\n"))
            sb.append(System.lineSeparator());
        sb.append(block);
        return sb.toString();
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
            appendToYamlIfMissing(yaml, SPRING_CONFIG_IMPORT_YAML);
            return;
        }
        if (Files.exists(props))
        {
            appendToPropsIfMissing(props, SPRING_CONFIG_IMPORT);
            return;
        }

        Files.createDirectories(resourcesDir);
        if (useYaml)
            Files.writeString(yaml, SPRING_CONFIG_IMPORT_YAML, StandardOpenOption.CREATE_NEW);
        else
            Files.writeString(props, SPRING_CONFIG_IMPORT, StandardOpenOption.CREATE_NEW);
    }
}
