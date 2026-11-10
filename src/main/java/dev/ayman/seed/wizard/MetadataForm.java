package dev.ayman.seed.wizard;

import dev.ayman.seed.model.InitializrMetadata;
import dev.ayman.seed.util.TerminalUi;

import java.io.BufferedReader;
import java.io.PrintWriter;

import static org.fusesource.jansi.Ansi.ansi;

public class MetadataForm
{
    private final BufferedReader in;
    private final PrintWriter out;
    private final InitializrMetadata meta;

    public MetadataForm(InitializrMetadata meta, BufferedReader in, PrintWriter out)
    {
        this.meta = meta;
        this.in = in;
        this.out = out;
    }

    public void fill(ProjectConfig config) throws Exception
    {
        out.println(ansi().bold().fgCyan().a(
                "\n  ┌─ Project Metadata " + "─".repeat(36)).reset());
        out.println(TerminalUi.faint(
                "  │  Press ENTER to accept the default in [brackets]"));
        out.println(ansi().fgCyan().a(
                "  └" + "─".repeat(55)).reset());

        String groupDefault = meta.getGroupId().getDefaultValue();
        String groupId = prompt("  Group          ", groupDefault);
        config.setGroupId(groupId.isBlank() ? groupDefault : groupId);

        String artifactDefault = meta.getArtifactId().getDefaultValue();
        String artifactId = prompt("  Artifact       ", artifactDefault);
        config.setArtifactId(artifactId.isBlank() ? artifactDefault : artifactId);

        String name = prompt("  App Name       ", config.getArtifactId());
        config.setName(name.isBlank() ? config.getArtifactId() : name);

        String descriptionDefault = optionalDefault(meta.getDescription());
        String description = prompt("  Description    ", descriptionDefault);
        config.setDescription(description.isBlank() ? descriptionDefault : description);

        String versionDefault = meta.getVersion().getDefaultValue();
        String version = prompt("  Version        ", versionDefault);
        config.setVersion(version.isBlank() ? versionDefault : version);

        String packageName = prompt("  Package        ", config.derivePackageName());
        config.setPackageName(packageName.isBlank() ? config.derivePackageName() : packageName);
    }

    private static String optionalDefault(InitializrMetadata.TextDefault value)
    {
        return value == null || value.getDefaultValue() == null ? "" : value.getDefaultValue();
    }

    private String prompt(String label, String defaultValue) throws Exception
    {
        String defaultDisplay = (defaultValue != null && !defaultValue.isBlank())
                ? TerminalUi.faint(" [" + defaultValue + "]")
                : "";

        out.print(ansi().bold().a(label).reset().a(defaultDisplay).a(" "));
        out.flush();

        String line = in.readLine();
        if (line == null)
            throw new WizardCancelledException();
        return line.trim();
    }
}