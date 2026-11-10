package dev.ayman.seed.cli;

import dev.ayman.seed.SeedCli;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "init", aliases = {"new", "create"},
        description = "Scaffold a Spring Boot project non-interactively using flags",
        version = "seed " + SeedCli.VERSION,
        mixinStandardHelpOptions = true)
public class InitCommand implements Callable<Integer>
{
    @Mixin
    public ProjectOptions options = new ProjectOptions();

    @Option(names = { "-r", "--refresh" }, description = "Force refresh cached metadata from start.spring.io")
    public boolean refresh;

    @Override
    public Integer call()
    {
        try
        {
            return new NonInteractiveRunner(options, refresh).run();
        }
        catch (Exception e)
        {
            System.err.println("\n  ✗ Error: " + e.getMessage());
            if (System.getProperty("seed.debug") != null)
                e.printStackTrace();

            return 1;
        }
    }
}
