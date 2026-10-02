package com.CognitiveMemory.demo.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryWorthinessUtilTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            // Sentence-initial / capitalised pronouns — previously rejected
            "My favorite cricket player is Rohit Sharma        | true",
            "My favorite player is now Virat Kohli             | true",
            "MY NEW ADDRESS IS 12 PARK STREET                  | true",
            "Mine is the blue car parked outside               | true",
            "I'm switching jobs to a product company           | true",
            "We moved to Pune last month for work              | true",
            // Pronoun next to punctuation
            "Honestly, my exam went really well today          | true",
            "The final decision was made by me and I.          | true",
            // Personal questions still count
            "What is my favorite cricket player again?         | true",
            // No personal signal — not stored
            "For football it's Messi and tennis it's Federer   | false",
            "This time the island trip was beautiful           | false",
            "What is the capital of France again?              | false",
            "Explain dependency injection in Spring Boot       | false",
            "hello there how are you doing                     | false",
            "short my msg                                      | false",
    })
    void detectsPersonalContentRegardlessOfPositionAndCase(String message, boolean expected) {
        assertEquals(expected, MemoryWorthinessUtil.isMemoryWorthy(message.trim()), message);
    }
}
