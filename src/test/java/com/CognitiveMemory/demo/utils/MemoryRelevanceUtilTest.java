package com.CognitiveMemory.demo.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryRelevanceUtilTest {

    @ParameterizedTest
    @CsvSource({
            "write python code to square a number, false",
            "what is a binary search tree, false",
            "explain dependency injection, false",
            "how do I reverse a linked list, false",
            "what is my job title, true",
            "how do I get back to my project from yesterday, true",
    })
    void classifiesGenericVsPersonalQueries(String message, boolean expectedRelevant) {
        assertEquals(expectedRelevant, MemoryRelevanceUtil.isMemoryRelevant(message));
    }

    @ParameterizedTest
    @CsvSource({"''", "'   '"})
    void blankMessagesAreNotRelevant(String message) {
        assertFalse(MemoryRelevanceUtil.isMemoryRelevant(message));
    }

    @org.junit.jupiter.api.Test
    void nullMessageIsNotRelevant() {
        assertFalse(MemoryRelevanceUtil.isMemoryRelevant(null));
    }

    @org.junit.jupiter.api.Test
    void nonGenericPersonalStatementIsRelevant() {
        assertTrue(MemoryRelevanceUtil.isMemoryRelevant("remind me about the AWS reminder I set yesterday"));
    }
}
