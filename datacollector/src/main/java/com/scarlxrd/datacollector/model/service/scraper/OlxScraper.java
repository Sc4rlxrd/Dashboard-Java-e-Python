package com.scarlxrd.datacollector.model.service.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class OlxScraper implements ProductScraper {

    private static final Pattern JSON_LD_PATTERN = Pattern.compile(
            "<script[^>]*"
                    + "type=[\"']application/ld\\+json[\"']"
                    + "[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE
                    | Pattern.DOTALL);

    private static final Pattern DISCOUNTED_PRICE_PATTERN = Pattern.compile(
            "\"discountedAdPriceValue\"\\s*:\\s*"
                    + "([0-9]+(?:[.,][0-9]+)?)",
            Pattern.CASE_INSENSITIVE);

    private final JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(
                    HttpClient.Redirect.NORMAL)
            .connectTimeout(
                    Duration.ofSeconds(15))
            .build();

    @Override
    public Store store() {
        return Store.OLX;
    }

    @Override
    public boolean supports(URI uri) {
        String host = uri.getHost();

        if (host == null || host.isBlank()) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);

        return normalizedHost.equals("olx.com.br")
                || normalizedHost.endsWith(".olx.com.br");
    }

    @Override
    public ScrapedProduct scrape(String url) {
        URI cleanUri = cleanUri(url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(cleanUri)
                .timeout(Duration.ofSeconds(30))
                .header(
                        "Accept",
                        "text/html,"
                                + "application/xhtml+xml,"
                                + "application/xml;q=0.9,"
                                + "*/*;q=0.8")
                .header(
                        "Accept-Language",
                        "pt-BR,pt;q=0.9,en;q=0.8")
                .header(
                        "User-Agent",
                        "PriceMonitor/1.0")
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        validateResponse(
                response.statusCode(),
                response.body(),
                cleanUri);

        JsonNode product = extractProductJsonLd(response.body());

        String model = required(
                extractString(
                        product.path("name")),
                "Nome do produto");

        String rawPrice = firstNonBlank(
                extractDiscountedPrice(
                        response.body()),
                extractOfferPrice(product));

        rawPrice = required(
                rawPrice,
                "Preço");

        return new ScrapedProduct(
                model.trim(),
                PriceParser.parse(rawPrice));
    }

    private HttpResponse<String> send(
            HttpRequest request) {
        try {
            return httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Coleta da OLX interrompida",
                    exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Falha de comunicação com a OLX",
                    exception);
        }
    }

    private void validateResponse(
            int statusCode,
            String html,
            URI uri) {
        String normalizedHtml = html == null
                ? ""
                : normalize(html);

        boolean cloudflareBlocked = statusCode == 403
                || statusCode == 429
                || normalizedHtml.contains(
                        "attention required")
                || normalizedHtml.contains(
                        "cloudflare ray id")
                || normalizedHtml.contains(
                        "verify you are human")
                || normalizedHtml.contains(
                        "just a moment");

        if (cloudflareBlocked) {
            throw new IllegalStateException(
                    "OLX bloqueou a requisição automatizada. "
                            + "HTTP "
                            + statusCode
                            + ". URL: "
                            + uri);
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(
                    "OLX respondeu com HTTP "
                            + statusCode
                            + ". URL: "
                            + uri);
        }

        boolean unavailable = normalizedHtml.contains(
                "este anuncio nao esta mais disponivel")
                || normalizedHtml.contains(
                        "anuncio nao encontrado")
                || normalizedHtml.contains(
                        "pagina nao encontrada");

        if (unavailable) {
            throw new IllegalStateException(
                    "O anúncio da OLX não está mais disponível. "
                            + "URL: "
                            + uri);
        }
    }

    private JsonNode extractProductJsonLd(
            String html) {
        Matcher matcher = JSON_LD_PATTERN.matcher(html);

        while (matcher.find()) {
            String rawJson = matcher.group(1).trim();

            try {
                JsonNode root = jsonMapper.readTree(rawJson);

                JsonNode product = findProductNode(root);

                if (product != null) {
                    return product;
                }
            } catch (JacksonException exception) {
                log.warn("Bloco JSON-LD inválido ou incompatível na página da OLX", exception);
            }
        }

        throw new IllegalStateException(
                "JSON-LD do produto não encontrado "
                        + "na página da OLX");
    }

    private JsonNode findProductNode(
            JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            String type = extractString(
                    node.path("@type"));

            if ("Product".equalsIgnoreCase(type)) {
                return node;
            }
        }

        for (JsonNode child : node) {
            JsonNode product = findProductNode(child);

            if (product != null) {
                return product;
            }
        }

        return null;
    }

    private String extractOfferPrice(
            JsonNode product) {
        JsonNode offers = product.path("offers");

        if (offers.isArray() && !offers.isEmpty()) {
            offers = offers.get(0);
        }

        return extractString(
                offers.path("price"));
    }

    private String extractDiscountedPrice(
            String html) {
        Matcher matcher = DISCOUNTED_PRICE_PATTERN.matcher(html);

        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    private String extractString(
            JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        String value = node.stringValue();

        return isBlank(value)
                ? null
                : value;
    }

    private URI cleanUri(
            String url) {
        try {
            URI original = URI.create(url);

            if (original.getScheme() == null
                    || original.getHost() == null) {
                throw new IllegalArgumentException(
                        "URL sem protocolo ou domínio");
            }

            return new URI(
                    original.getScheme(),
                    original.getAuthority(),
                    original.getPath(),
                    null,
                    null);
        } catch (
                IllegalArgumentException
                | URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "URL inválida da OLX: " + url,
                    exception);
        }
    }

    private String firstNonBlank(
            String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }

        return null;
    }

    private String required(
            String value,
            String field) {
        if (isBlank(value)) {
            throw new IllegalStateException(
                    field
                            + " não encontrado "
                            + "na página da OLX");
        }

        return value;
    }

    private String normalize(
            String value) {
        return java.text.Normalizer
                .normalize(
                        value,
                        java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private boolean isBlank(
            String value) {
        return value == null || value.isBlank();
    }
}