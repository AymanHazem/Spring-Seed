package dev.ayman.seed.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.wizard.ProjectConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
/**
 * Downloads a project ZIP from start.spring.io and extracts it to the target
 * directory.
 */
public class ProjectGeneratorService
{
    private static final String BASE_URL = "https://start.spring.io/starter.zip";
    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;

    public ProjectGeneratorService()
    {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void generate(ProjectConfig config, Path outputDir) throws IOException, InterruptedException
    {
        String url = buildUrl(config);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "seed-cli/1.0")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200)
            throw new IOException(buildFailureMessage(config, response.statusCode(), response.body()));

        extractZip(response.body(), outputDir);
        postProcessGeneratedProject(config, outputDir);
    }

    /** Surfaces the error "message" from start.spring.io JSON and adds a hint for known upstream Gradle BOM failures. */
    // Package-private for unit tests.
    String buildFailureMessage(ProjectConfig config, int statusCode, byte[] body)
    {
        String raw = new String(body, StandardCharsets.UTF_8);
        String detail = extractJsonMessage(raw);
        String shown = (detail != null && !detail.isBlank()) ? detail : raw;

        StringBuilder message = new StringBuilder("Failed to generate project: HTTP ").append(statusCode)
                .append(": ").append(shown);

        boolean isGradleProject = config.getType() != null && config.getType().startsWith("gradle");
        boolean looksLikeBomResolutionFailure = shown.contains("could not be resolved") || shown.contains("Bom '");

        if (statusCode >= 500 && isGradleProject && looksLikeBomResolutionFailure)
        {
            message.append("\n  This looks like a known issue on start.spring.io's Gradle build generation ")
                    .append("(it currently fails to resolve the Spring Boot BOM for Gradle projects), ")
                    .append("not a bug in Seed. Try again with Maven, or retry later once start.spring.io ")
                    .append("resolves the issue on their end.");
        }

        return message.toString();
    }

    private String extractJsonMessage(String raw)
    {
        try
        {
            JsonNode messageNode = ERROR_BODY_MAPPER.readTree(raw).get("message");
            return messageNode != null ? messageNode.asText() : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    // Package-private for unit tests.
    String buildUrl(ProjectConfig config)
    {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("type", config.getType());
        params.put("language", config.getLanguage());
        params.put("bootVersion", stripReleaseSuffix(config.getBootVersion()));
        params.put("groupId", config.getGroupId());
        params.put("artifactId", config.getArtifactId());
        params.put("name", config.getName());
        params.put("description", config.getDescription());
        params.put("packageName", config.getPackageName());
        params.put("packaging", config.getPackaging());
        params.put("javaVersion", config.getJavaVersion());
        params.put("version", config.getVersion());

        List<String> deps = config.getDependencies();
        if (deps != null && !deps.isEmpty())
            params.put("dependencies", String.join(",", deps));

        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&", BASE_URL + "?", ""));
    }

    private static String stripReleaseSuffix(String bootVersion)
    {
        if (bootVersion != null && bootVersion.endsWith(".RELEASE"))
            return bootVersion.substring(0, bootVersion.length() - ".RELEASE".length());
        return bootVersion;
    }

    private String enc(String value)
    {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    // Package-private for unit tests.
    void extractZip(byte[] zipBytes, Path targetDir) throws IOException
    {
        // Normalize to an absolute path so relative targets work with startsWith().
        Path canonicalTarget = targetDir.toAbsolutePath().normalize();

        // Reject non-empty directories because Files.write truncates matching paths.
        if (isNonEmptyDirectory(canonicalTarget))
            throw new IOException("Target directory already exists and is not empty: " + canonicalTarget
                    + ": refusing to overwrite existing files. Choose an empty or new directory.");

        Files.createDirectories(canonicalTarget);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes)))
        {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null)
            {
                Path resolved = canonicalTarget.resolve(entry.getName()).normalize();

                // Prevent zip slip outside canonicalTarget.
                if (!resolved.startsWith(canonicalTarget))
                    throw new IOException("Zip slip attack detected: " + entry.getName());

                if (entry.isDirectory())
                    Files.createDirectories(resolved);
                else
                {
                    Files.createDirectories(resolved.getParent());
                    Files.write(resolved, zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
    }

    private static boolean isNonEmptyDirectory(Path dir) throws IOException
    {
        if (!Files.isDirectory(dir))
            return false;
        try (var stream = Files.list(dir))
        {
            return stream.findAny().isPresent();
        }
    }

    private void postProcessGeneratedProject(ProjectConfig config, Path outputDir) throws IOException
    {
        Path pom = outputDir.resolve("pom.xml");
        if (!Files.exists(pom))
            return;

        String bootVersion = config.getBootVersion();
        if (bootVersion == null || !bootVersion.endsWith(".RELEASE"))
            return;

        String from = "<version>" + bootVersion + "</version>";
        String to = "<version>" + stripReleaseSuffix(bootVersion) + "</version>";
        String content = Files.readString(pom, StandardCharsets.UTF_8);
        if (content.contains(from))
            Files.writeString(pom, content.replace(from, to), StandardCharsets.UTF_8);
    }
}