package com.scarlxrd.datacollector.model.service.scraper;

import com.scarlxrd.datacollector.model.exception.CollectionErrorType;
import com.scarlxrd.datacollector.model.exception.CollectionException;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TerabyteScraper extends AbstractSeleniumScraper {

    private static final Pattern CASH_PRICE_PATTERN = Pattern.compile(
            "por:\\s*R\\$\\s*([0-9.]+,[0-9]{2})"
                    + ".*?a\\s+vista",
            Pattern.CASE_INSENSITIVE
                    | Pattern.UNICODE_CASE);

    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "R\\$\\s*([0-9.]+,[0-9]{2})",
            Pattern.CASE_INSENSITIVE
                    | Pattern.UNICODE_CASE);

    @Override
    public Store store() {
        return Store.TERABYTE;
    }

    @Override
    public boolean supports(URI uri) {
        return hostMatches(
                uri,
                "terabyteshop.com.br");
    }

    @Override
    protected ScrapedProduct capture(
            WebDriver driver,
            WebDriverWait wait,
            String url) {
        /*
         * A Terabyte pode apresentar temporariamente
         * a página "Just a moment..." do Cloudflare.
         *
         * Diferente da KaBuM, damos alguns segundos
         * para essa página intermediária desaparecer.
         */
        waitForProductPage(
                driver,
                wait,
                url);

        validatePage(
                driver,
                url);

        String model = waitForValue(
                wait,
                currentDriver -> {
                    validatePage(
                            currentDriver,
                            url);

                    return firstNonBlank(
                            firstText(
                                    currentDriver,
                                    By.tagName("h1")),
                            firstAttribute(
                                    currentDriver,
                                    "content",
                                    By.cssSelector(
                                            "meta[property='og:title']")));
                },
                "Nome do produto",
                CollectionErrorType.SELECTOR_CHANGED,
                url);

        String rawPrice = waitForValue(
                wait,
                currentDriver -> {
                    validatePage(
                            currentDriver,
                            url);

                    return capturePrice(
                            currentDriver);
                },
                "Preço",
                CollectionErrorType.PRICE_NOT_FOUND,
                url);

        return new ScrapedProduct(
                model,
                parsePrice(
                        rawPrice,
                        url));
    }

    private void waitForProductPage(
            WebDriver driver,
            WebDriverWait wait,
            String url) {
        try {
            wait.until(
                    currentDriver -> {
                        String title = normalize(
                                currentDriver.getTitle());

                        /*
                         * Enquanto estiver na tela transitória
                         * do Cloudflare, continuamos esperando.
                         */
                        if (isChallengeTitle(title)) {
                            return false;
                        }

                        String model = firstNonBlank(
                                firstText(
                                        currentDriver,
                                        By.tagName("h1")),
                                firstAttribute(
                                        currentDriver,
                                        "content",
                                        By.cssSelector(
                                                "meta[property='og:title']")));

                        return model != null
                                && !model.isBlank();
                    });

        } catch (TimeoutException exception) {
            /*
             * Depois da espera, validamos a página.
             *
             * Se ainda estiver em "Just a moment...",
             * será classificada como ANTI_BOT_BLOCKED.
             */
            validatePage(
                    driver,
                    url);

            throw new CollectionException(
                    CollectionErrorType.TIMEOUT,
                    Store.TERABYTE,
                    url,
                    "Página do produto da TerabyteShop "
                            + "não ficou disponível a tempo",
                    exception);
        }
    }

    private String capturePrice(
            WebDriver driver) {
        String bodyText = firstTextContent(
                driver,
                By.tagName("body"));

        String normalizedBody = normalizePriceText(
                bodyText);

        /*
         * Prioridade 1:
         *
         * De: R$ 2.117,64 por:
         * R$ 1.699,99
         * 20% OFF
         * à vista ...
         */
        String cashPrice = extractPrice(
                normalizedBody,
                CASH_PRICE_PATTERN);

        if (cashPrice != null) {
            return cashPrice;
        }

        /*
         * Prioridade 2:
         * metadados estruturados.
         */
        String metadataPrice = firstNonBlank(
                firstAttribute(
                        driver,
                        "content",
                        By.cssSelector(
                                "meta[itemprop='price']"),
                        By.cssSelector(
                                "meta[property='product:price:amount']"),
                        By.cssSelector(
                                "meta[property='og:price:amount']")));

        if (metadataPrice != null
                && !metadataPrice.isBlank()) {
            return metadataPrice;
        }

        /*
         * Último fallback:
         * primeiro preço encontrado no texto.
         */
        return extractPrice(
                normalizedBody,
                PRICE_PATTERN);
    }

    private String extractPrice(
            String text,
            Pattern pattern) {
        if (text == null
                || text.isBlank()) {
            return null;
        }

        Matcher matcher = pattern.matcher(
                text);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    private String normalizePriceText(
            String value) {
        if (value == null
                || value.isBlank()) {
            return "";
        }

        String normalizedWhitespace = value
                .replace(
                        '\u00A0',
                        ' ')
                .replace(
                        '\u202F',
                        ' ')
                .replaceAll(
                        "\\s+",
                        " ");

        return Normalizer
                .normalize(
                        normalizedWhitespace,
                        Normalizer.Form.NFD)
                .replaceAll(
                        "\\p{M}",
                        "")
                .trim();
    }

    private void validatePage(
            WebDriver driver,
            String url) {
        String currentUrl = driver.getCurrentUrl();

        if (currentUrl == null
                || currentUrl.isBlank()) {
            throw new CollectionException(
                    CollectionErrorType.UNKNOWN,
                    Store.TERABYTE,
                    url,
                    "TerabyteShop retornou uma página sem URL válida");
        }

        String title = driver.getTitle();

        String body = firstTextContent(
                driver,
                By.tagName("body"));

        String normalizedUrl = currentUrl
                .toLowerCase(
                        Locale.ROOT);

        String normalizedTitle = normalize(
                title);

        String normalizedBody = normalize(
                body);

        boolean antiBotByUrl = normalizedUrl.contains(
                "/challenge")
                || normalizedUrl.contains(
                        "/verify/")
                || normalizedUrl.contains(
                        "cf_chl");

        boolean antiBotByTitle = isChallengeTitle(
                normalizedTitle);

        boolean antiBotByBody = normalizedBody.contains(
                "verify you are human")
                || normalizedBody.contains(
                        "verifique se voce e humano")
                || normalizedBody.contains(
                        "you have been blocked")
                || normalizedBody.contains(
                        "seu acesso foi bloqueado")
                || normalizedBody.contains(
                        "acesso temporariamente bloqueado");

        if (antiBotByUrl
                || antiBotByTitle
                || antiBotByBody) {
            throw new CollectionException(
                    CollectionErrorType.ANTI_BOT_BLOCKED,
                    Store.TERABYTE,
                    url,
                    "TerabyteShop retornou uma página explícita "
                            + "de bloqueio ou verificação");
        }

        boolean productNotFound = normalizedTitle.contains(
                "produto nao encontrado")
                || normalizedTitle.contains(
                        "pagina nao encontrada")
                || normalizedBody.contains(
                        "produto nao encontrado")
                || normalizedBody.contains(
                        "produto inexistente");

        if (productNotFound) {
            throw new CollectionException(
                    CollectionErrorType.PRODUCT_NOT_FOUND,
                    Store.TERABYTE,
                    url,
                    "Produto da TerabyteShop não encontrado");
        }
    }

    private boolean isChallengeTitle(
            String normalizedTitle) {
        return normalizedTitle.contains(
                "just a moment")
                || normalizedTitle.contains(
                        "access denied")
                || normalizedTitle.contains(
                        "verify you are human")
                || normalizedTitle.contains(
                        "verifique se voce e humano")
                || normalizedTitle.contains(
                        "attention required");
    }

    private String normalize(
            String value) {
        if (value == null
                || value.isBlank()) {
            return "";
        }

        return Normalizer
                .normalize(
                        value,
                        Normalizer.Form.NFD)
                .replaceAll(
                        "\\p{M}",
                        "")
                .toLowerCase(
                        Locale.ROOT)
                .replace(
                        '\u00A0',
                        ' ')
                .replace(
                        '\u202F',
                        ' ')
                .replaceAll(
                        "\\s+",
                        " ")
                .trim();
    }
}