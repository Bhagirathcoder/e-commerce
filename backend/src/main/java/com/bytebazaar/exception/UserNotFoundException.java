package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class UserNotFoundException extends ApiException {
    public UserNotFoundException(String message) { super(HttpStatus.NOT_FOUND, message); }
}
