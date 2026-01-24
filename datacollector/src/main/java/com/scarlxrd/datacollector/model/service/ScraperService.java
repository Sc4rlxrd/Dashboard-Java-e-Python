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
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(url);

            // Espera até 10 segundos para o elemento de preço aparecer na tela
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement precoElemento = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'R$')]")
            ));

            String modelo = driver.findElement(By.tagName("h1")).getText();
            String precoRaw = precoElemento.getText();

            Product p = new Product();
            p.setModel(modelo.trim());
            p.setPrice(clearPrice(precoRaw));
            p.setCollectionDate(LocalDateTime.now());
            p.setUrl(url);

            return p;
        } finally {
            driver.quit(); // FECHA o Chrome
        }
    }

    private BigDecimal clearPrice(String texto) {
        // Pega apenas os números e vírgula: "R$ 3.499,99" -> "3499.99"
        String limpo = texto.replaceAll("[^0-9,]", "").replace(",", ".");
        return new BigDecimal(limpo);
    }
}