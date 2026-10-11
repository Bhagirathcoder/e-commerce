package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class ReturnExpiredException extends ApiException {
    public ReturnExpiredException(String message) { super(HttpStatus.BAD_REQUEST, message); }
}
