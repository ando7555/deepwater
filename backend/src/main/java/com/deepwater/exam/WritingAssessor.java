package com.deepwater.exam;

import java.util.List;
import java.util.Map;

/**
 * Marks the open answers of the practice exam (writing, and speaking as a transcript). There are two
 * implementations: an AI examiner on the Claude API, and a fixed rubric used when no API key is set or
 * the AI is unavailable.
 */
public interface WritingAssessor {
    String AI = "AI";
    String RULES = "RULES";

    /** Marks every answer in one go. Returns an assessment for each item id it could mark. */
    Review review(List<OpenAnswer> answers);

    /** One task and the learner's text. kind is WRITING or SPEAKING. */
    record OpenAnswer(String itemId, String kind, String prompt, int minWords, int maxWords, List<String> topicWords, String text) {}

    /**
     * score 0-100 is the sum of four 0-25 criteria: task, coherence, vocabulary and grammar.
     * level is a CEFR estimate (A1, A2, B1, B2 or C1).
     */
    record ItemAssessment(int score, Criteria criteria, String level, String summary, List<String> strengths,
                          List<Correction> corrections, List<String> tips, String markedBy) {}

    record Criteria(int task, int coherence, int vocabulary, int grammar) {}

    record Correction(String original, String corrected, String explanation) {}

    record Review(Map<String, ItemAssessment> items, String overallLevel, String overallSummary, String markedBy) {}
}
