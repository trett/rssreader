package ru.trett.rss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import ru.trett.rss.core.UserService;
import ru.trett.rss.models.Settings;
import ru.trett.rss.models.User;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class UserFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(UserFilter.class);
    private static final Map<String, User> userCache = new ConcurrentHashMap<>();
    private final UserService userService;

    @Autowired
    public UserFilter(UserService userRepository) {
        this.userService = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CustomAuthenticationToken authentication =
                (CustomAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            String name = (String) authentication.getPrincipal();
            LOG.info("Loading user with sub: " + name + " from cache");
            User cachedUser = userCache.get(name);
            if (cachedUser == null) {
                LOG.info("Loading user with sub: " + name);
                userService
                        .getUser(name)
                        .ifPresentOrElse(
                                u -> userCache.put(name, u),
                                () ->
                                        userCache.put(
                                                name, saveUser(name, authentication.getEmail())));
            }
        }
        chain.doFilter(request, response);
    }

    private User saveUser(String name, String email) {
        LOG.info("Creating user with sub: " + name);
        User user = new User(name, email);
        user.setSettings(new Settings());
        userService.save(user);
        LOG.info("User with sub: " + name + " has been saved");
        return user;
    }
}
