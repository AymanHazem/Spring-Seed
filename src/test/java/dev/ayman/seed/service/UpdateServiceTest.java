package dev.ayman.seed.service;

import dev.ayman.seed.model.UpdateInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServiceTest
{
    @TempDir
    Path tempDir;

    private Path cacheFile;

    @BeforeEach
    void setUp()
    {
        cacheFile = tempDir.resolve("update-check.json");
    }

    static class FakeUpdateService extends UpdateService
    {
        String mockTag;
        Exception mockError;
        int networkCallsCount = 0;

        FakeUpdateService(Path cacheFile, String currentVersion, String mockTag)
        {
            super(null, cacheFile, "http://localhost", currentVersion);
            this.mockTag = mockTag;
        }

        @Override
        public boolean isCheckDisabled()
        {
            return false;
        }

        @Override
        public String fetchLatestVersionFromGitHub() throws IOException
        {
            networkCallsCount++;
            if (mockError != null)
            {
                if (mockError instanceof IOException ioe)
                    throw ioe;
                throw new IOException(mockError);
            }
            return mockTag;
        }
    }

    @Test
    void testIsNewerBasicSemver()
    {
        assertTrue(UpdateService.isNewer("v1.2.0", "1.1.0"));
        assertTrue(UpdateService.isNewer("1.1.1", "1.1.0"));
        assertTrue(UpdateService.isNewer("2.0.0", "1.1.0"));
        assertTrue(UpdateService.isNewer("v1.1.0.1", "1.1.0"));
        assertTrue(UpdateService.isNewer("1.1.0", "1.1.0-SNAPSHOT"));

        assertFalse(UpdateService.isNewer("v1.1.0", "1.1.0"));
        assertFalse(UpdateService.isNewer("1.1.0", "v1.1.0"));
        assertFalse(UpdateService.isNewer("1.0.0", "1.1.0"));
        assertFalse(UpdateService.isNewer("0.9.9", "1.1.0"));
        assertFalse(UpdateService.isNewer("1.1.0-SNAPSHOT", "1.1.0"));
        assertFalse(UpdateService.isNewer(null, "1.1.0"));
        assertFalse(UpdateService.isNewer("", "1.1.0"));
    }

    @Test
    void testCacheReadWriteAndFreshness()
    {
        FakeUpdateService service = new FakeUpdateService(cacheFile, "1.1.0", "v1.2.0");

        assertNull(service.readCache());

        UpdateInfo info = new UpdateInfo(System.currentTimeMillis(), "v1.2.0");
        service.writeCache(info);

        assertTrue(Files.exists(cacheFile));
        UpdateInfo read = service.readCache();
        assertNotNull(read);
        assertEquals("v1.2.0", read.getLatestVersion());
        assertTrue(service.isCacheFresh(read));

        long oldTime = System.currentTimeMillis() - (25 * 3600 * 1000L);
        read.setLastChecked(oldTime);
        assertFalse(service.isCacheFresh(read));
    }

    @Test
    void testCheckExplicitlyUpdateAvailable()
    {
        FakeUpdateService service = new FakeUpdateService(cacheFile, "1.1.0", "v1.2.0");
        UpdateService.CheckResult result = service.checkExplicitly(true);

        assertEquals(UpdateService.CheckStatus.UPDATE_AVAILABLE, result.status());
        assertEquals("1.1.0", result.currentVersion());
        assertEquals("v1.2.0", result.latestVersion());
        assertEquals(1, service.networkCallsCount);

        UpdateInfo cached = service.readCache();
        assertNotNull(cached);
        assertEquals("v1.2.0", cached.getLatestVersion());
    }

    @Test
    void testCheckExplicitlyUpToDate()
    {
        FakeUpdateService service = new FakeUpdateService(cacheFile, "1.1.0", "v1.1.0");
        UpdateService.CheckResult result = service.checkExplicitly(true);

        assertEquals(UpdateService.CheckStatus.UP_TO_DATE, result.status());
        assertEquals("v1.1.0", result.latestVersion());
        assertEquals(1, service.networkCallsCount);
    }

    @Test
    void testCheckExplicitlyUsesFreshCacheWhenNotForced()
    {
        FakeUpdateService service = new FakeUpdateService(cacheFile, "1.1.0", "v1.5.0");
        service.writeCache(new UpdateInfo(System.currentTimeMillis(), "v1.2.5"));

        UpdateService.CheckResult result = service.checkExplicitly(false);

        assertEquals(UpdateService.CheckStatus.UPDATE_AVAILABLE, result.status());
        assertEquals("v1.2.5", result.latestVersion());
        assertEquals(0, service.networkCallsCount);
    }

    @Test
    void testCheckExplicitlyErrorHandling()
    {
        FakeUpdateService service = new FakeUpdateService(cacheFile, "1.1.0", null);
        service.mockError = new IOException("GitHub API rate limit exceeded");

        UpdateService.CheckResult result = service.checkExplicitly(true);

        assertEquals(UpdateService.CheckStatus.ERROR, result.status());
        assertTrue(result.errorMessage().contains("rate limit"));
    }

    @Test
    void testStartBackgroundCheckWithFreshCache()
    {
        FakeUpdateService service = new FakeUpdateService(cacheFile, "1.1.0", "v2.0.0");
        service.writeCache(new UpdateInfo(System.currentTimeMillis(), "v1.3.0"));

        service.startBackgroundCheck();

        assertEquals("v1.3.0", service.getAvailableUpdateVersion());
        assertEquals(0, service.networkCallsCount);
    }

    @Test
    void testPrintNoticeOutput()
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        UpdateService.printNotice(pw, "1.1.0", "v1.2.0");

        String output = sw.toString();
        assertTrue(output.contains("Update available!"));
        assertTrue(output.contains("v1.1.0"));
        assertTrue(output.contains("v1.2.0"));
        assertTrue(output.contains("install.sh | sh to update."));
        assertFalse(output.contains("╭"), "notice should not use a fixed-width box that overflows on 80-col terminals");
    }
}
