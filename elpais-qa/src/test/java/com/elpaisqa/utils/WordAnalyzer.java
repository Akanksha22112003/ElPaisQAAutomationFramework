/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Word count frequency analyzer class excluding common English stop words.
 */
package com.elpaisqa.utils;

import com.aventstack.extentreports.Status;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WordAnalyzer {

    private static final Set<String> STOP_WORDS = new HashSet<>();

    static {
        STOP_WORDS.addAll(Arrays.asList(
            "the", "a", "an", "of", "in", "is", "to", "and",
            "for", "on", "with", "at", "by", "from", "it", "as",
            "are", "was", "were", "be", "been", "being",
            "has", "have", "had", "do", "does", "did",
            "will", "would", "could", "should", "may", "might",
            "this", "that", "these", "those",
            "he", "she", "they", "we", "you", "i", "me", "him", "her",
            "his", "hers", "their", "theirs", "its", "our", "ours",
            "not", "no", "yes", "but", "or", "if", "so",
            "than", "then", "up", "down", "out", "off", "over",
            "about", "after", "before", "into", "through",
            "who", "what", "when", "where", "why", "how", "which",
            "s", "t", "d", "ll", "re", "ve", "m"
        ));
    }

    /**
     * Counts occurrences of meaningful words across headers, stripping punctuation and contractions.
     */
    public static Map<String, Integer> countWords(List<String> headers) {
        Map<String, Integer> counts = new HashMap<>();
        if (headers == null) {
            return counts;
        }

        for (String header : headers) {
            if (header == null || header.trim().isEmpty()) {
                continue;
            }

            // Clean string: first remove apostrophes (don't -> dont), then strip all non-alphabet characters and numbers
            String clean = header.toLowerCase()
                    .replaceAll("'", "")
                    .replaceAll("[^a-zA-Z\\s]", " ");

            String[] words = clean.split("\\s+");
            for (String word : words) {
                word = word.trim();
                // Filter: word must be non-empty, longer than 2 characters, and not in the stop words list
                if (!word.isEmpty() && word.length() > 2 && !STOP_WORDS.contains(word)) {
                    counts.put(word, counts.getOrDefault(word, 0) + 1);
                }
            }
        }
        return counts;
    }

    /**
     * Performs word frequency analysis, logging repeating terms in descending sorted order.
     */
    public static void analyzeHeaders(List<String> translatedHeaders) {
        LoggerUtil.info("--- Word Frequency Analysis (Repeated > 2 times) ---");
        ExtentManager.log("--- Word Frequency Analysis (Repeated > 2 times) ---", Status.INFO);

        Map<String, Integer> counts = countWords(translatedHeaders);

        // Sort by value descending and log
        long repeatedCount = counts.entrySet().stream()
            .filter(e -> e.getValue() > 2)
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .peek(e -> {
                LoggerUtil.info("Word: '" + e.getKey() + "' | Count: " + e.getValue());
                ExtentManager.log("Word: '" + e.getKey() + "' | Count: " + e.getValue(), Status.INFO);
            })
            .count();

        if (repeatedCount == 0) {
            LoggerUtil.info("No words repeated more than twice across the headers.");
            ExtentManager.log("No words repeated more than twice across the headers.", Status.INFO);
        }
        
        LoggerUtil.info("----------------------------------------------------");
    }
}
