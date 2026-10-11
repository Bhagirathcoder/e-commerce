package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class ProductNotFoundException extends ApiException {
    public ProductNotFoundException(Object id) { super(HttpStatus.NOT_FOUND, "Product not found: " + id); }
}
