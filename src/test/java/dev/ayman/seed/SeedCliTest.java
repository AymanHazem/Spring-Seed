package dev.ayman.seed;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedCliTest
{
    @Test
    void pathOptionAcceptsShortName()
    {
        CommandLine.ParseResult result = new CommandLine(new SeedCli())
                .parseArgs("-p", "/tmp/my-project");

        assertEquals(Path.of("/tmp/my-project"), result.matchedOptionValue("-p", null));
    }

    @Test
    void pathOptionAcceptsLongName()
    {
        CommandLine.ParseResult result = new CommandLine(new SeedCli())
                .parseArgs("--path", "projects/my-project");

        assertEquals(Path.of("projects/my-project"), result.matchedOptionValue("--path", null));
    }

    @Test
    void testHelpAndVersionOptionsAreAvailable()
    {
        CommandLine cmd = new CommandLine(new SeedCli());
        assertTrue(cmd.getCommandSpec().optionsMap().containsKey("-h"));
        assertTrue(cmd.getCommandSpec().optionsMap().containsKey("--help"));
        assertTrue(cmd.getCommandSpec().optionsMap().containsKey("-V"));
        assertTrue(cmd.getCommandSpec().optionsMap().containsKey("--version"));
        assertTrue(cmd.getCommandSpec().optionsMap().containsKey("-v"));
        assertTrue(cmd.getCommandSpec().optionsMap().containsKey("--project-version"));
    }
}
