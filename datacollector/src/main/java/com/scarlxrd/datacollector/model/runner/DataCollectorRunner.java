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
                "https://boadica.com.br/produtos/p211057/f7-12gb512gb-black-preto",
                "https://boadica.com.br/produtos/p199610/iphone-15-128gb-desbloqueado-fabrica-preto",
                "https://boadica.com.br/produtos/p205764/iphone-16-128gb-desbloqueado-fabrica-preto",
                "https://boadica.com.br/pesquisa/compu_notebook/precos?ClasseProdutoX=1&CodCategoriaX=2&XF=1785",
                "https://boadica.com.br/pesquisa/compu_notebook/precos?ClasseProdutoX=1&CodCategoriaX=2&XF=1785",
                "https://boadica.com.br/pesquisa/compu_notebook/precos?ClasseProdutoX=1&CodCategoriaX=2&XF=1785",
                "https://boadica.com.br/produtos/p186891/latitude-3420",
                "https://boadica.com.br/produtos/p185513/latitude-5420",
                "https://boadica.com.br/produtos/p197084/latitude-5430",
                "https://boadica.com.br/produtos/p212956/ideapad-slim-3-15irh10",
                "https://boadica.com.br/produtos/p201048/aspire-5-a515-57-727c",
                "https://boadica.com.br/produtos/p213551/aspire-go-15-ag15-71p-76z8",
                "https://boadica.com.br/produtos/p206808/nitro-v15-anv15-51-73e9",
                "https://boadica.com.br/produtos/p203331/tuf-gaming-f15-fx507zc4-hn112-mecha-gray",
                "https://boadica.com.br/produtos/p207535/thinkpad-t14-gen-4-21hesg6c00"
             );

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