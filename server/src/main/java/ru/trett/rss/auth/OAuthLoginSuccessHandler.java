package ru.trett.rss.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import ru.trett.rss.core.UserService;
import ru.trett.rss.models.User;

import java.io.IOException;

@Component
public class OAuthLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

    @Autowired UserService userService;


    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        CustomOAuth2User oauth2User = (CustomOAuth2User) authentication.getPrincipal();
        String sub = oauth2User.getName();
        LOG.info("Loading user with sub: " + sub);
        var user = userService.getUser(sub);
        if (!user.isPresent()) {
            LOG.info("Saving user with sub: " + sub);
            userService.save(new User(sub, oauth2User.getEmail()));
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
