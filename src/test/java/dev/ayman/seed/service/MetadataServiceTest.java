package dev.ayman.seed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.model.InitializrMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MetadataServiceTest {

    @Test
    void acceptsCurrentInitializrMetadataWithoutNameOrDescriptionDefaults() throws Exception {
        String json = """
                {
                  "type": {
                    "default": "maven-project",
                    "values": [{
                      "id": "maven-project",
                      "name": "Maven",
                      "tags": {"format": "project"}
                    }]
                  },
                  "language": {
                    "default": "java",
                    "values": [{"id": "java", "name": "Java"}]
                  },
                  "bootVersion": {
                    "default": "4.1.0.RELEASE",
                    "values": [{"id": "4.1.0.RELEASE", "name": "4.1.0"}]
                  },
                  "packaging": {
                    "default": "jar",
                    "values": [{"id": "jar", "name": "Jar"}]
                  },
                  "javaVersion": {
                    "default": "17",
                    "values": [{"id": "17", "name": "17"}]
                  },
                  "groupId": {"default": "com.example"},
                  "artifactId": {"default": "demo"},
                  "version": {"default": "0.0.1-SNAPSHOT"},
                  "name": {"type": "text"},
                  "description": {"type": "text"}
                }
                """;
        InitializrMetadata metadata = new ObjectMapper().readValue(json, InitializrMetadata.class);

        assertDoesNotThrow(() -> MetadataService.validate(metadata));
    }
}
