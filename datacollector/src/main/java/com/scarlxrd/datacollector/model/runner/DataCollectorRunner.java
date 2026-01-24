package com.scarlxrd.datacollector.model.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scarlxrd.datacollector.model.entity.Product;
import com.scarlxrd.datacollector.model.repository.ProductRepository;
import com.scarlxrd.datacollector.model.service.ScraperService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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

        List<String> urls = List.of(
                "https://boadica.com.br/produtos/p206263/14t-512gb--12gb-titan-black-preto" ,
                "https://boadica.com.br/produtos/p212724/15t--512gb--12gb-black",
                "https://boadica.com.br/produtos/p211057/f7-12gb512gb-black-preto"
        );

        System.out.println("Iniciando a rodada de coleta para  "  + urls.size());
        for (String url : urls) {
            try {
                Thread.sleep(8000);
                Product produto = scraperService.captureData(url);
                Thread.sleep(8000);
                System.out.println("Salvando no banco...");
                productRepository.save(produto);
            }catch (Exception e){
                System.err.println("❌ Erro ao coletar URL: " + url + " | Erro: " + e.getMessage());
            }
        }


        System.out.println("Exportando para JSON...");
        List<Product> allProducts = productRepository.findAll();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Caminho onde o Docker mapeia a pasta do seu PC
        mapper.writeValue(new File("/app/output/precos.json"), allProducts);

        System.out.println("Sucesso! Finalizando processo...");
        System.exit(0); // Garante que o container pare e libere sua RAM
    }

    }

