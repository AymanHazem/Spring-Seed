package dev.ayman.seed.wizard;
import dev.ayman.seed.model.Option;
import dev.ayman.seed.model.ProjectType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
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

    /**
     * Package-visible constructor allowing injection of I/O streams for testing.
     */
    SelectionPrompt(BufferedReader in, PrintWriter out)
    {
        this.in = in;
        this.out = out;
    }

    public ProjectType selectProjectType(List<ProjectType> options, String defaultId)
    {
        return select("Project Type", options, defaultId,
                ProjectType::getId, ProjectType::getName, ProjectType::getDescription,
                _ -> false);
    }

    public Option selectOption(String label, List<Option> options, String defaultId)
    {
        return select(label, options, defaultId,
                Option::getId, Option::getName, _ -> null,
                Option::isUnstable);
    }

    /**
     * Shared single-selection flow: renders numbered options (unstable ones in
     * yellow), then reads a validated 1-based choice; empty input picks the default.
     */
    private <T> T select(String label, List<T> options, String defaultId,
            Function<T, String> idOf, Function<T, String> nameOf,
            Function<T, String> descriptionOf, Predicate<T> isUnstable)
    {
        out.println(ansi().bold().fgBrightCyan().a("\n  ┌─ " + label + " ──────────────────────────────").reset());
        for (int i = 0; i < options.size(); i++)
        {
            T opt = options.get(i);
            String nameDisplay = isUnstable.test(opt)
                    ? ansi().fgYellow().a(nameOf.apply(opt)).reset().toString()
                    : nameOf.apply(opt);
            printOption(i + 1, nameDisplay, descriptionOf.apply(opt), idOf.apply(opt).equals(defaultId));
        }
        out.println(ansi().fgBrightCyan().a("  └──────────────────────────────────────────── ").reset());

        int idx = readChoice(options.size(), defaultId, options.stream()
                .map(idOf).toList());
        return options.get(idx);
    }

    public boolean askYesNo(String question)
    {
        while (true)
        {
            out.print(ansi().bold().fgBrightYellow().a("\n  ? ").reset()
                    .a(question).fgBrightBlack().a(" [Y/n] ").reset());
            out.flush();

            String line = readLine();
            if (line.isBlank())
                return true;

            String normalized = line.trim().toLowerCase();
            if (normalized.equals("y") || normalized.equals("yes"))
                return true;
            if (normalized.equals("n") || normalized.equals("no"))
                return false;

            out.println(ansi().fgRed().a("  ✗ Please answer 'y'/'yes' or 'n'/'no'.").reset());
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

            String line = readLine().trim();

            if (line.isEmpty() && defaultId != null)
            {
                int defIdx = ids.indexOf(defaultId);
                return Math.max(defIdx, 0);
            }

            if (line.matches("\\d+"))
            {
                try
                {
                    int choice = Integer.parseInt(line);
                    if (choice >= 1 && choice <= count)
                        return choice - 1;
                }
                catch (NumberFormatException ignored)
                {
                    // Report the overlong numeric value as an invalid choice below.
                }
            }

            out.println(ansi().fgRed().a("  ✗ Invalid choice. Please enter a number between 1 and " + count + ".")
                    .reset());
        }
    }

    private String readLine()
    {
        try
        {
            String line = in.readLine();
            if (line == null)
                throw new WizardCancelledException();
            return line;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Failed to read terminal input", e);
        }
    }
}