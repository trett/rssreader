package ru.trett.rss.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class LoginController {

    @GetMapping(value = "/login")
    @ResponseBody
    public ResponseEntity login() {
        return new ResponseEntity(HttpStatus.MOVED_PERMANENTLY);
    }
}
