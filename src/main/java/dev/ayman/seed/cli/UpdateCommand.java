package dev.ayman.seed.cli;

import dev.ayman.seed.service.UpdateService;
import dev.ayman.seed.util.TerminalUi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "update", aliases = {"check-update"},
        description = "Check for available Spring Seed updates from GitHub Releases",
        version = "seed 1.1.0",
        mixinStandardHelpOptions = true)
public class UpdateCommand implements Callable<Integer>
{
    @Option(names = {"-r", "--refresh", "-f", "--force"},
            description = "Force check online, bypassing the 24-hour local cache")
    public boolean forceRefresh;

    private final UpdateService updateService;
    private final PrintWriter injectedOut;
    private final PrintWriter injectedErr;

    public UpdateCommand()
    {
        this(new UpdateService(), null, null);
    }

    public UpdateCommand(UpdateService updateService)
    {
        this(updateService, null, null);
    }

    public UpdateCommand(UpdateService updateService, PrintWriter out, PrintWriter err)
    {
        this.updateService = updateService;
        this.injectedOut = out;
        this.injectedErr = err;
    }

    @Override
    public Integer call()
    {
        boolean manageAnsi = (injectedOut == null && injectedErr == null);
        if (manageAnsi)
        {
            TerminalUi.installConsole();
        }

        PrintWriter out = injectedOut != null ? injectedOut : new PrintWriter(System.out, true);
        PrintWriter err = injectedErr != null ? injectedErr : new PrintWriter(System.err, true);

        try
        {
            out.println();
            out.println(TerminalUi.faint("  ⟳ Checking for updates..."));
            out.flush();

            UpdateService.CheckResult result = updateService.checkExplicitly(forceRefresh);

            switch (result.status())
            {
                case UPDATE_AVAILABLE -> {
                    UpdateService.printNotice(out, result.currentVersion(), result.latestVersion());
                    return 0;
                }
                case UP_TO_DATE -> {
                    String v = result.currentVersion().startsWith("v") ? result.currentVersion() : "v" + result.currentVersion();
                    out.println(ansi().fgGreen().a("  ✔ Spring Seed is up to date (" + v + ").").reset());
                    out.println();
                    out.flush();
                    return 0;
                }
                case ERROR -> {
                    err.println(ansi().fgRed().a("  ✗ Failed to check for updates: " + result.errorMessage()).reset());
                    err.println();
                    err.flush();
                    return 1;
                }
            }
            return 0;
        }
        finally
        {
            if (manageAnsi)
            {
                TerminalUi.uninstallConsole();
            }
        }
    }
}
