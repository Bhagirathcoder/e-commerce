package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class SellerNotFoundException extends ApiException {
    public SellerNotFoundException(String message) { super(HttpStatus.NOT_FOUND, message); }
}
