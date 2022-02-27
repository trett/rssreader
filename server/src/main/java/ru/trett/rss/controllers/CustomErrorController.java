package ru.trett.rss.controllers;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError() {
        return "error.html";
    }

    @Override
    public String getErrorPath() {
        return "/error";
    }
}