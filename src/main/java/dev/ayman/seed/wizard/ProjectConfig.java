package dev.ayman.seed.wizard;
import java.util.ArrayList;
import java.util.List;
/**
 * Holds all user-selected configuration options for the Spring Boot project.
 * This is the data object passed from the wizard to the generator.
 */
public class ProjectConfig
{

    private String type;
    private String language;
    private String bootVersion;
    private String groupId;
    private String artifactId;
    private String name;
    private String description;
    private String packageName;
    private String packaging;
    private String javaVersion;
    private String version;
    private List<String> dependencies = new ArrayList<>();


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getBootVersion() {return bootVersion;}

    public void setBootVersion(String bootVersion) {
        this.bootVersion = bootVersion;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }


    public void setCreateEnvFiles(boolean createEnvFiles) {}

    public void setUseYaml(boolean useYaml) {}

    public void setInitGit(boolean initGit) {}


    public void setOutputDirectory(String outputDirectory) {}

    /** Derive package name from groupId + artifactId (replaces hyphens). */
    public String derivePackageName()
    {
        String safe = (artifactId == null ? "" : artifactId).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return (groupId == null ? "com.example" : groupId) + "." + safe;
    }
}
