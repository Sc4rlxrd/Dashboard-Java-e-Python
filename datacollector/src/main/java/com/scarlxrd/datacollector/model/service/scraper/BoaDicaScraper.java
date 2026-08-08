package com.scarlxrd.datacollector.model.service.scraper;

import com.scarlxrd.datacollector.model.exception.CollectionErrorType;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class BoaDicaScraper
        extends AbstractSeleniumScraper {

    @Override
    public Store store() {
        return Store.BOA_DICA;
    }

    @Override
    public boolean supports(URI uri) {
        return hostMatches(
                uri,
                "boadica.com.br"
        );
    }

    @Override
    protected ScrapedProduct capture(
            WebDriver driver,
            WebDriverWait wait,
            String url
    ) {
        String model = firstText(
                driver,
                By.cssSelector(
                        ".nome-produto"
                ),
                By.cssSelector(
                        "div.nome-produto"
                )
        );

        if (!isValidModel(model)) {
            model =
                    normalizePageTitle(
                            driver.getTitle()
                    );
        }

        if (!isValidModel(model)) {
            model =
                    extractModelFromUrl(url);
        }

        model = required(
                model,
                "Nome do produto",
                CollectionErrorType.SELECTOR_CHANGED,
                url
        );

        String rawPrice = waitForValue(
                wait,
                currentDriver -> firstNonBlank(
                        firstAttribute(
                                currentDriver,
                                "content",
                                By.cssSelector(
                                        "meta[itemprop='price']"
                                )
                        ),
                        firstTextContent(
                                currentDriver,
                                By.cssSelector(
                                        "[itemprop='price']"
                                ),
                                By.xpath(
                                        "//*[contains("
                                                + "normalize-space(text()), "
                                                + "'R$')]"
                                )
                        )
                ),
                "Preço",
                CollectionErrorType.PRICE_NOT_FOUND,
                url
        );

        return new ScrapedProduct(
                model,
                parsePrice(
                        rawPrice,
                        url
                )
        );
    }

    private boolean isValidModel(
            String model
    ) {
        return model != null
                && !model.isBlank()
                && !model.equalsIgnoreCase(
                        "BoaDica"
                )
                && !model.equalsIgnoreCase(
                        "Informações do Produto"
                )
                && !model.contains(
                        "Informações"
                );
    }

    private String extractModelFromUrl(
            String url
    ) {
        String path =
                URI.create(url)
                        .getPath();

        if (path == null
                || path.isBlank()) {
            return null;
        }

        String[] parts =
                path.split("/");

        for (
                int index = parts.length - 1;
                index >= 0;
                index--
        ) {
            if (!parts[index].isBlank()) {
                return parts[index]
                        .replace(
                                "-",
                                " "
                        )
                        .replace(
                                "_",
                                " "
                        )
                        .toUpperCase();
            }
        }

        return null;
    }

    private String normalizePageTitle(
            String pageTitle
    ) {
        if (pageTitle == null
                || pageTitle.isBlank()) {
            return null;
        }

        String title =
                pageTitle
                        .replace(
                                "BoaDica - ",
                                ""
                        )
                        .trim();

        if (
                title.isBlank()
                        || title.equalsIgnoreCase(
                        "BoaDica"
                )
        ) {
            return null;
        }

        return title;
    }
}