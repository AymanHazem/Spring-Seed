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
        return new SelectionPrompt(reader, writer);
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
        // "no" must return false instead of falling through to true.
        assertFalse(promptFor("no\n").askYesNo("Continue?"));
    }

    @Test
    void noIsCaseInsensitive() {
        assertFalse(promptFor("No\n").askYesNo("Continue?"));
        assertFalse(promptFor("NO\n").askYesNo("Continue?"));
    }

    @Test
    void invalidInputRePromptsUntilValidAnswerGiven() {
        assertFalse(promptFor("maybe\nno\n").askYesNo("Continue?"));
    }

    @Test
    void eofCancelsWizard() {
        assertThrows(WizardCancelledException.class,
                () -> promptFor("").askYesNo("Continue?"));
    }

    @Test
    void emptyOptionListThrowsClearError() {
        assertThrows(IllegalStateException.class,
                () -> promptFor("").selectOption("Language", java.util.List.of(), null));
    }
}
