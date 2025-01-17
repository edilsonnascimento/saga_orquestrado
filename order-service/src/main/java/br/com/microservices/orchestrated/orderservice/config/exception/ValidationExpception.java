package br.com.microservices.orchestrated.orderservice.config.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationExpception extends RuntimeException{

    public ValidationExpception(String message) {
        super(message);
    }
}