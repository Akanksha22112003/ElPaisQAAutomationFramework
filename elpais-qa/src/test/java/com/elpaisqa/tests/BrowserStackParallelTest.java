/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Parameterized test suite executing parallel Chrome, Firefox, and Safari sessions on BrowserStack.
 */
package com.elpaisqa.tests;

import com.aventstack.extentreports.Status;
import com.elpaisqa.config.BrowserStackConfig;
import com.elpaisqa.utils.DriverManager;
import com.elpaisqa.utils.ExtentManager;
import com.elpaisqa.utils.LoggerUtil;
import com.elpaisqa.utils.ScraperUtils;
import com.elpaisqa.utils.ScraperUtils.ArticleData;
import com.elpaisqa.utils.TranslatorUtils;
import com.elpaisqa.utils.WordAnalyzer;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class BrowserStackParallelTest extends BaseTest {

    private static final String BASE_URL = "https://elpais.com";
    private static final String OPINION_URL = BASE_URL + "/opinion/";

    private final ThreadLocal<String> currentTestName = new ThreadLocal<>();

    @BeforeMethod
    @Parameters({ "platformType", "browser", "os", "osVersion", "browserVersion", "deviceName" })
    public void setUp(
            @org.testng.annotations.Optional("desktop") String platformType,
            @org.testng.annotations.Optional("chrome") String browser,
            @org.testng.annotations.Optional("Windows") String os,
            @org.testng.annotations.Optional("11") String osVersion,
            @org.testng.annotations.Optional("latest") String browserVersion,
            @org.testng.annotations.Optional("") String deviceName,
            Method method) throws Exception {

        String testCaseName = method.getName() + "_" + browser + "_" +
                (deviceName.isEmpty() ? (os + "_" + osVersion) : deviceName.replaceAll("\\s+", ""));
        currentTestName.set(testCaseName);

        LoggerUtil.startTestCase(testCaseName);
        ExtentManager.createTest(testCaseName);

        if (!BrowserStackConfig.isConfigured()) {
            String errorMsg = "BrowserStack credentials are not set in environment variables! " +
                    "Ensure BROWSERSTACK_USERNAME and BROWSERSTACK_ACCESS_KEY are configured.";
            logStep(errorMsg, Status.FAIL);
            throw new RuntimeException(errorMsg);
        }

        logStep(String.format("Generating BrowserStack capabilities for %s (%s, %s)...", browser, os, deviceName), Status.INFO);

        // Generate capabilities using config helper
        MutableCapabilities capabilities = BrowserStackConfig.getCapabilities(
                platformType, browser, os, osVersion, browserVersion, deviceName, method.getName());

        logStep("Initializing RemoteWebDriver session on BrowserStack...", Status.INFO);

        // Initialize remote web driver session
        DriverManager.initRemoteDriver(capabilities);
    }

    @Test
    public void runElPaisParallelTest() {
        WebDriver driver = DriverManager.getDriver();

        logStep("Navigating to " + BASE_URL, Status.INFO);
        driver.get(BASE_URL);

        logStep("Handling cookies...", Status.INFO);
        ScraperUtils.handleCookies(driver);

        logStep("Verifying Spanish language...", Status.INFO);
        boolean isSpanish = ScraperUtils.verifySpanish(driver);
        logStep("Spanish language verification completed: " + isSpanish, isSpanish ? Status.PASS : Status.WARNING);
        Assert.assertTrue(isSpanish, "The homepage language is not in Spanish ('es')!");

        logStep("Navigating to Opinion section: " + OPINION_URL, Status.INFO);
        driver.get(OPINION_URL);
        
        // El País cookie banner reappears on /opinion sometimes
        ScraperUtils.handleCookies(driver);

        List<ArticleData> articles = ScraperUtils.scrapeOpinionArticles(driver);
        logStep("Articles found for scraping: " + articles.size(), Status.INFO);
        Assert.assertFalse(articles.isEmpty(), "No articles were scraped");
        Assert.assertEquals(articles.size(), 5, "Expected to scrape 5 articles from the Opinion section, but got: " + articles.size());

        List<String> translatedTitles = new ArrayList<>();
        int index = 1;

        for (ArticleData article : articles) {
            logStep("Processing article " + index + ": " + article.getTitle(), Status.INFO);

            try {
                String scrapedContent = ScraperUtils.scrapeArticleContent(driver, article.getUrl());
                article.setContent(scrapedContent);
                logStep("Excerpt scraped: " + article.getContent(), Status.INFO);
            } catch (Exception e) {
                logStep("Exception scraping content for article index " + index + " at URL: " + article.getUrl() + " | Error: " + e.getMessage(), Status.FAIL);
                throw e;
            }

            if (article.getImageUrl() != null && !article.getImageUrl().isEmpty()) {
                logStep("Downloading cover image: " + article.getImageUrl(), Status.INFO);
                ScraperUtils.downloadArticleImage(article.getImageUrl(), index);
            } else {
                logStep("No cover image found for article " + index, Status.INFO);
            }

            logStep("Translating title to English...", Status.INFO);
            String englishTitle = TranslatorUtils.translateToEnglish(article.getTitle());
            Assert.assertNotNull(englishTitle, "Translation returned null for title: " + article.getTitle());
            
            // Print ES alongside EN title
            logStep("Spanish Title: " + article.getTitle(), Status.INFO);
            logStep("English Translation: " + englishTitle, Status.INFO);
            
            translatedTitles.add(englishTitle);
            index++;
        }

        logStep("Starting word frequency analysis on translated headers...", Status.INFO);
        WordAnalyzer.analyzeHeaders(translatedTitles);
        logStep("Word frequency analysis completed.", Status.PASS);
    }

    @AfterMethod
    public void tearDown(ITestResult result) {
        WebDriver driver = DriverManager.getDriver();
        if (driver != null) {
            String sessionId = null;
            try {
                if (driver instanceof RemoteWebDriver) {
                    sessionId = ((RemoteWebDriver) driver).getSessionId().toString();
                }
            } catch (Exception e) {
                LoggerUtil.error("Error retrieving session ID: " + e.getMessage());
            }

            try {
                if (result.getStatus() == ITestResult.FAILURE) {
                    if (result.getThrowable() != null) {
                        LoggerUtil.error(result.getThrowable().getMessage());
                        ExtentManager.getTest().fail(result.getThrowable());
                    }
                    if (sessionId != null) {
                        String reason = result.getThrowable() != null ? result.getThrowable().getMessage()
                                : "Test execution failed";
                        BrowserStackConfig.updateSessionStatus(sessionId, "failed", reason);
                    }
                } else {
                    LoggerUtil.info("Test execution completed successfully.");
                    ExtentManager.log("Test execution completed successfully.", Status.PASS);

                    if (sessionId != null) {
                        BrowserStackConfig.updateSessionStatus(sessionId, "passed", "Test completed successfully.");
                    }
                }
            } finally {
                LoggerUtil.info("Quitting RemoteWebDriver session...");
                DriverManager.quitDriver();
                LoggerUtil.endTestCase(currentTestName.get());
                currentTestName.remove();
            }
        }
    }
}
