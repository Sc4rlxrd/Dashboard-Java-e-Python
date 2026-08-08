package com.scarlxrd.datacollector.model.exception;

import com.scarlxrd.datacollector.model.service.scraper.Store;

import java.util.Objects;

public class CollectionException extends RuntimeException {

    private final CollectionErrorType errorType;
    private final Store store;
    private final String url;

    public CollectionException(CollectionErrorType errorType,Store store,String url,String message) {
        this(
                errorType,
                store,
                url,
                message,
                null
        );
    }

    public CollectionException(CollectionErrorType errorType, Store store,String url,String message,Throwable cause) {
        super(message, cause);

        this.errorType = Objects.requireNonNull(
                errorType,
                "errorType não pode ser nulo"
        );

        this.store = store;
        this.url = url;
    }

    public CollectionErrorType getErrorType() {
        return errorType;
    }

    public Store getStore() {
        return store;
    }

    public String getUrl() {
        return url;
    }

    public String getStoreName() {
        if (store == null) {
            return "UNKNOWN";
        }

        return store.getDisplayName();
    }
    
}
