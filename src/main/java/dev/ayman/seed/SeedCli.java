package dev.ayman.seed;

import dev.ayman.seed.cli.InitCommand;
import dev.ayman.seed.cli.NonInteractiveRunner;
import dev.ayman.seed.cli.ProjectOptions;
import dev.ayman.seed.cli.UpdateCommand;
import dev.ayman.seed.wizard.WizardCancelledException;
import dev.ayman.seed.wizard.WizardRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Entry point for the Seed CLI.
 */
@Command(name = "seed", version = "seed 1.1.2", mixinStandardHelpOptions = true,
        subcommands = { InitCommand.class, UpdateCommand.class, CommandLine.HelpCommand.class },
        description = {
                "",
                "@|bold,green  Seed: Spring Boot project scaffolding CLI|@",
                " Bootstrap Spring Boot projects interactively or via flags from your terminal.",
                " Uses @|underline https://start.spring.io|@",
                ""
        }, footer = {
                "",
                "  Examples:",
                "    @|bold seed|@                                : start the interactive wizard",
                "    @|bold seed init my-app -d web,data-jpa|@     : scaffold without prompts",
                "    @|bold seed my-app -d web,lombok --java 21|@ : quick generation without 'init'",
                "    @|bold seed update|@                         : check GitHub Releases for updates",
                "    @|bold seed -p ./projects|@                  : create ./projects/<artifact>",
                "    @|bold seed --refresh|@                      : force metadata refresh",
                ""
        })
public class SeedCli implements Callable<Integer>
{
    public static final String VERSION = "1.1.2";

    @Option(names = { "-r", "--refresh" }, description = "Force refresh the cached metadata from start.spring.io")
    private boolean refresh;

    @Option(names = "--check-update", description = "Check for Spring Seed updates and exit")
    private boolean checkUpdate;

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
            if (checkUpdate)
            {
                return new UpdateCommand().call();
            }

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
