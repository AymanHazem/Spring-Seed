package dev.ayman.seed;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayman.seed.model.Dependency;
import dev.ayman.seed.wizard.DependencySelector;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencySelectorTest {

    private static Dependency dep(String id, String name, String description) {
        try {
            String json = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\"}", id, name,
                    description);
            return new ObjectMapper().readValue(json, Dependency.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final List<Dependency> DEPS = List.of(
            dep("web", "Spring Web", "Build web, including RESTful, applications using Spring MVC."),
            dep("lombok", "Lombok", "Java annotation library which helps to reduce boilerplate code."),
            dep("devtools", "Spring Boot DevTools", "Provides fast application restarts, LiveReload."));

    private static DependencySelector newSelector(String input, StringWriter capturedOut) throws Exception {
        BufferedReader reader = new BufferedReader(new StringReader(input));
        PrintWriter writer = new PrintWriter(capturedOut, true);
        Constructor<DependencySelector> ctor = DependencySelector.class
                .getDeclaredConstructor(List.class, BufferedReader.class, PrintWriter.class);
        ctor.setAccessible(true);
        return ctor.newInstance(DEPS, reader, writer);
    }

    @Test
    void searchFieldResetsAfterTogglingResultByNumber() throws Exception {
        StringWriter capturedOut = new StringWriter();
        // Search "web", toggle the top result (#1), then finish with a blank line.
        DependencySelector selector = newSelector("web\n1\n\n", capturedOut);

        List<String> selected = selector.select();

        assertEquals(List.of("web"), selected);

        String lastSearchLine = null;
        for (String line : capturedOut.toString().split("\n")) {
            if (line.contains("Search:"))
                lastSearchLine = line;
        }
        assertNotNull(lastSearchLine, "expected at least one 'Search:' prompt to be printed");
        assertFalse(lastSearchLine.contains("web"),
                "search field must be cleared after toggling a result by number, but the last prompt was: "
                        + lastSearchLine);
    }

    @Test
    void searchFieldStaysUsableForANewSearchAfterToggle() throws Exception {
        StringWriter capturedOut = new StringWriter();
        // Search "web", toggle #1, then search again for "devtools", toggle #1, finish.
        DependencySelector selector = newSelector("web\n1\ndevtools\n1\n\n", capturedOut);

        List<String> selected = selector.select();

        assertEquals(List.of("web", "devtools"), selected);
    }

    @Test
    void overlongNumericInputDoesNotCrashTheSelector() throws Exception {
        // Regression test: a digit string that overflows int (matches \d+ but
        // fails Integer.parseInt) used to throw an uncaught NumberFormatException
        // and crash the whole wizard. It should instead be treated as an
        // out-of-range choice, and the selector should keep running normally.
        StringWriter capturedOut = new StringWriter();
        DependencySelector selector = newSelector("99999999999999999999\n1\n\n", capturedOut);

        List<String> selected = assertDoesNotThrow(selector::select);

        assertEquals(List.of("web"), selected, "selector should recover and still allow toggling a valid choice");
    }
}
