package dev.ayman.seed.cli;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;

public class ProjectOptions
{
    @Parameters(index = "0", arity = "0..1", paramLabel = "<artifact>",
            description = "Project artifact ID and directory name (e.g. order-service)")
    public String artifactId;

    @Option(names = {"-n", "--name"}, description = "Application name (default: artifactId)")
    public String name;

    @Option(names = {"-g", "--group", "--group-id"}, description = "Group ID (default: com.example)")
    public String groupId;

    @Option(names = {"-d", "--deps", "--dependencies"}, split = ",",
            description = "Comma-separated dependency IDs (e.g. web,data-jpa,postgresql)")
    public List<String> dependencies;

    @Option(names = {"-j", "--java", "--java-version"}, description = "Java version (e.g. 17, 21, 25)")
    public String javaVersion;

    @Option(names = {"-b", "--boot", "--boot-version"}, description = "Spring Boot version")
    public String bootVersion;

    @Option(names = {"-t", "--type", "--build"}, description = "Build system: maven, gradle, or gradle-kotlin")
    public String type;

    @Option(names = {"-l", "--lang", "--language"}, description = "Language: java, kotlin, or groovy")
    public String language;

    @Option(names = "--package-name", description = "Package name (default: derived from group + artifact)")
    public String packageName;

    @Option(names = "--description", description = "Project description")
    public String description;

    @Option(names = "--packaging", description = "Packaging: jar or war (default: jar)")
    public String packaging;

    @Option(names = {"-V", "--project-version", "--version"}, description = "Project version (default: 0.0.1-SNAPSHOT)")
    public String version;

    @Option(names = {"-p", "--path"}, paramLabel = "<path>",
            description = "Parent directory for the generated <artifact> project folder (default: current directory)")
    public Path outputPath;

    @Option(names = "--yaml", description = "Generate application.yml instead of application.properties (default: true)")
    public boolean yaml = false;

    @Option(names = {"--properties", "--no-yaml"}, description = "Generate application.properties instead of application.yml")
    public boolean properties = false;

    @Option(names = "--env", description = "Create .env and .env.example files (default: false)")
    public boolean createEnvFiles = false;

    @Option(names = "--no-env", description = "Do not create .env files")
    public boolean noEnv = false;

    @Option(names = "--git", description = "Initialize a git repository (default: false)")
    public boolean initGit = false;

    @Option(names = "--no-git", description = "Do not initialize a git repository")
    public boolean noGit = false;

    public boolean isUseYaml()
    {
        return !properties;
    }

    public boolean isCreateEnvFiles()
    {
        if (noEnv)
            return false;
        return createEnvFiles;
    }

    public boolean isInitGit()
    {
        if (noGit)
            return false;
        return initGit;
    }

    public boolean hasExplicitGenerationOptions()
    {
        return artifactId != null || name != null || dependencies != null
                || javaVersion != null || bootVersion != null || type != null
                || language != null || groupId != null || packageName != null
                || description != null || packaging != null || version != null;
    }
}
