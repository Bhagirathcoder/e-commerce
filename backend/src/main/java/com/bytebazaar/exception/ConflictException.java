package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class ConflictException extends ApiException {
    public ConflictException(String message) { super(HttpStatus.CONFLICT, message); }
}
