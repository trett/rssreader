package ru.trett.rss.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import ru.trett.rss.core.UserService;
import ru.trett.rss.models.User;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class OAuthLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OAuthLoginSuccessHandler.class);

    @Autowired UserService userService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        CustomOAuth2User oauth2User = (CustomOAuth2User) authentication.getPrincipal();
        String sub = oauth2User.getName();
        LOG.info("Loading user with sub: " + sub);
        LOG.info(oauth2User.getName());
        var user = userService.getUser(sub);
        if (!user.isPresent()) {
            LOG.info("Saving user with sub: " + sub);
            userService.save(new User(sub, oauth2User.getEmail()));
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
