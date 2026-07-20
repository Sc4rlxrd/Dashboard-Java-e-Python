package com.scarlxrd.datacollector.model.service.scraper;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class AmazonScraper extends AbstractSeleniumScraper {

    @Override
    public Store store() {
        return Store.AMAZON;
    }

    @Override
    public boolean supports(URI uri) {
        return hostMatches(uri, "amazon.com.br")
                || hostMatches(uri, "amazon.com");
    }

 @Override
protected ScrapedProduct capture(
        WebDriver driver,
        WebDriverWait wait,
        String url
) {
    String model = waitForValue(
            wait,
            currentDriver -> firstNonBlank(
                    firstText(
                            currentDriver,
                            By.id("productTitle"),
                            By.cssSelector("h1#title span")
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
                    firstTextContent(
                            currentDriver,
                            By.cssSelector(
                                    "#corePrice_feature_div "
                                            + ".a-price .a-offscreen"
                            ),
                            By.cssSelector(
                                    "#corePriceDisplay_desktop_feature_div "
                                            + ".a-price .a-offscreen"
                            ),
                            By.cssSelector(
                                    ".apexPriceToPay .a-offscreen"
                            ),
                            By.id("price_inside_buybox"),
                            By.id("priceblock_ourprice"),
                            By.id("priceblock_dealprice")
                    )
            ),
            "Preço"
    );

    return new ScrapedProduct(
            model,
            PriceParser.parse(rawPrice)
    );
}
}