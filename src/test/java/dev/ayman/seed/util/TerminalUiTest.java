package dev.ayman.seed.util;

import dev.ayman.seed.wizard.ProjectConfig;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerminalUiTest {

    @Test
    void resolveJansiModeRespectsNoColorAndDumbTerminal() {
        assertEquals("strip", TerminalUi.resolveJansiMode("1", "xterm-256color", "1", true));
        assertEquals("strip", TerminalUi.resolveJansiMode(null, "dumb", "1", true));
    }

    @Test
    void resolveJansiModeForcesColorsForInteractiveTerminalOrCliColorForce() {
        assertEquals("force", TerminalUi.resolveJansiMode(null, "xterm-256color", null, true));
        assertEquals("force", TerminalUi.resolveJansiMode("", "xterm", "1", false));
        assertNull(TerminalUi.resolveJansiMode(null, "xterm", "0", false));
        assertNull(TerminalUi.resolveJansiMode(null, null, null, false));
    }

    @Test
    void printProjectSummaryFormatsAlignedFieldsWithoutWideLines() {
        ProjectConfig config = new ProjectConfig();
        config.setType("maven-project");
        config.setLanguage("java");
        config.setBootVersion("4.0.3");
        config.setGroupId("dev.ayman");
        config.setArtifactId("demo");
        config.setJavaVersion("21");
        config.setDependencies(List.of("web", "data-jpa"));

        StringWriter sw = new StringWriter();
        TerminalUi.printProjectSummary(new PrintWriter(sw, true), config, Path.of("/tmp/demo"));

        String output = sw.toString().replaceAll("\\u001B\\[[;\\d]*m", "");
        assertTrue(output.contains("Project Created"));
        assertTrue(output.contains("Location    :"));
        assertTrue(output.contains("Dependencies: web, data-jpa"));
        assertFalse(output.contains("—"));
        assertFalse(output.contains("🌱"));
        for (String line : output.split("\\R")) {
            assertTrue(line.length() <= 80, "line exceeds 80 columns: " + line);
        }
    }
}
