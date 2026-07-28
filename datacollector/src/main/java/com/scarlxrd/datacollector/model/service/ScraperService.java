package com.scarlxrd.datacollector.model.service;

import com.scarlxrd.datacollector.model.entity.Product;
import com.scarlxrd.datacollector.model.service.scraper.ProductScraper;
import com.scarlxrd.datacollector.model.service.scraper.ScrapedProduct;
import com.scarlxrd.datacollector.model.service.scraper.Store;
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

    private static final ZoneId COLLECTION_ZONE =
            ZoneId.of("America/Sao_Paulo");

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

        /*
         A coleta utiliza a URL original.
          Isso preserva qualquer parâmetro que a loja
          possa precisar para abrir a página.
         */
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
        product.setUrl(
                normalizeUrlForStorage(
                        scraper,
                        uri,
                        url
                )
        );

        return product;
    }

    private String normalizeUrlForStorage(
            ProductScraper scraper,
            URI uri,
            String originalUrl
    ) {
        if (scraper.store() != Store.OLX) {
            return originalUrl;
        }

        String cleanedUrl =
                removeQueryAndFragment(uri);

        if (!cleanedUrl.equals(originalUrl)) {
            log.debug(
                    "URL da OLX normalizada para armazenamento: {}",
                    cleanedUrl
            );
        }

        return cleanedUrl;
    }

    private String removeQueryAndFragment(
            URI uri
    ) {
        String url = uri.toString();

        int queryIndex = url.indexOf('?');
        int fragmentIndex = url.indexOf('#');

        int cutIndex = findFirstValidIndex(
                queryIndex,
                fragmentIndex
        );

        if (cutIndex == -1) {
            return url;
        }

        return url.substring(0, cutIndex);
    }

    private int findFirstValidIndex(
            int first,
            int second
    ) {
        if (first == -1) {
            return second;
        }

        if (second == -1) {
            return first;
        }

        return Math.min(first, second);
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

            String scheme = uri.getScheme();

            if (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException(
                        "Protocolo não suportado: " + scheme
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