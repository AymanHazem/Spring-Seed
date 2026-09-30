package dev.ayman.seed;

import dev.ayman.seed.wizard.SelectionPrompt;
import dev.ayman.seed.wizard.WizardCancelledException;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class SelectionPromptTest {

    private static SelectionPrompt promptFor(String input) {
        BufferedReader reader = new BufferedReader(new StringReader(input));
        PrintWriter writer = new PrintWriter(new StringWriter());
        return newPrompt(reader, writer);
    }

    private static SelectionPrompt newPrompt(BufferedReader reader, PrintWriter writer) {
        try {
            var ctor = SelectionPrompt.class.getDeclaredConstructor(BufferedReader.class, PrintWriter.class);
            ctor.setAccessible(true);
            return ctor.newInstance(reader, writer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void blankInputDefaultsToYes() {
        assertTrue(promptFor("\n").askYesNo("Continue?"));
    }

    @Test
    void yReturnsTrue() {
        assertTrue(promptFor("y\n").askYesNo("Continue?"));
    }

    @Test
    void yesReturnsTrue() {
        assertTrue(promptFor("yes\n").askYesNo("Continue?"));
    }

    @Test
    void nReturnsFalse() {
        assertFalse(promptFor("n\n").askYesNo("Continue?"));
    }

    @Test
    void noReturnsFalse() {
        // Regression test for Rank 1: "no" used to be treated as "yes".
        assertFalse(promptFor("no\n").askYesNo("Continue?"));
    }

    @Test
    void noIsCaseInsensitive() {
        assertFalse(promptFor("No\n").askYesNo("Continue?"));
        assertFalse(promptFor("NO\n").askYesNo("Continue?"));
    }

    @Test
    void invalidInputRePromptsUntilValidAnswerGiven() {
        // First line is garbage, second line is a valid "no" — the method must
        // re-prompt instead of guessing.
        assertFalse(promptFor("maybe\nno\n").askYesNo("Continue?"));
    }

    @Test
    void eofCancelsWizard() {
        assertThrows(WizardCancelledException.class,
                () -> promptFor("").askYesNo("Continue?"));
    }
}
