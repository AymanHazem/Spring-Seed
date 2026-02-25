package dev.ayman.seed.wizard;

import dev.ayman.seed.model.Dependency;
import dev.ayman.seed.util.FuzzyMatcher;
import org.fusesource.jansi.Ansi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.fusesource.jansi.Ansi.ansi;

/**
 * Interactive dependency selector with live fuzzy search.
 *
 * Controls:
 * Type to filter — results update after each character
 * ↑/↓ arrows — move cursor (not available in raw mode via console; use numbers)
 * [number] — toggle dependency by its displayed index
 * ENTER — confirm selection with empty input
 * :clear — clear all selections
 * :done — finish selection
 */
public class DependencySelector {

    private static final int PAGE_SIZE = 12;

    private final List<Dependency> allDependencies;
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final BufferedReader in;
    private final PrintWriter out;

    public DependencySelector(List<Dependency> allDependencies) {
        this.allDependencies = allDependencies;
        this.in = new BufferedReader(new InputStreamReader(System.in));
        this.out = new PrintWriter(System.out, true);
    }

    /**
     * Run the interactive dependency selection loop.
     *
     * @return list of selected dependency IDs
     */
    public List<String> select() throws Exception {
        out.println();
        printBanner();

        String lastQuery = "";
        List<Dependency> filtered = initialFeaturedList();

        while (true) {
            printResults(filtered);
            printSelectedChips();

            out.print(ansi().fgBrightYellow().a("  Search: ").reset().a(lastQuery).toString());
            out.flush();

            String line = in.readLine();
            if (line == null)
                break;
            line = line.trim();

            if (line.equalsIgnoreCase(":done") || line.isEmpty()) {
                break;
            }
            if (line.equalsIgnoreCase(":clear")) {
                selectedIds.clear();
                lastQuery = "";
                filtered = initialFeaturedList();
                continue;
            }

            // If user types a number, toggle that result
            if (line.matches("\\d+")) {
                int idx = Integer.parseInt(line) - 1;
                if (idx >= 0 && idx < filtered.size()) {
                    String id = filtered.get(idx).getId();
                    if (selectedIds.contains(id)) {
                        selectedIds.remove(id);
                    } else {
                        selectedIds.add(id);
                    }
                }
                // After toggling, keep current query results if we are in search mode,
                // otherwise stay on the featured list.
                if (lastQuery.isBlank()) {
                    filtered = initialFeaturedList();
                } else {
                    filtered = FuzzyMatcher.search(allDependencies, lastQuery);
                }
            } else {
                // It's a search query
                lastQuery = line;
                filtered = FuzzyMatcher.search(allDependencies, lastQuery);
            }
        }

        out.println();
        return new ArrayList<>(selectedIds);
    }

    /**
     * Returns a small, curated list of featured dependencies to show when there
     * is no active search query. We prioritize commonly used ones such as Web,
     * Lombok, and DevTools.
     */
    private List<Dependency> initialFeaturedList() {
        List<Dependency> featured = new ArrayList<>();
        for (Dependency dep : allDependencies) {
            String id = dep.getId();
            if ("web".equals(id) || "lombok".equals(id) || "devtools".equals(id)) {
                featured.add(dep);
            }
        }
        // Fallback: if none of the expected IDs are present, just show the first page
        if (featured.isEmpty()) {
            int limit = Math.min(PAGE_SIZE, allDependencies.size());
            for (int i = 0; i < limit; i++) {
                featured.add(allDependencies.get(i));
            }
        }
        return featured;
    }

    private void printBanner() {
        out.println(ansi().bold().fgBrightCyan()
                .a("  ╔══════════════════════════════════════════╗").reset());
        out.println(ansi().bold().fgBrightCyan()
                .a("  ║      Dependency Search & Selection       ║").reset());
        out.println(ansi().bold().fgBrightCyan()
                .a("  ╚══════════════════════════════════════════╝").reset());
        out.println(ansi().fgDefault()
                .a("  Type to search  │  [number] to toggle  │  :done to finish  │  :clear to reset"));
        out.println();
    }

    private void printResults(List<Dependency> results) {
        // Clear previous lines by printing new ones (simple approach)
        out.println(ansi().a("  ─────────────────────────────────────────────────────────"));

        int limit = Math.min(PAGE_SIZE, results.size());
        for (int i = 0; i < limit; i++) {
            Dependency dep = results.get(i);
            boolean selected = selectedIds.contains(dep.getId());

            String checkbox = selected
                    ? ansi().fgBrightGreen().bold().a(" ✔ ").reset().toString()
                    : ansi().fgDefault().a(" ○ ").reset().toString();

            String num = ansi().fgBrightBlack().a(String.format("%2d", i + 1) + ". ").reset().toString();
            String name = selected
                    ? ansi().bold().fgBrightGreen().a(dep.getName()).reset().toString()
                    : ansi().bold().a(dep.getName()).reset().toString();
            String id = ansi().fgBrightBlack().a(" [" + dep.getId() + "]").reset().toString();
            String cat = dep.getCategory() != null
                    ? ansi().fgCyan().a(" · " + dep.getCategory()).reset().toString()
                    : "";

            out.println("  " + num + checkbox + name + id + cat);

            if (dep.getDescription() != null && !dep.getDescription().isBlank()) {
                String shortDesc = dep.getDescription().length() > 80
                        ? dep.getDescription().substring(0, 80) + "…"
                        : dep.getDescription();
                out.println(ansi().fgBrightBlack().a("       " + shortDesc).reset());
            }
        }

        if (results.size() > PAGE_SIZE) {
            out.println(ansi().fgBrightBlack()
                    .a("  … " + (results.size() - PAGE_SIZE) + " more (refine search to narrow down)").reset());
        }

        if (results.isEmpty()) {
            out.println(ansi().fgYellow().a("  No dependencies match your search.").reset());
        }

        out.println(ansi().a("  ─────────────────────────────────────────────────────────"));
    }

    private void printSelectedChips() {
        if (selectedIds.isEmpty()) {
            out.println(ansi().fgBrightBlack().a("  Selected: (none)").reset());
        } else {
            StringBuilder chips = new StringBuilder("  Selected: ");
            for (String id : selectedIds) {
                chips.append(ansi().bgGreen().fgBlack().bold().a(" " + id + " ").reset()).append(" ");
            }
            out.println(chips);
        }
        out.println();
    }
}
