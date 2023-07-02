package ru.trett.rss;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import ru.trett.rss.auth.CustomOAuth2UserService;
import ru.trett.rss.auth.OAuthLoginSuccessHandler;

@Configuration
public class SecurityConfig {

    @Autowired private CustomOAuth2UserService oauth2UserService;

    @Autowired private OAuthLoginSuccessHandler oauthLoginSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf()
                .disable()
                .cors()
                .and()
                .authorizeRequests()
                .antMatchers("/login")
                .permitAll()
                .anyRequest()
                .authenticated()
                .and()
                .oauth2Login()
                .loginPage("/login")
                .userInfoEndpoint()
                .userService(oauth2UserService)
                .and()
                .successHandler(oauthLoginSuccessHandler)
                .and()
                .exceptionHandling()
                .accessDeniedPage("/error");
        return http.build();
    }
}
