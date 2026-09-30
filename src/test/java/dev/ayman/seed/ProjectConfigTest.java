package dev.ayman.seed;

import dev.ayman.seed.wizard.ProjectConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectConfigTest {

    @Test
    void useYamlSetterAndGetterRoundTrip() {
        ProjectConfig config = new ProjectConfig();
        assertFalse(config.isUseYaml(), "should default to false");
        config.setUseYaml(true);
        assertTrue(config.isUseYaml(), "setUseYaml must persist the value for later retrieval");
    }

    @Test
    void createEnvFilesSetterAndGetterRoundTrip() {
        ProjectConfig config = new ProjectConfig();
        config.setCreateEnvFiles(true);
        assertTrue(config.isCreateEnvFiles());
    }

    @Test
    void initGitSetterAndGetterRoundTrip() {
        ProjectConfig config = new ProjectConfig();
        config.setInitGit(true);
        assertTrue(config.isInitGit());
    }

    @Test
    void outputDirectorySetterAndGetterRoundTrip() {
        ProjectConfig config = new ProjectConfig();
        config.setOutputDirectory("/home/user/my-app");
        assertEquals("/home/user/my-app", config.getOutputDirectory());
    }
}
