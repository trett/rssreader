package ru.trett.rss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenAuthenticationFilter.class);

    private final RestTemplate restTemplate;
    private final String authUrl;

    @Autowired
    public TokenAuthenticationFilter(
            @NonNull RestTemplate restTemplate, @Value("${auth.url}") String authUrl) {
        this.restTemplate = restTemplate;
        this.authUrl = authUrl;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HttpHeaders.AUTHORIZATION);
        try {
            UriComponentsBuilder builder =
                    UriComponentsBuilder.fromUriString(authUrl).queryParam("access_token", token);
            String uriString = builder.toUriString();
            LOGGER.info(uriString);
            ResponseEntity<UserInfo> userInfo =
                    restTemplate.getForEntity(uriString, UserInfo.class);
            Authentication authentication =
                    new CustomAuthenticationToken(userInfo.getBody().sub, userInfo.getBody().email);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (HttpClientErrorException e) {
            LOGGER.warn(e.getMessage());
        }
        chain.doFilter(request, response);
    }

    @NotNull
    private static class UserInfo {

        @NotNull public String sub;

        @NotNull public String email;
    }
}
