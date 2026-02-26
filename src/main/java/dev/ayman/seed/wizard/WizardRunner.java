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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.fusesource.jansi.Ansi.ansi;
public class WizardRunner
{
        private final MetadataService metadataService;
        private final ProjectGeneratorService generatorService;
        private final SelectionPrompt prompt;
        private final PrintWriter out;
        private final BufferedReader in;
        private final boolean forceRefresh;

        public WizardRunner(boolean forceRefresh)
        {
                this.metadataService = new MetadataService();
                this.generatorService = new ProjectGeneratorService();
                this.prompt = new SelectionPrompt();
                this.out = new PrintWriter(System.out, true);
                this.in = new BufferedReader(new InputStreamReader(System.in));
                this.forceRefresh = forceRefresh;
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

                        printStep(2, "Language");
                        Option selectedLang = prompt.selectOption("Language",
                                        meta.getLanguage().getValues(),
                                        meta.getLanguage().getDefaultValue());
                        config.setLanguage(selectedLang.getId());
                        printConfirm("Language", selectedLang.getName());

                        printStep(3, "Spring Boot Version");
                        Option selectedBoot = prompt.selectOption("Spring Boot Version",
                                        meta.getBootVersion().getValues(),
                                        meta.getBootVersion().getDefaultValue());
                        config.setBootVersion(selectedBoot.getId());
                        printConfirm("Spring Boot", selectedBoot.getName());

                        printStep(4, "Dependencies");
                        List<Dependency> allDeps = flattenDependencies(meta);
                        DependencySelector depSelector = new DependencySelector(allDeps);
                        List<String> selectedDeps = depSelector.select();
                        config.setDependencies(selectedDeps);
                        printConfirm("Dependencies",
                                        selectedDeps.isEmpty() ? "(none)" : String.join(", ", selectedDeps));

                        printStep(5, "Project Metadata");
                        new MetadataForm(meta).fill(config);

                        // ── Step 6: Packaging ─────────────────────────────────────────
                        printStep(6, "Packaging");
                        Option selectedPkg = prompt.selectOption("Packaging",
                                        meta.getPackaging().getValues(),
                                        meta.getPackaging().getDefaultValue());
                        config.setPackaging(selectedPkg.getId());
                        printConfirm("Packaging", selectedPkg.getName());

                        // ── Step 7: Java version ──────────────────────────────────────
                        printStep(7, "Java Version");
                        Option selectedJava = prompt.selectOption("Java Version",
                                        meta.getJavaVersion().getValues(),
                                        meta.getJavaVersion().getDefaultValue());
                        config.setJavaVersion(selectedJava.getId());
                        printConfirm("Java version", selectedJava.getName());

                        // ── Step 8: Output directory ──────────────────────────────────
                        printStep(8, "Output Directory");
                        String homeDir = System.getProperty("user.home");
                        String defaultDir = config.getArtifactId();
                        out.print(ansi().fgBrightYellow()
                                        .a("  Project will be under your home directory [" + defaultDir + "] ")
                                        .reset());
                        out.flush();
                        String dirInput = in.readLine();
                        String subDir = (dirInput == null || dirInput.isBlank()) ? defaultDir : dirInput.trim();
                        Path outputPath = Paths.get(homeDir, subDir).toAbsolutePath().normalize();
                        String outputDir = outputPath.toString();
                        config.setOutputDirectory(outputDir);

                        // ── Step 9: Config format ─────────────────────────────────────
                        boolean useYaml = prompt.askYesNo("Use YAML (application.yml) instead of .properties?");
                        config.setUseYaml(useYaml);

                        // ── Step 10: .env files ───────────────────────────────────────
                        boolean createEnv = prompt.askYesNo("Create .env and .env.example files?");
                        config.setCreateEnvFiles(createEnv);

                        // ── Step 11: Git init ─────────────────────────────────────────
                        boolean initGit = prompt.askYesNo("Initialize a git repository?");
                        config.setInitGit(initGit);

                        // ── Generate project ──────────────────────────────────────────
                        out.println();
                        out.print(ansi().fgBrightBlack().a("  ⟳ Generating project from start.spring.io...").reset());
                        out.flush();

                        Path target = Path.of(outputDir).toAbsolutePath();
                        generatorService.generate(config, target);

                        // Ensure the chosen configuration format is reflected on disk
                        EnvFileWriter.normalizeConfigFormat(target, useYaml);

                        out.println(ansi().cursorUpLine().eraseLine()
                                        .fgBrightGreen().a("  ✔ Project generated → " + target).reset());

                        // ── Post-processing ───────────────────────────────────────────
                        if (createEnv) {
                                EnvFileWriter.writeEnvFiles(target, useYaml);
                                out.println(ansi().fgBrightGreen()
                                                .a("  ✔ Created .env and .env.example").reset());
                        }

                        if (initGit) {
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
                "   ╚══════╝╚═╝     ╚═╝  ╚═╝╚═╝╚═╝  ╚═══╝ ╚═════╝     ╚══════╝╚══════╝╚══════╝╚═════╝ "
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