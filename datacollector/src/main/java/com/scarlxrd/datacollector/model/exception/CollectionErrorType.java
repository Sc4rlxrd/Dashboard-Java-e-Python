package com.scarlxrd.datacollector.model.exception;

public enum CollectionErrorType {
    NETWORK_ERROR,
    TIMEOUT,
    PRODUCT_NOT_FOUND,
    PRODUCT_UNAVAILABLE,
    PRICE_NOT_FOUND,
    SELECTOR_CHANGED,
    ANTI_BOT_BLOCKED,
    RATE_LIMITED,
    INVALID_URL,
    UNKNOWN
}