package dev.ayman.seed;

import dev.ayman.seed.cli.InitCommand;
import dev.ayman.seed.cli.NonInteractiveRunner;
import dev.ayman.seed.cli.ProjectOptions;
import dev.ayman.seed.wizard.WizardCancelledException;
import dev.ayman.seed.wizard.WizardRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Entry point for the Seed CLI tool.
 * Usage:
 * seed — runs the interactive wizard
 * seed init my-app -d web,data-jpa — generates project non-interactively
 * seed -p /path/to/parent — creates an artifact-named project folder inside the given path
 * seed --refresh — re-fetches metadata ignoring cache
 * seed --version — prints version
 * seed --help — prints usage
 */
@Command(name = "seed", version = "seed 1.1.0", mixinStandardHelpOptions = true,
        subcommands = { InitCommand.class, CommandLine.HelpCommand.class },
        description = {
                "",
                "@|bold,green  Seed — Spring Boot projects, planted fast 🌱 CLI|@",
                " Bootstrap Spring Boot projects interactively or via flags from your terminal.",
                " Powered by @|underline https://start.spring.io|@",
                ""
        }, footer = {
                "",
                "  Examples:",
                "    @|yellow seed|@                                — start the interactive wizard",
                "    @|yellow seed init my-app -d web,data-jpa|@     — scaffold non-interactively in one second",
                "    @|yellow seed my-app -d web,lombok --java 21|@ — quick generation without 'init'",
                "    @|yellow seed -p ./projects|@                  — create ./projects/<artifact>",
                "    @|yellow seed --refresh|@                      — force re-fetch of metadata from start.spring.io",
                ""
        })
public class SeedCli implements Callable<Integer>
{
    @Option(names = { "-r", "--refresh" }, description = "Force refresh the cached metadata from start.spring.io")
    private boolean refresh;

    @Mixin
    private ProjectOptions options = new ProjectOptions();

    public ProjectOptions getOptions()
    {
        return options;
    }

    @Override
    public Integer call()
    {
        try
        {
            if (options.hasExplicitGenerationOptions())
            {
                return new NonInteractiveRunner(options, refresh).run();
            }

            new WizardRunner(refresh, options.outputPath).run();
            return 0;
        }
        catch (WizardCancelledException e)
        {
            System.err.println("\n  Wizard cancelled.");
            return 130;
        }
        catch (Exception e)
        {
            System.err.println("\n  ✗ Error: " + e.getMessage());
            if (System.getProperty("seed.debug") != null)
                e.printStackTrace();

            return 1;
        }
    }

    public static void main(String[] args)
    {
        int exitCode = new CommandLine(new SeedCli())
                .setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.AUTO))
                .execute(args);
        System.exit(exitCode);
    }
}
