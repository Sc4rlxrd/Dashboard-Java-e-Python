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
                "https://www.amazon.com.br/gp/product/B0F4M66XWL/ref=ox_sc_act_title_2?smid=A1ZZFT5FULY4LN&psc=1",
                "https://www.mercadolivre.com.br/notebook-gamer-asus-tuf-gaming-a15-nvidia-rtx-3050-amd-ryzen-7-7435hs-31-ghz-8gb-ram-512gb-ssd-keepos-linux-tela-156-fhd-nivel-ips-144hz-graphite-black-fa506ncr-hn089/p/MLB46999993?matt_tool=68505111&forceInApp=true",
                "https://www.amazon.com.br/Notebook-Lenovo-15IAX9E-i5-12450HX-Windows/dp/B0DSXN1XBL/ref=sr_1_17?s=computers&sr=1-17",
                "https://www.casasbahia.com.br/notebook-lenovo-ideapad-slim-3-15arp10-amd-ryzen-7-7735hs-24gb-512gb-ssd-windows-11-153-quot-83mm0003bo-luna-grey/p/1578744633?IdSku=1578744633&idLojista=25052&tipoLojista=3P&&gad_campaignid=22846609334"
        );

        System.out.println("Iniciando a rodada de coleta para  "  + urls.size() + " Itens...");
        for (String url : urls) {
            try {
                Thread.sleep(8000);
                Product produto = scraperService.captureData(url);
                Thread.sleep(8000);
                System.out.println("Salvando no banco...");
                productRepository.save(produto);
            }catch (Exception e){
                System.err.println(" Erro ao coletar URL: " + url + " | Erro: " + e.getMessage());
            }
        }


        System.out.println("Exportando para JSON...");
        List<Product> allProducts = productRepository.findAll();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.writeValue(new File("/app/output/precos.json"), allProducts);

        System.out.println("Sucesso! Finalizando processo...");
        System.exit(0); // Garante que o container pare e libere sua RAM
    }

    }

