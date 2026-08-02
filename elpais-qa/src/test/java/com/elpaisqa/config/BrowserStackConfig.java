/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Configuration class managing credentials and generating desktop/mobile capabilities for BrowserStack.
 */
package com.elpaisqa.config;

import com.elpaisqa.utils.LoggerUtil;
import org.openqa.selenium.MutableCapabilities;
import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

public class BrowserStackConfig {

    private static String username = System.getenv("BROWSERSTACK_USERNAME");
    private static String accessKey = System.getenv("BROWSERSTACK_ACCESS_KEY");
    public static final String HUB_URL = "https://hub-cloud.browserstack.com/wd/hub";

    static {
        // Fallback: If environment variables are not set, attempt to load them from a local .env file
        if (username == null || username.isEmpty() || accessKey == null || accessKey.isEmpty()) {
            try {
                File envFile = new File(System.getProperty("user.dir") + "/.env");
                if (envFile.exists()) {
                    List<String> lines = Files.readAllLines(Paths.get(envFile.getAbsolutePath()));
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;

                        String[] parts = line.split("=", 2);
                        if (parts.length == 2) {
                            String key = parts[0].trim();
                            String value = parts[1].trim();

                            // Strip surrounding quotes if present
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            } else if (value.startsWith("'") && value.endsWith("'")) {
                                value = value.substring(1, value.length() - 1);
                            }

                            if ("BROWSERSTACK_USERNAME".equalsIgnoreCase(key)) {
                                username = value;
                            } else if ("BROWSERSTACK_ACCESS_KEY".equalsIgnoreCase(key)) {
                                accessKey = value;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // System.out.println is used here as LoggerUtil may not be fully initialized when the static block runs
                System.out.println("Could not parse .env file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static boolean isConfigured() {
        return username != null && !username.isEmpty() && accessKey != null && !accessKey.isEmpty();
    }

    public static MutableCapabilities getCapabilities(
            String platformType,
            String browser,
            String os,
            String osVersion,
            String browserVersion,
            String deviceName,
            String testMethodName) {

        MutableCapabilities capabilities = new MutableCapabilities();
        HashMap<String, Object> bstackOptions = new HashMap<>();

        bstackOptions.put("userName", username);
        bstackOptions.put("accessKey", accessKey);
        bstackOptions.put("projectName", "ElPais Automation");
        bstackOptions.put("buildName", "ElPais-QA-Build-1");
        
        // Dynamic session name using platform details and method
        String sessionName = "ElPais_" + testMethodName + "_" + browser;
        if ("mobile".equalsIgnoreCase(platformType)) {
            sessionName += "_" + deviceName.replaceAll("\\s+", "");
        } else {
            sessionName += "_" + os.replaceAll("\\s+", "") + "_" + osVersion;
        }
        bstackOptions.put("sessionName", sessionName);

        // Branching logic for mobile vs desktop capabilities setup
        if ("mobile".equalsIgnoreCase(platformType)) {
            capabilities.setCapability("browserName", browser);
            bstackOptions.put("deviceName", deviceName);
            bstackOptions.put("realMobile", "true");
            bstackOptions.put("osVersion", osVersion);
        } else {
            capabilities.setCapability("browserName", browser);
            bstackOptions.put("browserVersion", browserVersion);
            bstackOptions.put("os", os);
            bstackOptions.put("osVersion", osVersion);
        }

        if ("chrome".equalsIgnoreCase(browser)) {
            org.openqa.selenium.chrome.ChromeOptions chromeOptions = new org.openqa.selenium.chrome.ChromeOptions();
            chromeOptions.addArguments("--disable-blink-features=AutomationControlled");
            chromeOptions.addArguments("--disable-notifications");
            chromeOptions.addArguments("--disable-popup-blocking");
            capabilities.setCapability("goog:chromeOptions", chromeOptions);
        } else if ("firefox".equalsIgnoreCase(browser)) {
            org.openqa.selenium.firefox.FirefoxOptions firefoxOptions = new org.openqa.selenium.firefox.FirefoxOptions();
            firefoxOptions.addPreference("dom.webdriver.enabled", false);
            firefoxOptions.addPreference("general.useragent.override", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:120.0) Gecko/20100101 Firefox/120.0");
            capabilities.setCapability("moz:firefoxOptions", firefoxOptions);
        }

        capabilities.setCapability("bstack:options", bstackOptions);
        capabilities.setCapability("pageLoadStrategy", "eager");
        return capabilities;
    }

    /**
     * Updates the status of a BrowserStack session via their Automate REST API.
     */
    public static void updateSessionStatus(String sessionId, String status, String reason) {
        if (!isConfigured() || sessionId == null) {
            LoggerUtil.warn("BrowserStack credentials or session ID missing. Skipping API status update.");
            return;
        }

        // Truncate the reason to 250 characters to stay within BrowserStack API limits
        if (reason != null && reason.length() > 250) {
            reason = reason.substring(0, 250);
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.browserstack.com/automate/sessions/" + sessionId + ".json");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PUT"); // BrowserStack uses PUT to update sessions
            conn.setRequestProperty("Content-Type", "application/json");

            // Basic Authentication header encoding
            String auth = username + ":" + accessKey;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes("UTF-8"));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            conn.setDoOutput(true);

            // Construct payload safely using org.json to prevent quote formatting bugs
            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("status", status);
            payload.put("reason", reason != null ? reason : "");
            String jsonPayload = payload.toString();

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                LoggerUtil.info("BrowserStack session " + sessionId + " updated to status: " + status);
            } else {
                // Read error stream to see actual failure reasons from BrowserStack
                StringBuilder errorDetail = new StringBuilder();
                try (java.io.InputStream errorStream = conn.getErrorStream()) {
                    if (errorStream != null) {
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(errorStream, "UTF-8"))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                errorDetail.append(line);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                LoggerUtil.error("Failed to update BrowserStack session status. HTTP code: " + responseCode 
                        + " | Response: " + errorDetail.toString());
            }
        } catch (java.net.MalformedURLException e) {
            LoggerUtil.error("Malformed URL used to connect to BrowserStack REST API: " + e.getMessage());
        } catch (java.io.IOException e) {
            LoggerUtil.error("I/O error communicating with BrowserStack REST API: " + e.getMessage());
        } catch (Exception e) {
            LoggerUtil.error("Unexpected exception updating session status on BrowserStack: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
