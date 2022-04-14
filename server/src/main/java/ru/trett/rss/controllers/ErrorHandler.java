package ru.trett.rss.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import ru.trett.rss.core.ClientException;

@ControllerAdvice
public class ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ClientException.class);

    @ExceptionHandler(ClientException.class)
    protected ResponseEntity<Object> handleConflict(ClientException e, WebRequest request) {
        LOG.error("Error", e);
        return new ResponseEntity<>(e.getMessage(), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleConflict(Exception e, WebRequest request) {
        LOG.error("Error", e);
        return new ResponseEntity<>(
                "Internal server error", new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
