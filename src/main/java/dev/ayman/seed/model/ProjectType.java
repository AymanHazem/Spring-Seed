package dev.ayman.seed.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectType
{
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;


    @JsonProperty("tags")
    private Map<String, String> tags;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }


    public boolean isProjectFormat() {
        return tags != null && "project".equals(tags.get("format"));
    }

    @Override
    public String toString() {
        return name;
    }
}
