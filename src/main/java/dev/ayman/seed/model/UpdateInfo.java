package dev.ayman.seed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateInfo
{
    private long lastChecked;
    private String latestVersion;

    public UpdateInfo()
    {
    }

    public UpdateInfo(long lastChecked, String latestVersion)
    {
        this.lastChecked = lastChecked;
        this.latestVersion = latestVersion;
    }

    public long getLastChecked()
    {
        return lastChecked;
    }

    public void setLastChecked(long lastChecked)
    {
        this.lastChecked = lastChecked;
    }

    public String getLatestVersion()
    {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion)
    {
        this.latestVersion = latestVersion;
    }
}
