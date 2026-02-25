package dev.ayman.seed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DependencyGroup {

    @JsonProperty("name")
    private String name;

    @JsonProperty("values")
    private List<Dependency> values;

    public String getName() { return name; }
    public List<Dependency> getValues() { return values; }
}
