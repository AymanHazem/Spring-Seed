package dev.ayman.seed.cli;

import dev.ayman.seed.model.Dependency;
import dev.ayman.seed.model.DependencyGroup;
import dev.ayman.seed.model.InitializrMetadata;
import dev.ayman.seed.model.Option;
import dev.ayman.seed.service.MetadataService;
import dev.ayman.seed.service.ProjectGeneratorService;
import dev.ayman.seed.service.UpdateService;
import dev.ayman.seed.util.EnvFileWriter;
import dev.ayman.seed.util.FuzzyMatcher;
import dev.ayman.seed.util.GitInitializer;
import dev.ayman.seed.wizard.ProjectConfig;
import dev.ayman.seed.wizard.WizardRunner;
import org.fusesource.jansi.AnsiConsole;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Executes project scaffolding non-interactively using CLI options and flags.
 */
public class NonInteractiveRunner
{
    private final ProjectOptions options;
    private final MetadataService metadataService;
    private final ProjectGeneratorService generatorService;
    private final UpdateService updateService;
    private final PrintWriter out;
    private final boolean forceRefresh;

    private ProjectConfig generatedConfig;

    public NonInteractiveRunner(ProjectOptions options, boolean forceRefresh)
    {
        this(options, new MetadataService(), new ProjectGeneratorService(), new UpdateService(), new PrintWriter(System.out, true), forceRefresh);
    }

    public NonInteractiveRunner(ProjectOptions options, MetadataService metadataService,
                                ProjectGeneratorService generatorService, PrintWriter out, boolean forceRefresh)
    {
        this(options, metadataService, generatorService, new UpdateService(), out, forceRefresh);
    }

    public NonInteractiveRunner(ProjectOptions options, MetadataService metadataService,
                                ProjectGeneratorService generatorService, UpdateService updateService,
                                PrintWriter out, boolean forceRefresh)
    {
        this.options = options;
        this.metadataService = metadataService;
        this.generatorService = generatorService;
        this.updateService = updateService;
        this.out = out;
        this.forceRefresh = forceRefresh;
    }

    public ProjectConfig getGeneratedConfig()
    {
        return generatedConfig;
    }

    public int run() throws Exception
    {
        AnsiConsole.systemInstall();
        try
        {
            updateService.startBackgroundCheck();

            InitializrMetadata meta = metadataService.getMetadata(forceRefresh);
            ProjectConfig config = buildConfig(meta);
            this.generatedConfig = config;

            Path target = Path.of(config.getOutputDirectory()).toAbsolutePath();

            out.println();
            out.println(ansi().bold().fgBrightCyan().a("  🌱 Generating project → ").bold().a(target).reset());
            out.flush();

            generatorService.generate(config, target);
            EnvFileWriter.normalizeConfigFormat(target, config.isUseYaml());

            if (config.isCreateEnvFiles())
            {
                EnvFileWriter.writeEnvFiles(target, config.isUseYaml());
                out.println(ansi().fgBrightGreen().a("  ✔ Created .env and .env.example").reset());
            }

            if (config.isInitGit())
            {
                try
                {
                    GitInitializer.init(target);
                    out.println(ansi().fgBrightGreen().a("  ✔ Git repository initialized").reset());
                }
                catch (Exception e)
                {
                    out.println(ansi().fgYellow()
                            .a("  ⚠ Git initialization failed (" + e.getMessage() + "), continuing without Git.")
                            .reset());
                }
            }

            printSummary(config, target);
            updateService.printNoticeIfAvailable(out);
            return 0;
        }
        finally
        {
            AnsiConsole.systemUninstall();
        }
    }

    public ProjectConfig buildConfig(InitializrMetadata meta)
    {
        ProjectConfig config = new ProjectConfig();

        // 1. Artifact & Name
        String artifact = options.artifactId;
        if (artifact == null || artifact.isBlank())
            artifact = options.name;
        if (artifact == null || artifact.isBlank())
            artifact = meta.getArtifactId() != null && meta.getArtifactId().getDefaultValue() != null
                    ? meta.getArtifactId().getDefaultValue()
                    : "demo";

        config.setArtifactId(artifact);
        config.setName(options.name != null && !options.name.isBlank() ? options.name : artifact);

        // 2. Group ID
        String group = options.groupId;
        if (group == null || group.isBlank())
            group = meta.getGroupId() != null && meta.getGroupId().getDefaultValue() != null
                    ? meta.getGroupId().getDefaultValue()
                    : "com.example";
        config.setGroupId(group);

        // 3. Description & Version
        config.setDescription(options.description != null ? options.description
                : (meta.getDescription() != null ? meta.getDescription().getDefaultValue() : "Spring Boot application"));
        config.setVersion(options.version != null ? options.version
                : (meta.getVersion() != null ? meta.getVersion().getDefaultValue() : "0.0.1-SNAPSHOT"));

        // 4. Packaging
        String pkg = options.packaging != null ? options.packaging.toLowerCase().trim()
                : (meta.getPackaging() != null ? meta.getPackaging().getDefaultValue() : "jar");
        config.setPackaging(pkg);

        // 5. Type (Build system)
        config.setType(resolveType(options.type, meta));

        // 6. Language
        config.setLanguage(resolveLanguage(options.language, meta));

        // 7. Boot Version
        config.setBootVersion(resolveBootVersion(options.bootVersion, meta));

        // 8. Java Version
        config.setJavaVersion(resolveJavaVersion(options.javaVersion, meta));

        // 9. Package Name
        if (options.packageName != null && !options.packageName.isBlank())
            config.setPackageName(options.packageName.trim());
        else
            config.setPackageName(config.derivePackageName());

        // 10. Dependencies
        config.setDependencies(resolveDependencies(options.dependencies, meta));

        // 11. Output Directory
        Path outputPath = WizardRunner.resolveOutputDirectory(options.outputPath, config.getArtifactId());
        config.setOutputDirectory(outputPath.toString());

        // 12. Boolean flags
        config.setUseYaml(options.isUseYaml());
        config.setCreateEnvFiles(options.isCreateEnvFiles());
        config.setInitGit(options.isInitGit());

        return config;
    }

    private String resolveType(String rawType, InitializrMetadata meta)
    {
        if (rawType == null || rawType.isBlank())
            return meta.getType() != null && meta.getType().getDefaultValue() != null
                    ? meta.getType().getDefaultValue()
                    : "maven-project";

        String normalized = switch (rawType.toLowerCase().trim())
        {
            case "maven" -> "maven-project";
            case "gradle" -> "gradle-project";
            case "gradle-kotlin", "gradle-kts" -> "gradle-project-kotlin";
            default -> rawType.trim();
        };

        boolean valid = meta.getType() != null && meta.getType().getValues() != null &&
                meta.getType().getValues().stream().anyMatch(t -> t.getId().equalsIgnoreCase(normalized));

        if (!valid)
            throw new IllegalArgumentException("Unknown project type: '" + rawType + "'. Available: maven, gradle, gradle-kotlin");

        return normalized;
    }

    private String resolveLanguage(String rawLang, InitializrMetadata meta)
    {
        if (rawLang == null || rawLang.isBlank())
            return meta.getLanguage() != null && meta.getLanguage().getDefaultValue() != null
                    ? meta.getLanguage().getDefaultValue()
                    : "java";

        String lang = rawLang.toLowerCase().trim();
        boolean valid = meta.getLanguage() != null && meta.getLanguage().getValues() != null &&
                meta.getLanguage().getValues().stream().anyMatch(l -> l.getId().equalsIgnoreCase(lang));

        if (!valid)
            throw new IllegalArgumentException("Unknown language: '" + rawLang + "'. Available: java, kotlin, groovy");

        return lang;
    }

    private String resolveBootVersion(String rawBootVersion, InitializrMetadata meta)
    {
        if (rawBootVersion == null || rawBootVersion.isBlank())
            return meta.getBootVersion() != null && meta.getBootVersion().getDefaultValue() != null
                    ? meta.getBootVersion().getDefaultValue()
                    : "3.4.0";

        String version = rawBootVersion.trim();
        boolean valid = meta.getBootVersion() != null && meta.getBootVersion().getValues() != null &&
                meta.getBootVersion().getValues().stream().anyMatch(b -> b.getId().equalsIgnoreCase(version));

        if (!valid)
            throw new IllegalArgumentException("Unknown Spring Boot version: '" + rawBootVersion + "'.");

        return version;
    }

    private String resolveJavaVersion(String rawJavaVersion, InitializrMetadata meta)
    {
        if (rawJavaVersion == null || rawJavaVersion.isBlank())
            return meta.getJavaVersion() != null && meta.getJavaVersion().getDefaultValue() != null
                    ? meta.getJavaVersion().getDefaultValue()
                    : "17";

        String javaVer = rawJavaVersion.trim();
        boolean valid = meta.getJavaVersion() != null && meta.getJavaVersion().getValues() != null &&
                meta.getJavaVersion().getValues().stream().anyMatch(j -> j.getId().equalsIgnoreCase(javaVer));

        if (!valid)
        {
            String available = meta.getJavaVersion() != null && meta.getJavaVersion().getValues() != null
                    ? meta.getJavaVersion().getValues().stream().map(Option::getId).collect(Collectors.joining(", "))
                    : "17, 21, 25";
            throw new IllegalArgumentException("Unknown Java version: '" + rawJavaVersion + "'. Available: " + available);
        }

        return javaVer;
    }

    private List<String> resolveDependencies(List<String> requestedDeps, InitializrMetadata meta)
    {
        if (requestedDeps == null || requestedDeps.isEmpty())
            return List.of();

        List<Dependency> allDeps = flattenDependencies(meta);
        Map<String, Dependency> depMap = allDeps.stream()
                .collect(Collectors.toMap(d -> d.getId().toLowerCase(), d -> d, (a, b) -> a));

        List<String> valid = new ArrayList<>();
        for (String raw : requestedDeps)
        {
            if (raw == null) continue;
            String depId = raw.trim().toLowerCase();
            if (depId.isEmpty()) continue;

            if (depMap.containsKey(depId))
            {
                valid.add(depMap.get(depId).getId());
            }
            else
            {
                List<Dependency> matches = FuzzyMatcher.search(allDeps, depId);
                String suggestion = !matches.isEmpty() ? " Did you mean '" + matches.getFirst().getId() + "'?" : "";
                throw new IllegalArgumentException("Unknown dependency: '" + raw.trim() + "'." + suggestion);
            }
        }
        return valid;
    }

    private List<Dependency> flattenDependencies(InitializrMetadata meta)
    {
        List<Dependency> flat = new ArrayList<>();
        if (meta.getDependencies() == null || meta.getDependencies().getValues() == null)
            return flat;

        for (DependencyGroup group : meta.getDependencies().getValues())
        {
            if (group.getValues() == null)
                continue;
            for (Dependency dep : group.getValues())
            {
                dep.setCategory(group.getName());
                flat.add(dep);
            }
        }
        return flat;
    }

    private void printSummary(ProjectConfig config, Path target)
    {
        out.println();
        out.println(ansi().bold().fgBrightGreen().a(
                "  ╔══════════════════════════════════════════════╗").reset());
        out.println(ansi().bold().fgBrightGreen().a(
                "  ║          🌱  Project Created!                ║").reset());
        out.println(ansi().bold().fgBrightGreen().a(
                "  ╚══════════════════════════════════════════════╝").reset());
        out.println();
        out.println(ansi().fgBrightCyan().a("  Location   : ").reset().bold().a(target));
        out.println(ansi().fgBrightCyan().a("  Type       : ").reset().a(config.getType()));
        out.println(ansi().fgBrightCyan().a("  Language   : ").reset().a(config.getLanguage()));
        out.println(ansi().fgBrightCyan().a("  Spring Boot: ").reset().a(config.getBootVersion()));
        out.println(ansi().fgBrightCyan().a("  Group      : ").reset().a(config.getGroupId()));
        out.println(ansi().fgBrightCyan().a("  Artifact   : ").reset().a(config.getArtifactId()));
        out.println(ansi().fgBrightCyan().a("  Java       : ").reset().a(config.getJavaVersion()));
        if (!config.getDependencies().isEmpty())
        {
            out.println(ansi().fgBrightCyan().a("  Dependencies: ").reset()
                    .a(String.join(", ", config.getDependencies())));
        }
        out.println();
        out.println(ansi().bold().fgBrightGreen().a("  Happy coding!").reset());
        out.println();
    }
}
