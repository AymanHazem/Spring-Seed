package dev.ayman.seed.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InitializrMetadata
{

    @JsonProperty("type")
    private SelectGroup<ProjectType> type;

    @JsonProperty("language")
    private SelectGroup<Option> language;

    @JsonProperty("bootVersion")
    private SelectGroup<Option> bootVersion;

    @JsonProperty("packaging")
    private SelectGroup<Option> packaging;

    @JsonProperty("javaVersion")
    private SelectGroup<Option> javaVersion;

    @JsonProperty("groupId")
    private TextDefault groupId;

    @JsonProperty("artifactId")
    private TextDefault artifactId;

    @JsonProperty("version")
    private TextDefault version;

    @JsonProperty("name")
    private TextDefault name;

    @JsonProperty("description")
    private TextDefault description;

    @JsonProperty("packageName")
    private TextDefault packageName;

    @JsonProperty("dependencies")
    private DependencyGroups dependencies;

    public SelectGroup<ProjectType> getType() { return type; }
    public SelectGroup<Option> getLanguage() { return language; }
    public SelectGroup<Option> getBootVersion() { return bootVersion; }
    public SelectGroup<Option> getPackaging() { return packaging; }
    public SelectGroup<Option> getJavaVersion() { return javaVersion; }
    public TextDefault getGroupId() { return groupId; }
    public TextDefault getArtifactId() { return artifactId; }
    public TextDefault getVersion() { return version; }
    public TextDefault getName() { return name; }
    public TextDefault getDescription() { return description; }
    public TextDefault getPackageName() { return packageName; }
    public DependencyGroups getDependencies() { return dependencies; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SelectGroup<T> {
        @JsonProperty("default")
        private String defaultValue;
        @JsonProperty("values")
        private List<T> values;

        public String getDefaultValue() { return defaultValue; }
        public List<T> getValues() { return values; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DependencyGroups {
        @JsonProperty("values")
        private List<DependencyGroup> values;

        public List<DependencyGroup> getValues() { return values; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TextDefault {
        @JsonProperty("default")
        private String defaultValue;

        public String getDefaultValue() { return defaultValue; }
    }
}
