package com.scarlxrd.datacollector.model.service.scraper;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.TimeoutException;

import java.util.function.Function;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.List;

@Slf4j
public abstract class AbstractSeleniumScraper implements ProductScraper {

    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(45);

    private static final Duration ELEMENT_TIMEOUT = Duration.ofSeconds(30);

    @Override
    public final ScrapedProduct scrape(String url) {
        WebDriver driver = createDriver();

        try {
            log.info(
                    "Abrindo página da loja {}: {}",
                    store().getDisplayName(),
                    url
            );

            driver.manage()
                    .timeouts()
                    .pageLoadTimeout(PAGE_LOAD_TIMEOUT);

            driver.get(url);

            waitForDocumentReady(driver);

            WebDriverWait wait = new WebDriverWait(
                    driver,
                    ELEMENT_TIMEOUT
            );

            ScrapedProduct product = capture(
                    driver,
                    wait,
                    url
            );

            log.info(
                    "[{}] Coletado: {} -> R$ {}",
                    store().getDisplayName(),
                    product.model(),
                    product.price()
            );

            return product;
        } catch (Exception exception) {
            log.error(
                    "Falha ao coletar produto da loja {}. "
                            + "URL original: {} | URL final: {} | "
                            + "Título recebido: {}",
                    store().getDisplayName(),
                    url,
                    safeCurrentUrl(driver),
                    safeTitle(driver),
                    exception
            );

            throw new IllegalStateException(
                    "Falha na coleta da loja "
                            + store().getDisplayName(),
                    exception
            );
        } finally {
            driver.quit();
        }
    }

    protected abstract ScrapedProduct capture(
            WebDriver driver,
            WebDriverWait wait,
            String url
    );

    protected WebDriver createDriver() {
        String chromeBinary = System.getenv().getOrDefault(
                "CHROME_BIN",
                "/usr/bin/chromium-browser"
        );

        String driverPath = System.getenv().getOrDefault(
                "CHROMEDRIVER_PATH",
                "/usr/bin/chromedriver"
        );

        ChromeOptions options = new ChromeOptions();

        options.setBinary(chromeBinary);

        options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--window-size=1920,1080",
                "--lang=pt-BR",
                "--disable-notifications"
        );

        ChromeDriverService service =
                new ChromeDriverService.Builder()
                        .usingDriverExecutable(
                                new File(driverPath)
                        )
                        .build();

        return new ChromeDriver(service, options);
    }

    protected String firstText(
            WebDriver driver,
            By... selectors
    ) {
        for (By selector : selectors) {
            List<WebElement> elements =
                    driver.findElements(selector);

            for (WebElement element : elements) {
                try {
                    String text = element.getText();

                    if (text != null && !text.isBlank()) {
                        return text.trim();
                    }
                } catch (Exception ignored) {
                    // Tenta o próximo elemento.
                }
            }
        }

        return null;
    }

    protected String firstTextContent(
            WebDriver driver,
            By... selectors
    ) {
        for (By selector : selectors) {
            List<WebElement> elements =
                    driver.findElements(selector);

            for (WebElement element : elements) {
                try {
                    String text = element.getAttribute(
                            "textContent"
                    );

                    if (text != null && !text.isBlank()) {
                        return text.trim();
                    }
                } catch (Exception ignored) {
                    // Tenta o próximo elemento.
                }
            }
        }

        return null;
    }

    protected String firstAttribute(
            WebDriver driver,
            String attribute,
            By... selectors
    ) {
        for (By selector : selectors) {
            List<WebElement> elements =
                    driver.findElements(selector);

            for (WebElement element : elements) {
                try {
                    String value = element.getAttribute(
                            attribute
                    );

                    if (value != null && !value.isBlank()) {
                        return value.trim();
                    }
                } catch (Exception ignored) {
                    // Tenta o próximo elemento.
                }
            }
        }

        return null;
    }

    protected String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    protected String required(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    field + " não encontrado na página"
            );
        }

        return value.trim();
    }

    protected boolean hostMatches(
            URI uri,
            String domain
    ) {
        String host = uri.getHost();

        if (host == null) {
            return false;
        }

        String normalizedHost = host.toLowerCase();

        return normalizedHost.equals(domain)
                || normalizedHost.endsWith("." + domain);
    }

    private void waitForDocumentReady(
            WebDriver driver
    ) {
        WebDriverWait wait = new WebDriverWait(
                driver,
                PAGE_LOAD_TIMEOUT
        );

        wait.until(currentDriver -> {
            Object state =
                    ((JavascriptExecutor) currentDriver)
                            .executeScript(
                                    "return document.readyState"
                            );

            return "complete".equals(state);
        });
    }

    private String safeTitle(WebDriver driver) {
        try {
            return driver.getTitle();
        } catch (Exception exception) {
            return "indisponível";
        }
    }

    private String safeCurrentUrl(WebDriver driver) {
        try {
            return driver.getCurrentUrl();
        } catch (Exception exception) {
            return "indisponível";
        }
    }
    protected String waitForValue(
        WebDriverWait wait,
        Function<WebDriver, String> extractor,
        String fieldName
) {
    try {
        return wait.until(driver -> {
            String value = extractor.apply(driver);

            if (value == null || value.isBlank()) {
                return null;
            }

            return value.trim();
        });
    } catch (TimeoutException exception) {
        throw new IllegalStateException(
                fieldName + " não encontrado na página",
                exception
        );
    }
}

}