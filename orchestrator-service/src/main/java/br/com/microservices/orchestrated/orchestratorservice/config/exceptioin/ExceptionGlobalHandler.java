package br.com.microservices.orchestrated.orchestratorservice.config.exceptioin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ExceptionGlobalHandler {

    @ExceptionHandler(ValidationExpception.class)
    public ResponseEntity<?> handleValidationException(ValidationExpception validationExpception) {
        var details = new ExceptionDetails(HttpStatus.BAD_REQUEST.value(), validationExpception.getMessage());
        return new ResponseEntity<>(details, HttpStatus.BAD_REQUEST);
    }
}