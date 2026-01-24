package com.scarlxrd.datacollector.model.service;

import com.scarlxrd.datacollector.model.entity.Product;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class ScraperService {

    public Product captureData(String url) {
        ChromeOptions options = new ChromeOptions();
        options.setBinary("/usr/bin/chromium-browser");
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        
        
        options.addArguments("--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            
            WebElement precoElemento = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'R$')]")
            ));
            String precoRaw = precoElemento.getText();

            
            String modeloCapturado;
            try {
               
                modeloCapturado = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//h1 | //div[contains(@class, 'nome-produto')] | //div[@id='produto_nome'] | //span[@id='productTitle']")
                )).getText();
            } catch (Exception e) {
                
                modeloCapturado = driver.getTitle().split("-")[0]; 
            }

            
            String nomeLoja;
            if (url.contains("boadica.com.br")) {
                nomeLoja = "BoaDica";
            } else if (url.contains("amazon.com.br")) {
                nomeLoja = "Amazon";
            } else if (url.contains("kabum.com.br")) {
                nomeLoja = "KaBuM";
            } else if (url.contains("mercadolivre.com.br")) {
                nomeLoja = "Mercado Livre";
            } else {
                // Extrai o nome do domínio caso não esteja na lista (ex: www.site.com -> SITE)
                nomeLoja = url.replaceFirst("^(https?://)?(www\\.)?", "").split("\\.")[0].toUpperCase();
            }

            
            Product p = new Product();
            p.setModel(modeloCapturado.trim()); 
            p.setStore(nomeLoja);              
            p.setPrice(clearPrice(precoRaw));
            p.setCollectionDate(LocalDateTime.now());
            p.setUrl(url);

            System.out.println("[" + p.getStore() + "] Coletado: " + p.getModel() + " - R$" + p.getPrice());
            return p;

        } catch (Exception e) {
            System.err.println(" Falha na URL: " + url + " | Erro: " + e.getMessage());
            return null;
        } finally {
            driver.quit(); 
        }
    }

    private BigDecimal clearPrice(String texto) {
        try {
            // Limpa o texto para manter apenas números e a vírgula decimal
            String limpo = texto.replaceAll("[^0-9,]", "").replace(",", ".");
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}