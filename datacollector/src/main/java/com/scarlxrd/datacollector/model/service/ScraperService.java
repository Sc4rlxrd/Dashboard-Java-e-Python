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
import java.util.List;

@Service
public class ScraperService {

    public Product captureData(String url) {
        ChromeOptions options = new ChromeOptions();
        options.setBinary("/usr/bin/chromium-browser");
        options.addArguments("--headless=new", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            WebElement elementPrice = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'R$')]")
            ));
            String priceRaw = elementPrice.getText();

           
            String capturedModel = "";
            try {
                
                List<WebElement> possiveisTitulos = driver.findElements(By.xpath("//h1 | //div[contains(@class, 'nome-produto')] | //div[contains(@class, 'titulo')]"));
                
                for (WebElement el : possiveisTitulos) {
                    String texto = el.getText().trim();
                    if (!texto.isEmpty() && !texto.equalsIgnoreCase("Informações do Produto") && !texto.equalsIgnoreCase("BoaDica")) {
                        capturedModel = texto;
                        break;
                    }
                }

                // Se falhar no loop, tenta o título da página removendo o lixo
                if (capturedModel.isEmpty()) {
                    capturedModel = driver.getTitle().replace("BoaDica - ", "").split("-")[0].trim();
                }
            } catch (Exception e) {
                capturedModel = "Produto";
            }

            // Fallback Crítico: Se ainda estiver genérico, extrai da URL (Ex: 15t--512gb--12gb-black)
            if (capturedModel.length() < 5 || capturedModel.contains("Informações")) {
                String[] urlParts = url.split("/");
                String slug = urlParts[urlParts.length - 1];
                capturedModel = slug.replace("-", " ").replace("--", " ").toUpperCase();
            }

            // 3. Identificação Dinâmica da Loja
            String storeName = "Desconhecido";
            if (url.contains("boadica.com.br")) storeName = "BoaDica";
            else if (url.contains("amazon.com.br")) storeName = "Amazon";
            else if (url.contains("kabum.com.br")) storeName = "KaBuM";
            else if (url.contains("mercadolivre.com.br")) storeName = "Mercado Livre";

            Product p = new Product();
            p.setModel(capturedModel.trim());
            p.setStore(storeName);
            p.setPrice(clearPrice(priceRaw));
            p.setCollectionDate(LocalDateTime.now());
            p.setUrl(url);

            System.out.println(" [" + p.getStore() + "] Coletado: " + p.getModel() + " - R$" + p.getPrice());
            return p;

        } catch (Exception e) {
            System.err.println(" Erro em " + url + ": " + e.getMessage());
            return null;
        } finally {
            driver.quit();
        }
    }

    private BigDecimal clearPrice(String texto) {
        try {
            String clear = texto.replaceAll("[^0-9,]", "").replace(",", ".");
            return new BigDecimal(clear);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}