package com.scarlxrd.datacollector.model.service;

import com.scarlxrd.datacollector.model.entity.Product;
import com.scarlxrd.datacollector.model.exception.CollectionErrorType;
import com.scarlxrd.datacollector.model.exception.CollectionException;
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

        private static final ZoneId COLLECTION_ZONE = ZoneId.of("America/Sao_Paulo");

        private final List<ProductScraper> scrapers;

        public Product captureData(String url) {
                URI uri = parseUri(url);

                ProductScraper scraper = findScraper(uri, url);

                log.info(
                                "Scraper selecionado: {}",
                                scraper.store().getDisplayName());

                ScrapedProduct captured = scrape(scraper, url);

                Product product = new Product();

                product.setModel(captured.model());
                product.setPrice(captured.price());
                product.setStore(scraper.store().getDisplayName());
                product.setCollectionDate(LocalDateTime.now(COLLECTION_ZONE));
                product.setUrl( normalizeUrlForStorage(scraper,uri));

                return product;
        }

        private ProductScraper findScraper(
                        URI uri,
                        String originalUrl) {
                return scrapers.stream()
                                .filter(scraper -> scraper.supports(uri))
                                .findFirst()
                                .orElseThrow(() -> new CollectionException(
                                                CollectionErrorType.INVALID_URL,
                                                null,
                                                originalUrl,
                                                "Nenhum scraper disponível para o domínio: "
                                                                + uri.getHost()));
        }

        private ScrapedProduct scrape(
                        ProductScraper scraper,
                        String url) {
                try {
                        return scraper.scrape(url);

                } catch (CollectionException exception) {
                        /*
                         * O scraper já identificou corretamente
                         * o tipo do erro.
                         *
                         * Não devemos transformar, por exemplo,
                         * ANTI_BOT_BLOCKED em UNKNOWN.
                         */
                        throw exception;

                } catch (RuntimeException exception) {
                        /*
                         * Fallback temporário para erros que ainda
                         * não foram classificados pelos scrapers.
                         */
                        throw new CollectionException(
                                        CollectionErrorType.UNKNOWN,
                                        scraper.store(),
                                        url,
                                        "Erro não classificado durante a coleta da loja "
                                                        + scraper.store().getDisplayName(),
                                        exception);
                }
        }

        private String normalizeUrlForStorage(
                        ProductScraper scraper,
                        URI uri) {
                String originalUrl = uri.toString();

                String normalizedUrl;

                if (scraper.store() == Store.OLX) {
                        /*
                         * URLs da OLX podem conter parâmetros
                         * extremamente longos e informações de
                         * rastreamento que não são necessárias
                         * para identificar o produto.
                         */
                        normalizedUrl = removeQueryAndFragment(uri);
                } else {
                        /*
                         * Para as demais lojas preservamos a query
                         * string, pois ela pode ser necessária,
                         * removendo apenas o fragmento.
                         */
                        normalizedUrl = removeFragment(uri);
                }

                if (!normalizedUrl.equals(originalUrl)) {
                        log.debug(
                                        "URL normalizada para armazenamento: {} -> {}",
                                        originalUrl,
                                        normalizedUrl);
                }

                return normalizedUrl;
        }

        private String removeFragment(URI uri) {
                String url = uri.toString();

                int fragmentIndex = url.indexOf('#');

                if (fragmentIndex == -1) {
                        return url;
                }

                return url.substring(
                                0,
                                fragmentIndex);
        }

        private String removeQueryAndFragment(
                        URI uri) {
                String url = uri.toString();

                int queryIndex = url.indexOf('?');

                int fragmentIndex = url.indexOf('#');

                int cutIndex = findFirstValidIndex(queryIndex,fragmentIndex);

                if (cutIndex == -1) {return url;}

                return url.substring(
                                0,
                                cutIndex);
        }

        private int findFirstValidIndex(int first,int second) {
                if (first == -1) {
                        return second;
                }

                if (second == -1) {
                        return first;
                }

                return Math.min( first, second);
        }

        private URI parseUri(String url) {
                if (url == null || url.isBlank()) {
                        throw new CollectionException(
                                        CollectionErrorType.INVALID_URL,
                                        null,
                                        url,
                                        "A URL não pode estar vazia");
                }

                try {
                        URI uri = URI.create(url);

                        validateScheme(uri, url);

                        validateHost(uri,url);

                        validateUserInfo(uri,url);

                        validatePort(uri,url);

                        return uri;

                } catch (CollectionException exception) {
                        throw exception;

                } catch (IllegalArgumentException exception) {
                        throw new CollectionException(
                                        CollectionErrorType.INVALID_URL,
                                        null,
                                        url,
                                        "URL inválida: " + url,
                                        exception);
                }
        }

        private void validateScheme(URI uri,String originalUrl) {
                String scheme = uri.getScheme();

                if (scheme == null|| (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {

                        throw new CollectionException(
                                        CollectionErrorType.INVALID_URL,
                                        null,
                                        originalUrl,
                                        "Protocolo não suportado: "
                                                        + scheme);
                }
        }

        private void validateHost(URI uri,String originalUrl) {
                String host = uri.getHost();

                if (host == null|| host.isBlank()) {

                        throw new CollectionException(
                                        CollectionErrorType.INVALID_URL,
                                        null,
                                        originalUrl,
                                        "URL sem domínio válido: "
                                                        + originalUrl);
                }
        }

        private void validateUserInfo(URI uri,String originalUrl) {
                if (uri.getUserInfo() != null) {
                        throw new CollectionException(
                                        CollectionErrorType.INVALID_URL,
                                        null,
                                        originalUrl,
                                        "URLs contendo usuário ou senha não são permitidas");
                }
        }

        private void validatePort( URI uri,String originalUrl) {
                int port = uri.getPort();

                if (port != -1 && port != 80 && port != 443) {

                        throw new CollectionException(
                                        CollectionErrorType.INVALID_URL,
                                        null,
                                        originalUrl,
                                        "Porta não permitida na URL: "
                                                        + port);
                }
        }
}