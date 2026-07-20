package com.scarlxrd.datacollector.model.service.scraper;

import java.math.BigDecimal;

public record ScrapedProduct(
        String model,
        BigDecimal price
) {
}