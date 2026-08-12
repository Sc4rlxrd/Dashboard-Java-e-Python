package com.scarlxrd.datacollector.model.service.scraper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("PriceParser")
class PriceParserTest {

    @ParameterizedTest(name = "\"{0}\" deve resultar em {1}")
    @MethodSource("validPrices")
    @DisplayName("deve converter preços válidos")
    void shouldParseValidPrices(
            String text,
            String expected) {
        BigDecimal result = PriceParser.parse(text);

        assertThat(result)
                .isEqualByComparingTo(expected);
    }

    static Stream<Arguments> validPrices() {
        return Stream.of(
                Arguments.of(
                        "R$ 1.299,90",
                        "1299.90"),
                Arguments.of(
                        "R$ 89,99",
                        "89.99"),
                Arguments.of(
                        "1999,90",
                        "1999.90"),
                Arguments.of(
                        "1.999,00",
                        "1999.00"),
                Arguments.of(
                        "1,999.90",
                        "1999.90"),
                Arguments.of(
                        "R$ 10",
                        "10"),
                Arguments.of(
                        "R$ 1.299",
                        "1299"),
                Arguments.of(
                        "1.999.999",
                        "1999999"),
                Arguments.of(
                        "  R$ 49,90  ",
                        "49.90"),
                Arguments.of(
                        "R$ 0,01",
                        "0.01"),
                Arguments.of(
                        "R$ 1\u00A0299,90",
                        "1299.90"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "   ",
            "\t",
            "\n"
    })
    @DisplayName("deve rejeitar preço vazio")
    void shouldRejectBlankPrices(String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> PriceParser.parse(text))
                .withMessage("Preço vazio");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "Preço indisponível",
            "Produto sem estoque",
            "R$ --",
            "Não informado"
    })
    @DisplayName("deve rejeitar texto sem números")
    void shouldRejectTextWithoutNumbers(String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> PriceParser.parse(text))
                .withMessageContaining(
                        "Nenhum número encontrado");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "R$ 0",
            "0,00",
            "R$ 0,00",
            "0.00"
    })
    @DisplayName("deve rejeitar preço igual a zero")
    void shouldRejectZeroPrice(String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> PriceParser.parse(text))
                .withMessageContaining(
                        "O preço precisa ser maior que zero");
    }

    @Test
    @DisplayName("deve preservar causa de número malformado")
    void shouldPreserveMalformedNumberCause() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> PriceParser.parse(
                                "R$ 1,2,3"))
                .withMessageContaining(
                        "Preço inválido")
                .withCauseInstanceOf(
                        NumberFormatException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "-10",
            "-10,00",
            "R$ -89,90"
    })
    @DisplayName("deve rejeitar preços negativos")
    void shouldRejectNegativePrices(String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> PriceParser.parse(text))
                .withMessageContaining(
                        "O preço precisa ser maior que zero");
    }
}