package com.scarlxrd.datacollector.model.service.scraper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Store {

    AMAZON("Amazon"),
    MERCADO_LIVRE("Mercado Livre"),
    BOA_DICA("BoaDica"),
    SHOPEE("Shopee");

    private final String displayName;
}