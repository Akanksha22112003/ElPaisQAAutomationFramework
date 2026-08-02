/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Base Test class to initialize and finalize Extent Reports suite execution and capture screenshots on failures.
 */
package com.elpaisqa.tests;

import com.aventstack.extentreports.Status;
import com.elpaisqa.utils.DriverManager;
import com.elpaisqa.utils.ExtentManager;
import com.elpaisqa.utils.LoggerUtil;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BaseTest {

    protected static final Logger log = LoggerUtil.getLogger(BaseTest.class);

    @BeforeSuite
    public void setupSuite() {
        try {
            ExtentManager.setExtent();
        } catch (Exception e) {
            log.error("Extent setup failed: " + e.getMessage());
        }
    }

    /**
     * Centralized step logger that prints to console logs and reports to ExtentReports.
     */
    protected void logStep(String message, Status status) {
        if (status == Status.INFO) {
            log.info(message);
        } else if (status == Status.FAIL) {
            log.error(message);
        } else if (status == Status.WARNING) {
            log.warn(message);
        } else if (status == Status.PASS) {
            log.info("[PASS] " + message);
        }
        ExtentManager.log(message, status);
    }

    @AfterMethod
    public void afterEachTest(ITestResult result) {
        // capture screenshot only on failure
        if (result.getStatus() == ITestResult.FAILURE) {
            WebDriver driver = DriverManager.getDriver();
            if (driver != null) {
                try {
                    File screenDir = new File("screenshots");
                    if (!screenDir.exists()) {
                        screenDir.mkdirs();
                    }
                    File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                    String fileName = "screenshots/" + result.getName() 
                        + "_" + System.currentTimeMillis() + ".png";
                    java.nio.file.Path targetPath = Paths.get(fileName);
                    Files.copy(src.toPath(), targetPath);
                    log.info("Screenshot saved: " + fileName);
                    
                    // Link screenshot into Extent report
                    ExtentManager.getTest().addScreenCaptureFromPath(targetPath.toAbsolutePath().toString());
                } catch (Exception e) {
                    log.error("Screenshot capture failed: " + e.getMessage());
                }
            }
        }
    }

    @AfterSuite
    public void tearDownSuite() {
        ExtentManager.endReport();
    }
}
