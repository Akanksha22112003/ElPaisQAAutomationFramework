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

    public static void handleCookies(WebDriver driver) {
        try {
            By[] cookieSelectors = new By[] {
                By.id("didomi-notice-agree-button"),
                By.cssSelector("#didomi-host button"),
                By.cssSelector(".didomi-components-button--accept"),
                By.xpath("//button[contains(text(),'Aceptar')]"),
                By.xpath("//span[contains(text(),'Aceptar')]/..")
            };

            // Wait up to 6 seconds for at least one cookie consent button to become visible
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(6));
                wait.until(d -> {
                    for (By selector : cookieSelectors) {
                        try {
                            List<WebElement> elements = d.findElements(selector);
                            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                                return elements.get(0);
                            }
                        } catch (Exception ignored) {}
                    }
                    return null;
                });
            } catch (Exception ignored) {
            }

            for (By selector : cookieSelectors) {
                List<WebElement> elements = driver.findElements(selector);
                if (!elements.isEmpty()) {
                    WebElement acceptBtn = elements.get(0);
                    if (acceptBtn.isDisplayed()) {
                        try {
                            acceptBtn.click();
                            LoggerUtil.info("Cookie consent accepted using: " + selector);
                            Thread.sleep(1500);
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

    public static List<ArticleData> scrapeOpinionArticles(WebDriver driver) {
        List<ArticleData> articleList = new ArrayList<>();
        try {
            LoggerUtil.info("scrapeOpinionArticles: Current URL = " + driver.getCurrentUrl() + " | Title = " + driver.getTitle());
            
            // Initial check for DataDome block
            if (isDataDomeBlocked(driver)) {
                LoggerUtil.warn("DataDome block detected! Attempting to delete cookies and refresh...");
                driver.manage().deleteAllCookies();
                Thread.sleep(2000);
                driver.navigate().refresh();
                Thread.sleep(2000);
                LoggerUtil.info("After refresh: Current URL = " + driver.getCurrentUrl() + " | Title = " + driver.getTitle());
            }

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("article")));
            } catch (Exception e) {
                // If still blocked, attempt a hard reload
                if (isDataDomeBlocked(driver)) {
                    LoggerUtil.warn("Still blocked by DataDome! Attempting hard reload...");
                    driver.manage().deleteAllCookies();
                    Thread.sleep(2000);
                    driver.get(driver.getCurrentUrl());
                    Thread.sleep(2000);
                    LoggerUtil.info("After hard reload: Current URL = " + driver.getCurrentUrl() + " | Title = " + driver.getTitle());
                    
                    // Wait one more time
                    wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("article")));
                } else {
                    LoggerUtil.error("Timeout waiting for 'article' tags. Current URL: " + driver.getCurrentUrl() + " | Title: " + driver.getTitle() + " | Source snippet: " + getSourceSnippet(driver));
                    throw e;
                }
            }
            
            List<WebElement> cards = driver.findElements(By.tagName("article"));
            LoggerUtil.info("Found " + cards.size() + " article cards on Opinion page.");

            int count = 0;
            for (WebElement card : cards) {
                if (count >= 5) break;

                String title = "";
                String articleUrl = "";
                String imageUrl = "";

                try {
                    WebElement linkEl = card.findElement(By.cssSelector("h2 a, h3 a, h2.c_t a, .c-title a, a"));
                    title = getElementText(linkEl);
                    articleUrl = linkEl.getDomAttribute("href");
                    if (articleUrl == null) {
                        articleUrl = linkEl.getAttribute("href");
                    }
                } catch (Exception e) {
                    try {
                        WebElement headerEl = card.findElement(By.cssSelector("h2, h3, .c_t"));
                        title = getElementText(headerEl);
                    } catch (Exception ex) {
                        LoggerUtil.error("Could not extract title/url for article card index: " + count, ex);
                        continue;
                    }
                }

                if (title.isEmpty()) {
                    continue;
                }

                try {
                    WebElement imgEl = card.findElement(By.cssSelector("img, figure img, picture img"));
                    imageUrl = extractImageUrl(imgEl);
                } catch (Exception e) {
                }

                articleList.add(new ArticleData(title, articleUrl, imageUrl));
                count++;
            }
        } catch (Exception e) {
            LoggerUtil.error("Error scraping opinion articles list", e);
        }
        return articleList;
    }

    private static String extractImageUrl(WebElement imgEl) {
        String url = imgEl.getDomAttribute("src");
        if (url == null) {
            url = imgEl.getAttribute("src");
        }
        if (isValidImageUrl(url)) return url;

        url = imgEl.getDomAttribute("data-src");
        if (url == null) {
            url = imgEl.getAttribute("data-src");
        }
        if (isValidImageUrl(url)) return url;

        String srcset = imgEl.getDomAttribute("srcset");
        if (srcset == null) {
            srcset = imgEl.getAttribute("srcset");
        }
        if (srcset != null && !srcset.isEmpty()) {
            url = srcset.split(",")[0].trim().split("\\s+")[0];
            if (isValidImageUrl(url)) return url;
        }

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

    private static boolean isValidImageUrl(String url) {
        return url != null 
            && !url.isEmpty() 
            && !url.startsWith("data:")
            && !url.contains("placeholder")
            && (url.startsWith("http://") || url.startsWith("https://"));
    }

    public static String scrapeArticleContent(WebDriver driver, String articleUrl) {
        if (articleUrl == null || articleUrl.isEmpty()) {
            return "No valid article link available.";
        }

        try {
            driver.get(articleUrl);
            handleCookies(driver);
            Thread.sleep(1500);

            try {
                WebElement paywallClose = driver.findElement(By.cssSelector(".paywall-close, .tp-close, .modal-close"));
                if (paywallClose.isDisplayed()) {
                    paywallClose.click();
                    LoggerUtil.info("Dismissed subscription popup.");
                }
            } catch (Exception ignored) {
            }

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
                String text = getElementText(p);
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

    private static String getElementText(WebElement element) {
        if (element == null) {
            return "";
        }
        String text = element.getText();
        if (text != null) {
            text = text.trim();
        }
        if (text == null || text.isEmpty()) {
            text = element.getAttribute("innerText");
            if (text != null) {
                text = text.trim();
            }
        }
        if (text == null || text.isEmpty()) {
            text = element.getAttribute("textContent");
            if (text != null) {
                text = text.trim();
            }
        }
        return text != null ? text : "";
    }

    private static String getSourceSnippet(WebDriver driver) {
        try {
            String source = driver.getPageSource();
            if (source == null) return "null";
            return source.substring(0, Math.min(source.length(), 600));
        } catch (Exception e) {
            return "error getting source: " + e.getMessage();
        }
    }

    private static boolean isDataDomeBlocked(WebDriver driver) {
        try {
            String title = driver.getTitle();
            if (title != null && title.equalsIgnoreCase("elpais.com")) {
                return true;
            }
            String source = driver.getPageSource();
            if (source != null && (source.contains("dd=") || source.contains("cmsg"))) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
