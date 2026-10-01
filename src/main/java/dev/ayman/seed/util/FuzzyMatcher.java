package dev.ayman.seed.util;
import dev.ayman.seed.model.Dependency;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FuzzyMatcher
{
    private FuzzyMatcher() {}

     // Filter and rank dependencies by query relevance.
    public static List<Dependency> search(List<Dependency> dependencies, String query) {
        if (query == null || query.isBlank())
            return new ArrayList<>(dependencies);

        String q = query.trim().toLowerCase();

        record Scored(Dependency dep, int score) {}

        return dependencies.stream()
                .map(dep -> new Scored(dep, score(dep, q)))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .map(Scored::dep)
                .toList();
    }

    /**
     * Compute a match score for a single dependency against a lowercase query.
     */
    public static int score(Dependency dep, String query)
    {
        String id = dep.getId() == null ? "" : dep.getId().toLowerCase();
        String name = dep.getName() == null ? "" : dep.getName().toLowerCase();
        String desc = dep.getDescription() == null ? "" : dep.getDescription().toLowerCase();

        if (id.equals(query))
            return 100;
        if (name.startsWith(query))
            return 80;
        if (name.contains(query))
            return 60;
        if (id.contains(query))
            return 50;

        String[] tokens = name.split("[\\s\\-_]+");

        // Token-based name match
        for (String token : tokens)
        {
            if (token.startsWith(query))
                return 40;
        }

        if (desc.contains(query))
            return 20;

        // Levenshtein on individual name words
        for (String token : tokens)
        {
            if (token.length() >= 3 && levenshtein(token, query) <= 2)
                return 10;
        }

        return 0;
    }

    private static int levenshtein(String a, String b)
    {
        int m = a.length(), n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++)
            dp[i][0] = i;
        for (int j = 0; j <= n; j++)
            dp[0][j] = j;
        for (int i = 1; i <= m; i++)
        {
            for (int j = 1; j <= n; j++)
            {
                if (a.charAt(i - 1) == b.charAt(j - 1))
                    dp[i][j] = dp[i - 1][j - 1];
                else
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[m][n];
    }
}