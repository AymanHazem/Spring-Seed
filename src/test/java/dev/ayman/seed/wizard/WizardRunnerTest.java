package dev.ayman.seed.wizard;

import dev.ayman.seed.model.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;


import static org.junit.jupiter.api.Assertions.*;

class WizardRunnerTest {

    @Test
    void outputDirectoryUsesArtifactFolderInsideExistingParent(@TempDir Path parent) throws Exception {
        Path existingFile = parent.resolve("notes.txt");
        Files.writeString(existingFile, "Keep this file");

        Path target = WizardRunner.resolveOutputDirectory(parent, "hazem");

        assertEquals(parent.resolve("hazem").toAbsolutePath().normalize(), target);
        assertEquals("Keep this file", Files.readString(existingFile));
        assertFalse(Files.exists(target));
    }

    @Test
    void outputDirectoryDefaultsToArtifactFolderInsideHome() {
        assertEquals(Path.of(System.getProperty("user.home"), "hazem").toAbsolutePath().normalize(),
                WizardRunner.resolveOutputDirectory(null, "hazem"));
    }

    @Test
    void outputDirectoryNormalizesRelativeParent() {
        assertEquals(Path.of("projects", "hazem").toAbsolutePath().normalize(),
                WizardRunner.resolveOutputDirectory(Path.of("projects", "temp", ".."), "hazem"));
    }

    @Test
    void outputDirectoryRejectsArtifactsThatAreNotSingleDirectoryNames(@TempDir Path parent) {
        for (String artifact : List.of("", " ", ".", "..", "../hazem", "/hazem", "nested/hazem", "nested\\hazem")) {
            assertThrows(IllegalArgumentException.class,
                    () -> WizardRunner.resolveOutputDirectory(parent, artifact), artifact);
        }
        assertThrows(IllegalArgumentException.class,
                () -> WizardRunner.resolveOutputDirectory(parent, null));
    }

    private static Option option(String id, String name) {
        try {
            String json = String.format("{\"id\":\"%s\",\"name\":\"%s\"}", id, name);
            return new ObjectMapper().readValue(json, Option.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void isGradleProjectTypeRecognizesAllGradleVariants() {
        assertTrue(WizardRunner.isGradleProjectType("gradle-project"));
        assertTrue(WizardRunner.isGradleProjectType("gradle-project-kotlin"));
        assertTrue(WizardRunner.isGradleProjectType("gradle-build"));
    }

    @Test
    void isGradleProjectTypeRejectsMavenAndNull() {
        assertFalse(WizardRunner.isGradleProjectType("maven-project"));
        assertFalse(WizardRunner.isGradleProjectType("maven-build"));
        assertFalse(WizardRunner.isGradleProjectType(null));
    }

    @Test
    void snapshotBootVersionIsFlaggedUnstable() {
        // Regression test for the real-world case reported against Seed: picking
        // the top-listed (newest, but SNAPSHOT) Spring Boot version together with
        // Gradle is a combination known to fail on start.spring.io. The wizard
        // relies on Option.isUnstable() + WizardRunner.isGradleProjectType() to
        // decide when to show a warning for this combination.
        Option snapshot = option("4.1.1.BUILD-SNAPSHOT", "4.1.1 (SNAPSHOT)");
        Option stable = option("4.1.0.RELEASE", "4.1.0");

        assertTrue(snapshot.isUnstable());
        assertFalse(stable.isUnstable());
        assertTrue(WizardRunner.isGradleProjectType("gradle-project") && snapshot.isUnstable(),
                "this combination should trigger the Gradle + SNAPSHOT warning");
    }
}
