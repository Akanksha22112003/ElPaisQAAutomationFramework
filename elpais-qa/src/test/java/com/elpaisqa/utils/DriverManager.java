/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Thread-safe Driver Manager for initializing local and remote WebDrivers.
 */
package com.elpaisqa.utils;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

public class DriverManager {

    private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();

    public static WebDriver getDriver() {
        return driver.get();
    }

    public static void setDriver(WebDriver webDriver) {
        driver.set(webDriver);
    }

    public static void initLocalDriver(String browser) {
        initLocalDriver(browser, false);
    }

    public static void initLocalDriver(String browser, boolean headless) {
        WebDriver localDriver;

        if ("firefox".equalsIgnoreCase(browser)) {
            WebDriverManager.firefoxdriver().setup();
            FirefoxOptions options = new FirefoxOptions();
            options.addPreference("dom.webnotifications.enabled", false);
            options.addPreference("dom.push.enabled", false);
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            if (headless) {
                options.addArguments("-headless");
            }
            localDriver = new FirefoxDriver(options);
            localDriver.manage().window().maximize();

        } else if ("edge".equalsIgnoreCase(browser)) {
            WebDriverManager.edgedriver().setup();
            localDriver = new EdgeDriver();
            localDriver.manage().window().maximize();

        } else if ("safari".equalsIgnoreCase(browser)) {
            // Safari has built-in driver starting from macOS El Capitan, no setup needed
            localDriver = new SafariDriver();
            localDriver.manage().window().maximize();

        } else if ("chrome".equalsIgnoreCase(browser)) {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-notifications");
            options.addArguments("--disable-popup-blocking");
            options.addArguments("--start-maximized");
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            if (headless) {
                options.addArguments("--headless=new");
            }
            localDriver = new ChromeDriver(options);

        } else {
            throw new IllegalArgumentException("Unsupported browser: " + browser 
                    + ". Supported browsers are: chrome, firefox, edge, safari");
        }

        localDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        setDriver(localDriver);
    }

    public static void initRemoteDriver(MutableCapabilities capabilities) throws MalformedURLException {
        if (capabilities == null) {
            throw new IllegalArgumentException("Capabilities cannot be null");
        }

        URL hubUrl = new URL(com.elpaisqa.config.BrowserStackConfig.HUB_URL);
        RemoteWebDriver remoteDriver = new RemoteWebDriver(hubUrl, capabilities);
        remoteDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45));
        
        LoggerUtil.info("BrowserStack session started: " + remoteDriver.getSessionId().toString());
        setDriver(remoteDriver);
    }

    public static void quitDriver() {
        WebDriver activeDriver = getDriver();
        if (activeDriver != null) {
            try {
                activeDriver.quit();
            } catch (Exception e) {
                LoggerUtil.error("Exception while quitting driver", e);
            } finally {
                driver.remove();
            }
        }
    }
}
