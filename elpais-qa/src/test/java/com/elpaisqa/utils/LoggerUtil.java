/**
 * Author: Akanksha Singh
 * Date: 01/08/2026
 * Description: Logger utility wrapping Log4j logging configurations.
 */
package com.elpaisqa.utils;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public final class LoggerUtil {

    private static final Logger logger = LogManager.getLogger(LoggerUtil.class);

    public static Logger getLogger(Class<?> clazz) {
        return LogManager.getLogger(clazz);
    }

    public static void startTestCase(String testCaseName) {
        logger.info("===================================== " + testCaseName + " TEST START =========================================");
    }

    public static void endTestCase(String testCaseName) {
        logger.info("===================================== " + testCaseName + " TEST END =========================================");
    }

    public static void fatal(String message) {
        logger.fatal(message);
    }

    public static void error(String message) {
        logger.error(message);
    }

    public static void error(String message, Throwable t) {
        logger.error(message, t);
    }

    public static void warn(String message) {
        logger.warn(message);
    }

    public static void info(String message) {
        logger.info(message);
    }

    public static void debug(String message) {
        logger.debug(message);
    }

    public static void trace(String message) {
        logger.trace(message);
    }
}
