package org.fanta.corte.controllers;

import java.io.IOException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({InvalidFormatException.class, IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> handleBadRequest(Exception e) {
        LOGGER.error("Bad request: {}", e.getMessage(), e);
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIoException(IOException e) {
        LOGGER.error("I/O error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body("Errore interno del server. Riprova più tardi.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception e) {
        LOGGER.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body("Errore interno del server. Riprova più tardi.");
    }
}
