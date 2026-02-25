package dev.ayman.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.model.InitializrMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parses the live metadata JSON fixture to verify POJO mapping.
 * The fixture is the response saved from start.spring.io/metadata/client.
 */
class MetadataParserTest {

    private static final String FIXTURE_JSON = """
            {
              "type": {
                "type": "action",
                "default": "gradle-project",
                "values": [
                  {"id": "gradle-project", "name": "Gradle - Groovy", "action": "/starter.zip",
                   "tags": {"build": "gradle", "dialect": "groovy", "format": "project"}},
                  {"id": "maven-project",  "name": "Maven",           "action": "/starter.zip",
                   "tags": {"build": "maven", "format": "project"}}
                ]
              },
              "language": {
                "type": "single-select",
                "default": "java",
                "values": [
                  {"id": "java",   "name": "Java"},
                  {"id": "kotlin", "name": "Kotlin"}
                ]
              },
              "bootVersion": {
                "type": "single-select",
                "default": "4.0.3.RELEASE",
                "values": [
                  {"id": "4.0.3.RELEASE",         "name": "4.0.3"},
                  {"id": "4.1.0.BUILD-SNAPSHOT",   "name": "4.1.0 (SNAPSHOT)"}
                ]
              },
              "packaging": {
                "type": "single-select",
                "default": "jar",
                "values": [
                  {"id": "jar", "name": "Jar"},
                  {"id": "war", "name": "War"}
                ]
              },
              "javaVersion": {
                "type": "single-select",
                "default": "17",
                "values": [
                  {"id": "21", "name": "21"},
                  {"id": "17", "name": "17"}
                ]
              },
              "groupId":    {"default": "com.example"},
              "artifactId": {"default": "demo"},
              "version":    {"default": "0.0.1-SNAPSHOT"},
              "name":       {"default": "demo"},
              "description":{"default": "Demo project for Spring Boot"},
              "packageName":{"default": "com.example.demo"},
              "dependencies": {
                "type": "hierarchical-multi-select",
                "values": [
                  {
                    "name": "Web",
                    "values": [
                      {"id": "web",     "name": "Spring Web",     "description": "Build RESTful APIs"},
                      {"id": "webflux", "name": "Spring Reactive Web", "description": "Reactive web"}
                    ]
                  },
                  {
                    "name": "SQL",
                    "values": [
                      {"id": "data-jpa", "name": "Spring Data JPA", "description": "JPA persistence"}
                    ]
                  }
                ]
              }
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesProjectTypes() throws Exception {
        InitializrMetadata meta = mapper.readValue(FIXTURE_JSON, InitializrMetadata.class);
        assertNotNull(meta.getType());
        assertEquals("gradle-project", meta.getType().getDefaultValue());
        assertEquals(2, meta.getType().getValues().size());
        assertEquals("Gradle - Groovy", meta.getType().getValues().get(0).getName());
    }

    @Test
    void parsesLanguages() throws Exception {
        InitializrMetadata meta = mapper.readValue(FIXTURE_JSON, InitializrMetadata.class);
        assertEquals("java", meta.getLanguage().getDefaultValue());
        assertEquals(2, meta.getLanguage().getValues().size());
    }

    @Test
    void parsesBootVersions() throws Exception {
        InitializrMetadata meta = mapper.readValue(FIXTURE_JSON, InitializrMetadata.class);
        assertEquals("4.0.3.RELEASE", meta.getBootVersion().getDefaultValue());
        // SNAPSHOT version is flagged as unstable
        assertTrue(meta.getBootVersion().getValues().get(1).isUnstable());
        assertFalse(meta.getBootVersion().getValues().get(0).isUnstable());
    }

    @Test
    void parsesPackagingAndJavaVersion() throws Exception {
        InitializrMetadata meta = mapper.readValue(FIXTURE_JSON, InitializrMetadata.class);
        assertEquals("jar", meta.getPackaging().getDefaultValue());
        assertEquals("17", meta.getJavaVersion().getDefaultValue());
    }

    @Test
    void parsesTextDefaults() throws Exception {
        InitializrMetadata meta = mapper.readValue(FIXTURE_JSON, InitializrMetadata.class);
        assertEquals("com.example", meta.getGroupId().getDefaultValue());
        assertEquals("demo", meta.getArtifactId().getDefaultValue());
        assertEquals("0.0.1-SNAPSHOT", meta.getVersion().getDefaultValue());
        assertEquals("com.example.demo", meta.getPackageName().getDefaultValue());
    }

    @Test
    void parsesDependencyGroups() throws Exception {
        InitializrMetadata meta = mapper.readValue(FIXTURE_JSON, InitializrMetadata.class);
        assertNotNull(meta.getDependencies());
        assertEquals(2, meta.getDependencies().getValues().size());
        assertEquals("Web", meta.getDependencies().getValues().get(0).getName());
        assertEquals(2, meta.getDependencies().getValues().get(0).getValues().size());
    }

    @Test
    void projectTypeIsProjectFormat() throws Exception {
        InitializrMetadata meta = mapper.readValue(FIXTURE_JSON, InitializrMetadata.class);
        assertTrue(meta.getType().getValues().get(0).isProjectFormat());
    }
}
