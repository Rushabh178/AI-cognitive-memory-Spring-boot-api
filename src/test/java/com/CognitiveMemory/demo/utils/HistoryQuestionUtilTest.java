package com.CognitiveMemory.demo.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HistoryQuestionUtilTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            // History questions — must be detected
            "Who did I use to like before that?             | true",
            "what was my old phone number                   | true",
            "Who did I like before Kohli?                   | true",
            "What did I used to have as my editor?          | true",
            "Previously, what was my favorite movie?        | true",
            "Which team did I support originally?          | true",
            "What was my favorite cricket player?           | true",
            "I changed it from Rohit, remember?             | true",
            // Current-state questions and statements — must not be
            "Who is my favorite cricket player?             | false",
            "What is my phone number?                       | false",
            "I like Virat Kohli                             | false",
            "How old is Virat Kohli?                        | false",
            "My favorite cricket player is now Virat Kohli  | false",
            "Explain the history of cricket                 | false",
    })
    void detectsHistoryQuestions(String message, boolean expected) {
        assertEquals(expected, HistoryQuestionUtil.isHistoryQuestion(message.trim()), message);
    }

    @Test
    void nullAndBlankAreNotHistoryQuestions() {
        assertFalse(HistoryQuestionUtil.isHistoryQuestion(null));
        assertFalse(HistoryQuestionUtil.isHistoryQuestion("   "));
    }
}
