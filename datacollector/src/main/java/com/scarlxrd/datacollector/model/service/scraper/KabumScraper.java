package com.scarlxrd.datacollector.model.service.scraper;

import com.scarlxrd.datacollector.model.exception.CollectionErrorType;
import com.scarlxrd.datacollector.model.exception.CollectionException;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KabumScraper extends AbstractSeleniumScraper {

        private static final Pattern PIX_PRICE_PATTERN = Pattern.compile(
                        "R\\$\\s*([0-9.]+,[0-9]{2})"
                                        + "\\s*a\\s+vista\\s+no\\s+pix",
                        Pattern.CASE_INSENSITIVE
                                        | Pattern.UNICODE_CASE);

        private static final String PRODUCT_DATA_SCRIPT = """
                        function fromJsonLd() {
                          const scripts = document.querySelectorAll(
                              'script[type="application/ld+json"]');
                          for (const script of scripts) {
                            try {
                              let data = JSON.parse(script.textContent);
                              if (Array.isArray(data)) {
                                data = data.find(item => item && item['@type'] === 'Product');
                              }
                              if (data && data['@type'] === 'Product' && data.offers) {
                                const offer = Array.isArray(data.offers)
                                    ? data.offers[0] : data.offers;
                                return {
                                  name: data.name || '',
                                  price: offer.price == null ? '' : String(offer.price),
                                  available: offer.availability
                                      ? !/OutOfStock|SoldOut|Discontinued/i.test(offer.availability)
                                      : null
                                };
                              }
                            } catch (ignored) {}
                          }
                          return null;
                        }
                        function fromNextData() {
                          try {
                            const element = document.getElementById('__NEXT_DATA__');
                            const product = JSON.parse(element.textContent).props.pageProps.product;
                            const price = product.prices && product.prices.priceWithDiscount
                                ? product.prices.priceWithDiscount : product.price;
                            return {
                              name: product.title || '',
                              price: price == null ? '' : String(price),
                              available: typeof product.available === 'boolean'
                                  ? product.available : null
                            };
                          } catch (ignored) {
                            return null;
                          }
                        }
                        return fromJsonLd() || fromNextData();
                        """;

        private record ProductData(
                        String name,
                        BigDecimal price,
                        boolean inStock) {
        }

        @Override
        public Store store() {
                return Store.KABUM;
        }

        @Override
        public boolean supports(URI uri) {
                return hostMatches(
                                uri,
                                "kabum.com.br");
        }

        @Override
        protected Duration pageLoadTimeout() {
                return Duration.ofSeconds(120);
        }

        @Override
        protected Duration elementTimeout() {
                return Duration.ofSeconds(60);
        }

        @Override
        protected PageLoadStrategy pageLoadStrategy() {
                return PageLoadStrategy.EAGER;
        }

        @Override
        protected boolean blockImages() {
                return true;
        }

        @Override
        protected boolean continueOnPageLoadTimeout() {
                return true;
        }

        @Override
        protected ScrapedProduct capture(
                        WebDriver driver,
                        WebDriverWait wait,
                        String url) {
                ProductData data;

                try {
                        data = wait.until(currentDriver -> {
                                validatePage(
                                                currentDriver,
                                                url,
                                                false);

                                ProductData structured = readStructuredData(
                                                currentDriver);

                                if (structured != null) {
                                        return structured;
                                }

                                return readVisibleData(
                                                currentDriver);
                        });

                } catch (TimeoutException exception) {

                        validatePage(
                                        driver,
                                        url,
                                        true);

                        throw new CollectionException(
                                        CollectionErrorType.PRICE_NOT_FOUND,
                                        Store.KABUM,
                                        url,
                                        "Nome ou preço do produto não encontrados na página",
                                        exception);
                }

                if (!data.inStock()) {
                        throw new CollectionException(
                                        CollectionErrorType.OUT_OF_STOCK,
                                        Store.KABUM,
                                        url,
                                        "Produto esgotado na KaBuM!");
                }

                return new ScrapedProduct(
                                data.name(),
                                data.price());
        }

        private ProductData readStructuredData(
                        WebDriver driver) {
                Object result = ((JavascriptExecutor) driver)
                                .executeScript(
                                                PRODUCT_DATA_SCRIPT);

                if (!(result instanceof Map<?, ?> map)) {
                        return null;
                }

                String name = asText(
                                map.get("name"));

                boolean inStock = !Boolean.FALSE.equals(
                                map.get("available"));

                BigDecimal price = parseStructuredPrice(
                                asText(
                                                map.get("price")));

                if (name == null
                                || (price == null && inStock)) {
                        return null;
                }

                return new ProductData(
                                name,
                                price,
                                inStock);
        }

        private ProductData readVisibleData(
                        WebDriver driver) {
                String name = firstNonBlank(
                                firstText(
                                                driver,
                                                By.tagName("h1")),
                                firstAttribute(
                                                driver,
                                                "content",
                                                By.cssSelector(
                                                                "meta[property='og:title']")));

                if (name == null) {
                        return null;
                }

                String rawPrice = extractPrice(
                                normalizePriceText(
                                                firstTextContent(
                                                                driver,
                                                                By.tagName("body"))),
                                PIX_PRICE_PATTERN);

                if (rawPrice == null) {
                        return null;
                }

                try {
                        return new ProductData(
                                        name,
                                        PriceParser.parse(
                                                        rawPrice),
                                        true);

                } catch (IllegalArgumentException exception) {
                        return null;
                }
        }

        private BigDecimal parseStructuredPrice(
                        String rawPrice) {
                if (rawPrice == null) {
                        return null;
                }

                try {
                        BigDecimal price = new BigDecimal(
                                        rawPrice);

                        if (price.signum() <= 0) {
                                return null;
                        }

                        return price.setScale(
                                        2,
                                        RoundingMode.HALF_UP);

                } catch (NumberFormatException exception) {
                        return null;
                }
        }

        private String asText(
                        Object value) {
                if (value == null) {
                        return null;
                }

                String text = value.toString().trim();

                return text.isEmpty()
                                ? null
                                : text;
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
                        String url,
                        boolean includeBody) {
                String currentUrl = driver.getCurrentUrl();

                if (currentUrl == null
                                || currentUrl.isBlank()) {
                        throw new CollectionException(
                                        CollectionErrorType.UNKNOWN,
                                        Store.KABUM,
                                        url,
                                        "KaBuM! retornou uma página sem URL válida");
                }

                String title = driver.getTitle();

                String body = includeBody
                                ? firstTextContent(
                                                driver,
                                                By.tagName("body"))
                                : null;

                String normalizedUrl = currentUrl.toLowerCase(Locale.ROOT);

                String normalizedTitle = normalize(
                                title);

                String normalizedBody = normalize(
                                body);

                boolean antiBotByUrl = normalizedUrl.contains(
                                "/challenge")
                                || normalizedUrl.contains(
                                                "/verify/")
                                || normalizedUrl.contains(
                                                "/captcha")
                                || normalizedUrl.contains(
                                                "cf_chl");

                boolean antiBotByTitle = normalizedTitle.contains(
                                "access denied")
                                || normalizedTitle.contains(
                                                "verify you are human")
                                || normalizedTitle.contains(
                                                "verifique se voce e humano")
                                || normalizedTitle.contains(
                                                "just a moment")
                                || normalizedTitle.contains(
                                                "attention required");

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
                                        Store.KABUM,
                                        url,
                                        "KaBuM! retornou uma página explícita "
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
                                        Store.KABUM,
                                        url,
                                        "Produto da KaBuM! não encontrado");
                }
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