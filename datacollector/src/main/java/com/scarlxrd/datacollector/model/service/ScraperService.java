package com.scarlxrd.datacollector.model.service;

import com.scarlxrd.datacollector.model.entity.Product;
import com.scarlxrd.datacollector.model.service.scraper.ProductScraper;
import com.scarlxrd.datacollector.model.service.scraper.ScrapedProduct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    private static final ZoneId COLLECTION_ZONE = ZoneId.of("America/Sao_Paulo");

    private final List<ProductScraper> scrapers;

    public Product captureData(String url) {
        URI uri = parseUri(url);

        ProductScraper scraper = scrapers.stream()
                .filter(candidate -> candidate.supports(uri))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nenhum scraper disponível para: "
                                + uri.getHost()
                ));

        log.info(
                "Scraper selecionado: {}",
                scraper.store().getDisplayName()
        );

        ScrapedProduct captured = scraper.scrape(url);

        Product product = new Product();

        product.setModel(captured.model());
        product.setPrice(captured.price());
        product.setStore(
                scraper.store().getDisplayName()
        );
        product.setCollectionDate(
                LocalDateTime.now(COLLECTION_ZONE)
        );
        product.setUrl(url);

        return product;
    }

    private URI parseUri(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "A URL não pode estar vazia"
            );
        }

        try {
            URI uri = URI.create(url);

            if (uri.getHost() == null) {
                throw new IllegalArgumentException(
                        "URL sem domínio válido: " + url
                );
            }

            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "URL inválida: " + url,
                    exception
            );
        }
    }
}