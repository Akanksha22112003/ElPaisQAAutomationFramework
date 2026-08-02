# El País QA Automation Framework

A premium, production-ready Web Automation and Scraping framework built in Java using **Selenium 4**, **TestNG**, **Log4j2**, and **ExtentReports**. 

This framework scrapes article metadata from the **Opinion** section of the Spanish news portal **El País** (https://elpais.com), downloads cover images locally, translates article headlines using a synchronized rate-limited translation API, and performs word frequency analysis on the translated results. It is fully parameterized to support local multi-browser execution as well as thread-safe parallel execution on **BrowserStack** across desktop and mobile devices.

---

## 🛠️ Technology Stack

* **Core Language**: Java 11 (JDK 11+)
* **Build Tool**: Apache Maven
* **Web Automation**: Selenium WebDriver 4.x
* **Test Engine**: TestNG (for parameterization, annotations, and parallel execution)
* **HTML Reports**: ExtentReports 5 (Spark Reporter)
* **Logging Framework**: Log4j2 (Console & File Appenders)
* **Driver Management**: WebDriverManager (automated binary lifecycle handling)
* **JSON Manipulation**: org.json

---

## 📁 Project Directory Structure

```text
elpais-qa/
├── .env                              # Configured credentials for BrowserStack
├── .env.example                      # Template for BrowserStack credentials
├── testng-local.xml                  # Local parallel suite (Chrome & Firefox)
├── testng-browserstack.xml           # Parallel cloud suite (5 parallel threads)
├── pom.xml                           # Maven dependencies and Surefire plugin configuration
├── README.md                         # Detailed project documentation
├── logs/
│   └── elpais_execution.log          # Consolidated Log4j2 execution log file
├── test-output/
│   └── ExtentReport/
│       ├── Report_ElPais.html        # Interactive HTML execution status report
│       └── ReportFAIL_ElPais.html    # Failures-only execution status report
├── article_images/
│   ├── article_1.webp                # Scraped cover images saved dynamically
│   └── ...                           # (Supports .webp, .png, .jpg, and .svg)
├── screenshots/
│   └── [TestName]_[Timestamp].png    # Auto-saved screenshots on failure
└── src/
    └── test/
        ├── java/
        │   └── com/
        │       └── elpaisqa/
        │           ├── config/
        │           │   └── BrowserStackConfig.java      # Credentials loader (.env fallback) & session updater
        │           ├── utils/
        │           │   ├── DriverManager.java           # Thread-safe local/remote WebDriver manager
        │           │   ├── ScraperUtils.java            # Cookie handling, article & responsive image scraper
        │           │   ├── TranslatorUtils.java         # Synchronized, rate-limited translation engine
        │           │   ├── WordAnalyzer.java            # Word frequency counter & English stop word filter
        │           │   ├── LoggerUtil.java              # Log4j2 wrapper methods
        │           │   └── ExtentManager.java           # ExtentReports Spark setup (Theme.DARK)
        │           └── tests/
        │               ├── BaseTest.java                # Suite hooks, step logging, failure screenshots
        │               ├── LocalTest.java               # Local execution suite (Chrome/Firefox)
        │               └── BrowserStackParallelTest.java# BrowserStack parallel cloud suite (5 threads)
        └── resources/
            └── log4j2.xml            # Log4j2 logging format and appender rules
```

---

## ⚙️ Core Architecture & Features

### 1. Robust Scraper Engine (`ScraperUtils.java`)
* **Responsive Image Fallback**: Scrapes responsive images by parsing `srcset` and `data-srcset` attributes on lazy-loaded tags, falling back to `data-src` and `src` to extract real cover files instead of SVG placeholders.
* **Selenium 4 attribute mapping**: Uses `getDomAttribute(name)` (falling back to `getAttribute`) to ensure precise property reading.
* **Defensive cookie banners handling**: Checks selectors quickly using element arrays before executing waits, resolving overlays on landing pages and detail pages without compounding timeout waits.
* **Content extraction fallback**: Extracts article text by matching specific content selectors. Excludes navigation or ad lines by discarding tokens shorter than 30 characters.

### 2. Thread-Safe Rate-Limited Translation (`TranslatorUtils.java`)
* **Synchronized rate limiting**: Uses a global lock (`rateLock`) to restrict requests to **1 call per 1.5 seconds** across all parallel threads, preventing HTTP 429 Too Many Requests errors.
* **Transient error retries**: Automatically retries transient issues (e.g. `SocketTimeoutException`, connection errors, `502`/`503` statuses) up to 2 times.
* **Defensive JSON checking**: Reads payloads using `optJSONObject` and `optString` to protect against missing keys.

### 3. Word Analyzer (`WordAnalyzer.java`)
* **Expanded stop words**: Set contains over 50 noise words (auxiliary verbs, pronouns, contractions).
* **Contraction safety**: Strips apostrophes (e.g. converting `"don't"` to `"dont"`) before splitting punctuation.
* **Sorted descending frequencies**: Sorts entries by occurrence counts and outputs them in descending order.
* **Testable logic**: Exposes a public `countWords(List<String> headers)` method to facilitate writing unit tests.

### 4. Thread-Safe WebDrivers (`DriverManager.java`)
* **ThreadLocal drivers**: Manages local and remote browser instances inside `ThreadLocal<WebDriver>` fields, ensuring thread safety when running tests in parallel.
* **Eager Page Loading**: Configures the `EAGER` load strategy for local and remote browsers, letting the script interact with headers without waiting for analytic tags or ad modules to finish loading.

---

## 🚀 Setup and Configuration

### 1. Credentials Configuration
To run on the BrowserStack cloud, retrieve your credentials from the [BrowserStack Automate Dashboard](https://automate.browserstack.com/) and configure them. You can use either:

#### Option A: Local `.env` file (Recommended)
Rename `.env.example` in the project root directory to `.env` and fill in your keys:
```text
BROWSERSTACK_USERNAME=your_username_here
BROWSERSTACK_ACCESS_KEY=your_access_key_here
```

#### Option B: OS Environment Variables
Alternatively, export the credentials directly to your shell:
* **Windows (PowerShell)**:
  ```powershell
  $env:BROWSERSTACK_USERNAME="your_username_here"
  $env:BROWSERSTACK_ACCESS_KEY="your_access_key_here"
  ```
* **Linux/macOS**:
  ```bash
  export BROWSERSTACK_USERNAME="your_username_here"
  export BROWSERSTACK_ACCESS_KEY="your_access_key_here"
  ```

---

## 🎯 How to Run the Tests

You can execute the tests using Maven in a terminal, or import the project into Eclipse or IntelliJ IDEA.

### 1. Running via Maven CLI
Ensure you open your terminal inside the `d:\Assignment\elpais-qa` directory.

* **Run Locally in Parallel (Chrome & Firefox)**:
  ```bash
  mvn clean test -DsuiteXmlFile=testng-local.xml
  ```
* **Run on BrowserStack Cloud in Parallel (5 Parallel Threads)**:
  ```bash
  mvn clean test -DsuiteXmlFile=testng-browserstack.xml
  ```

### 2. Running via Eclipse IDE
1. Import the project as an **Existing Maven Project**.
2. If red squiggly compilation lines appear, right-click the project folder -> **Maven** -> **Update Project...** -> Check **Force Update of Snapshots/Releases** -> click **OK**.
3. To run:
   * Right-click **`testng-local.xml`** or **`testng-browserstack.xml`** -> **Run As** -> **TestNG Suite**.
   * Or open **Run Configurations...**, create a **Maven Build** configuration, set the Base Directory to `${project_loc:elpais-qa}`, set the Goals to `clean test -DsuiteXmlFile=testng-local.xml` (or `testng-browserstack.xml`), and click **Run**.

---

## 📊 Reports and Execution Outputs

Once the execution completes:
1. **Interactive HTML Reports**:
   * Open `test-output/ExtentReport/Report_ElPais.html` in a web browser to view logs, step timings, and side-by-side Spanish/English headline lists.
   * `ReportFAIL_ElPais.html` is generated on failures and contains embedded failure screenshots.
2. **Consolidated Log File**:
   * Inspect `logs/elpais_execution.log` to view clean Log4j2 logger outputs with timestamps and originating Thread identifiers.
3. **Scraped Cover Images**:
   * View the cover images downloaded under `article_images/` (saved with their correct extensions like `.webp` or `.png`).
4. **Auto-Saved Screenshots**:
   * If a test fails, screenshot images are captured and saved inside `screenshots/`.
