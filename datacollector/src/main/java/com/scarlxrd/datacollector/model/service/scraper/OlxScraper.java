package com.scarlxrd.datacollector.model.service.scraper;

import com.scarlxrd.datacollector.model.exception.CollectionErrorType;
import com.scarlxrd.datacollector.model.exception.CollectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class OlxScraper
        implements ProductScraper {

    private static final Pattern JSON_LD_PATTERN =
            Pattern.compile(
                    "<script[^>]*"
                            + "type=[\"']application/ld\\+json[\"']"
                            + "[^>]*>(.*?)</script>",
                    Pattern.CASE_INSENSITIVE
                            | Pattern.DOTALL
            );

    private static final Pattern
    DISCOUNTED_PRICE_PATTERN =
            Pattern.compile(
                    "\"discountedAdPriceValue\"\\s*:\\s*"
                            + "([0-9]+(?:[.,][0-9]+)?)",
                    Pattern.CASE_INSENSITIVE
            );

    private final JsonMapper jsonMapper;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .connectTimeout(
                            Duration.ofSeconds(15)
                    )
                    .build();

    @Override
    public Store store() {
        return Store.OLX;
    }

    @Override
    public boolean supports(
            URI uri
    ) {
        String host =
                uri.getHost();

        if (host == null
                || host.isBlank()) {
            return false;
        }

        String normalizedHost =
                host.toLowerCase(
                        Locale.ROOT
                );

        return normalizedHost.equals(
                "olx.com.br"
        ) || normalizedHost.endsWith(
                ".olx.com.br"
        );
    }

    @Override
    public ScrapedProduct scrape(
            String url
    ) {
        URI cleanUri =
                cleanUri(url);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(cleanUri)
                        .timeout(
                                Duration.ofSeconds(30)
                        )
                        .header(
                                "Accept",
                                "text/html,"
                                        + "application/xhtml+xml,"
                                        + "application/xml;q=0.9,"
                                        + "*/*;q=0.8"
                        )
                        .header(
                                "Accept-Language",
                                "pt-BR,pt;q=0.9,en;q=0.8"
                        )
                        .header(
                                "User-Agent",
                                "PriceMonitor/1.0"
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                send(request);

        validateResponse(
                response.statusCode(),
                response.body(),
                cleanUri
        );

        JsonNode product =
                extractProductJsonLd(
                        response.body(),
                        cleanUri.toString()
                );

        String model = required(
                extractString(
                        product.path("name")
                ),
                "Nome do produto",
                CollectionErrorType.SELECTOR_CHANGED,
                cleanUri.toString()
        );

        String rawPrice =
                firstNonBlank(
                        extractDiscountedPrice(
                                response.body()
                        ),
                        extractOfferPrice(
                                product
                        )
                );

        rawPrice = required(
                rawPrice,
                "Preço",
                CollectionErrorType.PRICE_NOT_FOUND,
                cleanUri.toString()
        );

        return new ScrapedProduct(
                model.trim(),
                parsePrice(
                        rawPrice,
                        cleanUri.toString()
                )
        );
    }

    private HttpResponse<String> send(
            HttpRequest request
    ) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

        } catch (HttpTimeoutException exception) {
            throw new CollectionException(
                    CollectionErrorType.TIMEOUT,
                    Store.OLX,
                    request.uri().toString(),
                    "Tempo limite excedido ao acessar a OLX",
                    exception
            );

        } catch (InterruptedException exception) {
            Thread.currentThread()
                    .interrupt();

            throw new CollectionException(
                    CollectionErrorType.UNKNOWN,
                    Store.OLX,
                    request.uri().toString(),
                    "Coleta da OLX foi interrompida",
                    exception
            );

        } catch (IOException exception) {
            throw new CollectionException(
                    CollectionErrorType.NETWORK_ERROR,
                    Store.OLX,
                    request.uri().toString(),
                    "Falha de comunicação com a OLX",
                    exception
            );
        }
    }

    private void validateResponse(
            int statusCode,
            String html,
            URI uri
    ) {
        String normalizedHtml =
                html == null
                        ? ""
                        : normalize(html);

        if (statusCode == 429) {
            throw new CollectionException(
                    CollectionErrorType.RATE_LIMITED,
                    Store.OLX,
                    uri.toString(),
                    "OLX limitou temporariamente as requisições. "
                            + "HTTP 429."
            );
        }

        boolean cloudflareBlocked =
                statusCode == 403
                        || normalizedHtml.contains(
                        "attention required"
                )
                        || normalizedHtml.contains(
                        "cloudflare ray id"
                )
                        || normalizedHtml.contains(
                        "verify you are human"
                )
                        || normalizedHtml.contains(
                        "just a moment"
                );

        if (cloudflareBlocked) {
            throw new CollectionException(
                    CollectionErrorType.ANTI_BOT_BLOCKED,
                    Store.OLX,
                    uri.toString(),
                    "OLX bloqueou a requisição automatizada. "
                            + "HTTP "
                            + statusCode
            );
        }

        if (
                statusCode == 404
                        || statusCode == 410
        ) {
            throw new CollectionException(
                    CollectionErrorType.PRODUCT_NOT_FOUND,
                    Store.OLX,
                    uri.toString(),
                    "Anúncio da OLX não encontrado. HTTP "
                            + statusCode
            );
        }

        if (statusCode >= 500) {
            throw new CollectionException(
                    CollectionErrorType.NETWORK_ERROR,
                    Store.OLX,
                    uri.toString(),
                    "OLX apresentou erro de servidor. HTTP "
                            + statusCode
            );
        }

        if (
                statusCode < 200
                        || statusCode >= 300
        ) {
            throw new CollectionException(
                    CollectionErrorType.UNKNOWN,
                    Store.OLX,
                    uri.toString(),
                    "OLX respondeu com HTTP "
                            + statusCode
            );
        }

        boolean unavailable =
                normalizedHtml.contains(
                        "este anuncio nao esta mais disponivel"
                )
                        || normalizedHtml.contains(
                        "anuncio nao encontrado"
                )
                        || normalizedHtml.contains(
                        "pagina nao encontrada"
                );

        if (unavailable) {
            throw new CollectionException(
                    CollectionErrorType.PRODUCT_NOT_FOUND,
                    Store.OLX,
                    uri.toString(),
                    "O anúncio da OLX não está mais disponível"
            );
        }
    }

    private JsonNode extractProductJsonLd(
            String html,
            String url
    ) {
        if (html == null
                || html.isBlank()) {

            throw new CollectionException(
                    CollectionErrorType.SELECTOR_CHANGED,
                    Store.OLX,
                    url,
                    "OLX retornou uma página sem conteúdo utilizável"
            );
        }

        Matcher matcher =
                JSON_LD_PATTERN.matcher(html);

        while (matcher.find()) {
            String rawJson =
                    matcher.group(1)
                            .trim();

            try {
                JsonNode root =
                        jsonMapper.readTree(
                                rawJson
                        );

                JsonNode product =
                        findProductNode(root);

                if (product != null) {
                    return product;
                }

            } catch (JacksonException exception) {
                log.warn(
                        "Bloco JSON-LD inválido ou incompatível "
                                + "na página da OLX",
                        exception
                );
            }
        }

        throw new CollectionException(
                CollectionErrorType.SELECTOR_CHANGED,
                Store.OLX,
                url,
                "JSON-LD do produto não encontrado "
                        + "na página da OLX"
        );
    }

    private JsonNode findProductNode(
            JsonNode node
    ) {
        if (node == null
                || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            String type =
                    extractString(
                            node.path("@type")
                    );

            if (
                    "Product".equalsIgnoreCase(type)
            ) {
                return node;
            }
        }

        for (JsonNode child : node) {
            JsonNode product =
                    findProductNode(child);

            if (product != null) {
                return product;
            }
        }

        return null;
    }

    private String extractOfferPrice(
            JsonNode product
    ) {
        JsonNode offers =
                product.path("offers");

        if (
                offers.isArray()
                        && !offers.isEmpty()
        ) {
            offers = offers.get(0);
        }

        return extractString(
                offers.path("price")
        );
    }

    private String extractDiscountedPrice(
            String html
    ) {
        if (html == null
                || html.isBlank()) {
            return null;
        }

        Matcher matcher =
                DISCOUNTED_PRICE_PATTERN
                        .matcher(html);

        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    private String extractString(
            JsonNode node
    ) {
        if (node == null
                || node.isNull()) {
            return null;
        }

        String value =
                node.stringValue();

        return isBlank(value)
                ? null
                : value;
    }

    private URI cleanUri(
            String url
    ) {
        try {
            URI original =
                    URI.create(url);

            String scheme =
                    original.getScheme();

            if (
                    !"http".equalsIgnoreCase(scheme)
                            && !"https".equalsIgnoreCase(scheme)
            ) {
                throw new CollectionException(
                        CollectionErrorType.INVALID_URL,
                        Store.OLX,
                        url,
                        "Protocolo inválido para URL da OLX: "
                                + scheme
                );
            }

            if (
                    original.getHost() == null
                            || original.getHost().isBlank()
            ) {
                throw new CollectionException(
                        CollectionErrorType.INVALID_URL,
                        Store.OLX,
                        url,
                        "URL da OLX sem domínio válido"
                );
            }

            return new URI(
                    original.getScheme(),
                    original.getAuthority(),
                    original.getPath(),
                    null,
                    null
            );

        } catch (CollectionException exception) {
            throw exception;

        } catch (
                IllegalArgumentException
                        | URISyntaxException exception
        ) {
            throw new CollectionException(
                    CollectionErrorType.INVALID_URL,
                    Store.OLX,
                    url,
                    "URL inválida da OLX: "
                            + url,
                    exception
            );
        }
    }

    private String firstNonBlank(
            String... values
    ) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }

        return null;
    }

    private String required(
            String value,
            String field,
            CollectionErrorType errorType,
            String url
    ) {
        if (isBlank(value)) {
            throw new CollectionException(
                    errorType,
                    Store.OLX,
                    url,
                    field
                            + " não encontrado na página da OLX"
            );
        }

        return value;
    }

    private BigDecimal parsePrice(
            String rawPrice,
            String url
    ) {
        try {
            return PriceParser.parse(
                    rawPrice
            );

        } catch (IllegalArgumentException exception) {
            throw new CollectionException(
                    CollectionErrorType.PRICE_NOT_FOUND,
                    Store.OLX,
                    url,
                    "Preço da OLX inválido ou não utilizável: "
                            + rawPrice,
                    exception
            );
        }
    }

    private String normalize(
            String value
    ) {
        return java.text.Normalizer
                .normalize(
                        value,
                        java.text.Normalizer.Form.NFD
                )
                .replaceAll(
                        "\\p{M}",
                        ""
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .trim();
    }

    private boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
    }
}