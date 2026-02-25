package dev.ayman.seed.wizard;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds all user-selected configuration options for the Spring Boot project.
 * This is the data object passed from the wizard to the generator.
 */
public class ProjectConfig {

    private String type; // e.g. "maven-project"
    private String language; // e.g. "java"
    private String bootVersion; // e.g. "4.0.3.RELEASE"
    private String groupId; // e.g. "com.example"
    private String artifactId; // e.g. "demo"
    private String name; // e.g. "demo"
    private String description; // e.g. "Demo project for Spring Boot"
    private String packageName; // e.g. "com.example.demo"
    private String packaging; // "jar" or "war"
    private String javaVersion; // "21", "17", etc.
    private String version; // "0.0.1-SNAPSHOT"
    private List<String> dependencies = new ArrayList<>();

    private boolean createEnvFiles;
    private boolean useYaml;
    private boolean initGit;
    private String outputDirectory;

    // ── Getters & Setters ──────────────────────────────────────────────────

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

    public String getBootVersion() {
        return bootVersion;
    }

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

    public boolean isCreateEnvFiles() {
        return createEnvFiles;
    }

    public void setCreateEnvFiles(boolean createEnvFiles) {
        this.createEnvFiles = createEnvFiles;
    }

    public boolean isUseYaml() {
        return useYaml;
    }

    public void setUseYaml(boolean useYaml) {
        this.useYaml = useYaml;
    }

    public boolean isInitGit() {
        return initGit;
    }

    public void setInitGit(boolean initGit) {
        this.initGit = initGit;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /** Derive package name from groupId + artifactId (replaces hyphens). */
    public String derivePackageName() {
        String safe = (artifactId == null ? "" : artifactId).replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return (groupId == null ? "com.example" : groupId) + "." + safe;
    }
}
