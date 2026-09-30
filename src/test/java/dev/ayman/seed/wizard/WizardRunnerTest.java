package dev.ayman.seed.wizard;

import dev.ayman.seed.model.Option;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;


import static org.junit.jupiter.api.Assertions.*;

class WizardRunnerTest {

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
