package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class PaymentException extends ApiException {
    public PaymentException(String message) { super(HttpStatus.BAD_REQUEST, message); }
}
