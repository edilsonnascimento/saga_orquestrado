package br.com.microservices.orchestrated.orchestratorservice.config.exceptioin;

public record ExceptionDetails(
        int status,
        String message) {
}