package dev.ayman.seed.service;
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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads a project ZIP from start.spring.io and extracts it to the target
 * directory.
 */
public class ProjectGeneratorService
{

    private static final String BASE_URL = "https://start.spring.io/starter.zip";

    private final HttpClient httpClient;

    public ProjectGeneratorService()
    {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Generates a Spring Boot project and extracts it to the given output
     * directory.
     */
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
            throw new IOException("Failed to generate project: HTTP " + response.statusCode()
                    + " — " + new String(response.body(), StandardCharsets.UTF_8));

        extractZip(response.body(), outputDir);
        postProcessGeneratedProject(config, outputDir);
    }

    private String buildUrl(ProjectConfig config)
    {
        StringBuilder sb = new StringBuilder(BASE_URL).append("?");
        sb.append("type=").append(enc(config.getType()));
        sb.append("&language=").append(enc(config.getLanguage()));
        sb.append("&bootVersion=").append(enc(config.getBootVersion()/*.substring*/));
        sb.append("&groupId=").append(enc(config.getGroupId()));
        sb.append("&artifactId=").append(enc(config.getArtifactId()));
        sb.append("&name=").append(enc(config.getName()));
        sb.append("&description=").append(enc(config.getDescription()));
        sb.append("&packageName=").append(enc(config.getPackageName()));
        sb.append("&packaging=").append(enc(config.getPackaging()));
        sb.append("&javaVersion=").append(enc(config.getJavaVersion()));
        sb.append("&version=").append(enc(config.getVersion()));

        List<String> deps = config.getDependencies();
        if (deps != null && !deps.isEmpty())
            sb.append("&dependencies=").append(enc(String.join(",", deps)));

        return sb.toString();
    }

    private String enc(String value)
    {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void extractZip(byte[] zipBytes, Path targetDir) throws IOException
    {
        // Normalise to absolute path BEFORE the loop so startsWith() works correctly
        // even when targetDir is a relative path like ./my-app
        Path canonicalTarget = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(canonicalTarget);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes)))
        {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null)
            {
                Path resolved = canonicalTarget.resolve(entry.getName()).normalize();

                // Zip slip protection — both paths are now absolute+normalized
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

    /**
     * Apply small adjustments to the generated project that are specific to how
     * Seed wants things to look, without changing what Spring Initializr
     * returns.
     */
    private void postProcessGeneratedProject(ProjectConfig config, Path outputDir) throws IOException
    {
        Path pom = outputDir.resolve("pom.xml");
        if (!Files.exists(pom))
            return;

        String content = Files.readString(pom, StandardCharsets.UTF_8);
        String bootVersion = config.getBootVersion();

        // If the parent version was generated as e.g. 4.0.3.RELEASE, normalize it
        // to 4.0.3 to match the modern Spring Boot version style.
        if (bootVersion != null && bootVersion.endsWith(".RELEASE"))
        {
            String normalized = bootVersion.substring(0, bootVersion.length() - ".RELEASE".length());
            String from = "<version>" + bootVersion + "</version>";
            String to = "<version>" + normalized + "</version>";
            if (content.contains(from))
            {
                content = content.replace(from, to);
                Files.writeString(pom, content, StandardCharsets.UTF_8);
            }
        }
    }
}
