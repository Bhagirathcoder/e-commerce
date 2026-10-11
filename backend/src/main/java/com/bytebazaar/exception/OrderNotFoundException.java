package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class OrderNotFoundException extends ApiException {
    public OrderNotFoundException(String message) { super(HttpStatus.NOT_FOUND, message); }
}
