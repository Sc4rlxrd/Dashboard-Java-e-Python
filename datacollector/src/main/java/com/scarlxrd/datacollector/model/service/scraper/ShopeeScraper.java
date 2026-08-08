package com.scarlxrd.datacollector.model.service.scraper;

import com.scarlxrd.datacollector.model.exception.CollectionErrorType;
import com.scarlxrd.datacollector.model.exception.CollectionException;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ShopeeScraper
        extends AbstractSeleniumScraper {

    @Override
    public Store store() {
        return Store.SHOPEE;
    }

    @Override
    public boolean supports(URI uri) {
        return hostMatches(
                uri,
                "shopee.com.br"
        ) || hostMatches(
                uri,
                "shp.ee"
        );
    }

    @Override
    protected ScrapedProduct capture(
            WebDriver driver,
            WebDriverWait wait,
            String url
    ) {
        validatePage(
                driver,
                url
        );

        String model = waitForValue(
                wait,
                currentDriver -> {
                validatePage(
                        currentDriver,
                        url
                );

                return firstNonBlank(
                        firstAttribute(
                                currentDriver,
                                "content",
                                By.cssSelector(
                                        "meta[property='og:title']"
                                )
                        ),
                        firstText(
                                currentDriver,
                                By.cssSelector("h1")
                        )
                );
                },
                "Nome do produto",
                CollectionErrorType.SELECTOR_CHANGED,
                url
        );
        String rawPrice = waitForValue(
                wait,
                currentDriver -> {
                validatePage(
                        currentDriver,
                        url
                );

                return firstNonBlank(
                        firstAttribute(
                                currentDriver,
                                "content",
                                By.cssSelector(
                                        "meta[property='product:price:amount']"
                                ),
                                By.cssSelector(
                                        "meta[property='og:price:amount']"
                                )
                        )
                );
                },
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

    private void validatePage(
            WebDriver driver,
            String url
    ) {
        String currentUrl =
                driver.getCurrentUrl();

        if (
                currentUrl != null
                        && (
                        currentUrl.contains(
                                "/verify/traffic/error"
                        )
                                || currentUrl.contains(
                                "/verify/traffic/"
                        )
                )
        ) {
            throw new CollectionException(
                    CollectionErrorType.ANTI_BOT_BLOCKED,
                    Store.SHOPEE,
                    url,
                    "Shopee bloqueou o acesso automatizado "
                            + "e redirecionou para a verificação de tráfego"
            );
        }
    }
}