package dev.ayman.seed.cli;

import dev.ayman.seed.SeedCli;
import dev.ayman.seed.service.UpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCommandTest
{
    private StringWriter outCapture;
    private StringWriter errCapture;
    private PrintWriter outWriter;
    private PrintWriter errWriter;

    @BeforeEach
    void setUp()
    {
        outCapture = new StringWriter();
        errCapture = new StringWriter();
        outWriter = new PrintWriter(outCapture);
        errWriter = new PrintWriter(errCapture);
    }

    static class StubUpdateService extends UpdateService
    {
        private final CheckResult stubResult;

        StubUpdateService(CheckResult stubResult)
        {
            super(null, null, "http://localhost", "1.1.0");
            this.stubResult = stubResult;
        }

        @Override
        public CheckResult checkExplicitly(boolean forceRefresh)
        {
            return stubResult;
        }
    }

    @Test
    void testUpdateCommandWhenUpdateAvailable()
    {
        UpdateService.CheckResult stubResult = new UpdateService.CheckResult(
                UpdateService.CheckStatus.UPDATE_AVAILABLE, "1.1.0", "v1.2.0", null);

        UpdateCommand command = new UpdateCommand(new StubUpdateService(stubResult), outWriter, errWriter);
        int exitCode = command.call();

        assertEquals(0, exitCode);
        String output = outCapture.toString();
        assertTrue(output.contains("Checking for updates..."));
        assertTrue(output.contains("Update available!"));
        assertTrue(output.contains("v1.1.0"));
        assertTrue(output.contains("v1.2.0"));
        assertTrue(output.contains("install.sh | sh to update."));
    }

    @Test
    void testUpdateCommandWhenUpToDate()
    {
        UpdateService.CheckResult stubResult = new UpdateService.CheckResult(
                UpdateService.CheckStatus.UP_TO_DATE, "1.1.0", "v1.1.0", null);

        UpdateCommand command = new UpdateCommand(new StubUpdateService(stubResult), outWriter, errWriter);
        int exitCode = command.call();

        assertEquals(0, exitCode);
        String output = outCapture.toString();
        assertTrue(output.contains("Checking for updates..."));
        assertTrue(output.contains("Spring Seed is up to date (v1.1.0)."));
    }

    @Test
    void testUpdateCommandWhenError()
    {
        UpdateService.CheckResult stubResult = new UpdateService.CheckResult(
                UpdateService.CheckStatus.ERROR, "1.1.0", null, "Network connection timed out");

        UpdateCommand command = new UpdateCommand(new StubUpdateService(stubResult), outWriter, errWriter);
        int exitCode = command.call();

        assertEquals(1, exitCode);
        String errOutput = errCapture.toString();
        assertTrue(errOutput.contains("Failed to check for updates: Network connection timed out"));
    }

    @Test
    void testSeedCliSubcommandRegistration()
    {
        SeedCli cli = new SeedCli();
        CommandLine cmd = new CommandLine(cli);

        assertTrue(cmd.getSubcommands().containsKey("update"), "Subcommand 'update' should be registered");
        assertTrue(cmd.getSubcommands().containsKey("check-update"), "Alias 'check-update' should be registered");
    }
}
