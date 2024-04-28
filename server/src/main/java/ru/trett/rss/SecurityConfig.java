package ru.trett.rss;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import ru.trett.rss.auth.CustomOAuth2UserService;
import ru.trett.rss.auth.OAuthLoginSuccessHandler;

@Configuration
public class SecurityConfig {

    @Autowired private CustomOAuth2UserService oauth2UserService;
    @Autowired private OAuthLoginSuccessHandler oauthLoginSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(
                        authorizer ->
                                authorizer
                                        .requestMatchers("/login")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        handler ->
                                handler.accessDeniedHandler(
                                        (request, response, accessDeniedException) ->
                                                response.sendError(401)))
                .oauth2Login(
                        login ->
                                login.loginPage("/login")
                                        .userInfoEndpoint(
                                                endpoint -> endpoint.userService(oauth2UserService))
                                        .successHandler(oauthLoginSuccessHandler));
        return http.build();
    }
}
