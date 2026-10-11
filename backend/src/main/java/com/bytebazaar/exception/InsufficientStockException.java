package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class InsufficientStockException extends ApiException {
    public InsufficientStockException(String product, int available) {
        super(HttpStatus.CONFLICT, "Only " + available + " unit(s) of \"" + product + "\" left in stock");
    }
}
