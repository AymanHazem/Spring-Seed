package dev.ayman.seed.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.model.InitializrMetadata;
import dev.ayman.seed.model.Option;
import dev.ayman.seed.model.ProjectType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
/**
 * Fetches Spring Initializr metadata and caches it locally for one hour.
 * A valid stale cache is used when refreshing from the network fails.
 */
public class MetadataService
{
    private static final String METADATA_URL = "https://start.spring.io/metadata/client";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Path CACHE_FILE = Path.of(
            System.getProperty("user.home"), ".cache", "seed", "metadata.json");

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

    public InitializrMetadata getMetadata(boolean forceRefresh) throws IOException, InterruptedException
    {
        InitializrMetadata cachedMetadata = null;
        IOException cacheFailure = null;

        if (Files.exists(CACHE_FILE))
        {
            try
            {
                cachedMetadata = loadFromCache();
                if (!forceRefresh && isCacheFresh())
                    return cachedMetadata;
            }
            catch (IOException e)
            {
                cacheFailure = e;
            }
        }

        try
        {
            return fetchAndCache();
        }
        catch (IOException fetchFailure)
        {
            if (cachedMetadata != null)
                return cachedMetadata;
            if (cacheFailure != null)
                fetchFailure.addSuppressed(cacheFailure);
            throw fetchFailure;
        }
    }

    private boolean isCacheFresh()
    {
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
        InitializrMetadata metadata = mapper.readValue(Files.readAllBytes(CACHE_FILE), InitializrMetadata.class);
        validate(metadata);
        return metadata;
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
        InitializrMetadata metadata = mapper.readValue(body, InitializrMetadata.class);
        validate(metadata);

        Files.createDirectories(CACHE_FILE.getParent());
        Files.write(CACHE_FILE, body);
        return metadata;
    }

    static void validate(InitializrMetadata metadata) throws IOException
    {
        if (metadata == null)
            throw invalid("response body is empty");

        requireSelectGroup("project type", metadata.getType(), ProjectType::getId, ProjectType::isProjectFormat);
        requireSelectGroup("language", metadata.getLanguage(), Option::getId, _ -> true);
        requireSelectGroup("Spring Boot version", metadata.getBootVersion(), Option::getId, _ -> true);
        requireSelectGroup("packaging", metadata.getPackaging(), Option::getId, _ -> true);
        requireSelectGroup("Java version", metadata.getJavaVersion(), Option::getId, _ -> true);

        requireTextDefault("groupId", metadata.getGroupId());
        requireTextDefault("artifactId", metadata.getArtifactId());
        requireTextDefault("version", metadata.getVersion());
    }

    private static <T> void requireSelectGroup(
            String name,
            InitializrMetadata.SelectGroup<T> group,
            Function<T, String> idExtractor,
            Predicate<T> selectable) throws IOException
    {
        if (group == null)
            throw invalid("missing " + name + " options");

        List<T> values = group.getValues();
        if (values == null || values.stream().noneMatch(selectable))
            throw invalid("no selectable " + name + " options were provided");

        String defaultValue = group.getDefaultValue();
        if (defaultValue == null || defaultValue.isBlank())
            throw invalid("missing default " + name);

        boolean defaultExists = values.stream()
                .filter(selectable)
                .map(idExtractor)
                .anyMatch(defaultValue::equals);
        if (!defaultExists)
            throw invalid("default " + name + " '" + defaultValue + "' is not selectable");
    }

    private static void requireTextDefault(String name, InitializrMetadata.TextDefault value) throws IOException
    {
        if (value == null || value.getDefaultValue() == null)
            throw invalid("missing default " + name);
    }

    private static IOException invalid(String detail)
    {
        return new IOException("Invalid Spring Initializr metadata: " + detail);
    }
}
