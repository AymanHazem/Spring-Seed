package dev.ayman.seed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.SeedCli;
import dev.ayman.seed.model.UpdateInfo;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Checks GitHub Releases for Seed updates using a 24-hour local cache.
 */
public class UpdateService
{
    public static final String DEFAULT_GITHUB_API_URL =
            "https://api.github.com/repos/AymanHazem/Spring-Seed/releases/latest";
    public static final String INSTALL_COMMAND =
            "curl -fsSL https://raw.githubusercontent.com/AymanHazem/Spring-Seed/main/install.sh | sh";
    public static final Duration CACHE_TTL = Duration.ofHours(24);
    public static final Path DEFAULT_CACHE_FILE =
            Path.of(System.getProperty("user.home"), ".cache", "seed", "update-check.json");

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Path cacheFile;
    private final String apiUrl;
    private final String currentVersion;

    private CompletableFuture<String> backgroundCheckFuture;
    private String latestFoundVersion;

    public UpdateService()
    {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                DEFAULT_CACHE_FILE,
                DEFAULT_GITHUB_API_URL,
                SeedCli.VERSION);
    }

    public UpdateService(HttpClient httpClient, Path cacheFile, String apiUrl, String currentVersion)
    {
        this.httpClient = httpClient;
        this.mapper = new ObjectMapper();
        this.cacheFile = cacheFile;
        this.apiUrl = apiUrl;
        this.currentVersion = currentVersion != null ? currentVersion : SeedCli.VERSION;
    }

    public boolean isCheckDisabled()
    {
        String noUpdate = System.getenv("SEED_NO_UPDATE_CHECK");
        if (noUpdate != null && !noUpdate.isBlank() && !"0".equals(noUpdate) && !"false".equalsIgnoreCase(noUpdate))
            return true;

        String ci = System.getenv("CI");
        return ("true".equalsIgnoreCase(ci) || "1".equals(ci));
    }

    /** Non-blocking background update check; failures are ignored silently. */
    public void startBackgroundCheck()
    {
        if (isCheckDisabled())
            return;

        UpdateInfo cached = readCache();
        if (cached != null && isCacheFresh(cached))
        {
            if (isNewer(cached.getLatestVersion(), currentVersion))
            {
                this.latestFoundVersion = cached.getLatestVersion();
            }
            return;
        }

        this.backgroundCheckFuture = CompletableFuture.supplyAsync(() -> {
            try
            {
                String latest = fetchLatestVersionFromGitHub();
                if (latest != null && !latest.isBlank())
                {
                    writeCache(new UpdateInfo(System.currentTimeMillis(), latest));
                    if (isNewer(latest, currentVersion))
                    {
                        return latest;
                    }
                }
            }
            catch (Exception ignored)
            {
            }
            return null;
        });
    }

    public String getAvailableUpdateVersion()
    {
        if (latestFoundVersion != null)
            return latestFoundVersion;

        if (backgroundCheckFuture != null)
        {
            String result = backgroundCheckFuture.getNow(null);
            if (result != null)
            {
                this.latestFoundVersion = result;
                return result;
            }
        }
        return null;
    }

    public void printNoticeIfAvailable(PrintWriter out)
    {
        String newerVersion = getAvailableUpdateVersion();
        if (newerVersion != null)
        {
            printNotice(out, currentVersion, newerVersion);
        }
    }

    public CheckResult checkExplicitly(boolean forceRefresh)
    {
        if (!forceRefresh)
        {
            UpdateInfo cached = readCache();
            if (cached != null && isCacheFresh(cached))
            {
                boolean newer = isNewer(cached.getLatestVersion(), currentVersion);
                return new CheckResult(newer ? CheckStatus.UPDATE_AVAILABLE : CheckStatus.UP_TO_DATE,
                        currentVersion, cached.getLatestVersion(), null);
            }
        }

        try
        {
            String latest = fetchLatestVersionFromGitHub();
            if (latest == null || latest.isBlank())
            {
                return new CheckResult(CheckStatus.ERROR, currentVersion, null, "No release tag found.");
            }

            writeCache(new UpdateInfo(System.currentTimeMillis(), latest));
            boolean newer = isNewer(latest, currentVersion);
            return new CheckResult(newer ? CheckStatus.UPDATE_AVAILABLE : CheckStatus.UP_TO_DATE,
                    currentVersion, latest, null);
        }
        catch (Exception e)
        {
            return new CheckResult(CheckStatus.ERROR, currentVersion, null, e.getMessage());
        }
    }

    public String fetchLatestVersionFromGitHub() throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "seed-cli/" + currentVersion)
                .timeout(Duration.ofSeconds(3))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200)
        {
            JsonNode root = mapper.readTree(response.body());
            JsonNode tagNode = root.path("tag_name");
            if (!tagNode.isMissingNode() && !tagNode.asText().isBlank())
            {
                return tagNode.asText().trim();
            }
        }
        else if (response.statusCode() == 404)
        {
            throw new IOException("No releases found on GitHub.");
        }
        else if (response.statusCode() == 403)
        {
            throw new IOException("GitHub API rate limit exceeded. Please try again later.");
        }
        else
        {
            throw new IOException("GitHub API returned HTTP " + response.statusCode());
        }
        return null;
    }

    public static boolean isNewer(String remote, String current)
    {
        if (remote == null || remote.isBlank())
            return false;
        if (current == null || current.isBlank())
            return true;

        String cleanRemote = remote.trim().replaceFirst("^[vV]", "");
        String cleanCurrent = current.trim().replaceFirst("^[vV]", "");

        if (cleanRemote.equalsIgnoreCase(cleanCurrent))
            return false;

        String baseRemote = cleanRemote.contains("-") ? cleanRemote.substring(0, cleanRemote.indexOf('-')) : cleanRemote;
        String baseCurrent = cleanCurrent.contains("-") ? cleanCurrent.substring(0, cleanCurrent.indexOf('-')) : cleanCurrent;

        String[] rParts = baseRemote.split("\\.");
        String[] cParts = baseCurrent.split("\\.");

        int maxLen = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < maxLen; i++)
        {
            int r = i < rParts.length ? parseSafeInt(rParts[i]) : 0;
            int c = i < cParts.length ? parseSafeInt(cParts[i]) : 0;
            if (r > c)
                return true;
            if (r < c)
                return false;
        }

        // If numeric parts are identical, release version is newer than pre-release/snapshot
        return cleanCurrent.contains("-") && !cleanRemote.contains("-");
    }

    private static int parseSafeInt(String s)
    {
        try
        {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        }
        catch (Exception e)
        {
            return 0;
        }
    }

    public boolean isCacheFresh(UpdateInfo info)
    {
        if (info == null || info.getLastChecked() <= 0)
            return false;

        long elapsed = System.currentTimeMillis() - info.getLastChecked();
        return elapsed >= 0 && elapsed < CACHE_TTL.toMillis();
    }

    public UpdateInfo readCache()
    {
        try
        {
            if (Files.exists(cacheFile))
            {
                byte[] bytes = Files.readAllBytes(cacheFile);
                return mapper.readValue(bytes, UpdateInfo.class);
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }

    public void writeCache(UpdateInfo info)
    {
        try
        {
            if (cacheFile.getParent() != null)
            {
                Files.createDirectories(cacheFile.getParent());
            }
            byte[] bytes = mapper.writeValueAsBytes(info);
            Files.write(cacheFile, bytes);
        }
        catch (Exception ignored)
        {
        }
    }

    public static void printNotice(PrintWriter out, String currentVer, String latestVer)
    {
        String from = currentVer.startsWith("v") ? currentVer : "v" + currentVer;
        String to = latestVer.startsWith("v") ? latestVer : "v" + latestVer;

        out.println();
        out.println(ansi().bold().fgYellow().a("  Update available! ").reset()
                .bold().a(from).fgYellow().a(" -> ").fgGreen().a(to).reset());
        out.println(ansi().fgCyan().a("  Run: ").reset().a(INSTALL_COMMAND + " to update.").reset());
        out.println();
        out.flush();
    }

    public enum CheckStatus
    {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        ERROR
    }

    public record CheckResult(CheckStatus status, String currentVersion, String latestVersion, String errorMessage)
    {
    }
}
