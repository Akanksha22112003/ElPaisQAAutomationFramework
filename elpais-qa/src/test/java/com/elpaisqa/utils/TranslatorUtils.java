/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Translator utility class integrating with MyMemory translation API.
 */
package com.elpaisqa.utils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/*
 * Using MyMemory free translation API here instead of Google Cloud Translate.
 * Google requires billing enabled even for free-tier usage which is unnecessary
 * overhead for translating 5 headlines. MyMemory handles it fine with no signup
 * or API key needed. If we ever need to swap to Google or DeepL, it's just this
 * one class that changes.
 */
public class TranslatorUtils {

    private static final Object rateLock = new Object();
    private static long lastCallTime = 0;

    /**
     * Translates a text string from Spanish to English with thread-safe rate limiting and retries.
     */
    public static String translateToEnglish(String spanishText) {
        if (spanishText == null || spanishText.trim().isEmpty()) {
            return "";
        }
        return translateWithRetry(spanishText, 2); // Retry up to 2 times on transient failures
    }

    /**
     * Helper to perform translation with retries on transient connection exceptions.
     */
    private static String translateWithRetry(String text, int retriesLeft) {
        // 1. Thread-safe Rate Limiter: Limit requests to 1 call per 1.5 seconds across all threads
        synchronized (rateLock) {
            long elapsed = System.currentTimeMillis() - lastCallTime;
            if (elapsed < 1500 && lastCallTime > 0) {
                try {
                    Thread.sleep(1500 - elapsed);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCallTime = System.currentTimeMillis();
        }

        try {
            return performTranslation(text);
        } catch (Exception e) {
            if (retriesLeft > 0 && isTransient(e)) {
                LoggerUtil.warn("Transient failure calling MyMemory: " + e.getMessage() 
                        + ". Retrying in 2 seconds (" + retriesLeft + " attempts left)...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return translateWithRetry(text, retriesLeft - 1);
            }
            LoggerUtil.error("Failed to translate text. Falling back to original. Error: " + e.getMessage(), e);
            return text;
        }
    }

    /**
     * Determines if a thrown exception represents a transient network glitch that is worth retrying.
     */
    private static boolean isTransient(Exception e) {
        return e instanceof java.net.SocketTimeoutException
            || e instanceof java.net.ConnectException
            || (e.getMessage() != null && (e.getMessage().contains("502") || e.getMessage().contains("503")));
    }

    /**
     * Dispatches the HTTP request to MyMemory API endpoints.
     */
    private static String performTranslation(String spanishText) throws Exception {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            String encodedText = URLEncoder.encode(spanishText.trim(), StandardCharsets.UTF_8.name());
            String apiEndpoint = "https://api.mymemory.translated.net/get?q=" + encodedText + "&langpair=es|en";

            URL url = new URL(apiEndpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();

            // Handle HTTP 429 Rate Limiting
            if (status == 429) {
                LoggerUtil.warn("MyMemory API returned HTTP 429 Rate Limit. Falling back to original Spanish title.");
                return spanishText;
            }

            if (status == HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JSONObject json = new JSONObject(response.toString());
                int responseStatus = json.optInt("responseStatus", 200);

                if (responseStatus == 403 || responseStatus == 429) {
                    LoggerUtil.warn("MyMemory API quota or rate limit exceeded in JSON payload (Status: " + responseStatus + "). Falling back to original Spanish title.");
                    return spanishText;
                }

                // Defensive JSON structure checks
                JSONObject responseData = json.optJSONObject("responseData");
                if (responseData == null) {
                    LoggerUtil.error("Unexpected MyMemory API response format (responseData is null)");
                    return spanishText;
                }

                String translatedText = responseData.optString("translatedText", spanishText);

                // Language detection check
                if (translatedText.equalsIgnoreCase(spanishText)) {
                    LoggerUtil.warn("Translation identical to source text. Translation might have failed silently.");
                }

                return translatedText;
            } else {
                LoggerUtil.error("MyMemory API request failed with status: " + status);
                throw new java.io.IOException("HTTP error code: " + status);
            }
        } finally {
            try {
                if (reader != null) reader.close();
                if (conn != null) conn.disconnect();
            } catch (Exception ignored) {}
        }
    }
}
