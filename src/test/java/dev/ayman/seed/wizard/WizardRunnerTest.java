package dev.ayman.seed.wizard;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.model.InitializrMetadata;
import dev.ayman.seed.model.Option;
import dev.ayman.seed.service.MetadataService;
import dev.ayman.seed.service.ProjectGeneratorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WizardRunnerTest {

    private static final String FIXTURE_METADATA_JSON = """
            {
              "type": {
                "type": "action",
                "default": "gradle-project",
                "values": [
                  {"id": "gradle-project", "name": "Gradle - Groovy", "action": "/starter.zip",
                   "tags": {"build": "gradle", "dialect": "groovy", "format": "project"}},
                  {"id": "maven-project",  "name": "Maven",           "action": "/starter.zip",
                   "tags": {"build": "maven", "format": "project"}}
                ]
              },
              "language": {
                "type": "single-select",
                "default": "java",
                "values": [
                  {"id": "java",   "name": "Java"},
                  {"id": "kotlin", "name": "Kotlin"}
                ]
              },
              "bootVersion": {
                "type": "single-select",
                "default": "4.0.3.RELEASE",
                "values": [
                  {"id": "4.0.3.RELEASE",         "name": "4.0.3"},
                  {"id": "4.1.0.BUILD-SNAPSHOT",   "name": "4.1.0 (SNAPSHOT)"}
                ]
              },
              "packaging": {
                "type": "single-select",
                "default": "jar",
                "values": [
                  {"id": "jar", "name": "Jar"},
                  {"id": "war", "name": "War"}
                ]
              },
              "javaVersion": {
                "type": "single-select",
                "default": "17",
                "values": [
                  {"id": "21", "name": "21"},
                  {"id": "17", "name": "17"}
                ]
              },
              "groupId":    {"default": "com.example"},
              "artifactId": {"default": "demo"},
              "version":    {"default": "0.0.1-SNAPSHOT"},
              "name":       {"default": "demo"},
              "description":{"default": "Demo project for Spring Boot"},
              "packageName":{"default": "com.example.demo"},
              "dependencies": {
                "type": "hierarchical-multi-select",
                "values": [
                  {
                    "name": "Web",
                    "values": [
                      {"id": "web",     "name": "Spring Web",     "description": "Build RESTful APIs"},
                      {"id": "webflux", "name": "Spring Reactive Web", "description": "Reactive web"}
                    ]
                  },
                  {
                    "name": "Developer Tools",
                    "values": [
                      {"id": "lombok",   "name": "Lombok",   "description": "Lombok library"},
                      {"id": "devtools", "name": "DevTools", "description": "Spring DevTools"}
                    ]
                  }
                ]
              }
            }
            """;

    private static class StubMetadataService extends MetadataService {
        private final InitializrMetadata metadata;

        StubMetadataService() {
            try {
                this.metadata = new ObjectMapper().readValue(FIXTURE_METADATA_JSON, InitializrMetadata.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public InitializrMetadata getMetadata(boolean forceRefresh) {
            return metadata;
        }
    }

    private static class StubGeneratorService extends ProjectGeneratorService {
        ProjectConfig capturedConfig;
        Path capturedOutputDir;

        @Override
        public void generate(ProjectConfig config, Path outputDir) throws IOException {
            this.capturedConfig = config;
            this.capturedOutputDir = outputDir;
            Path res = outputDir.resolve("src/main/resources");
            Files.createDirectories(res);
            Files.writeString(res.resolve("application.properties"), "spring.application.name=" + config.getName() + "\n");
        }
    }

    @Test
    void completeTranscriptFlowsThroughEveryStep(@TempDir Path tempDir) throws Exception {
        // Inputs:
        // 1. Project Type: '2' -> Maven
        // 2. Language: '' -> default (Java)
        // 3. Boot Version: '' -> default (4.0.3)
        // 4. Dependencies: 'web' -> '1' (toggle) -> '' (done)
        // 5. Metadata:
        //    Group: 'dev.ayman'
        //    Artifact: 'orders'
        //    Name: '' (default orders)
        //    Description: '' (default)
        //    Version: '' (default)
        //    Package: '' (default derived)
        // 6. Packaging: '' -> default (Jar)
        // 7. Java Version: '1' -> 21
        // 8. Output directory resolved to tempDir/orders
        // 9. YAML: 'y'
        // 10. .env: 'y'
        // 11. Git: 'n'
        String input = """
                2
                
                
                web
                1
                
                dev.ayman
                orders
                
                
                
                
                
                1
                y
                y
                n
                """;

        BufferedReader reader = new BufferedReader(new StringReader(input));
        StringWriter capturedOut = new StringWriter();
        PrintWriter writer = new PrintWriter(capturedOut, true);

        StubMetadataService metaService = new StubMetadataService();
        StubGeneratorService genService = new StubGeneratorService();

        WizardRunner runner = new WizardRunner(metaService, genService, reader, writer, false, tempDir);
        runner.run();

        assertNotNull(genService.capturedConfig, "Generator must be called with captured config");
        assertEquals("maven-project", genService.capturedConfig.getType());
        assertEquals("java", genService.capturedConfig.getLanguage());
        assertEquals("4.0.3.RELEASE", genService.capturedConfig.getBootVersion());
        assertEquals(List.of("web"), genService.capturedConfig.getDependencies());
        assertEquals("dev.ayman", genService.capturedConfig.getGroupId());
        assertEquals("orders", genService.capturedConfig.getArtifactId());
        assertEquals("21", genService.capturedConfig.getJavaVersion());
        assertTrue(genService.capturedConfig.isUseYaml());
        assertTrue(genService.capturedConfig.isCreateEnvFiles());
        assertFalse(genService.capturedConfig.isInitGit());

        Path projectDir = tempDir.resolve("orders");
        assertEquals(projectDir.toAbsolutePath().normalize(), genService.capturedOutputDir);
        assertTrue(Files.exists(projectDir.resolve(".env")));
        assertTrue(Files.exists(projectDir.resolve(".env.example")));

        Path yaml = projectDir.resolve("src/main/resources/application.yml");
        assertTrue(Files.exists(yaml));
        String yamlContent = Files.readString(yaml);
        assertTrue(yamlContent.contains("spring.application.name: \"orders\""));
        assertTrue(yamlContent.contains("spring.config.import"));
        assertFalse(Files.exists(projectDir.resolve("src/main/resources/application.properties")));
    }

    @Test
    void eofMidWizardCancelsWithoutGenerating(@TempDir Path tempDir) {
        // Input ends abruptly during metadata form
        String input = """
                2
                
                
                web
                1
                
                dev.ayman
                """;

        BufferedReader reader = new BufferedReader(new StringReader(input));
        StringWriter capturedOut = new StringWriter();
        PrintWriter writer = new PrintWriter(capturedOut, true);

        StubMetadataService metaService = new StubMetadataService();
        StubGeneratorService genService = new StubGeneratorService();

        WizardRunner runner = new WizardRunner(metaService, genService, reader, writer, false, tempDir);
        assertThrows(WizardCancelledException.class, runner::run);

        assertNull(genService.capturedConfig, "Generator should never be called when wizard is cancelled");
    }

    @Test
    void outputDirectoryUsesArtifactFolderInsideExistingParent(@TempDir Path parent) throws Exception {
        Path existingFile = parent.resolve("notes.txt");
        Files.writeString(existingFile, "Keep this file");

        Path target = WizardRunner.resolveOutputDirectory(parent, "hazem");

        assertEquals(parent.resolve("hazem").toAbsolutePath().normalize(), target);
        assertEquals("Keep this file", Files.readString(existingFile));
        assertFalse(Files.exists(target));
    }

    @Test
    void outputDirectoryDefaultsToArtifactFolderInsideCurrentDirectory() {
        assertEquals(Path.of(System.getProperty("user.dir"), "hazem").toAbsolutePath().normalize(),
                WizardRunner.resolveOutputDirectory(null, "hazem"));
    }

    @Test
    void outputDirectoryNormalizesRelativeParent() {
        assertEquals(Path.of("projects", "hazem").toAbsolutePath().normalize(),
                WizardRunner.resolveOutputDirectory(Path.of("projects", "temp", ".."), "hazem"));
    }

    @Test
    void outputDirectoryRejectsArtifactsThatAreNotSingleDirectoryNames(@TempDir Path parent) {
        for (String artifact : List.of("", " ", ".", "..", "../hazem", "/hazem", "nested/hazem", "nested\\hazem")) {
            assertThrows(IllegalArgumentException.class,
                    () -> WizardRunner.resolveOutputDirectory(parent, artifact), artifact);
        }
        assertThrows(IllegalArgumentException.class,
                () -> WizardRunner.resolveOutputDirectory(parent, null));
    }

    private static Option option(String id, String name) {
        try {
            String json = String.format("{\"id\":\"%s\",\"name\":\"%s\"}", id, name);
            return new ObjectMapper().readValue(json, Option.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void isGradleProjectTypeRecognizesAllGradleVariants() {
        assertTrue(WizardRunner.isGradleProjectType("gradle-project"));
        assertTrue(WizardRunner.isGradleProjectType("gradle-project-kotlin"));
        assertTrue(WizardRunner.isGradleProjectType("gradle-build"));
    }

    @Test
    void isGradleProjectTypeRejectsMavenAndNull() {
        assertFalse(WizardRunner.isGradleProjectType("maven-project"));
        assertFalse(WizardRunner.isGradleProjectType("maven-build"));
        assertFalse(WizardRunner.isGradleProjectType(null));
    }

    @Test
    void snapshotBootVersionIsFlaggedUnstable() {
        Option snapshot = option("4.1.1.BUILD-SNAPSHOT", "4.1.1 (SNAPSHOT)");
        Option stable = option("4.1.0.RELEASE", "4.1.0");

        assertTrue(snapshot.isUnstable());
        assertFalse(stable.isUnstable());
        assertTrue(WizardRunner.isGradleProjectType("gradle-project") && snapshot.isUnstable(),
                "this combination should trigger the Gradle + SNAPSHOT warning");
    }
}
