package ru.trett.rss;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private final String corsUrl;

    @Autowired
    public WebConfiguration(@Value("${cors.url}") String corsUrl) {
        this.corsUrl = corsUrl;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(corsUrl);
    }
}
