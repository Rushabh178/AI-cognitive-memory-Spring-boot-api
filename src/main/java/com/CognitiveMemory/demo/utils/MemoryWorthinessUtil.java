package com.CognitiveMemory.demo.utils;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Decides whether a chat message contains long-term-worthy personal content
 * (facts, preferences, goals, experiences, decisions, ongoing situations) as
 * opposed to greetings, acknowledgements, questions, or generic requests that
 * would otherwise pollute the memory store. Shared by AiController and
 * SessionController so both pipelines apply the same storage rules.
 */
public final class MemoryWorthinessUtil {

    /**
     * First-person words that mark a message as being about the user. Word-boundary
     * matching (\b), not space-padded substrings, so the word is found at the start of
     * the sentence ("My favorite..."), at the end, and next to punctuation ("my,",
     * "I."). CASE_INSENSITIVE handles capitalisation. \bi\b also covers contractions
     * ("I'm", "I've", "I'd") because an apostrophe is a non-word character.
     */
    private static final Pattern PERSONAL_PRONOUN = Pattern.compile(
            "\\b(i|my|mine|we|our)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Narrower personal signal that overrides the generic-knowledge-prefix rule, e.g.
     * "What is my blood type" or "Explain why I feel tired".
     */
    private static final Pattern GENERIC_PREFIX_PERSONAL_OVERRIDE = Pattern.compile(
            "\\b(my|i\\s+am|i['’]m|i\\s+have|i['’]ve|i\\s+feel)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private MemoryWorthinessUtil() {
    }

    public static boolean isMemoryWorthy(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String lower = message.toLowerCase().trim();

        // Too short to contain meaningful personal info — less than 5 words is
        // likely a greeting, question, or filler
        String[] words = lower.split("\\s+");
        if (words.length < 5) {
            return false;
        }

        // Pure questions rarely contain personal facts
        // Exception: "I have a question about my health" contains "my" — personal context present
        if (lower.endsWith("?") && !PERSONAL_PRONOUN.matcher(lower).find()) {
            return false;
        }

        // Generic knowledge requests — no personal info
        String[] genericPrefixes = {
                "what is", "what are", "how do", "how does",
                "explain", "define", "tell me about",
                "can you explain", "what does"
        };
        for (String prefix : genericPrefixes) {
            if (lower.startsWith(prefix)
                    && !GENERIC_PREFIX_PERSONAL_OVERRIDE.matcher(lower).find()) {
                return false;
            }
        }

        // Greetings and filler
        String[] fillers = {
                "hello", "hi", "hey", "thanks", "thank you",
                "ok", "okay", "got it", "sure", "alright",
                "good morning", "good evening", "good night",
                "bye", "goodbye", "see you"
        };
        for (String filler : fillers) {
            if (lower.equals(filler)
                    || lower.startsWith(filler + " ")) {
                return false;
            }
        }

        // Personal content signals — store if present; otherwise don't
        return PERSONAL_PRONOUN.matcher(lower).find();
    }

    /**
     * Excludes retrieved memories that are near-duplicates of the current message.
     * A memory can only look like a duplicate here if it was stored moments ago in a
     * prior request — this is a safety net on top of correct retrieve-before-store ordering.
     */
    public static List<String> filterNearDuplicates(String currentMessage, List<String> memories) {
        String messageLower = currentMessage.toLowerCase().trim();
        return memories.stream()
                .filter(memory -> {
                    String memLower = memory.toLowerCase().trim();
                    if (memLower.equals(messageLower)) {
                        return false;
                    }
                    if (messageLower.contains(memLower) && memLower.length() > 20) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }
}
