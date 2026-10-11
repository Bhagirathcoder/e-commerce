package com.bytebazaar.exception;
import org.springframework.http.HttpStatus;
public class BlockedAccountException extends ApiException {
    public BlockedAccountException(String message) { super(HttpStatus.FORBIDDEN, message); }
}
