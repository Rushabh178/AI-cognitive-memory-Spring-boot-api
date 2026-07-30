package com.CognitiveMemory.demo.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MVP placeholder for deciding whether a chat message warrants memory retrieval.
 * Generic coding/definition questions ("write code to...", "what is...") don't need
 * the user's personal context, and pulling it in anyway leaks unrelated details
 * (job skills, project info) into the prompt.
 * <p>
 * This is a crude keyword/regex heuristic only. It should be replaced by a proper
 * intent-classification call to the Python side once that's available.
 */
public final class MemoryRelevanceUtil {

    private static final Pattern GENERIC_QUERY_PREFIX = Pattern.compile(
            "^\\s*(write\\s+(a\\s+)?(python\\s+|java\\s+|js\\s+|javascript\\s+)?code|" +
                    "what\\s+is|explain|how\\s+do\\s+i|how\\s+does|define|show\\s+me\\s+(a|an|how))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PERSONAL_PRONOUN = Pattern.compile(
            "\\b(i|i'm|im|me|my|mine|myself)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private MemoryRelevanceUtil() {
    }

    public static boolean isMemoryRelevant(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String trimmed = message.trim();
        Matcher genericPrefix = GENERIC_QUERY_PREFIX.matcher(trimmed);
        if (!genericPrefix.find()) {
            return true;
        }
        // Only look for personal pronouns after the generic prefix itself — phrases like
        // "how do I" always contain "I" but aren't a signal of personal context on their own.
        String remainder = trimmed.substring(genericPrefix.end());
        return PERSONAL_PRONOUN.matcher(remainder).find();
    }
}
