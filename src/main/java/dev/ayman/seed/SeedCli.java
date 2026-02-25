package dev.ayman.seed;

import dev.ayman.seed.wizard.WizardRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Entry point for the Seed CLI tool.
 * Usage:
 * seed — runs the interactive wizard
 * seed --refresh — re-fetches metadata ignoring cache
 * seed --version — prints version
 * seed --help — prints usage
 */
@Command(name = "seed", version = "seed 1.0.0", mixinStandardHelpOptions = true, description = {
        "",
        "@|bold,green  Seed — Spring Boot projects, planted fast 🌱 CLI|@",
        " Bootstrap Spring Boot projects interactively from your terminal.",
        " Powered by @|underline https://start.spring.io|@",
        ""
}, footer = {
        "",
        "  Examples:",
        "    @|yellow seed|@            — start the interactive wizard",
        "    @|yellow seed --refresh|@  — force re-fetch of metadata from start.spring.io",
        ""
})
public class SeedCli implements Callable<Integer> {

    @Option(names = { "-r", "--refresh" }, description = "Force refresh the cached metadata from start.spring.io")
    private boolean refresh;

    @Override
    public Integer call() {
        try {
            new WizardRunner(refresh).run();
            return 0;
        } catch (Exception e) {
            System.err.println("\n  ✗ Error: " + e.getMessage());
            if (System.getProperty("seed.debug") != null) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SeedCli())
                .setColorScheme(CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.AUTO))
                .execute(args);
        System.exit(exitCode);
    }
}
