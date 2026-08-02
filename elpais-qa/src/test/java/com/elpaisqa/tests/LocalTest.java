/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Local test automation run executing scraping, translation, and word frequency analysis.
 */
package com.elpaisqa.tests;

import com.aventstack.extentreports.Status;
import com.elpaisqa.utils.DriverManager;
import com.elpaisqa.utils.ExtentManager;
import com.elpaisqa.utils.LoggerUtil;
import com.elpaisqa.utils.ScraperUtils;
import com.elpaisqa.utils.ScraperUtils.ArticleData;
import com.elpaisqa.utils.TranslatorUtils;
import com.elpaisqa.utils.WordAnalyzer;
import org.openqa.selenium.WebDriver;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class LocalTest extends BaseTest {

    private static final String BASE_URL = "https://elpais.com";
    private static final String OPINION_URL = BASE_URL + "/opinion/";

    private String currentTestName;

    @BeforeMethod
    @Parameters({"browser"})
    public void setUp(@org.testng.annotations.Optional("chrome") String browser, Method method) {
        currentTestName = method.getName() + "_" + browser;
        LoggerUtil.startTestCase(currentTestName);
        ExtentManager.createTest(currentTestName);

        logStep("Initializing local " + browser + " driver...", Status.INFO);
        DriverManager.initLocalDriver(browser);
    }

    @Test
    public void verifyOpinionSectionEndToEnd() {
        WebDriver driver = DriverManager.getDriver();

        logStep("Navigating to " + BASE_URL, Status.INFO);
        driver.get(BASE_URL);

        logStep("Handling cookies...", Status.INFO);
        ScraperUtils.handleCookies(driver);

        logStep("Verifying Spanish language...", Status.INFO);
        boolean isSpanish = ScraperUtils.verifySpanish(driver);
        logStep("Spanish language verification completed: " + isSpanish, isSpanish ? Status.PASS : Status.WARNING);
        Assert.assertTrue(isSpanish, "El País page is not in Spanish");

        logStep("Navigating to Opinion section: " + OPINION_URL, Status.INFO);
        driver.get(OPINION_URL);

        // El País cookie banner reappears on /opinion sometimes
        ScraperUtils.handleCookies(driver);

        List<ArticleData> articles = ScraperUtils.scrapeOpinionArticles(driver);
        logStep("Articles found for scraping: " + articles.size(), Status.INFO);
        Assert.assertFalse(articles.isEmpty(), "No articles were scraped");
        Assert.assertEquals(articles.size(), 5, "Expected 5 articles, got " + articles.size());

        List<String> translatedTitles = new ArrayList<>();
        int index = 1;

        for (ArticleData article : articles) {
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
            Assert.assertNotNull(englishTitle, "Translation failed for: " + article.getTitle());
            
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
        try {
            if (result.getStatus() == ITestResult.FAILURE) {
                if (result.getThrowable() != null) {
                    LoggerUtil.error(result.getThrowable().getMessage());
                    ExtentManager.getTest().fail(result.getThrowable());
                }
            } else {
                LoggerUtil.info("Local test completed successfully.");
                ExtentManager.log("Local test completed successfully.", Status.PASS);
            }
        } finally {
            if (DriverManager.getDriver() != null) {
                LoggerUtil.info("Quitting local WebDriver session...");
                DriverManager.quitDriver();
            }
            LoggerUtil.endTestCase(currentTestName);
            currentTestName = null;
        }
    }
}
