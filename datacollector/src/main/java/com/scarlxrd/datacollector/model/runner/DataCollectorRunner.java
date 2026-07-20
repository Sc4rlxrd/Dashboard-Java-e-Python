package com.scarlxrd.datacollector.model.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scarlxrd.datacollector.model.entity.Product;
import com.scarlxrd.datacollector.model.repository.ProductRepository;
import com.scarlxrd.datacollector.model.service.ScraperService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
@Component
public class DataCollectorRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataCollectorRunner.class);

    private static final String DEFAULT_URLS_FILE = "/app/urls.txt";
    private static final String DEFAULT_OUTPUT_FILE = "/app/output/precos.json";

    private static final long DEFAULT_DELAY_MS = 8_000L;

    private final ProductRepository productRepository;
    private final ScraperService scraperService;
    private final ObjectMapper objectMapper;

    public DataCollectorRunner(
            ProductRepository productRepository,
            ScraperService scraperService
    ) {
        this.productRepository = productRepository;
        this.scraperService = scraperService;

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                );
    }

    @Override
    public void run(String... args) throws Exception {
        Path urlsFile = resolvePath(
                "COLLECTOR_URLS_FILE",
                DEFAULT_URLS_FILE
        );

        Path outputFile = resolvePath(
                "COLLECTOR_OUTPUT_FILE",
                DEFAULT_OUTPUT_FILE
        );

        long delayBetweenUrls = resolveDelay();

        List<String> urls = loadUrls(urlsFile);

        logger.info(
                "Iniciando rodada de coleta com {} URL(s)",
                urls.size()
        );

        int successfulCollections = 0;
        int failedCollections = 0;

        for (int index = 0; index < urls.size(); index++) {
            String url = urls.get(index);

            logger.info(
                    "Processando URL {}/{}: {}",
                    index + 1,
                    urls.size(),
                    url
            );

            try {
                Product product = scraperService.captureData(url);

                if (product == null) {
                    failedCollections++;

                    logger.error(
                            "A captura não retornou dados para: {}",
                            url
                    );
                } else {
                    productRepository.save(product);
                    successfulCollections++;

                    logger.info(
                            "Produto salvo com sucesso: {}",
                            product.getModel()
                    );
                }
            } catch (
                    Exception exception) {
                failedCollections++;

                logger.error(
                        "Erro ao processar a URL: {}",
                        url,
                        exception
                );
            }

            boolean hasNextUrl = index < urls.size() - 1;

            if (hasNextUrl && delayBetweenUrls > 0) {
                pauseBetweenUrls(delayBetweenUrls);
            }
        }

        if (successfulCollections == 0) {
            throw new IllegalStateException(
                    "Nenhum produto foi coletado com sucesso"
            );
        }

        exportProducts(outputFile);

        if (failedCollections > 0) {
            logger.warn(
                    "Coleta concluída parcialmente: {} sucesso(s) e {} falha(s)",
                    successfulCollections,
                    failedCollections
            );
        } else {
            logger.info(
                    "Coleta concluída com sucesso: {} produto(s) coletado(s)",
                    successfulCollections
            );
        }

        logger.info(
                "JSON atualizado em: {}",
                outputFile
        );
    }

    private List<String> loadUrls(Path urlsFile) throws IOException {
        if (!Files.isRegularFile(urlsFile)) {
            throw new IllegalStateException(
                    "Arquivo de URLs não encontrado: " + urlsFile
            );
        }

        logger.info(
                "Carregando URLs do arquivo: {}",
                urlsFile
        );

        List<String> urls;

        try (Stream<String> lines = Files.lines(
                urlsFile,
                StandardCharsets.UTF_8
        )) {
            urls = lines
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("#"))
                    .distinct()
                    .toList();
        }

        if (urls.isEmpty()) {
            throw new IllegalStateException(
                    "Nenhuma URL válida encontrada em: " + urlsFile
            );
        }

        return urls;
    }

    private void exportProducts(Path outputFile) throws IOException {
        List<Product> products = productRepository.findAll();

        Path absoluteOutputFile =
                outputFile.toAbsolutePath().normalize();

        Path outputDirectory =
                absoluteOutputFile.getParent();

        if (outputDirectory == null) {
            throw new IllegalStateException(
                    "Diretório de saída inválido: " + outputFile
            );
        }

        Files.createDirectories(outputDirectory);

        Path temporaryFile = Files.createTempFile(
                outputDirectory,
                "precos-",
                ".json.tmp"
        );

        try {
            objectMapper.writeValue(
                    temporaryFile.toFile(),
                    products
            );

            replaceOutputFile(temporaryFile,absoluteOutputFile );
            Files.setPosixFilePermissions(absoluteOutputFile,PosixFilePermissions.fromString( "rw-r--r--"));
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void replaceOutputFile(
            Path temporaryFile,
            Path outputFile
    ) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    outputFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (
                AtomicMoveNotSupportedException exception) {
            logger.warn(
                    "Movimentação atômica não suportada. "
                            + "Usando substituição comum."
            );

            Files.move(
                    temporaryFile,
                    outputFile,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void pauseBetweenUrls(long delayMilliseconds)
            throws InterruptedException {
        logger.debug(
                "Aguardando {} ms antes da próxima URL",
                delayMilliseconds
        );

        try {
            Thread.sleep(delayMilliseconds);
        } catch (
                InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private Path resolvePath(
            String environmentVariable,
            String defaultValue
    ) {
        return Path.of(
                System.getenv().getOrDefault(
                        environmentVariable,
                        defaultValue
                )
        );
    }

    private long resolveDelay() {
        String configuredValue = System.getenv().getOrDefault(
                "COLLECTOR_DELAY_MS",
                String.valueOf(DEFAULT_DELAY_MS)
        );

        try {
            long delay = Long.parseLong(configuredValue);

            if (delay < 0) {
                throw new IllegalArgumentException(
                        "COLLECTOR_DELAY_MS não pode ser negativo"
                );
            }

            return delay;
        } catch (
                NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "COLLECTOR_DELAY_MS possui valor inválido: "
                            + configuredValue,
                    exception
            );
        }
    }
}