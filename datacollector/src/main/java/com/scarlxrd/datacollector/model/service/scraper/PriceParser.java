package com.scarlxrd.datacollector.model.service.scraper;

import java.math.BigDecimal;

public final class PriceParser {

    private PriceParser() {
    }

    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Preço vazio");
        }

        String normalized = text
                .replace('\u00A0', ' ')
                .replaceAll("[^0-9,.]", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "Nenhum número encontrado no preço: " + text
            );
        }

        int lastComma = normalized.lastIndexOf(',');
        int lastDot = normalized.lastIndexOf('.');

        if (lastComma >= 0 && lastDot >= 0) {
            if (lastComma > lastDot) {
                // Exemplo: 1.999,90
                normalized = normalized
                        .replace(".", "")
                        .replace(',', '.');
            } else {
                // Exemplo: 1,999.90
                normalized = normalized.replace(",", "");
            }
        } else if (lastComma >= 0) {
            // Exemplo: 1999,90
            normalized = normalized.replace(',', '.');
        } else if (lastDot >= 0) {
            long numberOfDots = normalized
                    .chars()
                    .filter(character -> character == '.')
                    .count();

            int decimalPlaces =
                    normalized.length() - lastDot - 1;

            if (numberOfDots > 1 || decimalPlaces == 3) {
                // Exemplos: 1.999 ou 1.999.999
                normalized = normalized.replace(".", "");
            }
        }

        try {
            BigDecimal price = new BigDecimal(normalized);

            if (price.signum() <= 0) {
                throw new IllegalArgumentException(
                        "O preço precisa ser maior que zero: " + text
                );
            }

            return price;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Preço inválido: " + text,
                    exception
            );
        }
    }
}