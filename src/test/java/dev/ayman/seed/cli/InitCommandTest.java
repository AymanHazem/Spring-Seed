package dev.ayman.seed.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.SeedCli;
import dev.ayman.seed.model.InitializrMetadata;
import dev.ayman.seed.service.MetadataService;
import dev.ayman.seed.service.ProjectGeneratorService;
import dev.ayman.seed.wizard.ProjectConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InitCommandTest
{
    private static final String FIXTURE_JSON = """
            {
              "type": {
                "type": "action",
                "default": "maven-project",
                "values": [
                  {"id": "maven-project",  "name": "Maven",           "action": "/starter.zip",
                   "tags": {"build": "maven", "format": "project"}},
                  {"id": "gradle-project", "name": "Gradle - Groovy", "action": "/starter.zip",
                   "tags": {"build": "gradle", "dialect": "groovy", "format": "project"}},
                  {"id": "gradle-project-kotlin", "name": "Gradle - Kotlin", "action": "/starter.zip",
                   "tags": {"build": "gradle", "dialect": "kotlin", "format": "project"}}
                ]
              },
              "language": {
                "type": "single-select",
                "default": "java",
                "values": [
                  {"id": "java",   "name": "Java"},
                  {"id": "kotlin", "name": "Kotlin"},
                  {"id": "groovy", "name": "Groovy"}
                ]
              },
              "bootVersion": {
                "type": "single-select",
                "default": "4.0.3.RELEASE",
                "values": [
                  {"id": "4.0.3.RELEASE", "name": "4.0.3"},
                  {"id": "4.1.0",         "name": "4.1.0"}
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
                  {"id": "17", "name": "17"},
                  {"id": "25", "name": "25"}
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
                      {"id": "web", "name": "Spring Web", "description": "Web with Spring MVC"},
                      {"id": "webflux", "name": "Spring Reactive Web", "description": "Reactive web"}
                    ]
                  },
                  {
                    "name": "SQL",
                    "values": [
                      {"id": "data-jpa", "name": "Spring Data JPA", "description": "JPA persistence"},
                      {"id": "postgresql", "name": "PostgreSQL Driver", "description": "PostgreSQL JDBC driver"}
                    ]
                  },
                  {
                    "name": "Developer Tools",
                    "values": [
                      {"id": "devtools", "name": "Spring Boot DevTools", "description": "Developer tools"},
                      {"id": "lombok", "name": "Lombok", "description": "Lombok library"}
                    ]
                  }
                ]
              }
            }
            """;

    private InitializrMetadata metadata;
    private StubGeneratorService generatorService;
    private StringWriter capturedOut;
    private PrintWriter writer;

    static class StubMetadataService extends MetadataService
    {
        private final InitializrMetadata metadata;
        StubMetadataService(InitializrMetadata metadata) { this.metadata = metadata; }
        @Override
        public InitializrMetadata getMetadata(boolean forceRefresh) { return metadata; }
    }

    static class StubGeneratorService extends ProjectGeneratorService
    {
        ProjectConfig capturedConfig;
        Path capturedOutputDir;

        @Override
        public void generate(ProjectConfig config, Path outputDir)
        {
            this.capturedConfig = config;
            this.capturedOutputDir = outputDir;
            try
            {
                Path res = outputDir.resolve("src/main/resources");
                Files.createDirectories(res);
                Files.writeString(res.resolve("application.properties"), "server.port=8080\n");
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception
    {
        metadata = new ObjectMapper().readValue(FIXTURE_JSON, InitializrMetadata.class);
        generatorService = new StubGeneratorService();
        capturedOut = new StringWriter();
        writer = new PrintWriter(capturedOut, true);
    }

    @Test
    void testDefaultScaffoldingWithNameOnly(@TempDir Path tempDir) throws Exception
    {
        ProjectOptions options = new ProjectOptions();
        options.artifactId = "order-service";
        options.outputPath = tempDir;

        NonInteractiveRunner runner = new NonInteractiveRunner(options,
                new StubMetadataService(metadata), generatorService, writer, false);

        int exitCode = runner.run();
        assertEquals(0, exitCode);

        ProjectConfig config = runner.getGeneratedConfig();
        assertNotNull(config);
        assertEquals("order-service", config.getArtifactId());
        assertEquals("order-service", config.getName());
        assertEquals("com.example", config.getGroupId());
        assertEquals("com.example.orderservice", config.getPackageName());
        assertEquals("maven-project", config.getType());
        assertEquals("java", config.getLanguage());
        assertEquals("4.0.3.RELEASE", config.getBootVersion());
        assertEquals("17", config.getJavaVersion());
        assertEquals("jar", config.getPackaging());
        assertTrue(config.isUseYaml());
        assertFalse(config.isCreateEnvFiles());
        assertFalse(config.isInitGit());

        Path projectDir = tempDir.resolve("order-service");
        assertEquals(projectDir.toAbsolutePath().normalize(), generatorService.capturedOutputDir);
        assertTrue(Files.exists(projectDir.resolve("src/main/resources/application.yml")));
    }

    @Test
    void testDependencyValidationSuccess(@TempDir Path tempDir) throws Exception
    {
        ProjectOptions options = new ProjectOptions();
        options.artifactId = "my-app";
        options.outputPath = tempDir;
        options.dependencies = List.of("web", "data-jpa", "postgresql");

        NonInteractiveRunner runner = new NonInteractiveRunner(options,
                new StubMetadataService(metadata), generatorService, writer, false);

        assertEquals(0, runner.run());
        assertEquals(List.of("web", "data-jpa", "postgresql"), runner.getGeneratedConfig().getDependencies());
    }

    @Test
    void testDependencyValidationFuzzyTypoSuggestion()
    {
        ProjectOptions options = new ProjectOptions();
        options.artifactId = "my-app";
        // 'postgres' typo for 'postgresql'
        options.dependencies = List.of("web", "postgres");

        NonInteractiveRunner runner = new NonInteractiveRunner(options,
                new StubMetadataService(metadata), generatorService, writer, false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, runner::run);
        assertTrue(ex.getMessage().contains("Unknown dependency: 'postgres'"));
        assertTrue(ex.getMessage().contains("Did you mean 'postgresql'?"));
    }

    @Test
    void testFlagOverrides(@TempDir Path tempDir) throws Exception
    {
        ProjectOptions options = new ProjectOptions();
        options.artifactId = "custom-api";
        options.outputPath = tempDir;
        options.groupId = "com.mycorp";
        options.name = "My Custom API";
        options.type = "gradle-kotlin";
        options.language = "kotlin";
        options.javaVersion = "21";
        options.bootVersion = "4.1.0";
        options.packaging = "war";
        options.packageName = "com.mycorp.api";
        options.properties = true;
        options.createEnvFiles = true;
        options.initGit = false;

        NonInteractiveRunner runner = new NonInteractiveRunner(options,
                new StubMetadataService(metadata), generatorService, writer, false);

        assertEquals(0, runner.run());

        ProjectConfig config = runner.getGeneratedConfig();
        assertEquals("com.mycorp", config.getGroupId());
        assertEquals("custom-api", config.getArtifactId());
        assertEquals("My Custom API", config.getName());
        assertEquals("gradle-project-kotlin", config.getType());
        assertEquals("kotlin", config.getLanguage());
        assertEquals("21", config.getJavaVersion());
        assertEquals("4.1.0", config.getBootVersion());
        assertEquals("war", config.getPackaging());
        assertEquals("com.mycorp.api", config.getPackageName());
        assertFalse(config.isUseYaml());
        assertTrue(config.isCreateEnvFiles());

        Path projectDir = tempDir.resolve("custom-api");
        assertTrue(Files.exists(projectDir.resolve(".env")));
        assertTrue(Files.exists(projectDir.resolve("src/main/resources/application.properties")));
    }

    @Test
    void testTypeAliases()
    {
        ProjectOptions options = new ProjectOptions();
        NonInteractiveRunner runner = new NonInteractiveRunner(options,
                new StubMetadataService(metadata), generatorService, writer, false);

        options.type = "maven";
        assertEquals("maven-project", runner.buildConfig(metadata).getType());

        options.type = "gradle";
        assertEquals("gradle-project", runner.buildConfig(metadata).getType());

        options.type = "gradle-kts";
        assertEquals("gradle-project-kotlin", runner.buildConfig(metadata).getType());
    }

    @Test
    void testRootCommandDelegationWithFlags()
    {
        SeedCli cli = new SeedCli();
        CommandLine cmd = new CommandLine(cli);
        cmd.parseArgs("order-service", "-d", "web,data-jpa", "--java", "21");

        assertTrue(cli.getOptions().hasExplicitGenerationOptions());
        assertEquals("order-service", cli.getOptions().artifactId);
        assertEquals(List.of("web", "data-jpa"), cli.getOptions().dependencies);
        assertEquals("21", cli.getOptions().javaVersion);
    }

    @Test
    void testRootCommandWithoutGenerationFlags()
    {
        SeedCli cli = new SeedCli();
        CommandLine cmd = new CommandLine(cli);
        cmd.parseArgs("-p", "/tmp/projects");

        assertFalse(cli.getOptions().hasExplicitGenerationOptions());
        assertEquals(Path.of("/tmp/projects"), cli.getOptions().outputPath);
    }

    @Test
    void testInitSubcommandParsing()
    {
        SeedCli cli = new SeedCli();
        CommandLine cmd = new CommandLine(cli);
        CommandLine.ParseResult parseResult = cmd.parseArgs("init", "order-service", "-d", "web,lombok", "--yaml");

        assertTrue(parseResult.hasSubcommand());
        CommandLine sub = parseResult.subcommand().commandSpec().commandLine();
        Object command = sub.getCommand();
        assertInstanceOf(InitCommand.class, command);

        InitCommand initCmd = (InitCommand) command;
        assertEquals("order-service", initCmd.options.artifactId);
        assertEquals(List.of("web", "lombok"), initCmd.options.dependencies);
        assertTrue(initCmd.options.isUseYaml());
    }

    @Test
    void testInitSubcommandAliases()
    {
        SeedCli cli = new SeedCli();
        CommandLine cmd = new CommandLine(cli);

        CommandLine.ParseResult newResult = cmd.parseArgs("new", "my-app");
        assertTrue(newResult.hasSubcommand());
        assertInstanceOf(InitCommand.class, newResult.subcommand().commandSpec().commandLine().getCommand());

        CommandLine.ParseResult createResult = cmd.parseArgs("create", "my-app");
        assertTrue(createResult.hasSubcommand());
        assertInstanceOf(InitCommand.class, createResult.subcommand().commandSpec().commandLine().getCommand());
    }
}
