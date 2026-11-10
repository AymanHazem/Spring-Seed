package dev.ayman.seed.util;

import dev.ayman.seed.wizard.ProjectConfig;
import org.fusesource.jansi.AnsiConsole;

import java.io.Console;
import java.io.PrintWriter;
import java.nio.file.Path;

import static org.fusesource.jansi.Ansi.*;

/**
 * Shared terminal setup, NO_COLOR handling, and summary rendering.
 */
public final class TerminalUi
{
    private TerminalUi() {}

    public static void installConsole()
    {
        String noColor = System.getenv("NO_COLOR");
        String term = System.getenv("TERM");
        String cliColorForce = System.getenv("CLICOLOR_FORCE");

        if (isNoColorRequested(noColor) || "dumb".equalsIgnoreCase(term))
        {
            setEnabled(false);
            if (System.getProperty("jansi.noreset") == null)
            {
                System.setProperty("jansi.noreset", "true");
            }
        }

        if (System.getProperty("jansi.mode") == null && System.getProperty("jansi.out.mode") == null)
        {
            String mode = resolveJansiMode(noColor, term, cliColorForce, isConsoleTerminal());
            if (mode != null)
            {
                System.setProperty("jansi.mode", mode);
            }
        }
        AnsiConsole.systemInstall();
    }

    public static void uninstallConsole()
    {
        AnsiConsole.systemUninstall();
    }

    static boolean isNoColorRequested(String noColor)
    {
        return noColor != null && !noColor.isEmpty();
    }

    static boolean isConsoleTerminal()
    {
        Console console = System.console();
        return console != null && console.isTerminal();
    }

    // Pure-JDK TTY fallback keeps colors enabled in native images even when Jansi JNI cannot load.
    static String resolveJansiMode(String noColor, String term, String cliColorForce, boolean isTerminal)
    {
        if (isNoColorRequested(noColor) || "dumb".equalsIgnoreCase(term))
            return "strip";
        if (cliColorForce != null && !cliColorForce.isEmpty() && !"0".equals(cliColorForce))
            return "force";
        if (isTerminal)
            return "force";
        return null;
    }

    public static String faint(String text)
    {
        return ansi().a(Attribute.INTENSITY_FAINT).a(text).reset().toString();
    }

    public static void printProjectSummary(PrintWriter out, ProjectConfig config, Path target)
    {
        out.println();
        out.println(ansi().bold().fgGreen().a("  ╔══════════════════════════════════════════════╗").reset());
        out.println(ansi().bold().fgGreen().a("  ║               Project Created                ║").reset());
        out.println(ansi().bold().fgGreen().a("  ╚══════════════════════════════════════════════╝").reset());
        out.println();
        out.println(ansi().fgCyan().a("  Location    : ").reset().bold().a(target).reset());
        out.println(ansi().fgCyan().a("  Type        : ").reset().a(config.getType()));
        out.println(ansi().fgCyan().a("  Language    : ").reset().a(config.getLanguage()));
        out.println(ansi().fgCyan().a("  Spring Boot : ").reset().a(config.getBootVersion()));
        out.println(ansi().fgCyan().a("  Group       : ").reset().a(config.getGroupId()));
        out.println(ansi().fgCyan().a("  Artifact    : ").reset().a(config.getArtifactId()));
        out.println(ansi().fgCyan().a("  Java        : ").reset().a(config.getJavaVersion()));
        if (config.getDependencies() != null && !config.getDependencies().isEmpty())
        {
            out.println(ansi().fgCyan().a("  Dependencies: ").reset()
                    .a(String.join(", ", config.getDependencies())));
        }
        out.println();
        out.println(ansi().bold().fgGreen().a("  Happy coding!").reset());
        out.println();
    }
}
