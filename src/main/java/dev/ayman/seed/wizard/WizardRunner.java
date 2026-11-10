package dev.ayman.seed.wizard;

import dev.ayman.seed.model.*;
import dev.ayman.seed.service.MetadataService;
import dev.ayman.seed.service.ProjectGeneratorService;
import dev.ayman.seed.service.UpdateService;
import dev.ayman.seed.util.EnvFileWriter;
import dev.ayman.seed.util.GitInitializer;
import dev.ayman.seed.util.TerminalUi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.fusesource.jansi.Ansi.ansi;

public class WizardRunner
{
    private final MetadataService metadataService;
    private final ProjectGeneratorService generatorService;
    private final UpdateService updateService;
    private final BufferedReader injectedIn;
    private final PrintWriter injectedOut;
    private final boolean forceRefresh;
    private final Path requestedOutputPath;

    private BufferedReader in;
    private PrintWriter out;
    private SelectionPrompt prompt;

    public WizardRunner(boolean forceRefresh, Path requestedOutputPath)
    {
        this(new MetadataService(), new ProjectGeneratorService(), new UpdateService(), null, null, forceRefresh, requestedOutputPath);
    }

    WizardRunner(MetadataService metadataService, ProjectGeneratorService generatorService,
                 BufferedReader in, PrintWriter out, boolean forceRefresh, Path requestedOutputPath)
    {
        this(metadataService, generatorService, new UpdateService(), in, out, forceRefresh, requestedOutputPath);
    }

    WizardRunner(MetadataService metadataService, ProjectGeneratorService generatorService,
                 UpdateService updateService, BufferedReader in, PrintWriter out,
                 boolean forceRefresh, Path requestedOutputPath)
    {
        this.metadataService = metadataService;
        this.generatorService = generatorService;
        this.updateService = updateService;
        this.injectedIn = in;
        this.injectedOut = out;
        this.forceRefresh = forceRefresh;
        this.requestedOutputPath = requestedOutputPath;
    }

    public void run() throws Exception
    {
        TerminalUi.installConsole();

        try
        {
            this.in = injectedIn != null ? injectedIn : new BufferedReader(new InputStreamReader(System.in));
            this.out = injectedOut != null ? injectedOut : new PrintWriter(System.out, true);
            this.prompt = new SelectionPrompt(in, out);

            updateService.startBackgroundCheck();
            printBanner();
            out.println(TerminalUi.faint("  ⟳ Fetching Spring Initializr metadata..."));
            out.flush();
            InitializrMetadata meta = metadataService.getMetadata(forceRefresh);

            ProjectConfig config = new ProjectConfig();

            printStep(1, "Project Type");
            List<ProjectType> projectTypes = meta.getType().getValues().stream()
                            .filter(ProjectType::isProjectFormat)
                            .toList();
            ProjectType selectedType = prompt.selectProjectType(projectTypes,
                            meta.getType().getDefaultValue());
            config.setType(selectedType.getId());
            printConfirm("Project type", selectedType.getName());

            config.setLanguage(askOption(2, "Language", meta.getLanguage()).getId());

            printStep(3, "Spring Boot Version");
            List<Option> allBootVersions = meta.getBootVersion().getValues();
            List<Option> bootVersionOptions = selectableBootVersions(allBootVersions, selectedType.getId());
            if (bootVersionOptions.size() < allBootVersions.size())
            {
                out.println(TerminalUi.faint(
                                "  (SNAPSHOT/milestone versions hidden: they currently fail to generate "
                                + "Gradle projects on start.spring.io)"));
            }
            Option selectedBoot = prompt.selectOption("Spring Boot Version",
                            bootVersionOptions,
                            meta.getBootVersion().getDefaultValue());
            config.setBootVersion(selectedBoot.getId());
            printConfirm("Spring Boot", selectedBoot.getName());

            if (selectedBoot.isUnstable() && isGradleProjectType(selectedType.getId()))
            {
                out.println(ansi().fgYellow()
                                .a("  ⚠ Warning: SNAPSHOT/milestone Spring Boot versions currently fail to ")
                                .a("generate Gradle projects on start.spring.io (a known upstream issue: ")
                                .a("the Spring Boot BOM can't be resolved for Gradle builds). ")
                                .a("Maven works fine with this version.").reset());
            }

            printStep(4, "Dependencies");
            List<Dependency> allDeps = flattenDependencies(meta);
            DependencySelector depSelector = new DependencySelector(allDeps, in, out);
            List<String> selectedDeps = depSelector.select();
            config.setDependencies(selectedDeps);
            printConfirm("Dependencies",
                            selectedDeps.isEmpty() ? "(none)" : String.join(", ", selectedDeps));

            printStep(5, "Project Metadata");
            new MetadataForm(meta, in, out).fill(config);

            config.setPackaging(askOption(6, "Packaging", meta.getPackaging()).getId());
            config.setJavaVersion(askOption(7, "Java Version", meta.getJavaVersion()).getId());

            printStep(8, "Output Directory");
            Path outputPath = resolveOutputDirectory(requestedOutputPath, config.getArtifactId());
            printConfirm("Output directory", outputPath.toString());
            config.setOutputDirectory(outputPath.toString());


            config.setUseYaml(prompt.askYesNo("Use YAML (application.yml) instead of .properties?"));


            config.setCreateEnvFiles(prompt.askYesNo("Create .env and .env.example files?"));


            config.setInitGit(prompt.askYesNo("Initialize a git repository?"));


            out.println();
            out.println(TerminalUi.faint("  ⟳ Generating project from start.spring.io..."));
            out.flush();

            Path target = Path.of(config.getOutputDirectory()).toAbsolutePath();
            generatorService.generate(config, target);

            EnvFileWriter.normalizeConfigFormat(target, config.isUseYaml());

            out.println(ansi().cursorUpLine().eraseLine()
                            .fgGreen().a("  ✔ Project generated at " + target).reset());

            if (config.isCreateEnvFiles()) {
                EnvFileWriter.writeEnvFiles(target, config.isUseYaml());
                out.println(ansi().fgGreen()
                                .a("  ✔ Created .env and .env.example").reset());
            }

            if (config.isInitGit()) {
                out.println(TerminalUi.faint("  ⟳ Initializing git repository..."));
                out.flush();
                try
                {
                    GitInitializer.init(target);
                    out.println(ansi().cursorUpLine().eraseLine()
                                    .fgGreen().a("  ✔ Git repository initialized").reset());
                }
                catch (Exception e)
                {
                    out.println(ansi().fgYellow()
                                    .a("  ⚠ Git initialization failed (" + e.getMessage() + "), continuing without Git.")
                                    .reset());
                }
            }

            TerminalUi.printProjectSummary(out, config, target);
            updateService.printNoticeIfAvailable(out);

        } finally {
            TerminalUi.uninstallConsole();
        }
    }

    public static Path resolveOutputDirectory(Path requestedParent, String artifactId)
    {
        if (artifactId == null || artifactId.isBlank() || artifactId.equals(".") || artifactId.equals("..")
                        || artifactId.contains("/") || artifactId.contains("\\"))
            throw new IllegalArgumentException("Artifact must be a single directory name, without path separators.");

        Path parent = requestedParent != null ? requestedParent : Path.of(System.getProperty("user.dir"));
        return parent.resolve(artifactId).toAbsolutePath().normalize();
    }

    private Option askOption(int step, String label, InitializrMetadata.SelectGroup<Option> group)
    {
        printStep(step, label);
        Option selected = prompt.selectOption(label, group.getValues(), group.getDefaultValue());
        printConfirm(label, selected.getName());
        return selected;
    }

    static boolean isGradleProjectType(String typeId)
    {
        return typeId != null && typeId.startsWith("gradle");
    }

    static List<Option> selectableBootVersions(List<Option> allBootVersions, String projectTypeId)
    {
        if (!isGradleProjectType(projectTypeId))
            return allBootVersions;

        List<Option> stableOnly = allBootVersions.stream()
                        .filter(o -> !o.isUnstable())
                        .toList();
        return stableOnly.isEmpty() ? allBootVersions : stableOnly;
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

    private void printBanner()
    {
        out.println();
        out.println(ansi().bold().fgGreen().a(
                "  ███████╗███████╗███████╗██████╗"
        ).reset());
        out.println(ansi().bold().fgGreen().a(
                "  ██╔════╝██╔════╝██╔════╝██╔══██╗"
        ).reset());
        out.println(ansi().bold().fgGreen().a(
                "  ███████╗█████╗  █████╗  ██║  ██║"
        ).reset());
        out.println(ansi().bold().fgGreen().a(
                "  ╚════██║██╔══╝  ██╔══╝  ██║  ██║"
        ).reset());
        out.println(ansi().bold().fgGreen().a(
                "  ███████║███████╗███████╗██████╔╝"
        ).reset());
        out.println(ansi().bold().fgGreen().a(
                "   ╚══════╝╚══════╝╚══════╝╚═════╝"
        ).reset());
        out.println(TerminalUi.faint(
                "  ───────────────────────────────────"));
        out.println(ansi().bold().fgGreen().a(
                "  Spring Boot project scaffolding CLI").reset());
        out.println();
    }

    private void printStep(int num, String label)
    {
        out.println();
        out.println(ansi().bold().fgCyan()
                        .a("  Step " + num + ": " + label).reset());
    }

    private void printConfirm(String label, String value)
    {
        out.println(ansi().fgGreen()
                        .a("  ✔ " + label + ": ").bold().a(value).reset());
    }
}