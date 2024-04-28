package ru.trett.rss;

import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableAutoConfiguration
@EnableScheduling
public class RssApplication {

    private static final int ONE_MINUTE_IN_MILLIS = 60_000;

    public static void main(String[] args) {
        SpringApplication.run(RssApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        var factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(ONE_MINUTE_IN_MILLIS);
        factory.setReadTimeout(ONE_MINUTE_IN_MILLIS);
        factory.setConnectionRequestTimeout(ONE_MINUTE_IN_MILLIS);
        var restTemplate = new RestTemplate(factory);
        var httpClient =
                HttpClientBuilder.create()
                        .setUserAgent("RSS App/1.0")
                        .setRedirectStrategy(new LaxRedirectStrategy())
                        .build();
        factory.setHttpClient(httpClient);
        return restTemplate;
    }
}
