package com.scarlxrd.datacollector.model.exception;

import com.scarlxrd.datacollector.model.service.scraper.Store;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("CollectionException")
class CollectionExceptionTest {

    @Test
    @DisplayName("deve armazenar tipo, loja, URL, mensagem e causa")
    void shouldStoreAllExceptionInformation() {
        RuntimeException cause = new RuntimeException("erro original");

        CollectionException exception = new CollectionException(
                CollectionErrorType.TIMEOUT,
                Store.AMAZON,
                "https://amazon.com.br/produto",
                "Tempo limite excedido",
                cause);

        assertThat(exception.getErrorType())
                .isEqualTo(CollectionErrorType.TIMEOUT);

        assertThat(exception.getStore())
                .isEqualTo(Store.AMAZON);

        assertThat(exception.getStoreName())
                .isEqualTo("Amazon");

        assertThat(exception.getUrl())
                .isEqualTo(
                        "https://amazon.com.br/produto");

        assertThat(exception.getMessage())
                .isEqualTo("Tempo limite excedido");

        assertThat(exception.getCause())
                .isSameAs(cause);
    }

    @Test
    @DisplayName("deve aceitar loja nula")
    void shouldAllowNullStore() {
        CollectionException exception = new CollectionException(
                CollectionErrorType.INVALID_URL,
                null,
                "ftp://produto",
                "URL inválida");

        assertThat(exception.getStore())
                .isNull();

        assertThat(exception.getStoreName())
                .isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("deve aceitar causa nula")
    void shouldAllowNullCause() {
        CollectionException exception = new CollectionException(
                CollectionErrorType.PRICE_NOT_FOUND,
                Store.SHOPEE,
                "https://shopee.com.br/produto",
                "Preço não encontrado");

        assertThat(exception.getCause())
                .isNull();

        assertThat(exception.getErrorType())
                .isEqualTo(
                        CollectionErrorType.PRICE_NOT_FOUND);
    }

    @Test
    @DisplayName("deve rejeitar errorType nulo")
    void shouldRejectNullErrorType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CollectionException(
                        null,
                        Store.AMAZON,
                        "https://amazon.com.br/produto",
                        "erro"))
                .withMessage(
                        "errorType não pode ser nulo");
    }
}