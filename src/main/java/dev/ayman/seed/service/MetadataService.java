package dev.ayman.seed.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.model.InitializrMetadata;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Fetches the Spring Initializr metadata from start.spring.io and caches it
 * locally.
 * Cache location: ~/.cache/seed/metadata.json
 * Cache TTL: 1 hour
 */
public class MetadataService
{
    private static final String METADATA_URL = "https://start.spring.io/metadata/client";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Path CACHE_FILE;

    static
    {
        String home = System.getProperty("user.home");
        CACHE_FILE = Path.of(home, ".cache", "seed", "metadata.json");
    }

    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public MetadataService()
    {
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }


    //Returns cached metadata if fresh, otherwise fetches from the API.
    //param forceRefresh bypass cache and fetch fresh data
    public InitializrMetadata getMetadata(boolean forceRefresh) throws IOException, InterruptedException
    {
        if (!forceRefresh && isCacheFresh())
            return loadFromCache();
        return fetchAndCache();
    }

    private boolean isCacheFresh()
    {
        if (!Files.exists(CACHE_FILE))
            return false;
        try
        {
            Instant lastModified = Files.getLastModifiedTime(CACHE_FILE).toInstant();
            return Duration.between(lastModified, Instant.now()).compareTo(CACHE_TTL) < 0;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    private InitializrMetadata loadFromCache() throws IOException
    {
        byte[] bytes = Files.readAllBytes(CACHE_FILE);
        return mapper.readValue(bytes, InitializrMetadata.class);
    }

    private InitializrMetadata fetchAndCache() throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(METADATA_URL))
                .header("Accept", "application/json")
                .header("User-Agent", "seed-cli/1.0")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200)
            throw new IOException("Failed to fetch metadata: HTTP " + response.statusCode());

        byte[] body = response.body();

        // Write to cache
        Files.createDirectories(CACHE_FILE.getParent());
        Files.write(CACHE_FILE, body);

        return mapper.readValue(body, InitializrMetadata.class);
    }
}
