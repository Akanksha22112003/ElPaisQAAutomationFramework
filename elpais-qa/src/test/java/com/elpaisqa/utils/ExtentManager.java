/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: ExtentReports manager configuration class supporting thread-safe reporting.
 */
package com.elpaisqa.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

import java.io.File;

public class ExtentManager {

    private static ExtentSparkReporter htmlReporter;
    private static ExtentSparkReporter htmlReporterFAIL;
    protected static ExtentReports extent;
    protected static final ThreadLocal<ExtentTest> test = new ThreadLocal<>();

    public static void setExtent() {
        File reportDir = new File(System.getProperty("user.dir") + "/test-output/ExtentReport");
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }

        // Initialize the main report
        htmlReporter = new ExtentSparkReporter(System.getProperty("user.dir") + "/test-output/ExtentReport/Report_ElPais.html");
        
        // Initialize the failure report filter
        htmlReporterFAIL = new ExtentSparkReporter(System.getProperty("user.dir") + "/test-output/ExtentReport/ReportFAIL_ElPais.html")
                .filter().statusFilter().as(new Status[] { Status.FAIL }).apply();

        htmlReporter.config().setTheme(Theme.DARK);
        htmlReporter.config().setDocumentTitle("El Pais QA Automation Report");
        htmlReporter.config().setReportName("El Pais Automation Run");

        extent = new ExtentReports();
        extent.attachReporter(htmlReporter, htmlReporterFAIL);

        extent.setSystemInfo("ProjectName", "ElPais QA Automation");
        extent.setSystemInfo("OS", System.getProperty("os.name"));
        extent.setSystemInfo("JavaVersion", System.getProperty("java.version"));
    }

    public static void endReport() {
        if (extent != null) {
            extent.flush();
        }
    }

    public static ExtentTest createTest(String testName) {
        ExtentTest t = extent.createTest(testName);
        test.set(t);
        return t;
    }

    public static ExtentTest getTest() {
        return test.get();
    }

    public static void log(String message, Status status) {
        ExtentTest currentTest = getTest();
        if (currentTest != null) {
            currentTest.log(status, message);
        } else {
            System.err.println("ExtentTest not initialized for this thread.");
        }
    }
}
