package com.scarlxrd.datacollector.model.service.scraper;

import com.scarlxrd.datacollector.model.exception.CollectionErrorType;
import com.scarlxrd.datacollector.model.exception.CollectionException;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Slf4j
public abstract class AbstractSeleniumScraper implements ProductScraper {

    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(45);

    private static final Duration ELEMENT_TIMEOUT = Duration.ofSeconds(30);

    protected Duration pageLoadTimeout() {
        return PAGE_LOAD_TIMEOUT;
    }

    protected Duration elementTimeout() {
        return ELEMENT_TIMEOUT;
    }

    protected PageLoadStrategy pageLoadStrategy() {
        return PageLoadStrategy.NORMAL;
    }

    protected boolean blockImages() {
        return false;
    }

    protected boolean continueOnPageLoadTimeout() {
        return false;
    }

    @Override
    public final ScrapedProduct scrape(String url) {
        WebDriver driver = null;

        try {
            driver = createDriver();

            log.info(
                    "Abrindo página da loja {}: {}",
                    store().getDisplayName(),
                    url);

            driver.manage()
                    .timeouts()
                    .pageLoadTimeout(pageLoadTimeout());

            boolean fullyLoaded = loadPage(
                    driver,
                    url);

            if (fullyLoaded) {
                waitForDocumentReady(driver);
            }

            WebDriverWait wait = new WebDriverWait(
                    driver,
                    elementTimeout());

            ScrapedProduct product = capture(
                    driver,
                    wait,
                    url);

            log.info(
                    "[{}] Coletado: {} -> R$ {}",
                    store().getDisplayName(),
                    product.model(),
                    product.price());

            return product;

        } catch (CollectionException exception) {
            logCollectionFailure(
                    driver,
                    url,
                    exception);

            throw exception;

        } catch (TimeoutException exception) {
            CollectionException classified = new CollectionException(
                    CollectionErrorType.TIMEOUT,
                    store(),
                    url,
                    "Tempo limite excedido durante a coleta da loja "
                            + store().getDisplayName(),
                    exception);

            logCollectionFailure(
                    driver,
                    url,
                    classified);

            throw classified;

        } catch (WebDriverException exception) {
            CollectionErrorType errorType = classifyWebDriverException(exception);

            CollectionException classified = new CollectionException(
                    errorType,
                    store(),
                    url,
                    buildWebDriverErrorMessage(
                            errorType),
                    exception);

            logCollectionFailure(
                    driver,
                    url,
                    classified);

            throw classified;

        } catch (RuntimeException exception) {
            CollectionException classified = new CollectionException(
                    CollectionErrorType.UNKNOWN,
                    store(),
                    url,
                    "Erro não classificado durante a coleta da loja "
                            + store().getDisplayName(),
                    exception);

            logCollectionFailure(
                    driver,
                    url,
                    classified);

            throw classified;

        } finally {
            safeQuit(driver);
        }
    }

    protected abstract ScrapedProduct capture(
            WebDriver driver,
            WebDriverWait wait,
            String url);

    protected WebDriver createDriver() {
        String chromeBinary = System.getenv().getOrDefault(
                "CHROME_BIN",
                "/usr/bin/chromium-browser");

        String driverPath = System.getenv().getOrDefault(
                "CHROMEDRIVER_PATH",
                "/usr/bin/chromedriver");

        ChromeOptions options = new ChromeOptions();

        options.setBinary(chromeBinary);

        options.setPageLoadStrategy(pageLoadStrategy());

        options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--no-sandbox",
                "--window-size=1920,1080",
                "--lang=pt-BR",
                "--disable-notifications",
                "--disable-extensions",
                "--disable-background-networking",
                "--disable-sync",
                "--mute-audio",
                "--renderer-process-limit=1");

        if (blockImages()) {
            options.setExperimentalOption(
                    "prefs",
                    Map.of(
                            "profile.managed_default_content_settings.images",
                            2));
        }

        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(
                        new File(driverPath))
                .build();

        return new ChromeDriver(
                service,
                options);
    }

    protected String firstText(
            WebDriver driver,
            By... selectors) {
        return firstExtracted(
                driver,
                WebElement::getText,
                selectors);
    }

    protected String firstTextContent(
            WebDriver driver,
            By... selectors) {
        return firstAttribute(
                driver,
                "textContent",
                selectors);
    }

    protected String firstAttribute(
            WebDriver driver,
            String attribute,
            By... selectors) {
        return firstExtracted(
                driver,
                element -> element.getAttribute(attribute),
                selectors);
    }

    private String firstExtracted(
            WebDriver driver,
            Function<WebElement, String> extractor,
            By... selectors) {
        for (By selector : selectors) {
            for (WebElement element : driver.findElements(selector)) {
                try {
                    String value = extractor.apply(element);

                    if (value != null
                            && !value.isBlank()) {
                        return value.trim();
                    }

                } catch (WebDriverException ignored) {
                    // Tenta o próximo elemento.
                }
            }
        }

        return null;
    }

    protected String firstNonBlank(
            String... values) {
        for (String value : values) {
            if (value != null
                    && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    protected String required(
            String value,
            String field,
            CollectionErrorType errorType,
            String url) {
        if (value == null
                || value.isBlank()) {

            throw new CollectionException(
                    errorType,
                    store(),
                    url,
                    field
                            + " não encontrado na página");
        }

        return value.trim();
    }

    protected String waitForValue(
            WebDriverWait wait,
            Function<WebDriver, String> extractor,
            String fieldName,
            CollectionErrorType errorType,
            String url) {
        try {
            return wait.until(driver -> {
                String value = extractor.apply(driver);

                if (value == null
                        || value.isBlank()) {
                    return null;
                }

                return value.trim();
            });

        } catch (TimeoutException exception) {
            throw new CollectionException(
                    errorType,
                    store(),
                    url,
                    fieldName
                            + " não encontrado na página",
                    exception);
        }
    }

    protected BigDecimal parsePrice(
            String rawPrice,
            String url) {
        try {
            return PriceParser.parse(rawPrice);

        } catch (IllegalArgumentException exception) {
            throw new CollectionException(
                    CollectionErrorType.PRICE_NOT_FOUND,
                    store(),
                    url,
                    "Preço inválido ou não utilizável: "
                            + rawPrice,
                    exception);
        }
    }

    protected boolean hostMatches(
            URI uri,
            String domain) {
        String host = uri.getHost();

        if (host == null) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);

        return normalizedHost.equals(domain)
                || normalizedHost.endsWith(
                        "." + domain);
    }

    private boolean loadPage(
            WebDriver driver,
            String url) {
        try {
            driver.get(url);
            return true;

        } catch (TimeoutException exception) {
            if (!continueOnPageLoadTimeout()) {
                throw exception;
            }

            log.warn(
                    "[{}] Timeout no carregamento; interrompendo "
                            + "a página e tentando capturar: {}",
                    store().getDisplayName(),
                    url);

            try {
                ((JavascriptExecutor) driver)
                        .executeScript("window.stop()");

            } catch (WebDriverException ignored) {
                // Se o renderer não responde, a captura falhará
                // com o erro já classificado.
            }

            return false;
        }
    }

    private void waitForDocumentReady(
            WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(
                driver,
                pageLoadTimeout());

        boolean acceptInteractive = pageLoadStrategy() != PageLoadStrategy.NORMAL;

        wait.until(currentDriver -> {
            Object state = ((JavascriptExecutor) currentDriver)
                    .executeScript(
                            "return document.readyState");

            return "complete".equals(state)
                    || (acceptInteractive
                            && "interactive".equals(state));
        });
    }

    private CollectionErrorType classifyWebDriverException(
            WebDriverException exception) {
        String message = exception.getMessage();

        if (message == null) {
            return CollectionErrorType.UNKNOWN;
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);

        if (normalizedMessage.contains(
                "net::err_name_not_resolved")
                || normalizedMessage.contains(
                        "net::err_connection")
                || normalizedMessage.contains(
                        "net::err_internet_disconnected")
                || normalizedMessage.contains(
                        "net::err_network_changed")
                || normalizedMessage.contains(
                        "net::err_proxy_connection_failed")) {
            return CollectionErrorType.NETWORK_ERROR;
        }

        return CollectionErrorType.UNKNOWN;
    }

    private String buildWebDriverErrorMessage(
            CollectionErrorType errorType) {
        if (errorType == CollectionErrorType.NETWORK_ERROR) {

            return "Erro de rede durante a coleta da loja "
                    + store().getDisplayName();
        }

        return "Erro do navegador durante a coleta da loja "
                + store().getDisplayName();
    }

    private void logCollectionFailure(
            WebDriver driver,
            String url,
            CollectionException exception) {
        log.error(
                "Falha na coleta | "
                        + "store={} | "
                        + "errorType={} | "
                        + "url={} | "
                        + "finalUrl={} | "
                        + "title={} | "
                        + "message={}",
                store().getDisplayName(),
                exception.getErrorType(),
                url,
                safeCurrentUrl(driver),
                safeTitle(driver),
                exception.getMessage(),
                exception);
    }

    private String safeTitle(
            WebDriver driver) {
        if (driver == null) {
            return "indisponível";
        }

        try {
            return driver.getTitle();

        } catch (WebDriverException exception) {
            return "indisponível";
        }
    }

    private String safeCurrentUrl(
            WebDriver driver) {
        if (driver == null) {
            return "indisponível";
        }

        try {
            return driver.getCurrentUrl();

        } catch (WebDriverException exception) {
            return "indisponível";
        }
    }

    private void safeQuit(
            WebDriver driver) {
        if (driver == null) {
            return;
        }

        try {
            driver.quit();

        } catch (WebDriverException exception) {
            log.warn(
                    "Não foi possível encerrar o WebDriver da loja {}",
                    store().getDisplayName(),
                    exception);
        }
    }
}