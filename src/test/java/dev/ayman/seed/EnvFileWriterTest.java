package dev.ayman.seed;

import dev.ayman.seed.util.EnvFileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnvFileWriterTest {

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
}
