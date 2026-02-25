package dev.ayman.seed.util;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Initializes a git repository in a project directory and creates an initial
 * commit.
 */
public class GitInitializer {

    private GitInitializer() {
    }

    /**
     * Runs: git init → git add . → git commit -m "Initial commit (spring-init)"
     *
     * @param projectDir the directory to initialize git in
     * @throws IOException          if a process fails to start
     * @throws InterruptedException if the process is interrupted
     */
    public static void init(Path projectDir) throws IOException, InterruptedException {
        run(projectDir, "git", "init");
        run(projectDir, "git", "add", ".");
        run(projectDir, "git", "commit", "-m", "Initial commit (seed)");
    }

    private static void run(Path dir, String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .inheritIO();

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0)
            throw new IOException("Command failed (exit " + exitCode + "): " + String.join(" ", command));
    }
}
