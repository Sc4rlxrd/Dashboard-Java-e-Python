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

            //  (Aguarda o R$ aparecer)
            WebElement precoElemento = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'R$')]")
            ));
            String precoRaw = precoElemento.getText();

            
            String modelo;
            try {
                modelo = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//h1 | //div[contains(@class, 'nome-produto')]")
                )).getText();
            } catch (Exception e) {
                // Se tudo falhar, pega o título da aba do navegador
                modelo = driver.getTitle().split("-")[0]; 
            }

            Product p = new Product();
            p.setModel(modelo.trim());
            p.setPrice(clearPrice(precoRaw));
            p.setCollectionDate(LocalDateTime.now());
            p.setUrl(url);

            System.out.println("✅ Coletado: " + p.getModel() + " - " + p.getPrice());
            return p;

        } catch (Exception e) {
            System.err.println("❌ Falha na URL: " + url + " | Erro: " + e.getMessage());
            return null;
        } finally {
            driver.quit();
        }
    }

    private BigDecimal clearPrice(String texto) {
        // "R$ 3.499,99" -> "3499.99"
        try {
            String limpo = texto.replaceAll("[^0-9,]", "").replace(",", ".");
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}