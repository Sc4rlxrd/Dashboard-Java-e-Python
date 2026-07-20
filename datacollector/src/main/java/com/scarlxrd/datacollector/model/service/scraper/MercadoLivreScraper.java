package com.scarlxrd.datacollector.model.service.scraper;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import java.util.Locale;
import java.net.URI;

@Component
public class MercadoLivreScraper
        extends AbstractSeleniumScraper {

    @Override
    public Store store() {
        return Store.MERCADO_LIVRE;
    }

    @Override
    public boolean supports(URI uri) {
        return hostMatches(uri, "mercadolivre.com.br")
                || hostMatches(uri, "mercadolivre.com");
    }

    @Override
    protected ScrapedProduct capture(
            WebDriver driver,
            WebDriverWait wait,
            String url
    ) {
        validatePage(driver);
        String model = waitForValue(
                wait,
                currentDriver -> firstNonBlank(
                        firstText(
                                currentDriver,
                                By.cssSelector("h1.ui-pdp-title"),
                                By.cssSelector(
                                        ".ui-pdp-header__title-container h1"
                                ),
                                By.tagName("h1")
                        ),
                        firstAttribute(
                                currentDriver,
                                "content",
                                By.cssSelector(
                                        "meta[property='og:title']"
                                )
                        )
                ),
                "Nome do produto"
        );

        String rawPrice = waitForValue(
                wait,
                currentDriver -> firstNonBlank(
                        firstAttribute(
                                currentDriver,
                                "content",
                                By.cssSelector(
                                        "meta[itemprop='price']"
                                ),
                                By.cssSelector(
                                        "meta[property='product:price:amount']"
                                )
                        ),
                        capturePriceParts(currentDriver)
                ),
                "Preço"
        );

        return new ScrapedProduct(
                model,
                PriceParser.parse(rawPrice)
        );
    }

    private String capturePriceParts(WebDriver driver) {
        String whole = firstText(
                driver,
                By.cssSelector(
                        ".ui-pdp-price__second-line "
                                + ".andes-money-amount__fraction"
                ),
                By.cssSelector(
                        ".ui-pdp-price__main-container "
                                + ".andes-money-amount__fraction"
                )
        );

        if (whole == null || whole.isBlank()) {
            return null;
        }

        String cents = firstText(
                driver,
                By.cssSelector(
                        ".ui-pdp-price__second-line "
                                + ".andes-money-amount__cents"
                ),
                By.cssSelector(
                        ".ui-pdp-price__main-container "
                                + ".andes-money-amount__cents"
                )
        );

        return cents == null || cents.isBlank()
                ? whole
                : whole + "," + cents;
    }

    private void validatePage(WebDriver driver) {
    String body = firstTextContent(
            driver,
            By.tagName("body")
    );

    if (body == null || body.isBlank()) {
        throw new IllegalStateException(
                "Mercado Livre retornou uma página vazia"
        );
    }

    String normalizedBody =
            body.toLowerCase(Locale.ROOT);

    if (
            normalizedBody.contains(
                    "hubo un error accediendo a esta pagina"
            )
                    || normalizedBody.contains(
                    "ir a la página principal"
            )
    ) {
        throw new IllegalStateException(
                "Mercado Livre retornou uma página de erro "
                        + "em vez da página do produto"
        );
    }
}
}