package dev.ayman.seed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Option {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    public String getId() { return id; }
    public String getName() { return name; }

    public boolean isUnstable() {
        String lower = id == null ? "" : id.toLowerCase();
        return lower.contains("snapshot") || lower.contains(".m");
    }

    @Override
    public String toString() {
        return name;
    }
}
