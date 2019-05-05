package com.trett.rss.controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleConflict(Exception e, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("error-message", e.getMessage());
        return new ResponseEntity<>(e, headers, HttpStatus.BAD_REQUEST);
    }
}
