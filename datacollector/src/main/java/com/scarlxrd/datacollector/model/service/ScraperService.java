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
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

@Service
public class ScraperService {

    public Product captureData(String url) {
        ChromeOptions options = new ChromeOptions();
        options.setBinary("/usr/bin/chromium-browser");
        options.addArguments("--headless=new", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        
        // Camuflagem Anti-Bot
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            
            String priceRaw = "0";
            String capturedModel = "";
            String storeName = identifyStore(url);

            if (storeName.equals("Amazon")) {
                capturedModel = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("productTitle"))).getText();
                priceRaw = driver.findElement(By.className("a-price-whole")).getText() + "," + 
                           driver.findElement(By.className("a-price-fraction")).getText();

            } else if (storeName.equals("Mercado Livre")) {
                capturedModel = wait.until(ExpectedConditions.presenceOfElementLocated(By.className("ui-pdp-title"))).getText();
                try {
                    priceRaw = driver.findElement(By.xpath("//meta[@itemprop='price']")).getAttribute("content");
                } catch (Exception e) {
                    priceRaw = driver.findElement(By.className("andes-money-amount__fraction")).getText();
                }

            } else if (storeName.equals("Casas Bahia")) {
                // Seletor mais abrangente para Casas Bahia
                WebElement titleEl = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//h1 | //h1[@id='product-name'] | //h1[contains(@class, 'Title')]")
                ));
                capturedModel = titleEl.getText();
                
                WebElement priceEl = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[@id='product-price'] | //*[contains(@class, 'priceSales')] | //*[contains(@id, 'valor-pix')]")
                ));
                priceRaw = priceEl.getText();

            } else { // BOA DICA
                priceRaw = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[contains(text(), 'R$')]"))).getText();
                List<WebElement> titles = driver.findElements(By.xpath("//div[contains(@class, 'nome-produto')] | //h1"));
                for (WebElement el : titles) {
                    String text = el.getText();
                    if (!text.isEmpty() && !text.contains("Informações")) {
                        capturedModel = text;
                        break;
                    }
                }
                if (capturedModel.isEmpty() || capturedModel.equalsIgnoreCase("BoaDica")) {
                    capturedModel = driver.getTitle().replace("BoaDica - ", "").trim();
                }
            }

            if (capturedModel.isEmpty() || capturedModel.equalsIgnoreCase("BoaDica") || capturedModel.contains("Informações")) {
                String[] urlParts = url.split("/");
                capturedModel = urlParts[urlParts.length - 1].replace("-", " ").toUpperCase();
            }

            Product p = new Product();
            p.setModel(capturedModel.trim());
            p.setStore(storeName);
            p.setPrice(clearPrice(priceRaw));
            p.setCollectionDate(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
            p.setUrl(url);

            System.out.println("  [" + p.getStore() + "] Coletado: " + p.getModel() + " -> R$" + p.getPrice());
            return p;

        } catch (Exception e) {
            System.err.println("  Falha na captura (" + url + "): " + e.getMessage());
            return null;
        } finally {
            driver.quit();
        }
    }

    private String identifyStore(String url) {
        if (url.contains("amazon.com")) return "Amazon";
        if (url.contains("mercadolivre.com")) return "Mercado Livre";
        if (url.contains("casasbahia.com")) return "Casas Bahia";
        return "BoaDica";
    }

    private BigDecimal clearPrice(String text) {
        if (text == null) return BigDecimal.ZERO;
        try {
            String clean = text.replaceAll("[^0-9,]", "").replace(",", ".");
            return new BigDecimal(clean);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}