/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Scraper utility class for cookie acceptance, article scraping, and image downloads.
 */
package com.elpaisqa.utils;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ScraperUtils {

    /**
     * Checks if the page language contains 'es'.
     */
    public static boolean verifySpanish(WebDriver driver) {
        try {
            WebElement htmlTag = driver.findElement(By.tagName("html"));
            String lang = htmlTag.getDomAttribute("lang");
            if (lang == null) {
                lang = htmlTag.getAttribute("lang");
            }
            boolean isSpanish = lang != null && lang.toLowerCase().contains("es");
            LoggerUtil.info("Page is in Spanish: " + isSpanish);
            return isSpanish;
        } catch (Exception e) {
            LoggerUtil.error("Failed to verify language", e);
            return false;
        }
    }

    /**
     * Handles Didomi or generic cookie banners idempotently and quickly.
     */
    public static void handleCookies(WebDriver driver) {
        try {
            By[] cookieSelectors = new By[] {
                By.id("didomi-notice-agree-button"),
                By.cssSelector("#didomi-host button"),
                By.cssSelector(".didomi-components-button--accept"),
                By.xpath("//button[contains(text(),'Aceptar')]"),
                By.xpath("//span[contains(text(),'Aceptar')]/..")
            };

            for (By selector : cookieSelectors) {
                List<WebElement> elements = driver.findElements(selector);
                if (!elements.isEmpty()) {
                    WebElement acceptBtn = elements.get(0);
                    if (acceptBtn.isDisplayed()) {
                        try {
                            acceptBtn.click();
                            LoggerUtil.info("Cookie consent accepted using: " + selector);
                            Thread.sleep(1500); // Small sleep to let the overlay transition out
                            return;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            LoggerUtil.info("No cookie banner detected or handled.");
        } catch (Exception e) {
            LoggerUtil.error("Error handling cookie banner", e);
        }
    }

    /**
     * Structure to hold article details during scrape.
     */
    public static class ArticleData {
        private String title;
        private String url;
        private String imageUrl;
        private String content;

        public ArticleData(String title, String url, String imageUrl) {
            this.title = title;
            this.url = url;
            this.imageUrl = imageUrl;
            this.content = "";
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * Scrapes the first 5 articles in the opinion page.
     */
    public static List<ArticleData> scrapeOpinionArticles(WebDriver driver) {
        List<ArticleData> articleList = new ArrayList<>();
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // Wait for articles to load
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("article")));
            
            List<WebElement> cards = driver.findElements(By.tagName("article"));
            LoggerUtil.info("Found " + cards.size() + " article cards on Opinion page.");

            int count = 0;
            for (WebElement card : cards) {
                if (count >= 5) break;

                String title = "";
                String articleUrl = "";
                String imageUrl = "";

                // 1. Title & URL extraction
                try {
                    WebElement linkEl = card.findElement(By.cssSelector("h2 a, h3 a, h2.c_t a, .c-title a, a"));
                    title = linkEl.getText().trim();
                    articleUrl = linkEl.getDomAttribute("href");
                    if (articleUrl == null) {
                        articleUrl = linkEl.getAttribute("href");
                    }
                } catch (Exception e) {
                    try {
                        WebElement headerEl = card.findElement(By.cssSelector("h2, h3, .c_t"));
                        title = headerEl.getText().trim();
                    } catch (Exception ex) {
                        LoggerUtil.error("Could not extract title/url for article card index: " + count, ex);
                        continue;
                    }
                }

                if (title.isEmpty()) {
                    continue; // Skip cards without headlines
                }

                // 2. Image URL extraction with fallbacks for responsive and lazy-loaded elements
                try {
                    WebElement imgEl = card.findElement(By.cssSelector("img, figure img, picture img"));
                    imageUrl = extractImageUrl(imgEl);
                } catch (Exception e) {
                    // No image for this card - that's fine
                }

                articleList.add(new ArticleData(title, articleUrl, imageUrl));
                count++;
            }
        } catch (Exception e) {
            LoggerUtil.error("Error scraping opinion articles list", e);
        }
        return articleList;
    }

    /**
     * Helper to extract image URL safely with fallbacks for data-src, srcset, and data-srcset attributes.
     */
    private static String extractImageUrl(WebElement imgEl) {
        // Try src first
        String url = imgEl.getDomAttribute("src");
        if (url == null) {
            url = imgEl.getAttribute("src");
        }
        if (isValidImageUrl(url)) return url;

        // Try data-src (lazy loading)
        url = imgEl.getDomAttribute("data-src");
        if (url == null) {
            url = imgEl.getAttribute("data-src");
        }
        if (isValidImageUrl(url)) return url;

        // Try srcset - grab the first URL
        String srcset = imgEl.getDomAttribute("srcset");
        if (srcset == null) {
            srcset = imgEl.getAttribute("srcset");
        }
        if (srcset != null && !srcset.isEmpty()) {
            url = srcset.split(",")[0].trim().split("\\s+")[0];
            if (isValidImageUrl(url)) return url;
        }

        // Try data-srcset
        srcset = imgEl.getDomAttribute("data-srcset");
        if (srcset == null) {
            srcset = imgEl.getAttribute("data-srcset");
        }
        if (srcset != null && !srcset.isEmpty()) {
            url = srcset.split(",")[0].trim().split("\\s+")[0];
            if (isValidImageUrl(url)) return url;
        }

        return "";
    }

    /**
     * Checks if the image URL is valid and is not a placeholder or base64 SVG.
     */
    private static boolean isValidImageUrl(String url) {
        return url != null 
            && !url.isEmpty() 
            && !url.startsWith("data:")
            && !url.contains("placeholder")
            && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * Navigates to the article URL, handles popups, and fetches the body text.
     */
    public static String scrapeArticleContent(WebDriver driver, String articleUrl) {
        if (articleUrl == null || articleUrl.isEmpty()) {
            return "No valid article link available.";
        }

        try {
            driver.get(articleUrl);
            handleCookies(driver); // Re-handle cookies on article page to prevent modals blocking content
            Thread.sleep(1500);

            // Handle potential subscription paywall popup
            try {
                WebElement paywallClose = driver.findElement(By.cssSelector(".paywall-close, .tp-close, .modal-close"));
                if (paywallClose.isDisplayed()) {
                    paywallClose.click();
                    LoggerUtil.info("Dismissed subscription popup.");
                }
            } catch (Exception ignored) {
            }

            // Paragraph selectors to extract body text from most specific to least specific
            By[] paragraphSelectors = new By[] {
                By.cssSelector("[itemprop='articleBody'] p"),
                By.cssSelector("#ctn_article_body p"),
                By.cssSelector(".article_body p"),
                By.cssSelector("[data-dtm-region='articulo_cuerpo'] p"),
                By.cssSelector("article > p"),
                By.cssSelector("article p")
            };

            StringBuilder sb = new StringBuilder();
            List<WebElement> paragraphs = new ArrayList<>();
            
            for (By selector : paragraphSelectors) {
                paragraphs = driver.findElements(selector);
                if (!paragraphs.isEmpty()) break;
            }

            for (WebElement p : paragraphs) {
                String text = p.getText().trim();
                // Filter out short elements (typically navigation or ads)
                if (text.length() > 30) {
                    sb.append(text).append(" ");
                }
                if (sb.length() >= 350) break;
            }

            String fullText = sb.toString().trim();
            if (fullText.length() < 50) {
                return "Content restricted — showing excerpt only";
            }

            if (fullText.length() > 200) {
                return fullText.substring(0, 200) + "...";
            }
            return fullText;

        } catch (Exception e) {
            LoggerUtil.error("Exception scraping content from: " + articleUrl, e);
            return "Error retrieving article content.";
        }
    }

    /**
     * Programmatically downloads an image locally, detecting its real format.
     */
    public static void downloadArticleImage(String imageUrl, int articleIndex) {
        if (imageUrl == null || imageUrl.isEmpty() || imageUrl.startsWith("data:")) {
            LoggerUtil.info("No valid image URL found for article " + articleIndex);
            return;
        }

        File dir = new File("article_images");
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                LoggerUtil.info("Created directory 'article_images/'");
            }
        }

        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;

        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                // Detect extension dynamically from URL or Content-Type header
                String extension = ".jpg"; 
                if (imageUrl.contains(".webp")) {
                    extension = ".webp";
                } else if (imageUrl.contains(".png")) {
                    extension = ".png";
                } else if (imageUrl.contains(".svg")) {
                    extension = ".svg";
                } else {
                    String contentType = conn.getContentType();
                    if (contentType != null) {
                        if (contentType.contains("webp")) extension = ".webp";
                        else if (contentType.contains("png")) extension = ".png";
                        else if (contentType.contains("svg")) extension = ".svg";
                        else if (contentType.contains("jpeg") || contentType.contains("jpg")) extension = ".jpg";
                    }
                }

                String destFileName = "article_images/article_" + articleIndex + extension;
                in = conn.getInputStream();
                out = new FileOutputStream(new File(destFileName));
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                LoggerUtil.info("Saved cover image to: " + destFileName);
            } else {
                LoggerUtil.info("Could not download image, response code: " + status + " for URL: " + imageUrl);
            }
        } catch (Exception e) {
            LoggerUtil.error("Error saving image for article " + articleIndex, e);
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (conn != null) conn.disconnect();
            } catch (Exception ignored) {}
        }
    }
}
