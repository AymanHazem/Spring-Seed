package dev.ayman.seed.service;

import dev.ayman.seed.wizard.ProjectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProjectGeneratorServiceTest {

    private static byte[] zipWithSingleFile(String entryName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    void extractZipWritesFilesIntoEmptyTargetDirectory(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("new-project");
        byte[] zip = zipWithSingleFile("pom.xml", "<project></project>");

        new ProjectGeneratorService().extractZip(zip, target);

        assertEquals("<project></project>", Files.readString(target.resolve("pom.xml")));
    }

    @Test
    void extractZipRefusesToOverwriteNonEmptyTargetDirectory(@TempDir Path tempDir) throws Exception {
        // Regression test for Rank 5: extracting into a directory that already has
        // unrelated content must not silently overwrite it.
        Path target = tempDir.resolve("existing-project");
        Files.createDirectories(target);
        Path preexisting = target.resolve("pom.xml");
        Files.writeString(preexisting, "MY REAL PROJECT — DO NOT TOUCH");

        byte[] zip = zipWithSingleFile("pom.xml", "<project>generated</project>");

        ProjectGeneratorService service = new ProjectGeneratorService();
        IOException ex = assertThrows(IOException.class, () -> service.extractZip(zip, target));
        assertTrue(ex.getMessage().contains("not empty"));

        // The original file must be untouched.
        assertEquals("MY REAL PROJECT — DO NOT TOUCH", Files.readString(preexisting));
    }

    @Test
    void extractZipSucceedsWhenTargetDirectoryExistsButIsEmpty(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("empty-dir");
        Files.createDirectories(target);
        byte[] zip = zipWithSingleFile("pom.xml", "<project></project>");

        new ProjectGeneratorService().extractZip(zip, target);

        assertTrue(Files.exists(target.resolve("pom.xml")));
    }

    @Test
    void buildFailureMessageExtractsMessageFieldFromJsonErrorBody() {
        ProjectConfig config = new ProjectConfig();
        config.setType("maven-project");
        byte[] body = "{\"timestamp\":\"now\",\"status\":500,\"message\":\"Something went wrong\"}"
                .getBytes(StandardCharsets.UTF_8);

        String message = new ProjectGeneratorService().buildFailureMessage(config, 500, body);

        assertTrue(message.contains("Something went wrong"), "should surface the JSON 'message' field, got: " + message);
        assertFalse(message.contains("timestamp"), "should not just dump the full raw JSON blob");
    }

    @Test
    void buildFailureMessageAddsGradleHintForKnownBomResolutionFailure() {
        // Regression test for the real-world case reported against Seed: choosing
        // Gradle currently fails on start.spring.io's side because it can't resolve
        // the Spring Boot BOM for Gradle builds. Verified independently against the
        // live start.spring.io API (Maven succeeds, Gradle fails for every boot
        // version) — this is an upstream issue, not something Seed's request is
        // doing wrong, so the message should say so instead of just showing the
        // raw Spring stack trace text.
        ProjectConfig config = new ProjectConfig();
        config.setType("gradle-project");
        byte[] body = ("{\"message\":\"Failed to instantiate [GradleBuild]: Factory method " +
                "'gradleBuild' threw exception with message: Bom 'org.springframework.boot:" +
                "spring-boot-dependencies:4.1.0.RELEASE' could not be resolved\"}")
                .getBytes(StandardCharsets.UTF_8);

        String message = new ProjectGeneratorService().buildFailureMessage(config, 500, body);

        assertTrue(message.contains("start.spring.io"),
                "should explain this is an upstream start.spring.io issue, got: " + message);
        assertTrue(message.toLowerCase().contains("maven"),
                "should suggest Maven as a workaround, got: " + message);
    }

    @Test
    void buildUrlStripsReleaseSuffixFromBootVersion() {
        // Regression test: sending the raw ".RELEASE"-suffixed id (as returned by
        // start.spring.io's own metadata) breaks Gradle project generation with an
        // HTTP 500, because start.spring.io can't resolve that BOM coordinate on
        // Maven Central. Verified independently against the live start.spring.io
        // API: bootVersion=4.0.7.RELEASE + Gradle -> HTTP 500, bootVersion=4.0.7 +
        // Gradle -> HTTP 200. Maven works fine either way, so stripping the
        // suffix is safe for both build systems.
        ProjectConfig config = new ProjectConfig();
        config.setType("gradle-project");
        config.setLanguage("java");
        config.setBootVersion("4.0.7.RELEASE");
        config.setGroupId("com.example");
        config.setArtifactId("demo");
        config.setPackaging("jar");
        config.setJavaVersion("17");

        String url = new ProjectGeneratorService().buildUrl(config);

        assertTrue(url.contains("bootVersion=4.0.7"), "expected normalized bootVersion in URL, got: " + url);
        assertFalse(url.contains("RELEASE"), "the .RELEASE suffix must be stripped before sending, got: " + url);
    }

    @Test
    void buildUrlLeavesSnapshotBootVersionUntouched() {
        // Snapshot qualifiers are real, currently-published coordinates (unlike
        // the legacy ".RELEASE" convention), so they must not be stripped.
        ProjectConfig config = new ProjectConfig();
        config.setType("gradle-project");
        config.setBootVersion("4.1.1.BUILD-SNAPSHOT");

        String url = new ProjectGeneratorService().buildUrl(config);

        assertTrue(url.contains(enc("4.1.1.BUILD-SNAPSHOT")),
                "snapshot qualifier must be preserved as-is, got: " + url);
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void buildFailureMessageDoesNotAddGradleHintForMavenProjects() {
        // The same BOM-resolution error text should not trigger the Gradle-specific
        // hint when the project type is Maven (where this failure mode doesn't
        // occur in practice).
        ProjectConfig config = new ProjectConfig();
        config.setType("maven-project");
        byte[] body = "{\"message\":\"Bom 'x:y:1.0.RELEASE' could not be resolved\"}"
                .getBytes(StandardCharsets.UTF_8);

        String message = new ProjectGeneratorService().buildFailureMessage(config, 500, body);

        assertFalse(message.contains("start.spring.io's Gradle build generation"),
                "the Gradle-specific hint should not appear for Maven projects, got: " + message);
    }
}
