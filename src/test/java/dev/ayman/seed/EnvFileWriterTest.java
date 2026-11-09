package dev.ayman.seed;

import dev.ayman.seed.util.EnvFileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvFileWriterTest {

    private static Map<String, Object> loadYamlAsFlattenedMap(String yamlContent) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Yaml yaml = new Yaml(options);
        Object loaded = yaml.load(yamlContent);
        Map<String, Object> flattened = new LinkedHashMap<>();
        if (loaded instanceof Map<?, ?> map) {
            flattenMap("", map, flattened);
        }
        return flattened;
    }

    private static void flattenMap(String prefix, Map<?, ?> source, Map<String, Object> target) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey().toString() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> nested) {
                flattenMap(key, nested, target);
            } else {
                target.put(key, entry.getValue());
            }
        }
    }

    @Test
    void normalizeConfigFormatMigratesNonTrivialPropertiesIntoYamlInsteadOfDeletingOrBackingUp(
            @TempDir Path projectRoot) throws Exception {
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path props = resourcesDir.resolve("application.properties");
        Files.writeString(props, "spring.datasource.url=jdbc:postgresql://localhost/mydb\n");

        EnvFileWriter.normalizeConfigFormat(projectRoot, true);

        assertFalse(Files.exists(props), "application.properties should be removed once migrated");
        assertFalse(Files.exists(resourcesDir.resolve("application.properties.bak")),
                "no backup file should be left behind — content is migrated into application.yml instead");

        Path yaml = resourcesDir.resolve("application.yml");
        assertTrue(Files.exists(yaml));
        String yamlContent = Files.readString(yaml);
        assertTrue(yamlContent.contains("spring.datasource.url:"), "the property key must be preserved");
        assertTrue(yamlContent.contains("jdbc:postgresql://localhost/mydb"), "the property value must be preserved");
    }

    @Test
    void normalizeConfigFormatDeletesEmptyOrCommentOnlyProperties(@TempDir Path projectRoot) throws Exception {
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path props = resourcesDir.resolve("application.properties");
        Files.writeString(props, "# just a comment\n\n! another comment\n");

        EnvFileWriter.normalizeConfigFormat(projectRoot, true);

        assertFalse(Files.exists(props));
        assertFalse(Files.exists(resourcesDir.resolve("application.properties.bak")),
                "comment-only files should not need a backup");
        assertTrue(Files.exists(resourcesDir.resolve("application.yml")));
    }

    @Test
    void normalizeConfigFormatLeavesOnlyOneConfigFileAfterMigration(@TempDir Path projectRoot) throws Exception {
        // Regression test: after switching to YAML, exactly one config file
        // (application.yml) should remain — no application.properties, no .bak.
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Files.writeString(resourcesDir.resolve("application.properties"),
                "server.port=8080\nspring.application.name=demo\n");

        EnvFileWriter.normalizeConfigFormat(projectRoot, true);

        try (var files = Files.list(resourcesDir)) {
            List<String> remaining = files.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(List.of("application.yml"), remaining,
                    "exactly one config file should remain after migrating to YAML");
        }
    }

    @Test
    void normalizeConfigFormatLeavesPropertiesAloneWhenNotUsingYaml(@TempDir Path projectRoot) throws Exception {
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);
        Path props = resourcesDir.resolve("application.properties");
        Files.writeString(props, "server.port=8080\n");

        EnvFileWriter.normalizeConfigFormat(projectRoot, false);

        assertTrue(Files.exists(props));
        assertEquals("server.port=8080\n", Files.readString(props));
    }

    @Test
    void writeEnvFilesIsSafeToRunTwice(@TempDir Path projectRoot) throws Exception {
        // Regression test for Rank 3: re-running against the same output directory
        // used to throw FileAlreadyExistsException.
        assertDoesNotThrow(() -> EnvFileWriter.writeEnvFiles(projectRoot, false));
        assertDoesNotThrow(() -> EnvFileWriter.writeEnvFiles(projectRoot, false));

        assertTrue(Files.exists(projectRoot.resolve(".env")));
        assertTrue(Files.exists(projectRoot.resolve(".env.example")));
    }

    @Test
    void writeEnvFilesDoesNotOverwriteExistingEnvSecrets(@TempDir Path projectRoot) throws Exception {
        Path envFile = projectRoot.resolve(".env");
        Files.writeString(envFile, "DATABASE_PASSWORD=super-secret\n");

        EnvFileWriter.writeEnvFiles(projectRoot, false);

        assertEquals("DATABASE_PASSWORD=super-secret\n", Files.readString(envFile),
                "existing .env content must never be overwritten");
    }

    @Test
    void writeEnvFilesTwiceDoesNotDuplicateSpringConfigImport(@TempDir Path projectRoot) throws Exception {
        EnvFileWriter.writeEnvFiles(projectRoot, false);
        EnvFileWriter.writeEnvFiles(projectRoot, false);

        Path props = projectRoot.resolve("src/main/resources/application.properties");
        String content = Files.readString(props);
        int firstIndex = content.indexOf("spring.config.import");
        int lastIndex = content.lastIndexOf("spring.config.import");
        assertTrue(firstIndex >= 0, "spring.config.import should be present");
        assertEquals(firstIndex, lastIndex, "spring.config.import block must not be duplicated on repeat runs");
    }

    @Test
    void writeEnvFilesTwiceDoesNotDuplicateYamlImport(@TempDir Path projectRoot) throws Exception {
        EnvFileWriter.writeEnvFiles(projectRoot, true);
        EnvFileWriter.writeEnvFiles(projectRoot, true);

        Path yaml = projectRoot.resolve("src/main/resources/application.yml");
        String content = Files.readString(yaml);
        int firstIndex = content.indexOf("spring.config.import");
        int lastIndex = content.lastIndexOf("spring.config.import");
        assertTrue(firstIndex >= 0, "spring.config.import should be present in yaml");
        assertEquals(firstIndex, lastIndex, "spring.config.import block must not be duplicated in yaml on repeat runs");

        Map<String, Object> map = loadYamlAsFlattenedMap(content);
        assertEquals("optional:file:.env[.properties]", map.get("spring.config.import"));
    }

    @Test
    void yamlImportMergesWithExistingNestedSpringMapping(@TempDir Path projectRoot) throws Exception {
        Path resources = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resources);
        Path yaml = resources.resolve("application.yml");
        String initialYaml = """
                spring:
                  application:
                    name: demo
                """;
        Files.writeString(yaml, initialYaml);

        EnvFileWriter.writeEnvFiles(projectRoot, true);

        String updatedContent = Files.readString(yaml);
        // Loads like Spring (disallowing duplicate keys)
        Map<String, Object> map = loadYamlAsFlattenedMap(updatedContent);
        assertEquals("demo", map.get("spring.application.name"));
        assertEquals("optional:file:.env[.properties]", map.get("spring.config.import"));
    }

    @Test
    void existingYamlImportIsLeftUntouched(@TempDir Path projectRoot) throws Exception {
        Path resources = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resources);
        Path yaml = resources.resolve("application.yml");
        String initialYaml = """
                spring:
                  config:
                    import: optional:configserver:http://localhost:8888
                """;
        Files.writeString(yaml, initialYaml);

        EnvFileWriter.writeEnvFiles(projectRoot, true);

        assertEquals(initialYaml, Files.readString(yaml), "existing import must not be touched or duplicated");
    }

    @Test
    void freshProjectYamlLoadsLikeSpring(@TempDir Path projectRoot) throws Exception {
        Path resources = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resources);
        Path props = resources.resolve("application.properties");
        Files.writeString(props, "spring.application.name=orders\nserver.port=8080\n");

        EnvFileWriter.normalizeConfigFormat(projectRoot, true);
        EnvFileWriter.writeEnvFiles(projectRoot, true);

        Path yaml = resources.resolve("application.yml");
        assertTrue(Files.exists(yaml));
        String content = Files.readString(yaml);

        Map<String, Object> map = loadYamlAsFlattenedMap(content);
        assertEquals("orders", map.get("spring.application.name"));
        assertEquals("8080", map.get("server.port"));
        assertEquals("optional:file:.env[.properties]", map.get("spring.config.import"));
    }

    @Test
    void multiDocumentYamlGetsImportInFirstDocument(@TempDir Path projectRoot) throws Exception {
        Path resources = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resources);
        Path yaml = resources.resolve("application.yml");
        String multiDoc = """
                ---
                spring:
                  config:
                    activate:
                      on-profile: prod
                  datasource:
                    url: jdbc:postgresql://prod:5432/db
                """;
        Files.writeString(yaml, multiDoc);

        EnvFileWriter.writeEnvFiles(projectRoot, true);

        String result = Files.readString(yaml);
        int importIdx = result.indexOf("spring.config.import");
        int docMarkerIdx = result.indexOf("---");
        assertTrue(importIdx >= 0);
        assertTrue(importIdx < docMarkerIdx, "Import must be placed in the first/default document before profile docs");
    }

    @Test
    void commentMentioningImportDoesNotBlockPropertiesImport(@TempDir Path projectRoot) throws Exception {
        Path resources = projectRoot.resolve("src/main/resources");
        Files.createDirectories(resources);
        Path props = resources.resolve("application.properties");
        Files.writeString(props, "# see spring.config.import documentation\nserver.port=8080\n");

        EnvFileWriter.writeEnvFiles(projectRoot, false);

        String content = Files.readString(props);
        assertTrue(EnvFileWriter.propertiesDefinesImport(content));
        // Verify real property was appended
        assertTrue(content.contains("spring.config.import=optional:file:.env[.properties]"));
    }

    @Test
    void yamlDefinesImportCases() {
        assertTrue(EnvFileWriter.yamlDefinesImport("spring.config.import: optional:file:.env[.properties]"));
        assertTrue(EnvFileWriter.yamlDefinesImport("""
                spring:
                  config:
                    import: 'optional:file:.env[.properties]'
                """));
        assertTrue(EnvFileWriter.yamlDefinesImport("""
                spring:
                  config.import: 'optional:file:.env[.properties]'
                """));
        assertTrue(EnvFileWriter.yamlDefinesImport("""
                spring.config:
                  import: 'optional:file:.env[.properties]'
                """));
        assertTrue(EnvFileWriter.yamlDefinesImport("""
                spring.config.import[0]: 'optional:file:.env[.properties]'
                """));

        assertFalse(EnvFileWriter.yamlDefinesImport("# spring.config.import: optional:file:.env[.properties]"));
        assertFalse(EnvFileWriter.yamlDefinesImport("""
                description: |
                  spring.config.import: foo
                """));
        assertFalse(EnvFileWriter.yamlDefinesImport("""
                description: >
                  spring.config.import: foo
                """));
        assertFalse(EnvFileWriter.yamlDefinesImport("spring.application.name: demo"));
    }

    @Test
    void propertiesDefinesImportCases() {
        assertTrue(EnvFileWriter.propertiesDefinesImport("spring.config.import=optional:file:.env[.properties]"));
        assertTrue(EnvFileWriter.propertiesDefinesImport("spring.config.import: optional:file:.env[.properties]"));
        assertTrue(EnvFileWriter.propertiesDefinesImport("spring.config.import[0]=optional:file:.env[.properties]"));
        assertFalse(EnvFileWriter.propertiesDefinesImport("# spring.config.import=something"));
        assertFalse(EnvFileWriter.propertiesDefinesImport("! spring.config.import=something"));
        assertFalse(EnvFileWriter.propertiesDefinesImport("server.port=8080"));
    }
}
