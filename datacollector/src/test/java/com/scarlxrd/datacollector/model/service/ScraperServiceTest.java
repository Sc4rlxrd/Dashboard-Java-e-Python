package com.scarlxrd.datacollector.model.service;

import com.scarlxrd.datacollector.model.entity.Product;
import com.scarlxrd.datacollector.model.exception.CollectionErrorType;
import com.scarlxrd.datacollector.model.exception.CollectionException;
import com.scarlxrd.datacollector.model.service.scraper.ProductScraper;
import com.scarlxrd.datacollector.model.service.scraper.ScrapedProduct;
import com.scarlxrd.datacollector.model.service.scraper.Store;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ScraperService")
class ScraperServiceTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "   ",
            "\t"
    })
    @DisplayName("deve rejeitar URL vazia")
    void shouldRejectBlankUrl(
            String url
    ) {
        ScraperService service =
                new ScraperService(List.of());

        CollectionException exception =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(url)
                );

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.INVALID_URL
                );

        assertThat(exception.getStore())
                .isNull();
    }

    @Test
    @DisplayName("deve rejeitar protocolo FTP")
    void shouldRejectUnsupportedProtocol() {
        ScraperService service = new ScraperService(List.of());

        CollectionException exception =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(
                                "ftp://amazon.com.br/produto"
                        )
                );

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.INVALID_URL
                );
    }

    @Test
    @DisplayName("deve rejeitar URL sem domínio")
    void shouldRejectUrlWithoutDomain() {
        ScraperService service = new ScraperService(List.of());

        CollectionException exception =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(
                                "https:///produto"
                        )
                );

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.INVALID_URL
                );
    }

    @Test
    @DisplayName("deve rejeitar URL malformada")
    void shouldRejectMalformedUrl() {
        ScraperService service = new ScraperService(List.of());

        CollectionException exception =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(
                                "https://["
                        )
                );

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.INVALID_URL
                );
    }

    @Test
    @DisplayName("deve rejeitar credenciais embutidas na URL")
    void shouldRejectEmbeddedCredentials() {
        ScraperService service = new ScraperService(List.of());

        CollectionException exception =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(
                                "https://usuario:senha"
                                        + "@amazon.com.br/produto"
                        )
                );

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.INVALID_URL
                );
    }

    @Test
    @DisplayName("deve rejeitar porta não permitida")
    void shouldRejectUnsupportedPort() {
        ScraperService service = new ScraperService(List.of());

        CollectionException exception =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(
                                "https://amazon.com.br:8080/produto"
                        )
                );

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.INVALID_URL
                );
    }

    @Test
    @DisplayName("deve rejeitar domínio não suportado")
    void shouldRejectUnsupportedDomain() {
        ScraperService service = new ScraperService(allFakeScrapers());

        CollectionException exception =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(
                                "https://example.com/produto"
                        )
                );

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.INVALID_URL
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://amazon.com.br/produto",
            "https://amazon.com.br/produto"
    })
    @DisplayName("deve aceitar HTTP e HTTPS")
    void shouldAcceptHttpAndHttps(String url) {
        ScraperService service = new ScraperService(allFakeScrapers());

        Product product = service.captureData(url);

        assertThat(product)
                .isNotNull();

        assertThat(product.getStore())
                .isEqualTo("Amazon");

        assertThat(product.getModel())
                .isEqualTo("Produto teste");

        assertThat(product.getPrice())
                .isEqualByComparingTo(
                        "99.90"
                );
    }

    @Test
    @DisplayName("deve selecionar Amazon pelo domínio")
    void shouldDetectAmazon() {
        assertStoreDetected(
                "https://amazon.com.br/produto",
                Store.AMAZON
        );
    }

    @Test
    @DisplayName(
            "deve selecionar Mercado Livre pelo domínio"
    )
    void shouldDetectMercadoLivre() {
        assertStoreDetected(
                "https://mercadolivre.com.br/produto",
                Store.MERCADO_LIVRE
        );
    }

    @Test
    @DisplayName("deve selecionar BoaDica pelo domínio")
    void shouldDetectBoaDica() {
        assertStoreDetected(
                "https://boadica.com.br/produto",
                Store.BOA_DICA
        );
    }

    @Test
    @DisplayName("deve selecionar Shopee pelo domínio")
    void shouldDetectShopee() {
        assertStoreDetected(
                "https://shopee.com.br/produto",
                Store.SHOPEE
        );
    }

    @Test
    @DisplayName("deve selecionar OLX pelo domínio")
    void shouldDetectOlx() {
        assertStoreDetected(
                "https://olx.com.br/produto",
                Store.OLX
        );
    }

    @Test
    @DisplayName("deve remover fragmento e preservar query")
    void shouldRemoveFragmentAndPreserveQuery() {
        ScraperService service = new ScraperService(allFakeScrapers());

        Product product =service.captureData(
                        "https://amazon.com.br/produto"
                                + "?tag=teste#reviews"
                );

        assertThat(product.getUrl())
                .isEqualTo(
                        "https://amazon.com.br/produto"
                                + "?tag=teste"
                );
    }

    @Test
    @DisplayName("deve remover query e fragmento da OLX")
    void shouldRemoveOlxQueryAndFragment() {
        ScraperService service =
                new ScraperService(
                        allFakeScrapers()
                );

        Product product =service.captureData(
                        "https://olx.com.br/produto"
                                + "?utm_source=teste"
                                + "#descricao"
                );

        assertThat(product.getUrl())
                .isEqualTo(
                        "https://olx.com.br/produto"
                );
    }

    @Test
    @DisplayName("deve preservar CollectionException do scraper")
    void shouldPreserveCollectionException() {
        String url ="https://amazon.com.br/produto";

        CollectionException original =
                new CollectionException(
                        CollectionErrorType.TIMEOUT,
                        Store.AMAZON,
                        url,
                        "Timeout de teste"
                );

        FakeScraper scraper =
                new FakeScraper(
                        Store.AMAZON,
                        "amazon.com.br",
                        ignored -> {
                            throw original;
                        }
                );

        ScraperService service =
                new ScraperService(
                        List.of(scraper)
                );

        CollectionException thrown =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(url)
                );

        assertThat(thrown)
                .isSameAs(original);
    }

    @Test
    @DisplayName("deve transformar RuntimeException em UNKNOWN")
    void shouldConvertRuntimeExceptionToUnknown() {
        String url ="https://amazon.com.br/produto";

        RuntimeException cause =
                new IllegalStateException(
                        "Falha inesperada"
                );

        FakeScraper scraper =
                new FakeScraper(
                        Store.AMAZON,
                        "amazon.com.br",
                        ignored -> {
                            throw cause;
                        }
                );

        ScraperService service =
                new ScraperService(
                        List.of(scraper)
                );

        CollectionException exception =
                assertThrows(
                        CollectionException.class,
                        () -> service.captureData(url)
                );

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.UNKNOWN
                );

        assertThat(exception.getStore())
                .isEqualTo(Store.AMAZON);

        assertThat(exception.getUrl())
                .isEqualTo(url);

        assertThat(exception.getCause())
                .isSameAs(cause);
    }

    private void assertStoreDetected(String url,Store expectedStore) {
        ScraperService service =new ScraperService(allFakeScrapers());

        Product product =service.captureData(url);

        assertThat(product.getStore())
                .isEqualTo(
                        expectedStore.getDisplayName()
                );
    }

    private List<ProductScraper>
    allFakeScrapers() {
        return List.of(
                successfulScraper(
                        Store.AMAZON,
                        "amazon.com.br"
                ),
                successfulScraper(
                        Store.MERCADO_LIVRE,
                        "mercadolivre.com.br"
                ),
                successfulScraper(
                        Store.BOA_DICA,
                        "boadica.com.br"
                ),
                successfulScraper(
                        Store.SHOPEE,
                        "shopee.com.br"
                ),
                successfulScraper(
                        Store.OLX,
                        "olx.com.br"
                )
        );
    }

    private ProductScraper successfulScraper(
            Store store,
            String domain
    ) {
        return new FakeScraper(
                store,
                domain,
                ignored ->
                        new ScrapedProduct(
                                "Produto teste",
                                new BigDecimal("99.90")
                        )
        );
    }

    private static final class FakeScraper implements ProductScraper {

        private final Store store;
        private final String domain;
        private final Function<String,ScrapedProduct> behavior;

        private FakeScraper(
                Store store,
                String domain,
                Function<String,ScrapedProduct> behavior
        ) {
            this.store = store;
            this.domain = domain;
            this.behavior = behavior;
        }

        @Override
        public Store store() {
            return store;
        }

        @Override
        public boolean supports(
                URI uri
        ) {
            String host =
                    uri.getHost();

            if (host == null) {
                return false;
            }

            String normalizedHost =
                    host.toLowerCase(
                            Locale.ROOT
                    );

            return normalizedHost.equals(domain)
                    || normalizedHost.endsWith(
                            "." + domain
                    );
        }

        @Override
        public ScrapedProduct scrape(
                String url
        ) {
            return behavior.apply(url);
        }
    }
}