package com.scarlxrd.datacollector.model.service.scraper;

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
        return hostMatches(uri, "shopee.com.br")
                || hostMatches(uri, "shp.ee");
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
                                    "meta[property='product:price:amount']"
                            ),
                            By.cssSelector(
                                    "meta[property='og:price:amount']"
                            )
                    )
            ),
            "Preço"
    );

    return new ScrapedProduct(
            model,
            PriceParser.parse(rawPrice)
    );
}

    private void validatePage(WebDriver driver) {
            String currentUrl = driver.getCurrentUrl();

            if (currentUrl.contains("/verify/traffic/error")) {
                throw new IllegalStateException(
                        "A Shopee bloqueou o acesso automatizado e "
                                + "redirecionou para a verificação de tráfego"
                );
            }
        }
}