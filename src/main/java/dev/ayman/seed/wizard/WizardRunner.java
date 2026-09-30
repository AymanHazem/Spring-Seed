package dev.ayman.seed.wizard;
import dev.ayman.seed.model.Dependency;
import dev.ayman.seed.model.DependencyGroup;
import dev.ayman.seed.model.InitializrMetadata;
import dev.ayman.seed.model.Option;
import dev.ayman.seed.model.ProjectType;
import dev.ayman.seed.service.MetadataService;
import dev.ayman.seed.service.ProjectGeneratorService;
import dev.ayman.seed.util.EnvFileWriter;
import dev.ayman.seed.util.GitInitializer;
import org.fusesource.jansi.AnsiConsole;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.fusesource.jansi.Ansi.ansi;
public class WizardRunner
{
    private final MetadataService metadataService;
    private final ProjectGeneratorService generatorService;
    private final SelectionPrompt prompt;
    private final PrintWriter out;
    private final boolean forceRefresh;
    private final Path requestedOutputPath;

    public WizardRunner(boolean forceRefresh, Path requestedOutputPath)
    {
        this.metadataService = new MetadataService();
        this.generatorService = new ProjectGeneratorService();
        this.prompt = new SelectionPrompt();
        this.out = new PrintWriter(System.out, true);
        this.forceRefresh = forceRefresh;
        this.requestedOutputPath = requestedOutputPath;
    }

    public void run() throws Exception
    {
        AnsiConsole.systemInstall();

        try
        {
            printBanner();
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
                out.println(ansi().fgBrightBlack()
                                .a("  (SNAPSHOT/milestone versions hidden — they currently fail to generate ")
                                .a("Gradle projects on start.spring.io)").reset());
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
                                .a("generate Gradle projects on start.spring.io (a known upstream issue — ")
                                .a("the Spring Boot BOM can't be resolved for Gradle builds). ")
                                .a("Maven works fine with this version.").reset());
            }

            printStep(4, "Dependencies");
            List<Dependency> allDeps = flattenDependencies(meta);
            DependencySelector depSelector = new DependencySelector(allDeps);
            List<String> selectedDeps = depSelector.select();
            config.setDependencies(selectedDeps);
            printConfirm("Dependencies",
                            selectedDeps.isEmpty() ? "(none)" : String.join(", ", selectedDeps));

            printStep(5, "Project Metadata");
            new MetadataForm(meta).fill(config);

            config.setPackaging(askOption(6, "Packaging", meta.getPackaging()).getId());
            config.setJavaVersion(askOption(7, "Java Version", meta.getJavaVersion()).getId());

            // ── Step 8: Output directory ──────────────────────────────────
            printStep(8, "Output Directory");
            Path outputPath;
            if (requestedOutputPath != null)
            {
                outputPath = requestedOutputPath.toAbsolutePath().normalize();
                printConfirm("Output directory", outputPath.toString());
            }
            else
            {
                outputPath = Path.of(System.getProperty("user.home"), config.getArtifactId())
                                .toAbsolutePath().normalize();
                out.println(ansi().fgBrightYellow()
                                .a("  Project will be created in your home directory: " + outputPath)
                                .reset());
            }
            config.setOutputDirectory(outputPath.toString());

            // ── Step 9: Config format ─────────────────────────────────────
            config.setUseYaml(prompt.askYesNo("Use YAML (application.yml) instead of .properties?"));

            // ── Step 10: .env files ───────────────────────────────────────
            config.setCreateEnvFiles(prompt.askYesNo("Create .env and .env.example files?"));

            // ── Step 11: Git init ─────────────────────────────────────────
            config.setInitGit(prompt.askYesNo("Initialize a git repository?"));

            // ── Generate project ──────────────────────────────────────────
            out.println();
            out.print(ansi().fgBrightBlack().a("  ⟳ Generating project from start.spring.io...").reset());
            out.flush();

            Path target = Path.of(config.getOutputDirectory()).toAbsolutePath();
            generatorService.generate(config, target);

            // Ensure the chosen configuration format is reflected on disk
            EnvFileWriter.normalizeConfigFormat(target, config.isUseYaml());

            out.println(ansi().cursorUpLine().eraseLine()
                            .fgBrightGreen().a("  ✔ Project generated → " + target).reset());

            // ── Post-processing ───────────────────────────────────────────
            if (config.isCreateEnvFiles()) {
                EnvFileWriter.writeEnvFiles(target, config.isUseYaml());
                out.println(ansi().fgBrightGreen()
                                .a("  ✔ Created .env and .env.example").reset());
            }

            if (config.isInitGit()) {
                out.print(ansi().fgBrightBlack().a("  ⟳ Initializing git repository...").reset());
                out.flush();
                GitInitializer.init(target);
                out.println(ansi().cursorUpLine().eraseLine()
                                .fgBrightGreen().a("  ✔ Git repository initialized").reset());
            }

            printSummary(config, target);

        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    /**
     * Runs one wizard step that is a plain single-choice prompt: prints the
     * step header, asks the user to pick an option, and confirms the choice.
     */
    private Option askOption(int step, String label, InitializrMetadata.SelectGroup<Option> group)
    {
        printStep(step, label);
        Option selected = prompt.selectOption(label, group.getValues(), group.getDefaultValue());
        printConfirm(label, selected.getName());
        return selected;
    }

    /**
     * Gradle project type ids from start.spring.io always start with "gradle"
     * ("gradle-project", "gradle-project-kotlin", "gradle-build").
     * Package-visible + static so this small piece of logic can be unit
     * tested directly, since WizardRunner as a whole has no I/O seam yet.
     */
    static boolean isGradleProjectType(String typeId)
    {
        return typeId != null && typeId.startsWith("gradle");
    }

    /**
     * Returns the boot versions that should be offered for the given project
     * type. Gradle project generation on start.spring.io currently always
     * fails to resolve the Spring Boot BOM for SNAPSHOT/milestone versions
     * (confirmed against the live API), so those are excluded when the
     * project type is Gradle — unless doing so would leave no options at all
     * (e.g. during an early pre-GA period where every listed version is
     * unstable), in which case the full list is returned as a fallback.
     * Package-visible + static so this logic can be unit tested directly.
     */
    static List<Option> selectableBootVersions(List<Option> allBootVersions, String projectTypeId)
    {
        if (!isGradleProjectType(projectTypeId))
            return allBootVersions;

        List<Option> stableOnly = allBootVersions.stream()
                        .filter(o -> !o.isUnstable())
                        .toList();
        return stableOnly.isEmpty() ? allBootVersions : stableOnly;
    }

    //Flatten all dependency groups into a single list, tagging each Dependency with its category.
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
        out.println(ansi().bold().fgBrightGreen().a(
                "   ███████╗██████╗ ██████╗ ██╗███╗   ██╗ ██████╗     ███████╗███████╗███████╗██████╗ "
        ).reset());
        out.println(ansi().bold().fgBrightGreen().a(
                "   ██╔════╝██╔══██╗██╔══██╗██║████╗  ██║██╔════╝     ██╔════╝██╔════╝██╔════╝██╔══██╗"
        ).reset());
        out.println(ansi().bold().fgBrightGreen().a(
                "   ███████╗██████╔╝██████╔╝██║██╔██╗ ██║██║  ███╗    ███████╗█████╗  █████╗  ██║  ██║"
        ).reset());
        out.println(ansi().bold().fgBrightGreen().a(
                "   ╚════██║██╔═══╝ ██╔══██╗██║██║╚██╗██║██║   ██║    ╚════██║██╔══╝  ██╔══╝  ██║  ██║"
        ).reset());
        out.println(ansi().bold().fgBrightGreen().a(
                "   ███████║██║     ██║  ██║██║██║ ╚████║╚██████╔╝    ███████║███████╗███████╗██████╔╝"
        ).reset());
        out.println(ansi().bold().fgBrightGreen().a(
                "   ╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝ ╚══════╝     ╚══════╝╚══════╝╚══════╝╚═════╝ "
        ).reset());

        out.println();

        // Decorative separator
        out.println(ansi().fgBrightBlack().a(
                "   ───────────────────────────────────────────────────────────────────────────────"
        ).reset());

        out.println(ansi().bold().fgGreen().a(
                "                        Seed Your Spring Projects faster \uD83C\uDF31!"
        ).reset());

    }

    private void printStep(int num, String label)
    {
        out.println();
        out.println(ansi().bold().fgBrightCyan()
                        .a("  ┌── Step " + num + ": " + label + " ").reset());
    }

    private void printConfirm(String label, String value)
    {
        out.println(ansi().fgBrightGreen()
                        .a("  ✔ " + label + ": ").bold().a(value).reset());
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
            out.println(ansi().fgBrightCyan().a("  Dependencies       : ").reset()
                            .a(String.join(", ", config.getDependencies())));
        }
        out.println();
        out.println(ansi().bold().fgBrightGreen().a("  Happy coding!").reset());
        out.println();
    }
}
