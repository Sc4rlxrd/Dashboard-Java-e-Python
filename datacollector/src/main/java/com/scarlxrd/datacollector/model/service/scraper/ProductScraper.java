package com.scarlxrd.datacollector.model.service.scraper;

import java.net.URI;

public interface ProductScraper {

    Store store();

    boolean supports(URI uri);

    ScrapedProduct scrape(String url);
}