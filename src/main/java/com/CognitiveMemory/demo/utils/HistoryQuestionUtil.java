package com.CognitiveMemory.demo.utils;

import java.util.regex.Pattern;

/**
 * Decides whether a chat message asks about a PREVIOUS value ("who did I use to like
 * before that?", "what was my old phone number?") rather than the current one.
 * <p>
 * When it does, SessionController adds the user's revision history (from the Python
 * service's GET /graph/history) to the long-term context. Without it, nothing on the chat
 * path can see a superseded fact: the graph context and vector retrieval both return
 * current state only, by design.
 * <p>
 * A crude keyword/regex heuristic, in the same spirit as {@link MemoryRelevanceUtil}.
 * Deliberately inclusive: a false positive only adds a few clearly labelled history lines
 * (the Python side bounds it to 5), while a miss means a history question can't be
 * answered at all.
 */
public final class HistoryQuestionUtil {

    private static final Pattern HISTORY_PHRASE = Pattern.compile(
            "\\bus(?:e|ed)\\s+to\\b"                                  // used to / use to
                    + "|\\bbefore\\s+(?:that|this|then|now|him|her|it|them)\\b"
                    + "|\\bdid\\s+i\\b[^.?!]*\\bbefore\\b"                // who did I like before Kohli
                    + "|\\b(?:previously|previous|formerly|former|originally)\\b"
                    + "|\\bat\\s+first\\b|\\bback\\s+then\\b"
                    + "|\\b(?:my|the)\\s+old\\b"                          // my old phone number
                    + "|\\b(?:what|who|which|where)\\s+(?:was|were)\\s+my\\b"
                    + "|\\bchanged?\\s+(?:it\\s+)?from\\b|\\bswitched\\s+from\\b",
            Pattern.CASE_INSENSITIVE
    );

    private HistoryQuestionUtil() {
    }

    public static boolean isHistoryQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return HISTORY_PHRASE.matcher(message).find();
    }
}
