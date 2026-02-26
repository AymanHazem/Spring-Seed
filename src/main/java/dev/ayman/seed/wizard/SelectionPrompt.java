package dev.ayman.seed.wizard;
import dev.ayman.seed.model.Option;
import dev.ayman.seed.model.ProjectType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import static org.fusesource.jansi.Ansi.ansi;
/**
 * A reusable single-selection prompt that works with any list of items.
 * Renders numbered options with ANSI colors, reads user input, validates it.
 */
public class SelectionPrompt
{
    private final BufferedReader in;
    private final PrintWriter out;

    public SelectionPrompt()
    {
        this.in = new BufferedReader(new InputStreamReader(System.in));
        this.out = new PrintWriter(System.out, true);
    }

    public ProjectType selectProjectType(List<ProjectType> options, String defaultId)
    {
        out.println(ansi().bold().fgBrightCyan().a("\n  ┌─ Project Type ──────────────────────────────").reset());
        for (int i = 0; i < options.size(); i++)
        {
            ProjectType pt = options.get(i);
            boolean isDefault = pt.getId().equals(defaultId);
            printOption(i + 1, pt.getName(), pt.getDescription(), isDefault);
        }
        out.println(ansi().fgBrightCyan().a("  └──────────────────────────────────────────── ").reset());

        int idx = readChoice(options.size(), defaultId, options.stream()
                .map(ProjectType::getId).toList());
        return options.get(idx);
    }

    public Option selectOption(String label, List<Option> options, String defaultId)
    {
        out.println(ansi().bold().fgBrightCyan().a("\n  ┌─ " + label + " ──────────────────────────────").reset());
        for (int i = 0; i < options.size(); i++)
        {
            Option opt = options.get(i);
            boolean isDefault = opt.getId().equals(defaultId);
            boolean unstable = opt.isUnstable();
            String nameDisplay = unstable
                    ? ansi().fgYellow().a(opt.getName()).reset().toString()
                    : opt.getName();
            printOption(i + 1, nameDisplay, null, isDefault);
        }
        out.println(ansi().fgBrightCyan().a("  └──────────────────────────────────────────── ").reset());

        int idx = readChoice(options.size(), defaultId, options.stream()
                .map(Option::getId).toList());
        return options.get(idx);
    }

    public boolean askYesNo(String question)
    {
        out.print(ansi().bold().fgBrightYellow().a("\n  ? ").reset()
                .a(question).fgBrightBlack().a(" [Y/n] ").reset());
        out.flush();

        try
        {
            String line = in.readLine();
            if (line == null || line.isBlank() || line.trim().equalsIgnoreCase("y"))
                return true;
            return !line.trim().equalsIgnoreCase("n");
        }
        catch (Exception e)
        {
            return true;
        }
    }


    private void printOption(int num, String name, String description, boolean isDefault)
    {
        String marker = isDefault
                ? ansi().fgBrightGreen().bold().a("  ► ").reset().toString()
                : ansi().fgBrightBlack().a("    ").reset().toString();
        String numStr = ansi().fgBrightBlack().a("[" + num + "] ").reset().toString();
        String def = isDefault ? ansi().fgBrightGreen().a(" (default)").reset().toString() : "";

        out.print(marker + numStr + name + def);

        if (description != null && !description.isBlank())
        {
            String shortDesc = description.length() > 70
                    ? description.substring(0, 70) + "…"
                    : description;
            out.print(ansi().fgBrightBlack().a(" — " + shortDesc).reset());
        }
        out.println();
    }

    /**
     * Reads user choice (1-based index), returns 0-based index.
     * Accepts empty input → uses default.
     */
    private int readChoice(int count, String defaultId, List<String> ids)
    {
        while (true)
        {
            out.print(ansi().fgBrightYellow().a("  Enter choice [1-" + count + "]: ").reset());
            out.flush();

            try
            {
                String line = in.readLine();
                if (line == null)
                    return 0;
                line = line.trim();

                if (line.isEmpty() && defaultId != null)
                {
                    int defIdx = ids.indexOf(defaultId);
                    return Math.max(defIdx, 0);
                }

                if (line.matches("\\d+"))
                {
                    int choice = Integer.parseInt(line);
                    if (choice >= 1 && choice <= count)
                        return choice - 1;
                }

                out.println(ansi().fgRed().a("  ✗ Invalid choice. Please enter a number between 1 and " + count + ".")
                        .reset());
            }
            catch (Exception e)
            {
                return 0;
            }
        }
    }
}