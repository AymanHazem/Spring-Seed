package dev.ayman.seed.wizard;

import dev.ayman.seed.model.Dependency;
import dev.ayman.seed.util.FuzzyMatcher;
import dev.ayman.seed.util.TerminalUi;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.fusesource.jansi.Ansi.ansi;

public class DependencySelector
{

    private static final int PAGE_SIZE = 12;
    private final List<Dependency> allDependencies;
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final BufferedReader in;
    private final PrintWriter out;

    public DependencySelector(List<Dependency> allDependencies, BufferedReader in, PrintWriter out)
    {
        this.allDependencies = allDependencies;
        this.in = in;
        this.out = out;
    }

    public List<String> select() throws Exception
    {
        out.println();
        printBanner();

        String lastQuery = "";
        List<Dependency> filtered = initialFeaturedList();

        while (true)
        {
            printResults(filtered);
            printSelectedChips();
            out.print(ansi().bold().a("  Search: ").reset().a(lastQuery).toString());
            out.flush();

            String line = in.readLine();
            if (line == null)
                throw new WizardCancelledException();
            line = line.trim();

            if (line.equalsIgnoreCase(":done") || line.isEmpty())
                break;

            if (line.equalsIgnoreCase(":clear"))
            {
                selectedIds.clear();
                lastQuery = "";
                filtered = initialFeaturedList();
                continue;
            }
            if (line.matches("\\d+"))
            {
                try
                {
                    int idx = Integer.parseInt(line) - 1;
                    if (idx >= 0 && idx < filtered.size())
                    {
                        String id = filtered.get(idx).getId();
                        if (!selectedIds.remove(id))
                            selectedIds.add(id);
                    }
                }
                catch (NumberFormatException ignored)
                {
                    // Digit strings overflowing int should report invalid choice instead of crashing.
                    out.println(ansi().fgRed().a("  ✗ Invalid choice: " + line).reset());
                }
                // Clear the query after toggling so the next prompt starts fresh.
                lastQuery = "";
                filtered = initialFeaturedList();
            }
            else
            {
                lastQuery = line;
                filtered = FuzzyMatcher.search(allDependencies, lastQuery);
            }
        }

        out.println();
        return new ArrayList<>(selectedIds);
    }

    private List<Dependency> initialFeaturedList() {
        List<Dependency> featured = new ArrayList<>();
        for (Dependency dep : allDependencies)
        {
            String id = dep.getId();
            if ("web".equals(id) || "lombok".equals(id) || "devtools".equals(id))
                featured.add(dep);
        }
        if (featured.isEmpty())
            featured.addAll(allDependencies.stream().limit(PAGE_SIZE).toList());
        return featured;
    }

    private void printBanner()
    {
        out.println(ansi().bold().fgCyan()
                .a("  ╔══════════════════════════════════════════╗").reset());
        out.println(ansi().bold().fgCyan()
                .a("  ║      Dependency Search & Selection       ║").reset());
        out.println(ansi().bold().fgCyan()
                .a("  ╚══════════════════════════════════════════╝").reset());
        out.println(ansi().fgDefault()
                .a("  Type to search  |  [number] to toggle  |  [Enter] to finish"));
        out.println();
    }

    private void printResults(List<Dependency> results)
    {
        out.println("  ─────────────────────────────────────────────────────────");
        int limit = Math.min(PAGE_SIZE, results.size());
        for (int i = 0; i < limit; i++)
        {
            Dependency dep = results.get(i);
            boolean selected = selectedIds.contains(dep.getId());

            String checkbox = selected
                    ? ansi().fgGreen().bold().a(" ✔ ").reset().toString()
                    : " ○ ";

            String num = TerminalUi.faint(String.format("%2d. ", i + 1));
            String name = selected
                    ? ansi().bold().fgGreen().a(dep.getName()).reset().toString()
                    : ansi().bold().a(dep.getName()).reset().toString();

            String id = TerminalUi.faint(" [" + dep.getId() + "]");

            String cat = dep.getCategory() != null
                    ? ansi().fgCyan().a(" · " + dep.getCategory()).reset().toString()
                    : "";

            out.println("  " + num + checkbox + name + id + cat);

            if (dep.getDescription() != null && !dep.getDescription().isBlank())
            {
                String shortDesc = dep.getDescription().length() > 80
                        ? dep.getDescription().substring(0, 80) + "…"
                        : dep.getDescription();
                out.println(TerminalUi.faint("       " + shortDesc));
            }
        }

        if (results.size() > PAGE_SIZE)
            out.println(TerminalUi.faint("  … " + (results.size() - PAGE_SIZE) + " more (refine search to narrow down)"));

        if (results.isEmpty())
            out.println(ansi().fgYellow().a("  No dependencies match your search.").reset());

        out.println("  ─────────────────────────────────────────────────────────");
    }

    private void printSelectedChips()
    {
        if (selectedIds.isEmpty())
            out.println(TerminalUi.faint("  Selected: (none)"));

        else
        {
            StringBuilder chips = new StringBuilder("  Selected: ");
            for (String id : selectedIds)
                chips.append(ansi().bgGreen().fgBlack().bold().a(" " + id + " ").reset()).append(" ");

            out.println(chips);
        }
        out.println();
    }
}