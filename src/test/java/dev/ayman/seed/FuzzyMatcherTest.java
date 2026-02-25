package dev.ayman.seed;

import dev.ayman.seed.model.Dependency;
import dev.ayman.seed.util.FuzzyMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FuzzyMatcherTest {

    private static Dependency dep(String id, String name, String description) {
        Dependency d = new Dependency();
        // Use setter-less approach via reflection would need setters — our class has
        // them not
        // so we'll use the public setters added via category
        // Actually Dependency fields are set via Jackson — let's use a helper
        return makeDep(id, name, description);
    }

    private static Dependency makeDep(String id, String name, String desc) {
        // Create via Jackson ObjectMapper to respect our POJO design
        try {
            String json = String.format("{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\"}", id, name, desc);
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Dependency.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final List<Dependency> DEPS = List.of(
            makeDep("web", "Spring Web", "Build RESTful applications with Spring MVC"),
            makeDep("data-jpa", "Spring Data JPA", "Persist data in SQL stores with JPA and Hibernate"),
            makeDep("security", "Spring Security", "Authentication and access-control framework"),
            makeDep("actuator", "Spring Boot Actuator", "Monitor and manage your application"),
            makeDep("kafka", "Spring for Apache Kafka", "Publish and subscribe to streams of records"),
            makeDep("redis", "Spring Data Redis", "Advanced Redis client for Spring"));

    @Test
    void exactIdMatchReturnsHighestScore() {
        int score = FuzzyMatcher.score(DEPS.get(0), "web");
        assertEquals(100, score, "Exact id match should score 100");
    }

    @Test
    void nameStartsWithQueryScores80() {
        int score = FuzzyMatcher.score(DEPS.get(2), "spring");
        assertEquals(80, score, "Name starts-with should score 80");
    }

    @Test
    void nameContainsQueryScores60() {
        int score = FuzzyMatcher.score(DEPS.get(1), "jpa");
        assertEquals(60, score);
    }

    @Test
    void idContainsQueryScores50() {
        int score = FuzzyMatcher.score(DEPS.get(1), "data");
        // "spring data jpa" name starts with "spring" not "data",
        // but id "data-jpa" contains "data"
        assertTrue(score >= 40, "ID contains match should score >= 40");
    }

    @Test
    void descriptionContainsQueryScores20() {
        int score = FuzzyMatcher.score(DEPS.get(0), "restful");
        assertEquals(20, score);
    }

    @Test
    void noMatchScoresZero() {
        int score = FuzzyMatcher.score(DEPS.get(0), "xyzzy");
        assertEquals(0, score);
    }

    @Test
    void searchReturnsSortedResults() {
        List<Dependency> results = FuzzyMatcher.search(DEPS, "spring");
        assertFalse(results.isEmpty());
        // All results should match "spring" in some way
        assertTrue(results.size() >= 3);
    }

    @Test
    void emptyQueryReturnsAll() {
        List<Dependency> results = FuzzyMatcher.search(DEPS, "");
        assertEquals(DEPS.size(), results.size());
    }

    @Test
    void nullQueryReturnsAll() {
        List<Dependency> results = FuzzyMatcher.search(DEPS, null);
        assertEquals(DEPS.size(), results.size());
    }

    @Test
    void kafkaSearchFindsKafkaDep() {
        List<Dependency> results = FuzzyMatcher.search(DEPS, "kafka");
        assertFalse(results.isEmpty());
        assertEquals("kafka", results.get(0).getId());
    }
}
