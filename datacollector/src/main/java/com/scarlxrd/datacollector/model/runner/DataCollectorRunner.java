package com.scarlxrd.datacollector.model.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scarlxrd.datacollector.model.entity.Product;
import com.scarlxrd.datacollector.model.repository.ProductRepository;
import com.scarlxrd.datacollector.model.service.ScraperService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.nio.file.Files;

import java.io.File;
import java.util.List;

@Component
public class DataCollectorRunner implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final ScraperService scraperService;

    public DataCollectorRunner(ProductRepository productRepository, ScraperService scraperService) {
        this.productRepository = productRepository;
        this.scraperService = scraperService;
    }

    @Override
    public void run(String... args) throws Exception {
File file = new File("/app/urls.txt"); 
    List<String> urls = new java.util.ArrayList<>(); 

    if (file.exists()) {
        System.out.println("Carregando URLs do arquivo /app/urls.txt...");
        urls = Files.readAllLines(file.toPath());
    } else {
        System.err.println("ERRO: Arquivo /app/urls.txt NÃO encontrado no container!");
        urls.add("https://boadica.com.br/"); 
    }

    // Filtra linhas em branco para evitar erros no Selenium
    urls.removeIf(String::isBlank);
    System.out.println("Iniciando a rodada de coleta para " + urls.size() + " itens...");
        for (String url : urls) {
            try {
                Thread.sleep(8000); 
                System.out.println("Processando: " + url);
                Thread.sleep(8000); 
                
                Product product = scraperService.captureData(url);
                
                if (product != null) {
                    System.out.println("Salvando no banco...");
                    productRepository.save(product);
                } else {
                    System.err.println(" Captura falhou para esta URL. Pulando salvamento.");
                }
                
                Thread.sleep(8000);
            } catch (Exception e) {
                System.err.println("Erro ao coletar URL: " + url + " | Erro: " + e.getMessage());
            }
        }

        System.out.println("Exportando para JSON...");
        List<Product> allProducts = productRepository.findAll();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.writeValue(new File("/app/output/precos.json"), allProducts);

        System.out.println("Sucesso! Finalizando processo...");
        System.exit(0); 
    }
}