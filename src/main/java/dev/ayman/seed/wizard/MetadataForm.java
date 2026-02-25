package dev.ayman.seed.wizard;

import dev.ayman.seed.model.InitializrMetadata;
import org.fusesource.jansi.Ansi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Collects project metadata from the user (groupId, artifactId, name, etc.)
 * using a simple labeled-input form pattern.
 */
public class MetadataForm {

    private final BufferedReader in;
    private final PrintWriter out;
    private final InitializrMetadata meta;

    public MetadataForm(InitializrMetadata meta) {
        this.meta = meta;
        this.in = new BufferedReader(new InputStreamReader(System.in));
        this.out = new PrintWriter(System.out, true);
    }

    /**
     * Runs the metadata form interactively and populates the config.
     */
    public void fill(ProjectConfig config) throws Exception {
        out.println(ansi().bold().fgBrightCyan().a(
                "\n  ┌─ Project Metadata ──────────────────────────").reset());
        out.println(ansi().fgBrightBlack().a(
                "  │  Press ENTER to accept the default in [brackets]").reset());
        out.println(ansi().fgBrightCyan().a(
                "  └──────────────────────────────────────────────").reset());

        String groupId = prompt("  Group          ", meta.getGroupId().getDefaultValue());
        String artifactId = prompt("  Artifact       ", meta.getArtifactId().getDefaultValue());
        String name = prompt("  Name           ", artifactId.isEmpty()
                ? meta.getName().getDefaultValue()
                : artifactId);
        String description = prompt("  Description    ", meta.getDescription().getDefaultValue());
        String version = prompt("  Version        ", meta.getVersion().getDefaultValue());

        String defaultPkg = (groupId + "." + artifactId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase());
        String packageName = prompt("  Package name is :   ", defaultPkg);

        config.setGroupId(groupId.isBlank() ? meta.getGroupId().getDefaultValue() : groupId);
        config.setArtifactId(artifactId.isBlank() ? meta.getArtifactId().getDefaultValue() : artifactId);
        config.setName(name.isBlank() ? config.getArtifactId() : name);
        config.setDescription(description.isBlank() ? meta.getDescription().getDefaultValue() : description);
        config.setVersion(version.isBlank() ? meta.getVersion().getDefaultValue() : version);
        config.setPackageName(packageName.isBlank() ? config.derivePackageName() : packageName);
    }

    private String prompt(String label, String defaultValue) throws Exception
    {
        String defaultDisplay = (defaultValue != null && !defaultValue.isBlank())
                ? ansi().fgBrightBlack().a(" [" + defaultValue + "]").reset().toString()
                : "";

        out.print(ansi().fgBrightYellow().a(label).reset().a(defaultDisplay)
                .fgBrightYellow().a(" ").reset());
        out.flush();

        String line = in.readLine();
        return (line == null) ? "" : line.trim();
    }
}
