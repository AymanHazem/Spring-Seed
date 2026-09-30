package dev.ayman.seed.wizard;
import dev.ayman.seed.model.InitializrMetadata;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import static org.fusesource.jansi.Ansi.ansi;
/**
 * Collects project metadata from the user (groupId, artifactId, name, etc.)
 * using a simple labeled-input form pattern.
 */
public class MetadataForm
{
    private final BufferedReader in;
    private final PrintWriter out;
    private final InitializrMetadata meta;

    public MetadataForm(InitializrMetadata meta)
    {
        this.meta = meta;
        this.in = new BufferedReader(new InputStreamReader(System.in));
        this.out = new PrintWriter(System.out, true);
    }

     //Runs the metadata form interactively and populates the config.
    public void fill(ProjectConfig config) throws Exception
    {
        out.println(ansi().bold().fgBrightCyan().a(
                "\n  ┌─ Project Metadata ──────────────────────────").reset());
        out.println(ansi().fgBrightBlack().a(
                "  │  Press ENTER to accept the default in [brackets]").reset());
        out.println(ansi().fgBrightCyan().a(
                "  └──────────────────────────────────────────────").reset());

        String groupDefault = meta.getGroupId().getDefaultValue();
        String groupId = prompt("  Group          ", groupDefault);
        config.setGroupId(groupId.isBlank() ? groupDefault : groupId);

        String artifactDefault = meta.getArtifactId().getDefaultValue();
        String artifactId = prompt("  Artifact       ", artifactDefault);
        config.setArtifactId(artifactId.isBlank() ? artifactDefault : artifactId);

        String name = prompt("  Name           ", config.getArtifactId());
        config.setName(name.isBlank() ? config.getArtifactId() : name);

        String descriptionDefault = optionalDefault(meta.getDescription());
        String description = prompt("  Description    ", descriptionDefault);
        config.setDescription(description.isBlank() ? descriptionDefault : description);

        String versionDefault = meta.getVersion().getDefaultValue();
        String version = prompt("  Version        ", versionDefault);
        config.setVersion(version.isBlank() ? versionDefault : version);

        String packageName = prompt("  Package name is :   ", config.derivePackageName());
        config.setPackageName(packageName.isBlank() ? config.derivePackageName() : packageName);
    }

    private static String optionalDefault(InitializrMetadata.TextDefault value)
    {
        return value == null || value.getDefaultValue() == null ? "" : value.getDefaultValue();
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
        if (line == null)
            throw new WizardCancelledException();
        return line.trim();
    }
}